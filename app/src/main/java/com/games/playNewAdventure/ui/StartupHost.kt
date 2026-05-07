package com.games.playNewAdventure.ui

import android.app.Activity
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.games.playNewAdventure.BuildConfig
import com.games.playNewAdventure.startup.AppStartupController
import com.games.playNewAdventure.startup.domain.StartupResult
import kotlinx.coroutines.launch

@Composable
fun StartupHost(
    activity: Activity,
    startupController: AppStartupController,
    onPushPermissionAccepted: (onComplete: () -> Unit) -> Unit,
    initialWebViewUrl: String? = null,
    onShowWebFileChooser: (
        ValueCallback<Array<Uri>>,
        WebChromeClient.FileChooserParams
    ) -> Boolean = { _, _ -> false },
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var screen by rememberSaveable(stateSaver = StartupScreenSaver) { mutableStateOf<StartupScreen>(StartupScreen.Loading) }
    var currentWebViewUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var startupFlowExecuted by rememberSaveable { mutableStateOf(false) }
    var debugSnapshot by remember { mutableStateOf(startupController.debugSnapshot()) }

    suspend fun restartFlow() {
        currentWebViewUrl = null
        screen = StartupScreen.Loading
        screen = startupController.resolveStartup().toScreen()
        debugSnapshot = startupController.debugSnapshot()
        startupFlowExecuted = true
    }

    LaunchedEffect(startupController, initialWebViewUrl) {
        logDebug("StartupHost LaunchedEffect: initialWebViewUrl=$initialWebViewUrl, startupFlowExecuted=$startupFlowExecuted")
        if (initialWebViewUrl.isNullOrBlank()) {
            if (!startupFlowExecuted) {
                restartFlow()
            }
        } else {
            currentWebViewUrl = initialWebViewUrl
            screen = StartupScreen.WebView(initialWebViewUrl)
            debugSnapshot = startupController.debugSnapshot()
            startupFlowExecuted = true
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val current = screen) {
            StartupScreen.Loading ->
                LoadingScreen()

            StartupScreen.NoInternet ->
                NoInternetScreen(
                    onRetryClick = { scope.launch { restartFlow() } }
                )

            StartupScreen.Fantic ->
                FanticScreen()

            is StartupScreen.PushPermission ->
                PushPermissionScreen(
                    onAllowClick = {
                        onPushPermissionAccepted {
                            debugSnapshot = startupController.debugSnapshot()
                            screen = StartupScreen.WebView(current.url)
                        }
                    },
                    onSkipClick = {
                        startupController.declinePushPrompt()
                        debugSnapshot = startupController.debugSnapshot()
                        screen = StartupScreen.WebView(current.url)
                    }
                )

            is StartupScreen.WebView ->
                WebViewScreen(
                    url = currentWebViewUrl ?: current.url,
                    activity = activity,
                    onShowFileChooser = onShowWebFileChooser,
                    onCurrentUrlChanged = { 
                        logDebug("StartupHost currentWebViewUrl updated: $it")
                        currentWebViewUrl = it 
                    }
                )
        }

        if (BuildConfig.DEBUG) {
            DebugPanel(
                snapshot = debugSnapshot,
                currentWebViewUrl = currentWebViewUrl,
                onRefreshDiagnostics = {
                    scope.launch {
                        startupController.refreshDebugDiagnostics()
                        debugSnapshot = startupController.debugSnapshot()
                    }
                },
                onScenarioSelected = {
                    startupController.setMockConfigScenario(it)
                    debugSnapshot = startupController.debugSnapshot()
                },
                onConfigProviderModeSelected = {
                    startupController.setConfigProviderMode(it)
                    debugSnapshot = startupController.debugSnapshot()
                },
                onAttributionProviderModeSelected = {
                    startupController.setAttributionProviderMode(it)
                    scope.launch {
                        startupController.refreshDebugDiagnostics()
                        debugSnapshot = startupController.debugSnapshot()
                    }
                },
                onPushTokenProviderModeSelected = {
                    startupController.setPushTokenProviderMode(it)
                    scope.launch {
                        startupController.refreshDebugDiagnostics()
                        debugSnapshot = startupController.debugSnapshot()
                    }
                },
                onResetStateClick = {
                    startupController.resetLocalState()
                    scope.launch { restartFlow() }
                },
                onClearLastUrlClick = {
                    startupController.clearLastWebViewUrl()
                    debugSnapshot = startupController.debugSnapshot()
                },
                onRestartFlowClick = { scope.launch { restartFlow() } },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
            )
        }
    }
}

private sealed class StartupScreen {
    data object Loading : StartupScreen()
    data object NoInternet : StartupScreen()
    data object Fantic : StartupScreen()
    data class PushPermission(val url: String) : StartupScreen()
    data class WebView(val url: String) : StartupScreen()
}

private fun StartupResult.toScreen(): StartupScreen = when (this) {
    StartupResult.ShowFantic    -> StartupScreen.Fantic
    StartupResult.ShowNoInternet -> StartupScreen.NoInternet
    is StartupResult.ShowWebView -> if (shouldAskPushPermission) {
        StartupScreen.PushPermission(url)
    } else {
        StartupScreen.WebView(url)
    }
}

private val StartupScreenSaver = Saver<StartupScreen, android.os.Bundle>(
    save = { screen ->
        android.os.Bundle().apply {
            when (screen) {
                is StartupScreen.Loading -> putString("type", "Loading")
                is StartupScreen.NoInternet -> putString("type", "NoInternet")
                is StartupScreen.Fantic -> putString("type", "Fantic")
                is StartupScreen.PushPermission -> {
                    putString("type", "PushPermission")
                    putString("url", screen.url)
                }
                is StartupScreen.WebView -> {
                    putString("type", "WebView")
                    putString("url", screen.url)
                }
            }
        }
    },
    restore = { bundle ->
        when (bundle.getString("type")) {
            "Loading" -> StartupScreen.Loading
            "NoInternet" -> StartupScreen.NoInternet
            "Fantic" -> StartupScreen.Fantic
            "PushPermission" -> StartupScreen.PushPermission(bundle.getString("url") ?: "")
            "WebView" -> StartupScreen.WebView(bundle.getString("url") ?: "")
            else -> StartupScreen.Loading
        }
    }
)

private fun logDebug(message: String) {
    if (BuildConfig.DEBUG) {
        android.util.Log.d("TrafficRushStartupHost", message)
    }
}

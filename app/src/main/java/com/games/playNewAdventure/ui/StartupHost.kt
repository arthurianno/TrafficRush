package com.games.playNewAdventure.ui

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.games.playNewAdventure.BuildConfig
import com.games.playNewAdventure.startup.AppStartupController
import com.games.playNewAdventure.startup.StartupResult
import kotlinx.coroutines.launch

@Composable
fun StartupHost(
    activity: Activity,
    startupController: AppStartupController,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf<StartupScreen>(StartupScreen.Loading) }
    var currentWebViewUrl by remember { mutableStateOf<String?>(null) }
    var debugSnapshot by remember { mutableStateOf(startupController.debugSnapshot()) }

    suspend fun restartFlow() {
        currentWebViewUrl = null
        screen = StartupScreen.Loading
        screen = startupController.resolveStartup().toScreen()
        debugSnapshot = startupController.debugSnapshot()
    }

    LaunchedEffect(startupController) {
        restartFlow()
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
                        scope.launch {
                            startupController.requestPushPermission(activity)
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
                    url = current.url,
                    activity = activity,
                    onCurrentUrlChanged = { currentWebViewUrl = it }
                )
        }

        if (BuildConfig.DEBUG) {
            DebugPanel(
                snapshot = debugSnapshot,
                currentWebViewUrl = currentWebViewUrl,
                onScenarioSelected = {
                    startupController.setMockConfigScenario(it)
                    debugSnapshot = startupController.debugSnapshot()
                },
                onConfigProviderModeSelected = {
                    startupController.setConfigProviderMode(it)
                    debugSnapshot = startupController.debugSnapshot()
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
                    .align(Alignment.TopEnd)
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

package com.games.playNewAdventure.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.games.playNewAdventure.BuildConfig
import com.games.playNewAdventure.startup.AppStartupController
import com.games.playNewAdventure.startup.ConfigProviderMode
import com.games.playNewAdventure.startup.MockConfigScenario
import com.games.playNewAdventure.startup.StartupDebugSnapshot
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
    var showClickZones by remember { mutableStateOf(false) }
    var debugSnapshot by remember { mutableStateOf(startupController.debugSnapshot()) }

    suspend fun restartStartupFlow() {
        currentWebViewUrl = null
        screen = StartupScreen.Loading
        screen = startupController.resolveStartup().toScreen()
        debugSnapshot = startupController.debugSnapshot()
    }

    LaunchedEffect(startupController) {
        restartStartupFlow()
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (val currentScreen = screen) {
            StartupScreen.Loading -> LoadingScreen()

            StartupScreen.NoInternet -> NoInternetScreen(
                onRetryClick = {
                    scope.launch {
                        restartStartupFlow()
                    }
                }
            )

            StartupScreen.Fantic -> FanticScreen(
                showClickZones = BuildConfig.DEBUG && showClickZones
            )

            is StartupScreen.PushPermission -> PushPermissionScreen(
                onAllowClick = {
                    scope.launch {
                        startupController.requestPushPermission(activity)
                        debugSnapshot = startupController.debugSnapshot()
                        screen = StartupScreen.WebView(currentScreen.url)
                    }
                },
                onSkipClick = {
                    startupController.declinePushPrompt()
                    debugSnapshot = startupController.debugSnapshot()
                    screen = StartupScreen.WebView(currentScreen.url)
                },
                showClickZones = BuildConfig.DEBUG && showClickZones
            )

            is StartupScreen.WebView -> WebViewScreen(
                url = currentScreen.url,
                activity = activity,
                onCurrentUrlChanged = { currentWebViewUrl = it }
            )
        }

        if (BuildConfig.DEBUG) {
            DebugPanel(
                snapshot = debugSnapshot,
                currentWebViewUrl = currentWebViewUrl,
                onScenarioSelected = { scenario ->
                    startupController.setMockConfigScenario(scenario)
                    debugSnapshot = startupController.debugSnapshot()
                },
                onConfigProviderModeSelected = { mode ->
                    startupController.setConfigProviderMode(mode)
                    debugSnapshot = startupController.debugSnapshot()
                },
                onResetStateClick = {
                    startupController.resetLocalState()
                    scope.launch {
                        restartStartupFlow()
                    }
                },
                onClearLastUrlClick = {
                    startupController.clearLastWebViewUrl()
                    debugSnapshot = startupController.debugSnapshot()
                },
                onRestartFlowClick = {
                    scope.launch {
                        restartStartupFlow()
                    }
                },
                showClickZones = showClickZones,
                onShowClickZonesChanged = { showClickZones = it },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun DebugPanel(
    snapshot: StartupDebugSnapshot,
    currentWebViewUrl: String?,
    onScenarioSelected: (MockConfigScenario) -> Unit,
    onConfigProviderModeSelected: (ConfigProviderMode) -> Unit,
    onResetStateClick: () -> Unit,
    onClearLastUrlClick: () -> Unit,
    onRestartFlowClick: () -> Unit,
    showClickZones: Boolean,
    onShowClickZonesChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var scenarioMenuExpanded by remember { mutableStateOf(false) }
    var configModeMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(16.dp)
            .widthIn(max = 360.dp),
        horizontalAlignment = Alignment.End
    ) {
        OutlinedButton(onClick = { expanded = !expanded }) {
            Text(text = "Debug")
        }

        if (expanded) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .heightIn(max = 560.dp)
                    .testTag(UiTestTags.DEBUG_PANEL),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onResetStateClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(UiTestTags.DEBUG_RESET_STATE)
                        ) {
                            Text(text = "Reset state")
                        }
                        OutlinedButton(
                            onClick = onRestartFlowClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(UiTestTags.DEBUG_RESTART_FLOW)
                        ) {
                            Text(text = "Restart flow")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box {
                        OutlinedButton(
                            onClick = { configModeMenuExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(UiTestTags.DEBUG_CONFIG_SOURCE)
                        ) {
                            Text(text = "Config source: ${snapshot.configProviderMode.name}")
                        }
                        DropdownMenu(
                            expanded = configModeMenuExpanded,
                            onDismissRequest = { configModeMenuExpanded = false }
                        ) {
                            ConfigProviderMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(text = mode.name) },
                                    onClick = {
                                        configModeMenuExpanded = false
                                        onConfigProviderModeSelected(mode)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box {
                        OutlinedButton(
                            onClick = { scenarioMenuExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(UiTestTags.DEBUG_MOCK_SCENARIO)
                        ) {
                            Text(text = "Mock scenario: ${snapshot.mockConfigScenario.name}")
                        }
                        DropdownMenu(
                            expanded = scenarioMenuExpanded,
                            onDismissRequest = { scenarioMenuExpanded = false }
                        ) {
                            MockConfigScenario.entries.forEach { scenario ->
                                DropdownMenuItem(
                                    text = { Text(text = scenario.name) },
                                    onClick = {
                                        scenarioMenuExpanded = false
                                        onScenarioSelected(scenario)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onClearLastUrlClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Clear last URL")
                        }
                        OutlinedButton(
                            onClick = { onShowClickZonesChanged(!showClickZones) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = if (showClickZones) "Zones ON" else "Zones OFF")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Status",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "Mode: ${snapshot.appMode}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Push granted: ${snapshot.pushPermissionGranted}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Declined at: ${snapshot.pushPromptDeclinedAtSeconds}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    CompactUrlRow(
                        label = "Last URL",
                        url = snapshot.lastWebViewUrl
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CompactUrlRow(
                        label = "Current URL",
                        url = currentWebViewUrl
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactUrlRow(
    label: String,
    url: String?
) {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    var expanded by remember { mutableStateOf(false) }
    val displayUrl = url?.takeIf { it.isNotBlank() } ?: "-"

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = displayUrl,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TextButton(
                onClick = {
                    if (!url.isNullOrBlank()) {
                        clipboard.setPrimaryClip(ClipData.newPlainText(label, url))
                    }
                },
                enabled = !url.isNullOrBlank()
            ) {
                Text(text = "Copy")
            }
        }

        if (!url.isNullOrBlank()) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(text = if (expanded) "Collapse" else "Expand")
            }
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

private fun StartupResult.toScreen(): StartupScreen {
    return when (this) {
        StartupResult.ShowFantic -> StartupScreen.Fantic
        StartupResult.ShowNoInternet -> StartupScreen.NoInternet
        is StartupResult.ShowWebView -> {
            if (shouldAskPushPermission) {
                StartupScreen.PushPermission(url)
            } else {
                StartupScreen.WebView(url)
            }
        }
    }
}

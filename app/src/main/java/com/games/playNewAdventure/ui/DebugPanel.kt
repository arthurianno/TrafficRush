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
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.games.playNewAdventure.startup.ConfigProviderMode
import com.games.playNewAdventure.startup.MockConfigScenario
import com.games.playNewAdventure.startup.StartupDebugSnapshot

private val PANEL_MAX_WIDTH  = 360.dp
private val PANEL_MAX_HEIGHT = 560.dp
private val PANEL_PADDING    = 16.dp
private val PANEL_GAP        = 8.dp
private val PANEL_STATUS_GAP = 12.dp

@Composable
internal fun DebugPanel(
    snapshot: StartupDebugSnapshot,
    currentWebViewUrl: String?,
    onScenarioSelected: (MockConfigScenario) -> Unit,
    onConfigProviderModeSelected: (ConfigProviderMode) -> Unit,
    onResetStateClick: () -> Unit,
    onClearLastUrlClick: () -> Unit,
    onRestartFlowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var scenarioMenuExpanded by remember { mutableStateOf(false) }
    var configModeMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(PANEL_PADDING)
            .widthIn(max = PANEL_MAX_WIDTH),
        horizontalAlignment = Alignment.End
    ) {
        OutlinedButton(onClick = { expanded = !expanded }) {
            Text(text = "Debug")
        }

        if (expanded) {
            Surface(
                modifier = Modifier
                    .widthIn(max = PANEL_MAX_WIDTH)
                    .heightIn(max = PANEL_MAX_HEIGHT)
                    .testTag(UiTestTags.DEBUG_PANEL),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(PANEL_PADDING)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PANEL_GAP)
                    ) {
                        Button(
                            onClick = onResetStateClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(UiTestTags.DEBUG_RESET_STATE)
                        ) { Text(text = "Reset state") }
                        OutlinedButton(
                            onClick = onRestartFlowClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(UiTestTags.DEBUG_RESTART_FLOW)
                        ) { Text(text = "Restart flow") }
                    }

                    Spacer(modifier = Modifier.height(PANEL_GAP))

                    Box {
                        OutlinedButton(
                            onClick = { configModeMenuExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(UiTestTags.DEBUG_CONFIG_SOURCE)
                        ) { Text(text = "Config: ${snapshot.configProviderMode.name}") }
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

                    Spacer(modifier = Modifier.height(PANEL_GAP))

                    Box {
                        OutlinedButton(
                            onClick = { scenarioMenuExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(UiTestTags.DEBUG_MOCK_SCENARIO)
                        ) { Text(text = "Scenario: ${snapshot.mockConfigScenario.name}") }
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

                    Spacer(modifier = Modifier.height(PANEL_GAP))

                    OutlinedButton(
                        onClick = onClearLastUrlClick,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(text = "Clear last URL") }

                    Spacer(modifier = Modifier.height(PANEL_STATUS_GAP))

                    Text(text = "Status", style = MaterialTheme.typography.labelLarge)
                    Text(text = "Mode: ${snapshot.appMode}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Push granted: ${snapshot.pushPermissionGranted}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Declined at: ${snapshot.pushPromptDeclinedAtSeconds}", style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.height(PANEL_GAP))

                    CompactUrlRow(label = "Last URL", url = snapshot.lastWebViewUrl)
                    Spacer(modifier = Modifier.height(PANEL_GAP))
                    CompactUrlRow(label = "Current URL", url = currentWebViewUrl)
                }
            }
        }
    }
}

@Composable
private fun CompactUrlRow(label: String, url: String?) {
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    var urlExpanded by remember { mutableStateOf(false) }
    val displayUrl = url?.takeIf { it.isNotBlank() } ?: "-"

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PANEL_GAP),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.labelSmall)
                Text(
                    text = displayUrl,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (urlExpanded) Int.MAX_VALUE else 2,
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
            ) { Text(text = "Copy") }
        }
        if (!url.isNullOrBlank()) {
            TextButton(onClick = { urlExpanded = !urlExpanded }) {
                Text(text = if (urlExpanded) "Collapse" else "Expand")
            }
        }
    }
}

package com.games.playNewAdventure.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.LaunchedEffect
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
import com.games.playNewAdventure.AppConstants
import com.games.playNewAdventure.BuildConfig
import com.games.playNewAdventure.startup.domain.AttributionProviderMode
import com.games.playNewAdventure.startup.domain.ConfigDebugResultType
import com.games.playNewAdventure.startup.domain.ConfigProviderMode
import com.games.playNewAdventure.startup.domain.MockConfigScenario
import com.games.playNewAdventure.startup.domain.PushTokenProviderMode
import com.games.playNewAdventure.startup.domain.StartupDebugSnapshot

private val PANEL_MAX_WIDTH  = 360.dp
private val PANEL_MAX_HEIGHT = 560.dp
private val PANEL_PADDING    = 16.dp
private val PANEL_GAP        = 8.dp
private val PANEL_STATUS_GAP = 12.dp

@Composable
internal fun DebugPanel(
    snapshot: StartupDebugSnapshot,
    currentWebViewUrl: String?,
    onRefreshDiagnostics: () -> Unit,
    onScenarioSelected: (MockConfigScenario) -> Unit,
    onConfigProviderModeSelected: (ConfigProviderMode) -> Unit,
    onAttributionProviderModeSelected: (AttributionProviderMode) -> Unit,
    onPushTokenProviderModeSelected: (PushTokenProviderMode) -> Unit,
    onResetStateClick: () -> Unit,
    onClearLastUrlClick: () -> Unit,
    onRestartFlowClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var scenarioMenuExpanded by remember { mutableStateOf(false) }
    var configModeMenuExpanded by remember { mutableStateOf(false) }
    var attributionModeMenuExpanded by remember { mutableStateOf(false) }
    var pushModeMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = remember(context) {
        context.getSystemService(ClipboardManager::class.java)
    }
    val lastConfigResponse = snapshot.lastConfigResponse
    val notificationGranted = rememberNotificationPermissionGranted()
    val appsFlyerUid = snapshot.appsFlyerUid?.takeIf { it.isNotBlank() }
    val appsFlyerUidValue = appsFlyerUid.orEmpty()
    val appsFlyerUidIsMock = appsFlyerUid?.startsWith(MOCK_PREFIX, ignoreCase = true) == true
    val appsFlyerUidReady = snapshot.attributionProviderMode == AttributionProviderMode.REAL &&
        appsFlyerUid != null &&
        !appsFlyerUidIsMock
    val configSourceReady = snapshot.configProviderMode == ConfigProviderMode.REAL
    val pushSourceReady = snapshot.pushTokenProviderMode == PushTokenProviderMode.REAL
    val pushTestNotReadyReasons = buildPushTestNotReadyReasons(
        configSourceReady = configSourceReady,
        attributionSourceReady = snapshot.attributionProviderMode == AttributionProviderMode.REAL,
        pushSourceReady = pushSourceReady,
        appsFlyerUid = appsFlyerUid,
        appsFlyerUidIsMock = appsFlyerUidIsMock,
        appsFlyerUidSentToConfig = snapshot.appsFlyerUidSentToConfig,
        fcmTokenAvailable = snapshot.fcmTokenAvailable,
        notificationGranted = notificationGranted,
        pushTokenSentToConfig = snapshot.pushTokenSentToConfig
    )
    val readyForPushTest = pushTestNotReadyReasons.isEmpty()

    LaunchedEffect(
        expanded
    ) {
        if (expanded) {
            onRefreshDiagnostics()
        }
    }

    LaunchedEffect(
        expanded,
        snapshot.configProviderMode,
        snapshot.attributionProviderMode,
        snapshot.pushTokenProviderMode,
        appsFlyerUid,
        snapshot.appsFlyerUidSentToConfig,
        snapshot.fcmTokenAvailable,
        snapshot.pushTokenSentToConfig,
        lastConfigResponse,
        snapshot.lastStartupDecision,
        snapshot.lastStartupDecisionReason
    ) {
        if (expanded && BuildConfig.DEBUG) {
            Log.d(DEBUG_PANEL_LOG_TAG, "AppsFlyer Dev Key configured ${AppConstants.APPSFLYER_DEV_KEY.isNotBlank()}")
            Log.d(DEBUG_PANEL_LOG_TAG, "AppsFlyer UID loaded: $appsFlyerUidValue")
            Log.d(DEBUG_PANEL_LOG_TAG, "config source ${snapshot.configProviderMode}")
            Log.d(DEBUG_PANEL_LOG_TAG, "attribution source ${snapshot.attributionProviderMode}")
            Log.d(DEBUG_PANEL_LOG_TAG, "FCM token available ${snapshot.fcmTokenAvailable}")
            Log.d(DEBUG_PANEL_LOG_TAG, "af_id sent to config ${snapshot.appsFlyerUidSentToConfig}")
            Log.d(DEBUG_PANEL_LOG_TAG, "push_token sent to config ${snapshot.pushTokenSentToConfig}")
            Log.d(
                DEBUG_PANEL_LOG_TAG,
                "last config: source=${lastConfigResponse?.source ?: "-"} " +
                    "http=${lastConfigResponse?.httpStatus ?: "-"} " +
                    "result=${lastConfigResponse?.resultType?.displayName ?: "-"} " +
                    "ok=${lastConfigResponse?.ok ?: "-"} " +
                    "url=${lastConfigResponse?.url ?: "-"} " +
                    "error=${lastConfigResponse?.errorMessage ?: "-"}"
            )
            Log.d(
                DEBUG_PANEL_LOG_TAG,
                "startup decision ${snapshot.lastStartupDecision ?: "-"}; " +
                    "reason=${snapshot.lastStartupDecisionReason ?: "-"}"
            )
        }
    }

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
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Debug", style = MaterialTheme.typography.labelLarge)
                        TextButton(onClick = { expanded = false }) {
                            Text(text = "Close")
                        }
                    }

                    Spacer(modifier = Modifier.height(PANEL_GAP))

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

                    Box {
                        OutlinedButton(
                            onClick = { attributionModeMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(text = "Attribution: ${snapshot.attributionProviderMode.name}") }
                        DropdownMenu(
                            expanded = attributionModeMenuExpanded,
                            onDismissRequest = { attributionModeMenuExpanded = false }
                        ) {
                            AttributionProviderMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(text = mode.name) },
                                    onClick = {
                                        attributionModeMenuExpanded = false
                                        onAttributionProviderModeSelected(mode)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(PANEL_GAP))

                    Box {
                        OutlinedButton(
                            onClick = { pushModeMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(text = "Push token: ${snapshot.pushTokenProviderMode.name}") }
                        DropdownMenu(
                            expanded = pushModeMenuExpanded,
                            onDismissRequest = { pushModeMenuExpanded = false }
                        ) {
                            PushTokenProviderMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(text = mode.name) },
                                    onClick = {
                                        pushModeMenuExpanded = false
                                        onPushTokenProviderModeSelected(mode)
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

                    Spacer(modifier = Modifier.height(PANEL_STATUS_GAP))

                    LastConfigResponseSection(
                        snapshot = snapshot
                    )

                    Spacer(modifier = Modifier.height(PANEL_STATUS_GAP))

                    Text(text = "Push / AppsFlyer", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(PANEL_GAP / 2))

                    Text(
                        text = "AppsFlyer Dev Key: configured ${AppConstants.APPSFLYER_DEV_KEY.isNotBlank()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "AppsFlyer UID / af_id: ${appsFlyerUid ?: "AppsFlyer UID not available yet"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (appsFlyerUidIsMock) {
                        Text(
                            text = "Real AppsFlyer UID required for push test. mock_af_id cannot receive real push.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (!configSourceReady) {
                        Text(
                            text = "Config MOCK does not send real af_id + push_token to server. Switch Config to REAL and restart flow before check_push.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (!appsFlyerUidReady) {
                        Text(
                            text = "Run app with REAL AppsFlyer service and wait until AppsFlyer UID is loaded.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(text = "Config source: ${snapshot.configProviderMode}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Attribution source: ${snapshot.attributionProviderMode}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Push token source: ${snapshot.pushTokenProviderMode}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "FCM token: ${if (snapshot.fcmTokenAvailable) "available" else "not available"}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Firebase project: ${snapshot.firebaseProjectId ?: "-"}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Notification permission: $notificationGranted", style = MaterialTheme.typography.bodySmall)
                    Text(text = "af_id sent to config: ${snapshot.appsFlyerUidSentToConfig}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Push token sent to config: ${snapshot.pushTokenSentToConfig}", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Ready for push test: $readyForPushTest", style = MaterialTheme.typography.bodySmall)
                    pushTestNotReadyReasons.forEach { reason ->
                        Text(
                            text = "Not ready: $reason",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    if (readyForPushTest) {
                        Spacer(modifier = Modifier.height(PANEL_GAP / 2))
                        Text(
                            text = "Client is ready. If check_push sends nothing, server/Firebase sender side must be checked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(PANEL_GAP))

                    LastAppsFlyerParamsSection(snapshot = snapshot)
                    
                    Spacer(modifier = Modifier.height(PANEL_GAP))
                    
                    DeepLinkParamsSection(snapshot = snapshot)

                    Spacer(modifier = Modifier.height(PANEL_GAP))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PANEL_GAP)
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (appsFlyerUidReady) {
                                    clipboard.setPrimaryClip(ClipData.newPlainText("AppsFlyer UID / af_id", appsFlyerUidValue))
                                    logDebug("AppsFlyer UID / af_id copied")
                                }
                            },
                            enabled = appsFlyerUidReady,
                            modifier = Modifier.weight(1f)
                        ) { Text(text = "Copy af_id") }

                        OutlinedButton(
                            onClick = {
                                if (readyForPushTest) {
                                    val pushTestUrl = "$PUSH_TEST_URL_PREFIX$appsFlyerUidValue"
                                    clipboard.setPrimaryClip(ClipData.newPlainText("push_test_url", pushTestUrl))
                                    logDebug("copied push test URL: $pushTestUrl")
                                }
                            },
                            enabled = readyForPushTest,
                            modifier = Modifier.weight(1f)
                        ) { Text(text = "Copy push URL") }
                    }

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
private fun LastConfigResponseSection(snapshot: StartupDebugSnapshot) {
    val config = snapshot.lastConfigResponse
    Text(text = "Last Config Response", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(PANEL_GAP / 2))

    if (config == null) {
        Text(text = "No config request yet", style = MaterialTheme.typography.bodySmall)
        Text(
            text = "Startup decision: ${snapshot.lastStartupDecision ?: "-"}",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text = "Decision reason: ${snapshot.lastStartupDecisionReason ?: "-"}",
            style = MaterialTheme.typography.bodySmall
        )
        return
    }

    Text(text = "Source: ${config.source}", style = MaterialTheme.typography.bodySmall)
    Text(text = "HTTP status: ${config.httpStatus?.toString() ?: "-"}", style = MaterialTheme.typography.bodySmall)
    Text(text = "Result: ${config.resultType?.displayName ?: "-"}", style = MaterialTheme.typography.bodySmall)
    Text(text = "ok: ${config.ok?.toString() ?: "-"}", style = MaterialTheme.typography.bodySmall)
    Text(text = "url: ${config.url ?: "-"}", style = MaterialTheme.typography.bodySmall)
    Text(text = "error: ${config.errorMessage ?: "-"}", style = MaterialTheme.typography.bodySmall)
    Text(text = "request af_id: ${config.requestContainedAfId}", style = MaterialTheme.typography.bodySmall)
    Text(text = "request push_token: ${config.requestContainedPushToken}", style = MaterialTheme.typography.bodySmall)
    Text(
        text = "request firebase_project_id: ${config.requestContainedFirebaseProjectId}",
        style = MaterialTheme.typography.bodySmall
    )
    Text(text = "request af_status: ${config.requestAfStatus ?: "-"}", style = MaterialTheme.typography.bodySmall)
    Text(text = "request deep_link_value: ${config.requestDeepLinkValue ?: "-"}", style = MaterialTheme.typography.bodySmall)
    Text(text = "Startup decision: ${snapshot.lastStartupDecision ?: "-"}", style = MaterialTheme.typography.bodySmall)
    Text(text = "Decision reason: ${snapshot.lastStartupDecisionReason ?: "-"}", style = MaterialTheme.typography.bodySmall)

    config.outcomeMessage()?.let { message ->
        Spacer(modifier = Modifier.height(PANEL_GAP / 2))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (config.resultType == ConfigDebugResultType.NEGATIVE) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }

    config.sanitizedResponseBody?.takeIf { it.isNotBlank() }?.let { body ->
        Spacer(modifier = Modifier.height(PANEL_GAP / 2))
        Text(text = "body: $body", style = MaterialTheme.typography.bodySmall)
    }
}

private fun com.games.playNewAdventure.startup.domain.ConfigDebugSnapshot.outcomeMessage(): String? {
    return when (resultType) {
        ConfigDebugResultType.SUCCESS -> if (!url.isNullOrBlank()) {
            "Server returned WebView URL."
        } else {
            null
        }
        ConfigDebugResultType.NEGATIVE ->
            "Server returned negative config. Fantic is expected."
        ConfigDebugResultType.TRANSIENT_ERROR ->
            "Config transient error. First launch shows NoInternet; stored WEBVIEW uses cached URL."
        null -> null
    }
}

@Composable
private fun LastAppsFlyerParamsSection(snapshot: StartupDebugSnapshot) {
    Text(text = "Last AppsFlyer Params", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(PANEL_GAP / 2))

    val data = snapshot.lastAttributionData
    if (data == null || data.isEmpty()) {
        Text(text = "No AppsFlyer data received yet", style = MaterialTheme.typography.bodySmall)
        return
    }

    val keysToDisplay = listOf(
        "af_status", "pid", "c", "deep_link_value", "deep_link_sub1", "is_retargeting",
        "af_sub1", "af_sub2", "af_sub3", "af_sub4", "af_sub5",
        "adset", "af_adset", "af_c_id", "agency", "siteid"
    )

    keysToDisplay.forEach { key ->
        Text(
            text = "$key: ${data[key]?.toString() ?: "-"}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun DeepLinkParamsSection(snapshot: StartupDebugSnapshot) {
    Text(text = "Deep Link Params", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(PANEL_GAP / 2))

    Text(text = "deep_link_value: ${snapshot.deepLinkValue ?: "MISSING"}", style = MaterialTheme.typography.bodySmall)
    Text(text = "deep_link_sub1: ${snapshot.deepLinkSub1 ?: "MISSING"}", style = MaterialTheme.typography.bodySmall)
    if (snapshot.deepLinkSub2 != null) {
        Text(text = "deep_link_sub2: ${snapshot.deepLinkSub2}", style = MaterialTheme.typography.bodySmall)
    }
    Text(text = "has required deeplink params: ${snapshot.hasRequiredDeeplinkParams}", style = MaterialTheme.typography.bodySmall)
    
    val source = snapshot.lastAttributionData?.get("af_status")?.toString()
    Text(text = "last deeplink source: ${source ?: "-"}", style = MaterialTheme.typography.bodySmall)
    Text(text = "last config contained deep_link_value: ${snapshot.lastConfigContainedDeepLinkValue}", style = MaterialTheme.typography.bodySmall)
    Text(text = "last config contained any deep_link_sub: ${snapshot.lastConfigContainedAnyDeepLinkSub}", style = MaterialTheme.typography.bodySmall)

    if (snapshot.attributionProviderMode == AttributionProviderMode.REAL && !snapshot.hasRequiredDeeplinkParams) {
        Spacer(modifier = Modifier.height(PANEL_GAP / 2))
        Text(
            text = "Missing required AppsFlyer deeplink params. Open app from AppsFlyer/OneLink URL containing deep_link_value and deep_link_sub1.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
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

@Composable
private fun rememberNotificationPermissionGranted(): Boolean {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        true
    } else {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun buildPushTestNotReadyReasons(
    configSourceReady: Boolean,
    attributionSourceReady: Boolean,
    pushSourceReady: Boolean,
    appsFlyerUid: String?,
    appsFlyerUidIsMock: Boolean,
    appsFlyerUidSentToConfig: Boolean,
    fcmTokenAvailable: Boolean,
    notificationGranted: Boolean,
    pushTokenSentToConfig: Boolean
): List<String> {
    return buildList {
        if (!configSourceReady) {
            add("Config is MOCK")
        }
        if (!attributionSourceReady) {
            add("Attribution source is MOCK")
        }
        if (!pushSourceReady) {
            add("Push token source is MOCK")
        }
        if (appsFlyerUid.isNullOrBlank()) {
            add("af_id unavailable")
        } else if (appsFlyerUidIsMock) {
            add("mock_af_id cannot receive real push")
        }
        if (!appsFlyerUidSentToConfig) {
            add("af_id was not sent to config")
        }
        if (!fcmTokenAvailable) {
            add("FCM token unavailable")
        }
        if (!notificationGranted) {
            add("notification permission not granted")
        }
        if (!pushTokenSentToConfig) {
            add("push_token was not sent to config")
        }
    }
}

private const val MOCK_PREFIX = "mock"
private const val PUSH_TEST_URL_PREFIX = "https://web.team-s.club/check_push?af_id="
private const val DEBUG_PANEL_LOG_TAG = "DebugPanel"

private fun logDebug(message: String) {
    if (BuildConfig.DEBUG) {
        Log.d(DEBUG_PANEL_LOG_TAG, message)
    }
}

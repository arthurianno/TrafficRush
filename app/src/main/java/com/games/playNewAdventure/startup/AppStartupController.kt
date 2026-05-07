package com.games.playNewAdventure.startup

import com.games.playNewAdventure.startup.domain.AppMode
import com.games.playNewAdventure.startup.domain.AttributionData
import com.games.playNewAdventure.startup.domain.AttributionProvider
import com.games.playNewAdventure.startup.domain.AttributionProviderMode
import com.games.playNewAdventure.startup.domain.ConfigDebugResultType
import com.games.playNewAdventure.startup.domain.ConfigDebugSnapshot
import com.games.playNewAdventure.startup.domain.ConfigDiagnosticsProvider
import com.games.playNewAdventure.startup.domain.ConfigFetchResult
import com.games.playNewAdventure.startup.domain.ConfigProvider
import com.games.playNewAdventure.startup.domain.ConfigProviderMode
import com.games.playNewAdventure.startup.domain.DeviceDataProvider
import com.games.playNewAdventure.startup.domain.MockConfigScenario
import com.games.playNewAdventure.startup.domain.NetworkStatusProvider
import com.games.playNewAdventure.startup.domain.PushTokenProviderMode
import com.games.playNewAdventure.startup.domain.PushData
import com.games.playNewAdventure.startup.domain.PushTokenProvider
import com.games.playNewAdventure.startup.domain.StartupDebugSnapshot
import com.games.playNewAdventure.startup.domain.StartupResult
import com.games.playNewAdventure.startup.domain.StartupStateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppStartupController(
    private val storage: StartupStateRepository,
    private val networkStatusProvider: NetworkStatusProvider,
    private val attributionProvider: AttributionProvider,
    private val pushTokenProvider: PushTokenProvider,
    private val configProvider: ConfigProvider,
    private val deviceDataProvider: DeviceDataProvider,
    private val debugLogger: (String) -> Unit = {},
    private val currentEpochSeconds: () -> Long = { System.currentTimeMillis() / MILLIS_IN_SECOND }
) {
    // Cached for DebugPanel display — populated after each config request
    @Volatile private var lastAppsFlyerUid: String? = null
    @Volatile private var lastAppsFlyerUidSentToConfig: String? = null
    @Volatile private var lastFcmTokenAvailable: Boolean = false
    @Volatile private var lastFirebaseProjectId: String? = null
    @Volatile private var lastPushTokenSentToConfig: Boolean = false
    @Volatile private var lastConfigResponse: ConfigDebugSnapshot? = null
    @Volatile private var lastStartupDecision: String? = null
    @Volatile private var lastStartupDecisionReason: String? = null
    suspend fun resolveStartup(): StartupResult = withContext(Dispatchers.IO) {
        when (storage.appMode) {
            AppMode.FANTIC -> {
                recordStartupDecision(DECISION_FANTIC, "Stored FANTIC mode")
                StartupResult.ShowFantic
            }
            AppMode.WEBVIEW -> resolveStoredWebViewMode()
            AppMode.UNKNOWN -> resolveFirstLaunch()
        }
    }

    fun recordPushPermissionResult(granted: Boolean) {
        storage.pushPermissionGranted = granted
        if (!granted) {
            storage.pushPromptDeclinedAtSeconds = currentEpochSeconds()
        }
    }

    fun declinePushPrompt() {
        storage.pushPromptDeclinedAtSeconds = currentEpochSeconds()
    }

    fun resetLocalState() {
        storage.resetLocalState()
    }

    fun clearLastWebViewUrl() {
        storage.lastWebViewUrl = null
    }

    fun setMockConfigScenario(scenario: MockConfigScenario) {
        storage.mockConfigScenario = scenario
    }

    fun setConfigProviderMode(mode: ConfigProviderMode) {
        storage.configProviderMode = mode
        lastAppsFlyerUidSentToConfig = null
        lastPushTokenSentToConfig = false
    }

    fun setAttributionProviderMode(mode: AttributionProviderMode) {
        storage.attributionProviderMode = mode
        clearRuntimeDiagnostics()
    }

    fun setPushTokenProviderMode(mode: PushTokenProviderMode) {
        storage.pushTokenProviderMode = mode
        clearRuntimeDiagnostics()
    }

    suspend fun refreshDebugDiagnostics() = withContext(Dispatchers.IO) {
        val attributionData = runCatching {
            attributionProvider.getConversionData()
        }.getOrNull()
        val pushData = runCatching {
            pushTokenProvider.getPushDataOrNull()
        }.getOrNull()

        updateRuntimeDiagnostics(
            attributionData = attributionData,
            pushData = pushData
        )
    }

    fun debugSnapshot(): StartupDebugSnapshot {
        return StartupDebugSnapshot(
            appMode = storage.appMode,
            lastWebViewUrl = storage.lastWebViewUrl,
            pushPromptDeclinedAtSeconds = storage.pushPromptDeclinedAtSeconds,
            pushPermissionGranted = storage.pushPermissionGranted,
            mockConfigScenario = storage.mockConfigScenario,
            configProviderMode = storage.configProviderMode,
            attributionProviderMode = storage.attributionProviderMode,
            pushTokenProviderMode = storage.pushTokenProviderMode,
            appsFlyerUid = lastAppsFlyerUid,
            appsFlyerUidSentToConfig = !lastAppsFlyerUid.isNullOrBlank() &&
                lastAppsFlyerUid == lastAppsFlyerUidSentToConfig,
            fcmTokenAvailable = lastFcmTokenAvailable,
            firebaseProjectId = lastFirebaseProjectId,
            pushTokenSentToConfig = lastPushTokenSentToConfig,
            lastConfigResponse = lastConfigResponse,
            lastStartupDecision = lastStartupDecision,
            lastStartupDecisionReason = lastStartupDecisionReason
        )
    }

    private suspend fun resolveFirstLaunch(): StartupResult {
        if (!networkStatusProvider.isOnline()) {
            recordStartupDecision(DECISION_NO_INTERNET, "First launch is offline")
            return StartupResult.ShowNoInternet
        }

        return when (val result = requestFreshConfig()) {
            is ConfigFetchResult.Success -> {
                storage.appMode = AppMode.WEBVIEW
                storage.lastWebViewUrl = result.url
                StartupResult.ShowWebView(
                    url = result.url,
                    shouldAskPushPermission = shouldAskPushPermission()
                ).also {
                    recordStartupDecision(DECISION_WEBVIEW, "Server returned WebView URL.")
                }
            }
            is ConfigFetchResult.Negative -> {
                storage.appMode = AppMode.FANTIC
                recordStartupDecision(
                    DECISION_FANTIC,
                    "Server returned negative config. Fantic is expected."
                )
                StartupResult.ShowFantic
            }
            is ConfigFetchResult.TransientError -> {
                recordStartupDecision(
                    DECISION_NO_INTERNET,
                    "Config transient error. First launch shows NoInternet; stored WEBVIEW uses cached URL."
                )
                StartupResult.ShowNoInternet
            }
        }
    }

    private suspend fun resolveStoredWebViewMode(): StartupResult {
        if (!networkStatusProvider.isOnline()) {
            recordStartupDecision(DECISION_NO_INTERNET, "Stored WEBVIEW mode is offline")
            return StartupResult.ShowNoInternet
        }

        return when (val result = requestFreshConfig()) {
            is ConfigFetchResult.Success -> {
                storage.lastWebViewUrl = result.url
                showStoredWebViewOrNoInternet(
                    url = result.url,
                    webViewReason = "Server returned WebView URL.",
                    noInternetReason = "Server returned WebView URL but url is unavailable."
                )
            }
            is ConfigFetchResult.Negative ->
                showStoredWebViewOrNoInternet(
                    url = storage.lastWebViewUrl,
                    webViewReason = "Server returned negative config; stored WEBVIEW uses cached URL.",
                    noInternetReason = "Server returned negative config and no cached URL is available."
                )
            is ConfigFetchResult.TransientError ->
                showStoredWebViewOrNoInternet(
                    url = storage.lastWebViewUrl,
                    webViewReason = "Config transient error. Stored WEBVIEW uses cached URL.",
                    noInternetReason = "Config transient error and no cached URL is available."
                )
        }
    }

    private suspend fun requestFreshConfig(): ConfigFetchResult {
        val attributionData = runCatching {
            attributionProvider.getConversionData()
        }.getOrElse { failure ->
            recordSyntheticConfigTransientError("Attribution error: ${failure.message}")
            return ConfigFetchResult.TransientError(failure.message)
        }
        val deviceData = runCatching {
            deviceDataProvider.getDeviceData()
        }.getOrElse { failure ->
            recordSyntheticConfigTransientError("Device data error: ${failure.message}")
            return ConfigFetchResult.TransientError(failure.message)
        }
        val pushData = runCatching {
            pushTokenProvider.getPushDataOrNull()
        }.getOrNull()

        updateRuntimeDiagnostics(
            attributionData = attributionData,
            pushData = pushData
        )
        val configRequestSentToServer = storage.configProviderMode == ConfigProviderMode.REAL
        lastAppsFlyerUidSentToConfig = if (configRequestSentToServer) {
            lastAppsFlyerUid
        } else {
            null
        }
        lastPushTokenSentToConfig = configRequestSentToServer && lastFcmTokenAvailable

        return runCatching {
            configProvider.fetchConfig(
                attributionData = attributionData,
                pushData = pushData,
                deviceData = deviceData
            ).also { result ->
                recordConfigResponse(result)
            }
        }.getOrElse { failure ->
            recordSyntheticConfigTransientError("Config error: ${failure.message}")
            ConfigFetchResult.TransientError(failure.message)
        }
    }

    private fun showStoredWebViewOrNoInternet(
        url: String?,
        webViewReason: String,
        noInternetReason: String
    ): StartupResult {
        val targetUrl = url?.takeIf { it.isNotBlank() } ?: run {
            recordStartupDecision(DECISION_NO_INTERNET, noInternetReason)
            return StartupResult.ShowNoInternet
        }
        recordStartupDecision(DECISION_WEBVIEW, webViewReason)
        return StartupResult.ShowWebView(
            url = targetUrl,
            shouldAskPushPermission = shouldAskPushPermission()
        )
    }

    private fun shouldAskPushPermission(): Boolean {
        if (storage.pushPermissionGranted) {
            return false
        }

        val declinedAt = storage.pushPromptDeclinedAtSeconds
        if (declinedAt <= 0L) {
            return true
        }

        return currentEpochSeconds() - declinedAt >= PUSH_PROMPT_REPEAT_DELAY_SECONDS
    }

    private fun clearRuntimeDiagnostics() {
        lastAppsFlyerUid = null
        lastAppsFlyerUidSentToConfig = null
        lastFcmTokenAvailable = false
        lastFirebaseProjectId = null
        lastPushTokenSentToConfig = false
    }

    private fun recordConfigResponse(result: ConfigFetchResult) {
        lastConfigResponse = (configProvider as? ConfigDiagnosticsProvider)
            ?.lastConfigDebugSnapshot()
            ?: result.toSyntheticConfigDebugSnapshot()

        debugLogger(
            "Parsed config result: " +
                "source=${lastConfigResponse?.source ?: "-"} " +
                "http=${lastConfigResponse?.httpStatus ?: "-"} " +
                "result=${lastConfigResponse?.resultType?.displayName ?: "-"} " +
                "ok=${lastConfigResponse?.ok ?: "-"} " +
                "url=${lastConfigResponse?.url ?: "-"} " +
                "error=${lastConfigResponse?.errorMessage ?: "-"}"
        )
    }

    private fun recordSyntheticConfigTransientError(message: String) {
        lastConfigResponse = ConfigDebugSnapshot(
            source = storage.configProviderMode,
            resultType = ConfigDebugResultType.TRANSIENT_ERROR,
            errorMessage = message
        )
        debugLogger("Parsed config result: source=${storage.configProviderMode} result=TransientError error=$message")
    }

    private fun ConfigFetchResult.toSyntheticConfigDebugSnapshot(): ConfigDebugSnapshot {
        return ConfigDebugSnapshot(
            source = storage.configProviderMode,
            resultType = when (this) {
                is ConfigFetchResult.Success -> ConfigDebugResultType.SUCCESS
                is ConfigFetchResult.Negative -> ConfigDebugResultType.NEGATIVE
                is ConfigFetchResult.TransientError -> ConfigDebugResultType.TRANSIENT_ERROR
            },
            ok = when (this) {
                is ConfigFetchResult.Success -> true
                is ConfigFetchResult.Negative -> false
                is ConfigFetchResult.TransientError -> null
            },
            url = (this as? ConfigFetchResult.Success)?.url,
            errorMessage = when (this) {
                is ConfigFetchResult.Success -> null
                is ConfigFetchResult.Negative -> message
                is ConfigFetchResult.TransientError -> reason
            }
        )
    }

    private fun recordStartupDecision(decision: String, reason: String) {
        lastStartupDecision = decision
        lastStartupDecisionReason = reason
        debugLogger("Startup decision: $decision; $reason")
    }

    private fun updateRuntimeDiagnostics(
        attributionData: AttributionData?,
        pushData: PushData?
    ) {
        lastAppsFlyerUid = attributionData?.values?.get("af_id")?.toString()
            ?.takeIf { it.isNotBlank() }
        lastFcmTokenAvailable = !pushData?.pushToken.isNullOrBlank()
        lastFirebaseProjectId = pushData?.firebaseProjectId
    }

    private companion object {
        const val MILLIS_IN_SECOND = 1_000L
        const val PUSH_PROMPT_REPEAT_DELAY_SECONDS = 259_200L
        const val DECISION_WEBVIEW = "WEBVIEW"
        const val DECISION_FANTIC = "FANTIC"
        const val DECISION_NO_INTERNET = "NO_INTERNET"
    }
}

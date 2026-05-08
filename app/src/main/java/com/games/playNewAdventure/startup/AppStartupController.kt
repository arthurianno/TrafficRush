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
import com.games.playNewAdventure.startup.domain.DEEP_LINK_SOURCE_NONE
import com.games.playNewAdventure.startup.domain.DeviceDataProvider
import com.games.playNewAdventure.startup.domain.MockConfigScenario
import com.games.playNewAdventure.startup.domain.NetworkStatusProvider
import com.games.playNewAdventure.startup.domain.PushTokenProviderMode
import com.games.playNewAdventure.startup.domain.PushData
import com.games.playNewAdventure.startup.domain.PushTokenProvider
import com.games.playNewAdventure.startup.domain.StartupDebugSnapshot
import com.games.playNewAdventure.startup.domain.StartupResult
import com.games.playNewAdventure.startup.domain.StartupStateRepository
import com.games.playNewAdventure.startup.domain.TokenDebugInfo
import com.games.playNewAdventure.startup.domain.toTokenDebugInfo
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
    @Volatile private var lastFcmTokenAvailable: Boolean = false
    @Volatile private var lastFirebaseProjectId: String? = null
    @Volatile private var lastConfigResponse: ConfigDebugSnapshot? = null
    @Volatile private var lastStartupDecision: String? = null
    @Volatile private var lastStartupDecisionReason: String? = null
    @Volatile private var cachedAttributionData: Map<String, Any?>? = null
    @Volatile private var cachedDeepLinkSource: String = DEEP_LINK_SOURCE_NONE
    @Volatile private var lastFcmToken: TokenDebugInfo? = null
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
        val deepLinkValue = cachedAttributionData?.get(KEY_DEEP_LINK_VALUE).toNonBlankAttributionString()
        val deepLinkSub1 = cachedAttributionData?.get(KEY_DEEP_LINK_SUB1).toNonBlankAttributionString()
        val deepLinkSub2 = cachedAttributionData?.get(KEY_DEEP_LINK_SUB2).toNonBlankAttributionString()
        val hasAnyDeepLinkSub = cachedAttributionData.orEmpty().any { (key, value) ->
            key.startsWith(KEY_DEEP_LINK_SUB_PREFIX) && value.toNonBlankAttributionString() != null
        }
        val hasRequiredDeeplinkParams = deepLinkValue != null && hasAnyDeepLinkSub
        val lastSentPushToken = lastConfigResponse?.requestPushToken
        val latestFcmTokenSentToConfig = lastFcmToken != null &&
            lastFcmToken?.sha256 == lastSentPushToken?.sha256 &&
            lastConfigResponse?.source == ConfigProviderMode.REAL &&
            lastConfigResponse?.requestContainedPushToken == true

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
                lastAppsFlyerUid == lastConfigResponse?.requestAfId &&
                lastConfigResponse?.source == ConfigProviderMode.REAL,
            fcmTokenAvailable = lastFcmTokenAvailable,
            latestFcmToken = lastFcmToken,
            lastSentPushToken = lastSentPushToken,
            latestFcmTokenSentToConfig = latestFcmTokenSentToConfig,
            firebaseProjectId = lastFirebaseProjectId,
            lastSentAfId = lastConfigResponse?.requestAfId,
            firebaseProjectIdSentToConfig = lastConfigResponse?.source == ConfigProviderMode.REAL &&
                lastConfigResponse?.requestContainedFirebaseProjectId == true,
            bundleIdSentToConfig = lastConfigResponse?.source == ConfigProviderMode.REAL &&
                lastConfigResponse?.requestBundleId == EXPECTED_BUNDLE_ID,
            pushTokenSentToConfig = latestFcmTokenSentToConfig,
            lastConfigResponse = lastConfigResponse,
            lastStartupDecision = lastStartupDecision,
            lastStartupDecisionReason = lastStartupDecisionReason,
            lastAttributionData = cachedAttributionData,
            deepLinkSource = cachedDeepLinkSource,
            deepLinkValue = deepLinkValue,
            deepLinkSub1 = deepLinkSub1,
            deepLinkSub2 = deepLinkSub2,
            hasRequiredDeeplinkParams = hasRequiredDeeplinkParams,
            lastConfigContainedDeepLinkValue = lastConfigResponse?.requestDeepLinkValue?.isNotBlank() == true,
            lastConfigContainedAnyDeepLinkSub = lastConfigResponse?.requestContainedAnyDeepLinkSub == true
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
        return runCatching {
            configProvider.fetchConfig(
                attributionData = attributionData,
                pushData = pushData,
                deviceData = deviceData
            ).also { result ->
                recordConfigResponse(
                    result = result,
                    attributionData = attributionData,
                    pushData = pushData
                )
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
        lastFcmTokenAvailable = false
        lastFirebaseProjectId = null
        lastFcmToken = null
        cachedAttributionData = null
        cachedDeepLinkSource = DEEP_LINK_SOURCE_NONE
    }

    private fun recordConfigResponse(
        result: ConfigFetchResult,
        attributionData: AttributionData,
        pushData: PushData?
    ) {
        lastConfigResponse = (configProvider as? ConfigDiagnosticsProvider)
            ?.lastConfigDebugSnapshot()
            ?: result.toSyntheticConfigDebugSnapshot(
                attributionData = attributionData,
                pushData = pushData
            )

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

    private fun ConfigFetchResult.toSyntheticConfigDebugSnapshot(
        attributionData: AttributionData,
        pushData: PushData?
    ): ConfigDebugSnapshot {
        val afId = attributionData.values[KEY_AF_ID].toNonBlankAttributionString()
        val pushToken = pushData?.pushToken.toTokenDebugInfo()
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
            },
            requestContainedAfId = afId != null,
            requestContainedPushToken = pushToken != null,
            requestContainedFirebaseProjectId = !pushData?.firebaseProjectId.isNullOrBlank(),
            requestContainedBundleId = true,
            requestContainedAnyDeepLinkSub = attributionData.values.any { (key, value) ->
                key.startsWith(KEY_DEEP_LINK_SUB_PREFIX) && value.toNonBlankAttributionString() != null
            },
            requestAfId = afId,
            requestPushToken = pushToken,
            requestFirebaseProjectId = pushData?.firebaseProjectId?.takeIf { it.isNotBlank() },
            requestBundleId = EXPECTED_BUNDLE_ID,
            requestDeepLinkValue = attributionData.values[KEY_DEEP_LINK_VALUE].toNonBlankAttributionString()
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
        lastAppsFlyerUid = attributionData?.values?.get("af_id").toNonBlankAttributionString()
        cachedAttributionData = attributionData?.values
        cachedDeepLinkSource = attributionData?.deepLinkSource ?: DEEP_LINK_SOURCE_NONE
        lastFcmTokenAvailable = !pushData?.pushToken.isNullOrBlank()
        lastFcmToken = pushData?.pushToken.toTokenDebugInfo()
        lastFirebaseProjectId = pushData?.firebaseProjectId
    }

    private companion object {
        const val MILLIS_IN_SECOND = 1_000L
        const val PUSH_PROMPT_REPEAT_DELAY_SECONDS = 259_200L
        const val DECISION_WEBVIEW = "WEBVIEW"
        const val DECISION_FANTIC = "FANTIC"
        const val DECISION_NO_INTERNET = "NO_INTERNET"
        const val KEY_DEEP_LINK_VALUE = "deep_link_value"
        const val KEY_AF_ID = "af_id"
        const val KEY_DEEP_LINK_SUB_PREFIX = "deep_link_sub"
        const val KEY_DEEP_LINK_SUB1 = "deep_link_sub1"
        const val KEY_DEEP_LINK_SUB2 = "deep_link_sub2"
        const val EXPECTED_BUNDLE_ID = "com.games.playNewAdventure"
    }
}

private fun Any?.toNonBlankAttributionString(): String? {
    return this?.toString()
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
}

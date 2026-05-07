package com.games.playNewAdventure.startup.domain

enum class AppMode {
    UNKNOWN,
    WEBVIEW,
    FANTIC
}

sealed class StartupResult {
    data class ShowWebView(
        val url: String,
        val shouldAskPushPermission: Boolean
    ) : StartupResult()

    data object ShowFantic : StartupResult()
    data object ShowNoInternet : StartupResult()
}

data class AttributionData(
    val values: Map<String, Any?>
)

data class PushData(
    val pushToken: String?,
    val firebaseProjectId: String?,
    val firebaseProjectNumber: String? = null
)

sealed class ConfigFetchResult {
    data class Success(
        val url: String,
        val expires: Long?
    ) : ConfigFetchResult()

    data class Negative(
        val message: String?
    ) : ConfigFetchResult()

    data class TransientError(
        val reason: String?
    ) : ConfigFetchResult()
}

enum class MockConfigScenario {
    SUCCESS_WEBVIEW,
    NEGATIVE_RESPONSE,
    SERVER_ERROR,
    TIMEOUT,
    EMPTY_URL
}

enum class ConfigProviderMode {
    MOCK,
    REAL
}

enum class AttributionProviderMode {
    MOCK,
    REAL
}

enum class PushTokenProviderMode {
    MOCK,
    REAL
}

enum class ConfigDebugResultType(
    val displayName: String
) {
    SUCCESS("Success"),
    NEGATIVE("Negative"),
    TRANSIENT_ERROR("TransientError")
}

data class ConfigDebugSnapshot(
    val source: ConfigProviderMode,
    val httpStatus: Int? = null,
    val resultType: ConfigDebugResultType? = null,
    val ok: Boolean? = null,
    val url: String? = null,
    val errorMessage: String? = null,
    val requestContainedAfId: Boolean = false,
    val requestContainedPushToken: Boolean = false,
    val requestContainedFirebaseProjectId: Boolean = false,
    val requestContainedAnyDeepLinkSub: Boolean = false,
    val requestAfStatus: String? = null,
    val requestDeepLinkValue: String? = null,
    val sanitizedResponseBody: String? = null
)

data class StartupDebugSnapshot(
    val appMode: AppMode,
    val lastWebViewUrl: String?,
    val pushPromptDeclinedAtSeconds: Long,
    val pushPermissionGranted: Boolean,
    val mockConfigScenario: MockConfigScenario,
    val configProviderMode: ConfigProviderMode,
    val attributionProviderMode: AttributionProviderMode,
    val pushTokenProviderMode: PushTokenProviderMode,
    // Push/attribution fields for DebugPanel
    val appsFlyerUid: String? = null,
    val appsFlyerUidSentToConfig: Boolean = false,
    val fcmTokenAvailable: Boolean = false,
    val firebaseProjectId: String? = null,
    val pushTokenSentToConfig: Boolean = false,
    val lastConfigResponse: ConfigDebugSnapshot? = null,
    val lastStartupDecision: String? = null,
    val lastStartupDecisionReason: String? = null,
    val lastAttributionData: Map<String, Any?>? = null,
    val deepLinkValue: String? = null,
    val deepLinkSub1: String? = null,
    val deepLinkSub2: String? = null,
    val hasRequiredDeeplinkParams: Boolean = false,
    val lastConfigContainedDeepLinkValue: Boolean = false,
    val lastConfigContainedAnyDeepLinkSub: Boolean = false
)

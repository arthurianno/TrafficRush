package com.games.playNewAdventure.startup

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

data class ConfigResponse(
    val ok: Boolean,
    val url: String?,
    val message: String?,
    val expires: Long?
)

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

data class StartupDebugSnapshot(
    val appMode: AppMode,
    val lastWebViewUrl: String?,
    val pushPromptDeclinedAtSeconds: Long,
    val pushPermissionGranted: Boolean,
    val mockConfigScenario: MockConfigScenario,
    val configProviderMode: ConfigProviderMode
)

package com.games.playNewAdventure.startup.domain

import java.security.MessageDigest

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
    val values: Map<String, Any?>,
    val deepLinkSource: String = DEEP_LINK_SOURCE_NONE
)

data class PushData(
    val pushToken: String?,
    val firebaseProjectId: String?,
    val firebaseProjectNumber: String? = null
)

data class TokenDebugInfo(
    val length: Int,
    val sha256: String,
    val last4: String
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
    val requestContainedBundleId: Boolean = false,
    val requestContainedAnyDeepLinkSub: Boolean = false,
    val requestAfId: String? = null,
    val requestPushToken: TokenDebugInfo? = null,
    val requestFirebaseProjectId: String? = null,
    val requestBundleId: String? = null,
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
    val latestFcmToken: TokenDebugInfo? = null,
    val lastSentPushToken: TokenDebugInfo? = null,
    val latestFcmTokenSentToConfig: Boolean = false,
    val firebaseProjectId: String? = null,
    val lastSentAfId: String? = null,
    val firebaseProjectIdSentToConfig: Boolean = false,
    val bundleIdSentToConfig: Boolean = false,
    val pushTokenSentToConfig: Boolean = false,
    val lastConfigResponse: ConfigDebugSnapshot? = null,
    val lastStartupDecision: String? = null,
    val lastStartupDecisionReason: String? = null,
    val lastAttributionData: Map<String, Any?>? = null,
    val deepLinkSource: String = DEEP_LINK_SOURCE_NONE,
    val deepLinkValue: String? = null,
    val deepLinkSub1: String? = null,
    val deepLinkSub2: String? = null,
    val hasRequiredDeeplinkParams: Boolean = false,
    val lastConfigContainedDeepLinkValue: Boolean = false,
    val lastConfigContainedAnyDeepLinkSub: Boolean = false
)

const val DEEP_LINK_SOURCE_NONE = "none"
const val DEEP_LINK_SOURCE_UDL = "UDL"
const val DEEP_LINK_SOURCE_CONVERSION = "conversion"
const val DEEP_LINK_SOURCE_APP_OPEN_ATTRIBUTION = "appOpenAttribution"

fun String?.toTokenDebugInfo(): TokenDebugInfo? {
    val token = this?.takeIf { it.isNotBlank() } ?: return null
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte) }
        .take(TOKEN_HASH_PREFIX_LENGTH)

    return TokenDebugInfo(
        length = token.length,
        sha256 = digest,
        last4 = token.takeLast(TOKEN_LAST_CHARS)
    )
}

private const val TOKEN_HASH_PREFIX_LENGTH = 12
private const val TOKEN_LAST_CHARS = 4

package com.games.playNewAdventure.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.games.playNewAdventure.AppConstants
import com.games.playNewAdventure.BuildConfig
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
import com.games.playNewAdventure.startup.domain.PushData
import com.games.playNewAdventure.startup.domain.PushTokenProvider
import com.games.playNewAdventure.startup.domain.PushTokenProviderMode
import java.util.Locale
import kotlinx.coroutines.delay

class MockAttributionService : AttributionProvider {
    override suspend fun getConversionData(): AttributionData {
        logDebug("attribution source MOCK")
        return AttributionData(
            values = mapOf(
                "af_status" to "Non-organic",
                "campaign" to "mock_campaign",
                "media_source" to "Facebook Ads",
                "af_sub1" to "mock_sub1",
                "af_id" to "mock_af_id_123",
                "is_first_launch" to true
            )
        )
    }
}

class MockPushService : PushTokenProvider {
    override suspend fun getPushDataOrNull(): PushData {
        logDebug("push token source MOCK; FCM token available true")
        return PushData(
            pushToken = "mock_push_token_123",
            firebaseProjectId = "mock_firebase_project_id",
            firebaseProjectNumber = AppConstants.FIREBASE_PROJECT_NUMBER
        )
    }
}

class SwitchingAttributionService(
    private val modeProvider: () -> AttributionProviderMode,
    private val mockAttributionService: AttributionProvider,
    private val realAttributionService: AttributionProvider
) : AttributionProvider {
    override suspend fun getConversionData(): AttributionData {
        val mode = modeProvider()
        logDebug("attribution source $mode")
        val targetService = when (mode) {
            AttributionProviderMode.MOCK -> mockAttributionService
            AttributionProviderMode.REAL -> realAttributionService
        }
        return targetService.getConversionData()
    }
}

class SwitchingPushTokenService(
    private val modeProvider: () -> PushTokenProviderMode,
    private val mockPushService: PushTokenProvider,
    private val realPushService: PushTokenProvider
) : PushTokenProvider {
    override suspend fun getPushDataOrNull(): PushData? {
        val mode = modeProvider()
        logDebug("push token source $mode")
        return when (mode) {
            PushTokenProviderMode.MOCK -> mockPushService
            PushTokenProviderMode.REAL -> realPushService
        }.getPushDataOrNull()
    }
}

class MockConfigService(
    private val scenarioProvider: () -> MockConfigScenario = { MockConfigScenario.SUCCESS_WEBVIEW }
) : ConfigProvider, ConfigDiagnosticsProvider {
    @Volatile private var lastConfigDebugSnapshot: ConfigDebugSnapshot? = null

    override suspend fun fetchConfig(
        attributionData: AttributionData,
        pushData: PushData?,
        deviceData: Map<String, Any?>
    ): ConfigFetchResult {
        val result = when (scenarioProvider()) {
            MockConfigScenario.SUCCESS_WEBVIEW -> ConfigFetchResult.Success(
                url = AppConstants.MOCK_WEBVIEW_URL,
                expires = 1_893_456_000
            )

            MockConfigScenario.NEGATIVE_RESPONSE -> ConfigFetchResult.Negative(
                message = "No data"
            )

            MockConfigScenario.SERVER_ERROR -> ConfigFetchResult.TransientError(
                reason = "Mock server error"
            )

            MockConfigScenario.TIMEOUT -> {
                delay(MOCK_TIMEOUT_DELAY_MS)
                ConfigFetchResult.TransientError(reason = "Mock timeout")
            }

            MockConfigScenario.EMPTY_URL -> ConfigFetchResult.Negative(
                message = "Empty config URL"
            )
        }
        lastConfigDebugSnapshot = result.toConfigDebugSnapshot(
            source = ConfigProviderMode.MOCK,
            attributionData = attributionData,
            pushData = pushData
        )
        return result
    }

    override fun lastConfigDebugSnapshot(): ConfigDebugSnapshot? = lastConfigDebugSnapshot

    private companion object {
        const val MOCK_TIMEOUT_DELAY_MS = 2_000L
    }
}

class SwitchingConfigService(
    private val modeProvider: () -> ConfigProviderMode,
    private val mockConfigService: ConfigProvider,
    private val remoteConfigService: ConfigProvider
) : ConfigProvider, ConfigDiagnosticsProvider {
    @Volatile private var lastConfigDebugSnapshot: ConfigDebugSnapshot? = null

    override suspend fun fetchConfig(
        attributionData: AttributionData,
        pushData: PushData?,
        deviceData: Map<String, Any?>
    ): ConfigFetchResult {
        val mode = modeProvider()
        val targetService = when (mode) {
            ConfigProviderMode.MOCK -> mockConfigService
            ConfigProviderMode.REAL -> remoteConfigService
        }

        val result = targetService.fetchConfig(
            attributionData = attributionData,
            pushData = pushData,
            deviceData = deviceData
        )
        lastConfigDebugSnapshot = (targetService as? ConfigDiagnosticsProvider)
            ?.lastConfigDebugSnapshot()
            ?.copy(source = mode)
            ?: result.toConfigDebugSnapshot(
                source = mode,
                attributionData = attributionData,
                pushData = pushData
            )
        return result
    }

    override fun lastConfigDebugSnapshot(): ConfigDebugSnapshot? = lastConfigDebugSnapshot
}

class AndroidNetworkChecker(context: Context) : NetworkStatusProvider {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    override fun isOnline(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

class AndroidDeviceDataProvider : DeviceDataProvider {
    override fun getDeviceData(): Map<String, Any?> {
        return mapOf(
            "bundle_id" to AppConstants.APPLICATION_ID,
            "application_id" to AppConstants.APPLICATION_ID,
            "store_id" to AppConstants.APPLICATION_ID,
            "os" to "Android",
            "platform" to "Android",
            "locale" to Locale.getDefault().toLanguageTag()
        )
    }
}

private const val STARTUP_SERVICES_LOG_TAG = "StartupServices"
private const val KEY_AF_ID = "af_id"
private const val KEY_AF_STATUS = "af_status"
private const val KEY_DEEP_LINK_VALUE = "deep_link_value"

private fun logDebug(message: String) {
    if (BuildConfig.DEBUG) {
        runCatching {
            Log.d(STARTUP_SERVICES_LOG_TAG, message)
        }
    }
}

private fun ConfigFetchResult.toConfigDebugSnapshot(
    source: ConfigProviderMode,
    attributionData: AttributionData,
    pushData: PushData?,
    message: String? = null
): ConfigDebugSnapshot {
    return ConfigDebugSnapshot(
        source = source,
        httpStatus = null,
        resultType = toDebugResultType(),
        ok = defaultOkValue(),
        url = (this as? ConfigFetchResult.Success)?.url,
        errorMessage = errorMessage() ?: message,
        requestContainedAfId = attributionData.values[KEY_AF_ID]?.toString()?.isNotBlank() == true,
        requestContainedPushToken = !pushData?.pushToken.isNullOrBlank(),
        requestContainedFirebaseProjectId = !pushData?.firebaseProjectId.isNullOrBlank(),
        requestAfStatus = attributionData.values[KEY_AF_STATUS]?.toString()?.takeIf { it.isNotBlank() },
        requestDeepLinkValue = attributionData.values[KEY_DEEP_LINK_VALUE]?.toString()?.takeIf { it.isNotBlank() }
    )
}

private fun ConfigFetchResult.toDebugResultType(): ConfigDebugResultType {
    return when (this) {
        is ConfigFetchResult.Success -> ConfigDebugResultType.SUCCESS
        is ConfigFetchResult.Negative -> ConfigDebugResultType.NEGATIVE
        is ConfigFetchResult.TransientError -> ConfigDebugResultType.TRANSIENT_ERROR
    }
}

private fun ConfigFetchResult.defaultOkValue(): Boolean? {
    return when (this) {
        is ConfigFetchResult.Success -> true
        is ConfigFetchResult.Negative -> false
        is ConfigFetchResult.TransientError -> null
    }
}

private fun ConfigFetchResult.errorMessage(): String? {
    return when (this) {
        is ConfigFetchResult.Success -> null
        is ConfigFetchResult.Negative -> message
        is ConfigFetchResult.TransientError -> reason
    }
}

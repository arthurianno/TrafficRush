package com.games.playNewAdventure.service

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.ContextCompat
import com.games.playNewAdventure.AppConstants
import com.games.playNewAdventure.startup.ConfigProviderMode
import com.games.playNewAdventure.startup.AttributionData
import com.games.playNewAdventure.startup.ConfigResponse
import com.games.playNewAdventure.startup.MockConfigScenario
import com.games.playNewAdventure.startup.PushData
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale
import kotlinx.coroutines.delay

interface AttributionService {
    suspend fun getConversionData(): AttributionData
}

interface PushService {
    suspend fun getPushDataOrNull(): PushData?

    suspend fun requestNotificationPermission(activity: Activity): Boolean
}

interface ConfigService {
    suspend fun requestConfig(
        attributionData: AttributionData,
        pushData: PushData?,
        deviceData: Map<String, Any?>
    ): ConfigResponse
}

interface NetworkChecker {
    fun isOnline(): Boolean
}

interface DeviceDataProvider {
    fun getDeviceData(): Map<String, Any?>
}

class MockAttributionService : AttributionService {
    override suspend fun getConversionData(): AttributionData {
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

class MockPushService : PushService {
    override suspend fun getPushDataOrNull(): PushData {
        return PushData(
            pushToken = "mock_push_token_123",
            firebaseProjectId = "mock_firebase_project_id",
            firebaseProjectNumber = AppConstants.FIREBASE_PROJECT_NUMBER
        )
    }

    override suspend fun requestNotificationPermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val alreadyGranted = ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (alreadyGranted) {
                return true
            }
        }

        // Real implementation should request POST_NOTIFICATIONS via Activity Result API.
        delay(MOCK_PERMISSION_DELAY_MS)
        return true
    }

    private companion object {
        const val MOCK_PERMISSION_DELAY_MS = 250L
    }
}

class MockConfigService(
    private val scenarioProvider: () -> MockConfigScenario = { MockConfigScenario.SUCCESS_WEBVIEW }
) : ConfigService {
    override suspend fun requestConfig(
        attributionData: AttributionData,
        pushData: PushData?,
        deviceData: Map<String, Any?>
    ): ConfigResponse {
        return when (scenarioProvider()) {
            MockConfigScenario.SUCCESS_WEBVIEW -> ConfigResponse(
                ok = true,
                url = AppConstants.MOCK_WEBVIEW_URL,
                message = null,
                expires = 1_893_456_000
            )

            MockConfigScenario.NEGATIVE_RESPONSE -> ConfigResponse(
                ok = false,
                url = null,
                message = "No data",
                expires = null
            )

            MockConfigScenario.SERVER_ERROR -> throw IOException("Mock server error")

            MockConfigScenario.TIMEOUT -> {
                delay(MOCK_TIMEOUT_DELAY_MS)
                throw SocketTimeoutException("Mock timeout")
            }

            MockConfigScenario.EMPTY_URL -> ConfigResponse(
                ok = true,
                url = "",
                message = null,
                expires = null
            )
        }
    }

    private companion object {
        const val MOCK_TIMEOUT_DELAY_MS = 2_000L
    }
}

class SwitchingConfigService(
    private val modeProvider: () -> ConfigProviderMode,
    private val mockConfigService: ConfigService,
    private val remoteConfigService: ConfigService
) : ConfigService {
    override suspend fun requestConfig(
        attributionData: AttributionData,
        pushData: PushData?,
        deviceData: Map<String, Any?>
    ): ConfigResponse {
        val targetService = when (modeProvider()) {
            ConfigProviderMode.MOCK -> mockConfigService
            ConfigProviderMode.REAL -> remoteConfigService
        }

        return targetService.requestConfig(
            attributionData = attributionData,
            pushData = pushData,
            deviceData = deviceData
        )
    }
}

class AndroidNetworkChecker(context: Context) : NetworkChecker {
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
            "store_id" to AppConstants.APPLICATION_ID,
            "os" to "Android",
            "locale" to Locale.getDefault().toLanguageTag()
        )
    }
}

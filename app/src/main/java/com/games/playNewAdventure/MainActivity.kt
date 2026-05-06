package com.games.playNewAdventure

import android.content.pm.PackageManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.games.playNewAdventure.data.AppStorage
import com.games.playNewAdventure.service.AndroidDeviceDataProvider
import com.games.playNewAdventure.service.AndroidNetworkChecker
import com.games.playNewAdventure.service.AppsFlyerAttributionService
import com.games.playNewAdventure.service.AttributionService
import com.games.playNewAdventure.service.ConfigService
import com.games.playNewAdventure.service.FirebasePushService
import com.games.playNewAdventure.service.MockAttributionService
import com.games.playNewAdventure.service.MockConfigService
import com.games.playNewAdventure.service.MockPushService
import com.games.playNewAdventure.service.PushService
import com.games.playNewAdventure.service.RemoteConfigService
import com.games.playNewAdventure.service.SwitchingConfigService
import com.games.playNewAdventure.startup.AppStartupController
import com.games.playNewAdventure.startup.ConfigProviderMode
import com.games.playNewAdventure.startup.MockConfigScenario
import com.games.playNewAdventure.ui.StartupHost
import com.games.playNewAdventure.ui.theme.TrafficRushTheme

class MainActivity : ComponentActivity() {
    private val appStorage by lazy {
        AppStorage(applicationContext)
    }

    private val attributionService: AttributionService by lazy {
        if (BuildConfig.DEBUG) {
            MockAttributionService()
        } else {
            AppsFlyerAttributionService(applicationContext)
        }
    }

    private val pushService: PushService by lazy {
        if (BuildConfig.DEBUG) {
            MockPushService()
        } else {
            FirebasePushService()
        }
    }

    private val configService: ConfigService by lazy {
        val remoteConfigService = RemoteConfigService()
        if (BuildConfig.DEBUG) {
            SwitchingConfigService(
                modeProvider = { appStorage.configProviderMode },
                mockConfigService = MockConfigService { appStorage.mockConfigScenario },
                remoteConfigService = remoteConfigService
            )
        } else {
            remoteConfigService
        }
    }

    private val startupController by lazy {
        AppStartupController(
            storage = appStorage,
            networkChecker = AndroidNetworkChecker(applicationContext),
            attributionService = attributionService,
            pushService = pushService,
            configService = configService,
            deviceDataProvider = AndroidDeviceDataProvider()
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyDebugIntentOverrides(intent)

        setContent {
            TrafficRushTheme {
                StartupHost(
                    activity = this,
                    startupController = startupController
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (applyDebugIntentOverrides(intent)) {
            recreate()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != FirebasePushService.REQUEST_POST_NOTIFICATIONS) {
            return
        }

        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        appStorage.pushPermissionGranted = granted
        if (!granted) {
            appStorage.pushPromptDeclinedAtSeconds = System.currentTimeMillis() / MILLIS_IN_SECOND
        }
    }

    private companion object {
        const val MILLIS_IN_SECOND = 1_000L
        const val EXTRA_DEBUG_RESET = "debug_reset"
        const val EXTRA_DEBUG_CONFIG_SOURCE = "debug_config_source"
        const val EXTRA_DEBUG_MOCK_SCENARIO = "debug_mock_scenario"
    }

    private fun applyDebugIntentOverrides(intent: Intent?): Boolean {
        if (!BuildConfig.DEBUG || intent == null) {
            return false
        }

        var changed = false
        if (intent.getBooleanExtra(EXTRA_DEBUG_RESET, false)) {
            appStorage.resetLocalState()
            changed = true
        }

        intent.getStringExtra(EXTRA_DEBUG_CONFIG_SOURCE)?.let { rawMode ->
            runCatching { ConfigProviderMode.valueOf(rawMode.uppercase()) }.getOrNull()
                ?.let { mode ->
                    appStorage.configProviderMode = mode
                    changed = true
                }
        }

        intent.getStringExtra(EXTRA_DEBUG_MOCK_SCENARIO)?.let { rawScenario ->
            runCatching { MockConfigScenario.valueOf(rawScenario.uppercase()) }.getOrNull()
                ?.let { scenario ->
                    appStorage.mockConfigScenario = scenario
                    changed = true
                }
        }

        return changed
    }
}

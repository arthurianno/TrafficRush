package com.games.playNewAdventure

import android.app.Activity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.games.playNewAdventure.data.StartupStorage
import com.games.playNewAdventure.service.AttributionService
import com.games.playNewAdventure.service.ConfigService
import com.games.playNewAdventure.service.DeviceDataProvider
import com.games.playNewAdventure.service.NetworkChecker
import com.games.playNewAdventure.service.PushService
import com.games.playNewAdventure.startup.AppMode
import com.games.playNewAdventure.startup.AppStartupController
import com.games.playNewAdventure.startup.AttributionData
import com.games.playNewAdventure.startup.ConfigProviderMode
import com.games.playNewAdventure.startup.ConfigResponse
import com.games.playNewAdventure.startup.MockConfigScenario
import com.games.playNewAdventure.startup.PushData
import com.games.playNewAdventure.ui.StartupHost
import com.games.playNewAdventure.ui.UiTestTags
import com.games.playNewAdventure.ui.theme.TrafficRushTheme
import java.io.IOException
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StartupHostComposeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun successWebViewFlowShowsPushThenWebViewContainer() {
        val storage = InMemoryAppStorage()
        val controller = createController(
            storage = storage,
            configService = FakeConfigService.success(successResponse(AppConstants.MOCK_WEBVIEW_URL))
        )

        setStartupContent(controller)

        waitForTag(UiTestTags.PUSH_PERMISSION_SCREEN)
        composeRule.onNodeWithTag(UiTestTags.PUSH_SKIP, useUnmergedTree = true).performClick()
        waitForTag(UiTestTags.WEBVIEW_SCREEN)
    }

    @Test
    fun negativeResponseShowsFanticScreen() {
        val controller = createController(
            configService = FakeConfigService.success(ConfigResponse(false, null, "No data", null))
        )

        setStartupContent(controller)

        waitForTag(UiTestTags.FANTIC_SCREEN)
    }

    @Test
    fun offlineShowsNoInternetAndRetryRestartsFlow() {
        val networkChecker = MutableNetworkChecker(online = false)
        val controller = createController(
            networkChecker = networkChecker,
            configService = FakeConfigService.success(successResponse(AppConstants.MOCK_WEBVIEW_URL))
        )

        setStartupContent(controller)

        waitForTag(UiTestTags.NO_INTERNET_SCREEN)
        networkChecker.online = true
        composeRule.onNodeWithTag(UiTestTags.RETRY_BUTTON).performClick()
        waitForTag(UiTestTags.PUSH_PERMISSION_SCREEN)
    }

    @Test
    fun debugPanelShowsPrimaryControlsAndCompactUrlActions() {
        val storage = InMemoryAppStorage(
            initialMode = AppMode.WEBVIEW,
            initialLastUrl = "https://web.team-s.club/path/with/a/very/long/query?one=1&two=2&three=3&four=4"
        )
        val controller = createController(
            storage = storage,
            configService = FakeConfigService.failure(IOException("server error"))
        )

        setStartupContent(controller)
        composeRule.onNodeWithText("Debug").performClick()

        assertTagExists(UiTestTags.DEBUG_PANEL)
        assertTagExists(UiTestTags.DEBUG_RESET_STATE)
        assertTagExists(UiTestTags.DEBUG_RESTART_FLOW)
        assertTagExists(UiTestTags.DEBUG_CONFIG_SOURCE)
        assertTagExists(UiTestTags.DEBUG_MOCK_SCENARIO)
        assertTextExists("Clear last URL")
        assertTrue(composeRule.onAllNodesWithText("Copy").fetchSemanticsNodes().isNotEmpty())
    }

    private fun setStartupContent(controller: AppStartupController) {
        composeRule.setContent {
            TrafficRushTheme {
                StartupHost(
                    activity = composeRule.activity,
                    startupController = controller
                )
            }
        }
    }

    private fun waitForTag(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertTagExists(tag)
    }

    private fun assertTagExists(tag: String) {
        assertTrue(
            composeRule.onAllNodesWithTag(tag, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
    }

    private fun assertTextExists(text: String) {
        assertTrue(
            composeRule.onAllNodesWithText(text, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        )
    }
}

private fun createController(
    storage: InMemoryAppStorage = InMemoryAppStorage(),
    networkChecker: NetworkChecker = MutableNetworkChecker(online = true),
    configService: ConfigService = FakeConfigService.success(successResponse(AppConstants.MOCK_WEBVIEW_URL))
): AppStartupController {
    return AppStartupController(
        storage = storage,
        networkChecker = networkChecker,
        attributionService = FakeAttributionService(),
        pushService = FakePushService(),
        configService = configService,
        deviceDataProvider = FakeDeviceDataProvider()
    )
}

private fun successResponse(url: String): ConfigResponse {
    return ConfigResponse(
        ok = true,
        url = url,
        message = null,
        expires = 1_893_456_000
    )
}

private class InMemoryAppStorage(
    initialMode: AppMode = AppMode.UNKNOWN,
    initialLastUrl: String? = null
) : StartupStorage {
    override var appMode: AppMode = initialMode
    override var lastWebViewUrl: String? = initialLastUrl
    override var pushPromptDeclinedAtSeconds: Long = 0L
    override var pushPermissionGranted: Boolean = false
    override var mockConfigScenario: MockConfigScenario = MockConfigScenario.SUCCESS_WEBVIEW
    override var configProviderMode: ConfigProviderMode = ConfigProviderMode.MOCK

    override fun resetLocalState() {
        appMode = AppMode.UNKNOWN
        lastWebViewUrl = null
        pushPromptDeclinedAtSeconds = 0L
        pushPermissionGranted = false
    }
}

private class MutableNetworkChecker(
    var online: Boolean
) : NetworkChecker {
    override fun isOnline(): Boolean = online
}

private class FakeAttributionService : AttributionService {
    override suspend fun getConversionData(): AttributionData {
        return AttributionData(values = mapOf("af_status" to "Non-organic"))
    }
}

private class FakePushService : PushService {
    override suspend fun getPushDataOrNull(): PushData {
        return PushData(
            pushToken = "token",
            firebaseProjectId = "project"
        )
    }

    override suspend fun requestNotificationPermission(activity: Activity): Boolean {
        return true
    }
}

private class FakeConfigService(
    private val result: Result<ConfigResponse>
) : ConfigService {
    override suspend fun requestConfig(
        attributionData: AttributionData,
        pushData: PushData?,
        deviceData: Map<String, Any?>
    ): ConfigResponse {
        return result.getOrThrow()
    }

    companion object {
        fun success(response: ConfigResponse): FakeConfigService {
            return FakeConfigService(Result.success(response))
        }

        fun failure(throwable: Throwable): FakeConfigService {
            return FakeConfigService(Result.failure(throwable))
        }
    }
}

private class FakeDeviceDataProvider : DeviceDataProvider {
    override fun getDeviceData(): Map<String, Any?> {
        return mapOf(
            "bundle_id" to AppConstants.APPLICATION_ID,
            "store_id" to AppConstants.APPLICATION_ID,
            "os" to "Android",
            "locale" to "en-US"
        )
    }
}

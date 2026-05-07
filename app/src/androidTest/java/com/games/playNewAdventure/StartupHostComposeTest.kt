package com.games.playNewAdventure

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.games.playNewAdventure.startup.AppStartupController
import com.games.playNewAdventure.startup.domain.AppMode
import com.games.playNewAdventure.startup.domain.AttributionData
import com.games.playNewAdventure.startup.domain.AttributionProvider
import com.games.playNewAdventure.startup.domain.AttributionProviderMode
import com.games.playNewAdventure.startup.domain.ConfigFetchResult
import com.games.playNewAdventure.startup.domain.ConfigProvider
import com.games.playNewAdventure.startup.domain.ConfigProviderMode
import com.games.playNewAdventure.startup.domain.DeviceDataProvider
import com.games.playNewAdventure.startup.domain.MockConfigScenario
import com.games.playNewAdventure.startup.domain.NetworkStatusProvider
import com.games.playNewAdventure.startup.domain.PushData
import com.games.playNewAdventure.startup.domain.PushTokenProvider
import com.games.playNewAdventure.startup.domain.PushTokenProviderMode
import com.games.playNewAdventure.startup.domain.StartupStateRepository
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
            configProvider = FakeConfigProvider.success(successResult(AppConstants.MOCK_WEBVIEW_URL))
        )

        setStartupContent(controller)

        waitForTag(UiTestTags.PUSH_PERMISSION_SCREEN)
        composeRule.onNodeWithTag(UiTestTags.PUSH_SKIP, useUnmergedTree = true).performClick()
        waitForTag(UiTestTags.WEBVIEW_SCREEN)
    }

    @Test
    fun negativeResponseShowsFanticScreen() {
        val controller = createController(
            configProvider = FakeConfigProvider.success(ConfigFetchResult.Negative("No data"))
        )

        setStartupContent(controller)

        waitForTag(UiTestTags.FANTIC_SCREEN)
    }

    @Test
    fun fanticScreenSettingsOpensDialogWithPrivacyAndSupport() {
        val controller = createController(
            configProvider = FakeConfigProvider.success(ConfigFetchResult.Negative("No data"))
        )

        setStartupContent(controller)

        waitForTag(UiTestTags.FANTIC_SCREEN)
        
        composeRule.onNodeWithContentDescription("Settings").performClick()
        
        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.onAllNodesWithTag("SETTINGS_DIALOG", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        
        assertTagExists("SETTINGS_PRIVACY_BUTTON")
        assertTagExists("SETTINGS_SUPPORT_BUTTON")
        assertTagExists("SETTINGS_CLOSE_BUTTON")
    }

    @Test
    fun offlineShowsNoInternetAndRetryRestartsFlow() {
        val networkStatusProvider = MutableNetworkStatusProvider(online = false)
        val controller = createController(
            networkStatusProvider = networkStatusProvider,
            configProvider = FakeConfigProvider.success(successResult(AppConstants.MOCK_WEBVIEW_URL))
        )

        setStartupContent(controller)

        waitForTag(UiTestTags.NO_INTERNET_SCREEN)
        networkStatusProvider.online = true
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
            configProvider = FakeConfigProvider.failure(IOException("server error"))
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
                    startupController = controller,
                    onPushPermissionAccepted = {
                        controller.recordPushPermissionResult(true)
                    }
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
    networkStatusProvider: NetworkStatusProvider = MutableNetworkStatusProvider(online = true),
    configProvider: ConfigProvider = FakeConfigProvider.success(successResult(AppConstants.MOCK_WEBVIEW_URL))
): AppStartupController {
    return AppStartupController(
        storage = storage,
        networkStatusProvider = networkStatusProvider,
        attributionProvider = FakeAttributionProvider(),
        pushTokenProvider = FakePushTokenProvider(),
        configProvider = configProvider,
        deviceDataProvider = FakeDeviceDataProvider()
    )
}

private fun successResult(url: String): ConfigFetchResult {
    return ConfigFetchResult.Success(
        url = url,
        expires = 1_893_456_000
    )
}

private class InMemoryAppStorage(
    initialMode: AppMode = AppMode.UNKNOWN,
    initialLastUrl: String? = null
) : StartupStateRepository {
    override var appMode: AppMode = initialMode
    override var lastWebViewUrl: String? = initialLastUrl
    override var pushPromptDeclinedAtSeconds: Long = 0L
    override var pushPermissionGranted: Boolean = false
    override var mockConfigScenario: MockConfigScenario = MockConfigScenario.SUCCESS_WEBVIEW
    override var configProviderMode: ConfigProviderMode = ConfigProviderMode.MOCK
    override var attributionProviderMode: AttributionProviderMode = AttributionProviderMode.MOCK
    override var pushTokenProviderMode: PushTokenProviderMode = PushTokenProviderMode.MOCK

    override fun resetLocalState() {
        appMode = AppMode.UNKNOWN
        lastWebViewUrl = null
        pushPromptDeclinedAtSeconds = 0L
        pushPermissionGranted = false
    }
}

private class MutableNetworkStatusProvider(
    var online: Boolean
) : NetworkStatusProvider {
    override fun isOnline(): Boolean = online
}

private class FakeAttributionProvider : AttributionProvider {
    override suspend fun getConversionData(): AttributionData {
        return AttributionData(values = mapOf("af_status" to "Non-organic"))
    }
}

private class FakePushTokenProvider : PushTokenProvider {
    override suspend fun getPushDataOrNull(): PushData {
        return PushData(
            pushToken = "token",
            firebaseProjectId = "project"
        )
    }
}

private class FakeConfigProvider(
    private val result: Result<ConfigFetchResult>
) : ConfigProvider {
    override suspend fun fetchConfig(
        attributionData: AttributionData,
        pushData: PushData?,
        deviceData: Map<String, Any?>
    ): ConfigFetchResult {
        return result.getOrThrow()
    }

    companion object {
        fun success(response: ConfigFetchResult): FakeConfigProvider {
            return FakeConfigProvider(Result.success(response))
        }

        fun failure(throwable: Throwable): FakeConfigProvider {
            return FakeConfigProvider(Result.failure(throwable))
        }
    }
}

private class FakeDeviceDataProvider : DeviceDataProvider {
    override fun getDeviceData(): Map<String, Any?> {
        return mapOf(
            "bundle_id" to AppConstants.APPLICATION_ID,
            "application_id" to AppConstants.APPLICATION_ID,
            "store_id" to AppConstants.APPLICATION_ID,
            "os" to "Android",
            "platform" to "Android",
            "locale" to "en-US"
        )
    }
}

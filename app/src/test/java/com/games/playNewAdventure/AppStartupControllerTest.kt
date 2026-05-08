package com.games.playNewAdventure

import com.games.playNewAdventure.service.MockConfigService
import com.games.playNewAdventure.startup.AppStartupController
import com.games.playNewAdventure.startup.domain.AppMode
import com.games.playNewAdventure.startup.domain.AttributionData
import com.games.playNewAdventure.startup.domain.AttributionProvider
import com.games.playNewAdventure.startup.domain.AttributionProviderMode
import com.games.playNewAdventure.startup.domain.ConfigDebugResultType
import com.games.playNewAdventure.startup.domain.ConfigFetchResult
import com.games.playNewAdventure.startup.domain.ConfigProvider
import com.games.playNewAdventure.startup.domain.ConfigProviderMode
import com.games.playNewAdventure.startup.domain.DEEP_LINK_SOURCE_CONVERSION
import com.games.playNewAdventure.startup.domain.DEEP_LINK_SOURCE_NONE
import com.games.playNewAdventure.startup.domain.DeviceDataProvider
import com.games.playNewAdventure.startup.domain.MockConfigScenario
import com.games.playNewAdventure.startup.domain.NetworkStatusProvider
import com.games.playNewAdventure.startup.domain.PushData
import com.games.playNewAdventure.startup.domain.PushTokenProvider
import com.games.playNewAdventure.startup.domain.PushTokenProviderMode
import com.games.playNewAdventure.startup.domain.StartupResult
import com.games.playNewAdventure.startup.domain.StartupStateRepository
import com.games.playNewAdventure.startup.domain.toTokenDebugInfo
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStartupControllerTest {
    @Test
    fun firstLaunchOnlineWithMockSuccessShowsWebViewAndSavesModeAndUrl() = runBlocking {
        val storage = InMemoryAppStorage()
        val controller = createController(
            storage = storage,
            configProvider = MockConfigService { MockConfigScenario.SUCCESS_WEBVIEW }
        )

        val result = controller.resolveStartup()

        assertTrue(result is StartupResult.ShowWebView)
        result as StartupResult.ShowWebView
        assertEquals(AppConstants.MOCK_WEBVIEW_URL, result.url)
        assertTrue(result.shouldAskPushPermission)
        assertEquals(AppMode.WEBVIEW, storage.appMode)
        assertEquals(AppConstants.MOCK_WEBVIEW_URL, storage.lastWebViewUrl)
    }

    @Test
    fun firstLaunchOnlineWithNegativeResponseShowsFanticAndSavesFanticMode() = runBlocking {
        val storage = InMemoryAppStorage()
        val controller = createController(
            storage = storage,
            configProvider = MockConfigService { MockConfigScenario.NEGATIVE_RESPONSE }
        )

        val result = controller.resolveStartup()

        assertEquals(StartupResult.ShowFantic, result)
        assertEquals(AppMode.FANTIC, storage.appMode)

        val snapshot = controller.debugSnapshot()
        assertEquals(ConfigDebugResultType.NEGATIVE, snapshot.lastConfigResponse?.resultType)
        assertEquals("FANTIC", snapshot.lastStartupDecision)
        assertEquals("Server returned negative config. Fantic is expected.", snapshot.lastStartupDecisionReason)
    }

    @Test
    fun firstLaunchWithTransientTimeoutDoesNotSaveFanticAndShowsRetryScreen() = runBlocking {
        val storage = InMemoryAppStorage()
        val controller = createController(
            storage = storage,
            configProvider = FakeConfigProvider.success(
                ConfigFetchResult.TransientError("timeout")
            )
        )

        val result = controller.resolveStartup()

        assertEquals(StartupResult.ShowNoInternet, result)
        assertEquals(AppMode.UNKNOWN, storage.appMode)
        assertEquals(null, storage.lastWebViewUrl)

        val snapshot = controller.debugSnapshot()
        assertEquals(ConfigDebugResultType.TRANSIENT_ERROR, snapshot.lastConfigResponse?.resultType)
        assertEquals("NO_INTERNET", snapshot.lastStartupDecision)
    }

    @Test
    fun firstLaunchWithIOExceptionDoesNotSaveFanticAndShowsRetryScreen() = runBlocking {
        val storage = InMemoryAppStorage()
        val controller = createController(
            storage = storage,
            configProvider = FakeConfigProvider.failure(IOException("server error"))
        )

        val result = controller.resolveStartup()

        assertEquals(StartupResult.ShowNoInternet, result)
        assertEquals(AppMode.UNKNOWN, storage.appMode)
        assertEquals(null, storage.lastWebViewUrl)
    }

    @Test
    fun firstLaunchOfflineShowsNoInternetAndKeepsUnknownMode() = runBlocking {
        val storage = InMemoryAppStorage()
        val configProvider = FakeConfigProvider.success(successResult(AppConstants.MOCK_WEBVIEW_URL))
        val controller = createController(
            storage = storage,
            networkStatusProvider = FakeNetworkStatusProvider(isOnline = false),
            configProvider = configProvider
        )

        val result = controller.resolveStartup()

        assertEquals(StartupResult.ShowNoInternet, result)
        assertEquals(AppMode.UNKNOWN, storage.appMode)
        assertEquals(0, configProvider.requestCount)
    }

    @Test
    fun subsequentWebViewOnlineFallsBackToLastUrlWhenConfigTransientErrorOrThrows() = runBlocking {
        listOf(
            FakeConfigProvider.success(ConfigFetchResult.TransientError("server error")),
            FakeConfigProvider.failure(SocketTimeoutException("timeout"))
        ).forEach { configProvider ->
            val storage = InMemoryAppStorage(
                initialMode = AppMode.WEBVIEW,
                initialLastUrl = CACHED_WEBVIEW_URL
            )
            val controller = createController(storage = storage, configProvider = configProvider)

            val result = controller.resolveStartup()

            assertTrue(result is StartupResult.ShowWebView)
            result as StartupResult.ShowWebView
            assertEquals(CACHED_WEBVIEW_URL, result.url)
            assertEquals(AppMode.WEBVIEW, storage.appMode)
            assertEquals(1, configProvider.requestCount)
        }
    }

    @Test
    fun subsequentWebViewOnlineFallsBackToLastUrlWhenConfigNegative() = runBlocking {
        val storage = InMemoryAppStorage(
            initialMode = AppMode.WEBVIEW,
            initialLastUrl = CACHED_WEBVIEW_URL
        )
        val configProvider = FakeConfigProvider.success(ConfigFetchResult.Negative("No data"))
        val controller = createController(storage = storage, configProvider = configProvider)

        val result = controller.resolveStartup()

        assertTrue(result is StartupResult.ShowWebView)
        result as StartupResult.ShowWebView
        assertEquals(CACHED_WEBVIEW_URL, result.url)
        assertEquals(AppMode.WEBVIEW, storage.appMode)
        assertEquals(1, configProvider.requestCount)
    }

    @Test
    fun subsequentWebViewOfflineShowsNoInternetAndSkipsConfig() = runBlocking {
        val storage = InMemoryAppStorage(
            initialMode = AppMode.WEBVIEW,
            initialLastUrl = CACHED_WEBVIEW_URL
        )
        val configProvider = FakeConfigProvider.success(successResult(AppConstants.MOCK_WEBVIEW_URL))
        val controller = createController(
            storage = storage,
            networkStatusProvider = FakeNetworkStatusProvider(isOnline = false),
            configProvider = configProvider
        )

        val result = controller.resolveStartup()

        assertEquals(StartupResult.ShowNoInternet, result)
        assertEquals(AppMode.WEBVIEW, storage.appMode)
        assertEquals(0, configProvider.requestCount)
    }

    @Test
    fun subsequentFanticOfflineShowsFanticAndSkipsNetworkAndConfig() = runBlocking {
        val storage = InMemoryAppStorage(initialMode = AppMode.FANTIC)
        val configProvider = FakeConfigProvider.success(successResult(AppConstants.MOCK_WEBVIEW_URL))
        val controller = createController(
            storage = storage,
            networkStatusProvider = FakeNetworkStatusProvider(isOnline = false),
            configProvider = configProvider
        )

        val result = controller.resolveStartup()

        assertEquals(StartupResult.ShowFantic, result)
        assertEquals(0, configProvider.requestCount)
    }

    @Test
    fun pushPromptIsShownWhenNeverDeclined() = runBlocking {
        val storage = InMemoryAppStorage(
            initialMode = AppMode.WEBVIEW,
            initialLastUrl = CACHED_WEBVIEW_URL
        )
        storage.pushPromptDeclinedAtSeconds = 0L
        val controller = createController(
            storage = storage,
            configProvider = FakeConfigProvider.success(ConfigFetchResult.TransientError("server error"))
        )

        val result = controller.resolveStartup() as StartupResult.ShowWebView

        assertTrue(result.shouldAskPushPermission)
    }

    @Test
    fun pushPromptIsSuppressedWhenDeclinedLessThanThreeDaysAgo() = runBlocking {
        val nowSeconds = 1_000_000L
        val storage = InMemoryAppStorage(
            initialMode = AppMode.WEBVIEW,
            initialLastUrl = CACHED_WEBVIEW_URL
        )
        storage.pushPromptDeclinedAtSeconds = nowSeconds - (THREE_DAYS_SECONDS - 1)
        val controller = createController(
            storage = storage,
            configProvider = FakeConfigProvider.success(ConfigFetchResult.TransientError("server error")),
            currentEpochSeconds = { nowSeconds }
        )

        val result = controller.resolveStartup() as StartupResult.ShowWebView

        assertFalse(result.shouldAskPushPermission)
    }

    @Test
    fun pushPromptIsShownWhenDeclinedMoreThanThreeDaysAgo() = runBlocking {
        val nowSeconds = 1_000_000L
        val storage = InMemoryAppStorage(
            initialMode = AppMode.WEBVIEW,
            initialLastUrl = CACHED_WEBVIEW_URL
        )
        storage.pushPromptDeclinedAtSeconds = nowSeconds - (THREE_DAYS_SECONDS + 1)
        val controller = createController(
            storage = storage,
            configProvider = FakeConfigProvider.success(ConfigFetchResult.TransientError("server error")),
            currentEpochSeconds = { nowSeconds }
        )

        val result = controller.resolveStartup() as StartupResult.ShowWebView

        assertTrue(result.shouldAskPushPermission)
    }

    @Test
    fun pushPermissionResultIsPersistedWithoutActivityDependency() {
        val nowSeconds = 1_000_000L
        val storage = InMemoryAppStorage()
        val controller = createController(
            storage = storage,
            currentEpochSeconds = { nowSeconds }
        )

        controller.recordPushPermissionResult(true)
        assertTrue(storage.pushPermissionGranted)
        assertEquals(0L, storage.pushPromptDeclinedAtSeconds)

        controller.recordPushPermissionResult(false)
        assertFalse(storage.pushPermissionGranted)
        assertEquals(nowSeconds, storage.pushPromptDeclinedAtSeconds)
    }

    @Test
    fun refreshDebugDiagnosticsShowsRealAppsFlyerUidWithoutMarkingConfigSent() = runBlocking {
        val storage = InMemoryAppStorage().apply {
            attributionProviderMode = AttributionProviderMode.REAL
            pushTokenProviderMode = PushTokenProviderMode.REAL
        }
        val controller = createController(
            storage = storage,
            attributionProvider = FakeAttributionProvider(mapOf("af_id" to REAL_APPS_FLYER_UID))
        )

        controller.refreshDebugDiagnostics()

        val snapshot = controller.debugSnapshot()
        assertEquals(REAL_APPS_FLYER_UID, snapshot.appsFlyerUid)
        assertTrue(snapshot.fcmTokenAvailable)
        assertFalse(snapshot.appsFlyerUidSentToConfig)
        assertFalse(snapshot.pushTokenSentToConfig)
    }

    @Test
    fun realConfigRequestMarksCurrentAfIdAndPushTokenAsSentToConfig() = runBlocking {
        val storage = InMemoryAppStorage().apply {
            configProviderMode = ConfigProviderMode.REAL
            attributionProviderMode = AttributionProviderMode.REAL
            pushTokenProviderMode = PushTokenProviderMode.REAL
        }
        val controller = createController(
            storage = storage,
            attributionProvider = FakeAttributionProvider(mapOf("af_id" to REAL_APPS_FLYER_UID))
        )

        controller.resolveStartup()

        val snapshot = controller.debugSnapshot()
        assertEquals(REAL_APPS_FLYER_UID, snapshot.appsFlyerUid)
        assertTrue(snapshot.appsFlyerUidSentToConfig)
        assertTrue(snapshot.pushTokenSentToConfig)
        assertEquals("token".toTokenDebugInfo(), snapshot.latestFcmToken)
        assertEquals("token".toTokenDebugInfo(), snapshot.lastSentPushToken)
        assertTrue(snapshot.latestFcmTokenSentToConfig)
        assertEquals(REAL_APPS_FLYER_UID, snapshot.lastSentAfId)
        assertTrue(snapshot.firebaseProjectIdSentToConfig)
        assertTrue(snapshot.bundleIdSentToConfig)
    }

    @Test
    fun refreshedTokenAfterConfigIsMarkedUnsentUntilNextStartupRequest() = runBlocking {
        val storage = InMemoryAppStorage().apply {
            configProviderMode = ConfigProviderMode.REAL
            attributionProviderMode = AttributionProviderMode.REAL
            pushTokenProviderMode = PushTokenProviderMode.REAL
        }
        val pushProvider = FakePushTokenProvider(pushToken = "first_token")
        val controller = createController(
            storage = storage,
            attributionProvider = FakeAttributionProvider(mapOf("af_id" to REAL_APPS_FLYER_UID)),
            pushTokenProvider = pushProvider
        )

        controller.resolveStartup()
        pushProvider.pushToken = "second_token"
        controller.refreshDebugDiagnostics()

        val snapshotAfterRefresh = controller.debugSnapshot()
        assertEquals("second_token".toTokenDebugInfo(), snapshotAfterRefresh.latestFcmToken)
        assertEquals("first_token".toTokenDebugInfo(), snapshotAfterRefresh.lastSentPushToken)
        assertFalse(snapshotAfterRefresh.latestFcmTokenSentToConfig)
        assertFalse(snapshotAfterRefresh.pushTokenSentToConfig)

        controller.resolveStartup()

        val snapshotAfterRestart = controller.debugSnapshot()
        assertEquals("second_token".toTokenDebugInfo(), snapshotAfterRestart.latestFcmToken)
        assertEquals("second_token".toTokenDebugInfo(), snapshotAfterRestart.lastSentPushToken)
        assertTrue(snapshotAfterRestart.latestFcmTokenSentToConfig)
        assertTrue(snapshotAfterRestart.pushTokenSentToConfig)
    }

    @Test
    fun debugSnapshotRequiresNonBlankDeepLinkValueAndAnyDeepLinkSub() = runBlocking {
        val controller = createController(
            attributionProvider = FakeAttributionProvider(
                mapOf(
                    "af_id" to REAL_APPS_FLYER_UID,
                    "deep_link_value" to "deep_link_test",
                    "deep_link_sub1" to "",
                    "deep_link_sub2" to "deep_test_sub2"
                ),
                source = DEEP_LINK_SOURCE_CONVERSION
            ),
            configProvider = MockConfigService { MockConfigScenario.SUCCESS_WEBVIEW }
        )

        controller.resolveStartup()

        val snapshot = controller.debugSnapshot()
        assertEquals("deep_link_test", snapshot.deepLinkValue)
        assertEquals(null, snapshot.deepLinkSub1)
        assertEquals("deep_test_sub2", snapshot.deepLinkSub2)
        assertTrue(snapshot.hasRequiredDeeplinkParams)
        assertEquals(DEEP_LINK_SOURCE_CONVERSION, snapshot.deepLinkSource)
        assertTrue(snapshot.lastConfigContainedDeepLinkValue)
        assertTrue(snapshot.lastConfigContainedAnyDeepLinkSub)
    }

    @Test
    fun mockConfigSuccessReturnsUrlWithMockDeepLinkParamsWhenAttributionHasThem() = runBlocking {
        val controller = createController(
            attributionProvider = FakeAttributionProvider(
                values = mapOf(
                    "af_status" to "Non-organic",
                    "af_id" to "mock_af_id_123",
                    "deep_link_value" to "deep_link_test",
                    "deep_link_sub1" to "deep_test_sub1"
                ),
                source = DEEP_LINK_SOURCE_CONVERSION
            ),
            configProvider = MockConfigService { MockConfigScenario.SUCCESS_WEBVIEW }
        )

        val result = controller.resolveStartup()

        assertTrue(result is StartupResult.ShowWebView)
        result as StartupResult.ShowWebView
        assertTrue(result.url.contains("deep_link_value=deep_link_test"))
        assertTrue(result.url.contains("deep_link_sub1=deep_test_sub1"))

        val snapshot = controller.debugSnapshot()
        assertTrue(snapshot.lastConfigContainedDeepLinkValue)
        assertTrue(snapshot.lastConfigContainedAnyDeepLinkSub)
        assertEquals(DEEP_LINK_SOURCE_CONVERSION, snapshot.deepLinkSource)
    }

    private fun createController(
        storage: InMemoryAppStorage = InMemoryAppStorage(),
        networkStatusProvider: NetworkStatusProvider = FakeNetworkStatusProvider(isOnline = true),
        configProvider: ConfigProvider = FakeConfigProvider.success(successResult(AppConstants.MOCK_WEBVIEW_URL)),
        attributionProvider: AttributionProvider = FakeAttributionProvider(),
        pushTokenProvider: PushTokenProvider = FakePushTokenProvider(),
        currentEpochSeconds: () -> Long = { 1_000L }
    ): AppStartupController {
        return AppStartupController(
            storage = storage,
            networkStatusProvider = networkStatusProvider,
            attributionProvider = attributionProvider,
            pushTokenProvider = pushTokenProvider,
            configProvider = configProvider,
            deviceDataProvider = FakeDeviceDataProvider(),
            currentEpochSeconds = currentEpochSeconds
        )
    }

    private companion object {
        const val CACHED_WEBVIEW_URL = "https://web.team-s.club/cached"
        const val THREE_DAYS_SECONDS = 259_200L
        const val REAL_APPS_FLYER_UID = "1778154434343-9031619618851208459"

        fun successResult(url: String): ConfigFetchResult {
            return ConfigFetchResult.Success(
                url = url,
                expires = 1_893_456_000
            )
        }
    }
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

private class FakeNetworkStatusProvider(
    private val isOnline: Boolean
) : NetworkStatusProvider {
    override fun isOnline(): Boolean = isOnline
}

private class FakeAttributionProvider(
    private val values: Map<String, Any?> = mapOf("af_status" to "Non-organic"),
    private val source: String = DEEP_LINK_SOURCE_NONE
) : AttributionProvider {
    override suspend fun getConversionData(): AttributionData {
        return AttributionData(
            values = values,
            deepLinkSource = source
        )
    }
}

private class FakePushTokenProvider(
    var pushToken: String = "token",
    private val firebaseProjectId: String = "project"
) : PushTokenProvider {
    override suspend fun getPushDataOrNull(): PushData {
        return PushData(
            pushToken = pushToken,
            firebaseProjectId = firebaseProjectId
        )
    }
}

private class FakeConfigProvider(
    private val result: Result<ConfigFetchResult>
) : ConfigProvider {
    var requestCount: Int = 0
        private set

    override suspend fun fetchConfig(
        attributionData: AttributionData,
        pushData: PushData?,
        deviceData: Map<String, Any?>
    ): ConfigFetchResult {
        requestCount += 1
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

package com.games.playNewAdventure

import android.app.Activity
import com.games.playNewAdventure.data.StartupStorage
import com.games.playNewAdventure.service.AttributionService
import com.games.playNewAdventure.service.ConfigService
import com.games.playNewAdventure.service.DeviceDataProvider
import com.games.playNewAdventure.service.MockConfigService
import com.games.playNewAdventure.service.NetworkChecker
import com.games.playNewAdventure.service.PushService
import com.games.playNewAdventure.startup.AppMode
import com.games.playNewAdventure.startup.AppStartupController
import com.games.playNewAdventure.startup.AttributionData
import com.games.playNewAdventure.startup.ConfigProviderMode
import com.games.playNewAdventure.startup.ConfigResponse
import com.games.playNewAdventure.startup.MockConfigScenario
import com.games.playNewAdventure.startup.PushData
import com.games.playNewAdventure.startup.StartupResult
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
            configService = MockConfigService { MockConfigScenario.SUCCESS_WEBVIEW }
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
            configService = MockConfigService { MockConfigScenario.NEGATIVE_RESPONSE }
        )

        val result = controller.resolveStartup()

        assertEquals(StartupResult.ShowFantic, result)
        assertEquals(AppMode.FANTIC, storage.appMode)
    }

    @Test
    fun firstLaunchOfflineShowsNoInternetAndKeepsUnknownMode() = runBlocking {
        val storage = InMemoryAppStorage()
        val configService = FakeConfigService.success(successResponse(AppConstants.MOCK_WEBVIEW_URL))
        val controller = createController(
            storage = storage,
            networkChecker = FakeNetworkChecker(isOnline = false),
            configService = configService
        )

        val result = controller.resolveStartup()

        assertEquals(StartupResult.ShowNoInternet, result)
        assertEquals(AppMode.UNKNOWN, storage.appMode)
        assertEquals(0, configService.requestCount)
    }

    @Test
    fun subsequentWebViewOnlineFallsBackToLastUrlWhenConfigServerErrorOrTimeout() = runBlocking {
        listOf(
            IOException("server error"),
            SocketTimeoutException("timeout")
        ).forEach { failure ->
            val storage = InMemoryAppStorage(
                initialMode = AppMode.WEBVIEW,
                initialLastUrl = CACHED_WEBVIEW_URL
            )
            val configService = FakeConfigService.failure(failure)
            val controller = createController(storage = storage, configService = configService)

            val result = controller.resolveStartup()

            assertTrue(result is StartupResult.ShowWebView)
            result as StartupResult.ShowWebView
            assertEquals(CACHED_WEBVIEW_URL, result.url)
            assertEquals(AppMode.WEBVIEW, storage.appMode)
            assertEquals(1, configService.requestCount)
        }
    }

    @Test
    fun subsequentWebViewOfflineShowsNoInternetAndSkipsConfig() = runBlocking {
        val storage = InMemoryAppStorage(
            initialMode = AppMode.WEBVIEW,
            initialLastUrl = CACHED_WEBVIEW_URL
        )
        val configService = FakeConfigService.success(successResponse(AppConstants.MOCK_WEBVIEW_URL))
        val controller = createController(
            storage = storage,
            networkChecker = FakeNetworkChecker(isOnline = false),
            configService = configService
        )

        val result = controller.resolveStartup()

        assertEquals(StartupResult.ShowNoInternet, result)
        assertEquals(AppMode.WEBVIEW, storage.appMode)
        assertEquals(0, configService.requestCount)
    }

    @Test
    fun subsequentFanticOfflineShowsFanticAndSkipsNetworkAndConfig() = runBlocking {
        val storage = InMemoryAppStorage(initialMode = AppMode.FANTIC)
        val configService = FakeConfigService.success(successResponse(AppConstants.MOCK_WEBVIEW_URL))
        val controller = createController(
            storage = storage,
            networkChecker = FakeNetworkChecker(isOnline = false),
            configService = configService
        )

        val result = controller.resolveStartup()

        assertEquals(StartupResult.ShowFantic, result)
        assertEquals(0, configService.requestCount)
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
            configService = FakeConfigService.failure(IOException("server error"))
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
            configService = FakeConfigService.failure(IOException("server error")),
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
            configService = FakeConfigService.failure(IOException("server error")),
            currentEpochSeconds = { nowSeconds }
        )

        val result = controller.resolveStartup() as StartupResult.ShowWebView

        assertTrue(result.shouldAskPushPermission)
    }

    private fun createController(
        storage: InMemoryAppStorage = InMemoryAppStorage(),
        networkChecker: NetworkChecker = FakeNetworkChecker(isOnline = true),
        configService: ConfigService = FakeConfigService.success(successResponse(AppConstants.MOCK_WEBVIEW_URL)),
        currentEpochSeconds: () -> Long = { 1_000L }
    ): AppStartupController {
        return AppStartupController(
            storage = storage,
            networkChecker = networkChecker,
            attributionService = FakeAttributionService(),
            pushService = FakePushService(),
            configService = configService,
            deviceDataProvider = FakeDeviceDataProvider(),
            currentEpochSeconds = currentEpochSeconds
        )
    }

    private companion object {
        const val CACHED_WEBVIEW_URL = "https://web.team-s.club/cached"
        const val THREE_DAYS_SECONDS = 259_200L

        fun successResponse(url: String): ConfigResponse {
            return ConfigResponse(
                ok = true,
                url = url,
                message = null,
                expires = 1_893_456_000
            )
        }
    }
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

private class FakeNetworkChecker(
    private val isOnline: Boolean
) : NetworkChecker {
    override fun isOnline(): Boolean = isOnline
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
    var requestCount: Int = 0
        private set

    override suspend fun requestConfig(
        attributionData: AttributionData,
        pushData: PushData?,
        deviceData: Map<String, Any?>
    ): ConfigResponse {
        requestCount += 1
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

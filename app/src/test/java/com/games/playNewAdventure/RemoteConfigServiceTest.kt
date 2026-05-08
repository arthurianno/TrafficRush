package com.games.playNewAdventure

import com.games.playNewAdventure.service.RemoteConfigService
import com.games.playNewAdventure.startup.domain.AttributionData
import com.games.playNewAdventure.startup.domain.ConfigDebugResultType
import com.games.playNewAdventure.startup.domain.ConfigFetchResult
import com.games.playNewAdventure.startup.domain.PushData
import com.games.playNewAdventure.startup.domain.toTokenDebugInfo
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class RemoteConfigServiceTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun requestConfigWithSuccessfulResponseReturnsParsedConfig() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "ok": true,
                      "url": "https://web.team-s.club/",
                      "expires": 1893456000
                    }
                    """.trimIndent()
                )
        )

        val service = createService()
        val response = service.fetchConfig(
            attributionData = fakeAttributionData(),
            pushData = fakePushData(),
            deviceData = fakeDeviceData()
        )

        assertTrue(response is ConfigFetchResult.Success)
        response as ConfigFetchResult.Success
        assertEquals("https://web.team-s.club/", response.url)
        assertEquals(1_893_456_000L, response.expires)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("application/json", request.getHeader("Accept"))
        assertTrue(request.getHeader("Content-Type")?.startsWith("application/json") == true)

        val requestJson = JSONObject(request.body.readUtf8())
        assertEquals("Non-organic", requestJson.getString("af_status"))
        assertEquals("deep_link_test", requestJson.getString("deep_link_value"))
        assertEquals("deep_test_sub1", requestJson.getString("deep_link_sub1"))
        assertEquals("mock_af_id_123", requestJson.getString("af_id"))
        assertEquals("token", requestJson.getString("push_token"))
        assertEquals(AppConstants.FIREBASE_PROJECT_ID, requestJson.getString("firebase_project_id"))
        assertEquals(AppConstants.APPLICATION_ID, requestJson.getString("bundle_id"))
        assertEquals(AppConstants.APPLICATION_ID, requestJson.getString("application_id"))
        assertEquals("Android", requestJson.getString("platform"))

        val diagnostics = service.lastConfigDebugSnapshot()
        assertEquals(200, diagnostics?.httpStatus)
        assertEquals(ConfigDebugResultType.SUCCESS, diagnostics?.resultType)
        assertEquals(true, diagnostics?.ok)
        assertEquals("https://web.team-s.club/", diagnostics?.url)
        assertTrue(diagnostics?.requestContainedAfId == true)
        assertTrue(diagnostics?.requestContainedPushToken == true)
        assertTrue(diagnostics?.requestContainedFirebaseProjectId == true)
        assertTrue(diagnostics?.requestContainedBundleId == true)
        assertTrue(diagnostics?.requestContainedAnyDeepLinkSub == true)
        assertEquals("mock_af_id_123", diagnostics?.requestAfId)
        assertEquals("token".toTokenDebugInfo(), diagnostics?.requestPushToken)
        assertEquals(AppConstants.FIREBASE_PROJECT_ID, diagnostics?.requestFirebaseProjectId)
        assertEquals(AppConstants.APPLICATION_ID, diagnostics?.requestBundleId)
        assertEquals("Non-organic", diagnostics?.requestAfStatus)
        assertEquals("deep_link_test", diagnostics?.requestDeepLinkValue)
    }

    @Test
    fun requestConfigWith404NoDataReturnsNegative() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"ok":false,"message":"No data"}""")
        )

        val service = createService()
        val response = service.fetchConfig(
            attributionData = fakeAttributionData(),
            pushData = fakePushData(),
            deviceData = fakeDeviceData()
        )

        assertTrue(response is ConfigFetchResult.Negative)
        response as ConfigFetchResult.Negative
        assertEquals("No data", response.message)

        val diagnostics = service.lastConfigDebugSnapshot()
        assertEquals(404, diagnostics?.httpStatus)
        assertEquals(ConfigDebugResultType.NEGATIVE, diagnostics?.resultType)
        assertEquals(false, diagnostics?.ok)
        assertEquals("No data", diagnostics?.errorMessage)
    }

    @Test
    fun requestConfigWith5xxReturnsTransientError() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"ok":false,"message":"Server error"}""")
        )

        val response = createService().fetchConfig(
            attributionData = fakeAttributionData(),
            pushData = fakePushData(),
            deviceData = fakeDeviceData()
        )

        assertTrue(response is ConfigFetchResult.TransientError)
    }

    @Test
    fun requestConfigWithInvalidJsonReturnsTransientError() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("not json")
        )

        val response = createService().fetchConfig(
            attributionData = fakeAttributionData(),
            pushData = fakePushData(),
            deviceData = fakeDeviceData()
        )

        assertTrue(response is ConfigFetchResult.TransientError)
    }

    @Test
    fun requestConfigWithTimeoutReturnsTransientError() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"ok":true,"url":"https://web.team-s.club/"}""")
                .setBodyDelay(500, TimeUnit.MILLISECONDS)
        )

        val response = createService(readTimeoutMs = 100).fetchConfig(
            attributionData = fakeAttributionData(),
            pushData = fakePushData(),
            deviceData = fakeDeviceData()
        )

        assertTrue(response is ConfigFetchResult.TransientError)
    }

    private fun createService(readTimeoutMs: Int = 2_000): RemoteConfigService {
        return RemoteConfigService(
            configUrl = server.url("/config.php").toString(),
            connectTimeoutMs = 2_000,
            readTimeoutMs = readTimeoutMs
        )
    }
}

class RemoteConfigServiceRealIntegrationTest {
    @Test
    fun realConfigEndpointSmokeCheck() = runBlocking {
        assumeTrue(
            "Real config smoke check is opt-in. Set RUN_REAL_CONFIG_TEST=true or -PrunRealConfigTest=true.",
            shouldRunRealConfigTest()
        )

        val response = RemoteConfigService(connectTimeoutMs = 5_000, readTimeoutMs = 5_000)
            .fetchConfig(
                attributionData = fakeAttributionData(),
                pushData = fakePushData(),
                deviceData = fakeDeviceData()
            )

        when (response) {
            is ConfigFetchResult.Success ->
                assertFalse(response.url.isBlank())
            is ConfigFetchResult.Negative ->
                assertTrue(response.message?.isNotBlank() != false)
            is ConfigFetchResult.TransientError ->
                assertTrue(response.reason?.isNotBlank() != false)
        }
    }

    private fun shouldRunRealConfigTest(): Boolean {
        return System.getenv("RUN_REAL_CONFIG_TEST").equals("true", ignoreCase = true) ||
            System.getProperty("runRealConfigTest").equals("true", ignoreCase = true)
    }
}

private fun fakeAttributionData(): AttributionData {
    return AttributionData(
        values = mapOf(
            "af_status" to "Non-organic",
            "campaign" to "mock_campaign",
            "media_source" to "Facebook Ads",
            "af_sub1" to "mock_sub1",
            "af_id" to "mock_af_id_123",
            "deep_link_value" to "deep_link_test",
            "deep_link_sub1" to "deep_test_sub1",
            "is_first_launch" to true
        )
    )
}

private fun fakePushData(): PushData {
    return PushData(
        pushToken = "token",
        firebaseProjectId = AppConstants.FIREBASE_PROJECT_ID,
        firebaseProjectNumber = AppConstants.FIREBASE_PROJECT_NUMBER
    )
}

private fun fakeDeviceData(): Map<String, Any?> {
    return mapOf(
        "bundle_id" to AppConstants.APPLICATION_ID,
        "application_id" to AppConstants.APPLICATION_ID,
        "store_id" to AppConstants.APPLICATION_ID,
        "os" to "Android",
        "platform" to "Android",
        "locale" to "en-US"
    )
}

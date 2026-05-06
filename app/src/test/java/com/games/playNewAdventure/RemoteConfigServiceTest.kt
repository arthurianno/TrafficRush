package com.games.playNewAdventure

import com.games.playNewAdventure.service.RemoteConfigService
import com.games.playNewAdventure.startup.AttributionData
import com.games.playNewAdventure.startup.PushData
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONException
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

        val response = createService().requestConfig(
            attributionData = fakeAttributionData(),
            pushData = fakePushData(),
            deviceData = fakeDeviceData()
        )

        assertTrue(response.ok)
        assertEquals("https://web.team-s.club/", response.url)
        assertEquals(1_893_456_000L, response.expires)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("application/json", request.getHeader("Accept"))
        assertTrue(request.getHeader("Content-Type")?.startsWith("application/json") == true)

        val requestJson = JSONObject(request.body.readUtf8())
        assertEquals("Non-organic", requestJson.getString("af_status"))
        assertEquals("token", requestJson.getString("push_token"))
        assertEquals(AppConstants.APPLICATION_ID, requestJson.getString("bundle_id"))
    }

    @Test
    fun requestConfigWithHttpErrorThrowsIOException() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"ok":false,"message":"No data"}""")
        )

        val failure = runCatching {
            createService().requestConfig(
                attributionData = fakeAttributionData(),
                pushData = fakePushData(),
                deviceData = fakeDeviceData()
            )
        }.exceptionOrNull()

        assertTrue(failure is IOException)
    }

    @Test
    fun requestConfigWithInvalidJsonThrowsParsingFailure() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("not json")
        )

        val failure = runCatching {
            createService().requestConfig(
                attributionData = fakeAttributionData(),
                pushData = fakePushData(),
                deviceData = fakeDeviceData()
            )
        }.exceptionOrNull()

        assertTrue(failure is JSONException)
    }

    @Test
    fun requestConfigWithTimeoutThrowsTimeoutFailure() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"ok":true,"url":"https://web.team-s.club/"}""")
                .setBodyDelay(500, TimeUnit.MILLISECONDS)
        )

        val failure = runCatching {
            createService(readTimeoutMs = 100).requestConfig(
                attributionData = fakeAttributionData(),
                pushData = fakePushData(),
                deviceData = fakeDeviceData()
            )
        }.exceptionOrNull()

        assertTrue(failure is SocketTimeoutException)
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

        val result = runCatching {
            RemoteConfigService(connectTimeoutMs = 5_000, readTimeoutMs = 5_000)
                .requestConfig(
                    attributionData = fakeAttributionData(),
                    pushData = fakePushData(),
                    deviceData = fakeDeviceData()
                )
        }

        result.onSuccess { response ->
            if (response.ok) {
                assertFalse(response.url.isNullOrBlank())
            } else {
                assertTrue(response.message != null || response.url.isNullOrBlank())
            }
        }.onFailure { failure ->
            assertTrue(
                "Real config may fail with transport/HTTP errors, but should not fail unexpectedly: $failure",
                failure is IOException
            )
        }
        Unit
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
        "store_id" to AppConstants.APPLICATION_ID,
        "os" to "Android",
        "locale" to "en-US"
    )
}

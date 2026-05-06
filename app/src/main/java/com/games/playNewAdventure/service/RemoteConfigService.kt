package com.games.playNewAdventure.service

import com.games.playNewAdventure.AppConstants
import com.games.playNewAdventure.startup.AttributionData
import com.games.playNewAdventure.startup.ConfigResponse
import com.games.playNewAdventure.startup.PushData
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class RemoteConfigService(
    private val configUrl: String = AppConstants.CONFIG_URL,
    private val connectTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_TIMEOUT_MS
) : ConfigService {
    override suspend fun requestConfig(
        attributionData: AttributionData,
        pushData: PushData?,
        deviceData: Map<String, Any?>
    ): ConfigResponse = withContext(Dispatchers.IO) {
        val body = buildRequestBody(
            attributionData = attributionData,
            pushData = pushData,
            deviceData = deviceData
        )

        val connection = (URL(configUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = METHOD_POST
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON)
            setRequestProperty(HEADER_ACCEPT, CONTENT_TYPE_JSON)
        }

        try {
            val bytes = body.toString().toByteArray(StandardCharsets.UTF_8)
            connection.outputStream.use { outputStream ->
                outputStream.write(bytes)
            }

            val responseCode = connection.responseCode
            val responseBody = connection.readResponseBody(responseCode)

            if (responseCode !in HTTP_SUCCESS_RANGE) {
                throw IOException("Config request failed with HTTP $responseCode")
            }

            parseConfigResponse(responseBody)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildRequestBody(
        attributionData: AttributionData,
        pushData: PushData?,
        deviceData: Map<String, Any?>
    ): JSONObject {
        return JSONObject().apply {
            attributionData.values.forEach { (key, value) ->
                putWrapped(key, value)
            }

            pushData?.pushToken?.takeIf { it.isNotBlank() }?.let { token ->
                put(KEY_PUSH_TOKEN, token)
            }
            pushData?.firebaseProjectId?.takeIf { it.isNotBlank() }?.let { projectId ->
                put(KEY_FIREBASE_PROJECT_ID, projectId)
            }
            pushData?.firebaseProjectNumber?.takeIf { it.isNotBlank() }?.let { projectNumber ->
                put(KEY_FIREBASE_PROJECT_NUMBER, projectNumber)
            }

            deviceData.forEach { (key, value) ->
                putWrapped(key, value)
            }
        }
    }

    private fun parseConfigResponse(responseBody: String): ConfigResponse {
        val json = JSONObject(responseBody)
        return ConfigResponse(
            ok = json.optBoolean(KEY_OK, false),
            url = json.optionalString(KEY_URL),
            message = json.optionalString(KEY_MESSAGE),
            expires = if (json.has(KEY_EXPIRES) && !json.isNull(KEY_EXPIRES)) {
                json.optLong(KEY_EXPIRES)
            } else {
                null
            }
        )
    }

    private fun JSONObject.putWrapped(key: String, value: Any?) {
        put(key, value?.let(JSONObject::wrap) ?: JSONObject.NULL)
    }

    private fun JSONObject.optionalString(key: String): String? {
        return if (has(key) && !isNull(key)) {
            optString(key)
        } else {
            null
        }
    }

    private fun HttpURLConnection.readResponseBody(responseCode: Int): String {
        val stream = if (responseCode in HTTP_SUCCESS_RANGE) {
            inputStream
        } else {
            errorStream
        } ?: return ""

        return stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            reader.readText()
        }
    }

    private companion object {
        const val METHOD_POST = "POST"
        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_ACCEPT = "Accept"
        const val CONTENT_TYPE_JSON = "application/json"
        const val DEFAULT_TIMEOUT_MS = 15_000

        const val KEY_PUSH_TOKEN = "push_token"
        const val KEY_FIREBASE_PROJECT_ID = "firebase_project_id"
        const val KEY_FIREBASE_PROJECT_NUMBER = "firebase_project_number"
        const val KEY_OK = "ok"
        const val KEY_URL = "url"
        const val KEY_MESSAGE = "message"
        const val KEY_EXPIRES = "expires"

        val HTTP_SUCCESS_RANGE = 200..299
    }
}

package com.games.playNewAdventure.service

import android.util.Log
import com.games.playNewAdventure.AppConstants
import com.games.playNewAdventure.BuildConfig
import com.games.playNewAdventure.startup.domain.AttributionData
import com.games.playNewAdventure.startup.domain.ConfigDebugResultType
import com.games.playNewAdventure.startup.domain.ConfigDebugSnapshot
import com.games.playNewAdventure.startup.domain.ConfigDiagnosticsProvider
import com.games.playNewAdventure.startup.domain.ConfigFetchResult
import com.games.playNewAdventure.startup.domain.ConfigProvider
import com.games.playNewAdventure.startup.domain.ConfigProviderMode
import com.games.playNewAdventure.startup.domain.PushData
import com.games.playNewAdventure.startup.domain.TokenDebugInfo
import com.games.playNewAdventure.startup.domain.toTokenDebugInfo
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
) : ConfigProvider, ConfigDiagnosticsProvider {
    @Volatile private var lastConfigDebugSnapshot: ConfigDebugSnapshot? = null

    override suspend fun fetchConfig(
        attributionData: AttributionData,
        pushData: PushData?,
        deviceData: Map<String, Any?>
    ): ConfigFetchResult = withContext(Dispatchers.IO) {
        var requestDebug: ConfigRequestDebug? = null
        var responseCode: Int? = null
        var responseBody: String? = null

        runCatching {
            val body = buildRequestBody(
                attributionData = attributionData,
                pushData = pushData,
                deviceData = deviceData
            )
            requestDebug = body.toRequestDebug()
            logDebug(
                "Config request started: source=REAL " +
                    "containsAfId=${requestDebug?.containsAfId} " +
                    "containsPushToken=${requestDebug?.containsPushToken} " +
                    "containsFirebaseProjectId=${requestDebug?.containsFirebaseProjectId} " +
                    "containsBundleId=${requestDebug?.containsBundleId} " +
                    "containsAnyDeepLinkSub=${requestDebug?.containsAnyDeepLinkSub} " +
                    "af_status=${requestDebug?.afStatus ?: "-"} " +
                    "deep_link_value=${requestDebug?.deepLinkValue ?: "-"}"
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

                responseCode = connection.responseCode
                responseBody = connection.readResponseBody(responseCode ?: 0)
                logDebug("Config response HTTP status $responseCode")
                logDebug("Config response body sanitized ${sanitizeResponseBody(responseBody.orEmpty()) ?: "-"}")

                val result = classifyResponse(responseCode ?: 0, responseBody.orEmpty())
                recordConfigDiagnostics(
                    httpStatus = responseCode,
                    responseBody = responseBody,
                    requestDebug = requestDebug,
                    result = result
                )
                logParsedConfigResult(result)
                result
            } finally {
                connection.disconnect()
            }
        }.getOrElse { failure ->
            val result = ConfigFetchResult.TransientError(failure.message)
            recordConfigDiagnostics(
                httpStatus = responseCode,
                responseBody = responseBody,
                requestDebug = requestDebug,
                result = result
            )
            logParsedConfigResult(result)
            result
        }
    }

    override fun lastConfigDebugSnapshot(): ConfigDebugSnapshot? = lastConfigDebugSnapshot

    private fun buildRequestBody(
        attributionData: AttributionData,
        pushData: PushData?,
        deviceData: Map<String, Any?>
    ): JSONObject {
        logDebug("push_token sent to config ${!pushData?.pushToken.isNullOrBlank()}")
        return JSONObject().apply {
            attributionData.values.forEach { (key, value) ->
                putWrapped(key, value)
            }

            pushData?.pushToken?.takeIf { it.isNotBlank() }?.let { token ->
                put(KEY_PUSH_TOKEN, token)
                val tokenInfo = token.toTokenDebugInfo()
                logDebug(
                    "push_token length=${tokenInfo?.length ?: 0}, " +
                        "hash=${tokenInfo?.sha256 ?: "-"}, last4=${tokenInfo?.last4 ?: "-"}"
                )
            }
            pushData?.firebaseProjectId?.takeIf { it.isNotBlank() }?.let { projectId ->
                put(KEY_FIREBASE_PROJECT_ID, projectId)
            }
            pushData?.firebaseProjectNumber?.takeIf { it.isNotBlank() }?.let { projectNumber ->
                put(KEY_FIREBASE_PROJECT_NUMBER, projectNumber)
            }
            put(KEY_BUNDLE_ID, AppConstants.APPLICATION_ID)

            deviceData.forEach { (key, value) ->
                putWrapped(key, value)
            }
        }
    }

    private fun JSONObject.toRequestDebug(): ConfigRequestDebug {
        var anyDeepLinkSub = false
        keys().forEach { key ->
            if (key.startsWith(KEY_DEEP_LINK_SUB_PREFIX) && hasNonBlankString(key)) {
                anyDeepLinkSub = true
            }
        }
        return ConfigRequestDebug(
            containsAfId = hasNonBlankString(KEY_AF_ID),
            containsPushToken = hasNonBlankString(KEY_PUSH_TOKEN),
            containsFirebaseProjectId = hasNonBlankString(KEY_FIREBASE_PROJECT_ID),
            containsBundleId = hasNonBlankString(KEY_BUNDLE_ID),
            afId = optionalNonBlankString(KEY_AF_ID),
            pushToken = optionalNonBlankString(KEY_PUSH_TOKEN).toTokenDebugInfo(),
            firebaseProjectId = optionalNonBlankString(KEY_FIREBASE_PROJECT_ID),
            bundleId = optionalNonBlankString(KEY_BUNDLE_ID),
            afStatus = optionalString(KEY_AF_STATUS),
            deepLinkValue = optionalString(KEY_DEEP_LINK_VALUE),
            containsAnyDeepLinkSub = anyDeepLinkSub
        )
    }

    private fun classifyResponse(responseCode: Int, responseBody: String): ConfigFetchResult {
        return when {
            responseCode in HTTP_SUCCESS_RANGE ->
                parseConfigResponse(responseBody)

            responseCode in HTTP_TRANSIENT_ERROR_RANGE ->
                ConfigFetchResult.TransientError("HTTP $responseCode")

            responseCode == HTTP_NOT_FOUND ->
                parseConfigResponseOrNegative(responseBody, "HTTP $responseCode")

            responseCode in HTTP_CLIENT_ERROR_RANGE ->
                parseConfigResponseOrNegative(responseBody, "HTTP $responseCode")

            else ->
                ConfigFetchResult.TransientError("HTTP $responseCode")
        }
    }

    private fun parseConfigResponseOrNegative(
        responseBody: String,
        fallbackMessage: String
    ): ConfigFetchResult {
        return runCatching {
            when (val parsed = parseConfigResponse(responseBody)) {
                is ConfigFetchResult.Negative -> parsed
                is ConfigFetchResult.Success -> ConfigFetchResult.Negative(fallbackMessage)
                is ConfigFetchResult.TransientError -> parsed
            }
        }.getOrElse {
            ConfigFetchResult.Negative(fallbackMessage)
        }
    }

    private fun parseConfigResponse(responseBody: String): ConfigFetchResult {
        val json = JSONObject(responseBody)
        val message = json.optionalString(KEY_MESSAGE)
        val url = json.optionalString(KEY_URL)?.trim()

        return if (json.optBoolean(KEY_OK, false) && !url.isNullOrBlank()) {
            ConfigFetchResult.Success(
                url = url,
                expires = if (json.has(KEY_EXPIRES) && !json.isNull(KEY_EXPIRES)) {
                    json.optLong(KEY_EXPIRES)
                } else {
                    null
                }
            )
        } else {
            ConfigFetchResult.Negative(message)
        }
    }

    private fun recordConfigDiagnostics(
        httpStatus: Int?,
        responseBody: String?,
        requestDebug: ConfigRequestDebug?,
        result: ConfigFetchResult
    ) {
        lastConfigDebugSnapshot = ConfigDebugSnapshot(
            source = ConfigProviderMode.REAL,
            httpStatus = httpStatus,
            resultType = result.toDebugResultType(),
            ok = parseOkValue(responseBody) ?: result.defaultOkValue(),
            url = parseUrlValue(responseBody) ?: (result as? ConfigFetchResult.Success)?.url,
            errorMessage = result.errorMessage(),
            requestContainedAfId = requestDebug?.containsAfId == true,
            requestContainedPushToken = requestDebug?.containsPushToken == true,
            requestContainedFirebaseProjectId = requestDebug?.containsFirebaseProjectId == true,
            requestContainedBundleId = requestDebug?.containsBundleId == true,
            requestContainedAnyDeepLinkSub = requestDebug?.containsAnyDeepLinkSub == true,
            requestAfId = requestDebug?.afId,
            requestPushToken = requestDebug?.pushToken,
            requestFirebaseProjectId = requestDebug?.firebaseProjectId,
            requestBundleId = requestDebug?.bundleId,
            requestAfStatus = requestDebug?.afStatus,
            requestDeepLinkValue = requestDebug?.deepLinkValue,
            sanitizedResponseBody = sanitizeResponseBody(responseBody.orEmpty())
        )
    }

    private fun logParsedConfigResult(result: ConfigFetchResult) {
        val snapshot = lastConfigDebugSnapshot
        logDebug(
            "Parsed config result ${result.toDebugResultType().displayName}: " +
                "ok=${snapshot?.ok ?: "-"} " +
                "url=${snapshot?.url ?: "-"} " +
                "error=${snapshot?.errorMessage ?: "-"}"
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

    private fun parseOkValue(responseBody: String?): Boolean? {
        val rawBody = responseBody?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            JSONObject(rawBody).takeIf { it.has(KEY_OK) && !it.isNull(KEY_OK) }?.optBoolean(KEY_OK)
        }.getOrNull()
    }

    private fun parseUrlValue(responseBody: String?): String? {
        val rawBody = responseBody?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            JSONObject(rawBody).optionalString(KEY_URL)?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun sanitizeResponseBody(responseBody: String): String? {
        val rawBody = responseBody.trim().takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            val source = JSONObject(rawBody)
            JSONObject().apply {
                listOf(KEY_OK, KEY_URL, KEY_MESSAGE, KEY_EXPIRES).forEach { key ->
                    if (source.has(key) && !source.isNull(key)) {
                        val value = source.get(key)
                        put(
                            key,
                            if (key == KEY_URL && value is String) {
                                value.maskSensitiveQueryValues()
                            } else {
                                value
                            }
                        )
                    }
                }
            }.toString()
        }.getOrElse {
            rawBody.take(MAX_SANITIZED_RESPONSE_CHARS)
        }
    }

    private fun JSONObject.putWrapped(key: String, value: Any?) {
        put(key, value?.let(JSONObject::wrap) ?: JSONObject.NULL)
    }

    private fun JSONObject.hasNonBlankString(key: String): Boolean {
        return has(key) && !isNull(key) && optString(key).isNotBlank()
    }

    private fun JSONObject.optionalString(key: String): String? {
        return if (has(key) && !isNull(key)) {
            optString(key)
        } else {
            null
        }
    }

    private fun JSONObject.optionalNonBlankString(key: String): String? {
        return optionalString(key)?.takeIf { it.isNotBlank() }
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
        const val MAX_SANITIZED_RESPONSE_CHARS = 500

        const val KEY_AF_ID = "af_id"
        const val KEY_AF_STATUS = "af_status"
        const val KEY_DEEP_LINK_VALUE = "deep_link_value"
        const val KEY_DEEP_LINK_SUB_PREFIX = "deep_link_sub"
        const val KEY_PUSH_TOKEN = "push_token"
        const val KEY_FIREBASE_PROJECT_ID = "firebase_project_id"
        const val KEY_FIREBASE_PROJECT_NUMBER = "firebase_project_number"
        const val KEY_BUNDLE_ID = "bundle_id"
        const val KEY_OK = "ok"
        const val KEY_URL = "url"
        const val KEY_MESSAGE = "message"
        const val KEY_EXPIRES = "expires"

        val HTTP_SUCCESS_RANGE = 200..299
        val HTTP_CLIENT_ERROR_RANGE = 400..499
        val HTTP_TRANSIENT_ERROR_RANGE = 500..599
        const val HTTP_NOT_FOUND = 404
    }
}

private fun String.maskSensitiveQueryValues(): String {
    return SENSITIVE_URL_PARAM_KEYS.fold(this) { current, key ->
        current.replace(Regex("([?&]$key=)[^&#]*")) { matchResult ->
            "${matchResult.groupValues[1]}MASKED"
        }
    }
}

private data class ConfigRequestDebug(
    val containsAfId: Boolean,
    val containsPushToken: Boolean,
    val containsFirebaseProjectId: Boolean,
    val containsBundleId: Boolean,
    val afId: String?,
    val pushToken: TokenDebugInfo?,
    val firebaseProjectId: String?,
    val bundleId: String?,
    val afStatus: String?,
    val deepLinkValue: String?,
    val containsAnyDeepLinkSub: Boolean
)

private val SENSITIVE_URL_PARAM_KEYS = listOf("push_token", "sub_id_7")

private fun logDebug(message: String) {
    if (BuildConfig.DEBUG) {
        runCatching {
            Log.d("RemoteConfigService", message)
        }
    }
}

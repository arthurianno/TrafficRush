package com.games.playNewAdventure.service

import android.content.Context
import android.util.Log
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.appsflyer.deeplink.DeepLinkResult
import com.games.playNewAdventure.AppConstants
import com.games.playNewAdventure.BuildConfig
import com.games.playNewAdventure.startup.domain.AttributionData
import com.games.playNewAdventure.startup.domain.AttributionProvider
import com.games.playNewAdventure.startup.domain.DEEP_LINK_SOURCE_APP_OPEN_ATTRIBUTION
import com.games.playNewAdventure.startup.domain.DEEP_LINK_SOURCE_CONVERSION
import com.games.playNewAdventure.startup.domain.DEEP_LINK_SOURCE_NONE
import com.games.playNewAdventure.startup.domain.DEEP_LINK_SOURCE_UDL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

class AppsFlyerAttributionService(
    context: Context,
    private val devKey: String = AppConstants.APPSFLYER_DEV_KEY
) : AttributionProvider {
    private val appContext = context.applicationContext
    private val attributionDataLock = Any()
    @Volatile private var cachedConversionData: Map<String, Any?>? = null
    @Volatile private var cachedDeepLinkSource: String = DEEP_LINK_SOURCE_NONE

    override suspend fun getConversionData(): AttributionData = withContext(Dispatchers.IO) {
        val conversionData = cachedConversionData ?: waitForConversionData()

        val values = conversionData.toMutableMap()
        val appsFlyerUid = AppsFlyerLib.getInstance().getAppsFlyerUID(appContext)
        logDebug("AppsFlyer UID loaded: ${appsFlyerUid.orEmpty()}")
        appsFlyerUid
            ?.takeIf { it.isNotBlank() }
            ?.let { appsFlyerUid ->
                values[KEY_AF_ID] = appsFlyerUid
            }

        AttributionData(
            values = values,
            deepLinkSource = currentDeepLinkSource()
        )
    }

    private suspend fun waitForConversionData(): Map<String, Any?> {
        return withTimeoutOrNull(CONVERSION_DATA_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val resumed = AtomicBoolean(false)
                fun resumeWithAttributionData(values: Map<String, Any?>) {
                    if (resumed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(values)
                    }
                }

                val listener = object : AppsFlyerConversionListener {
                    override fun onConversionDataSuccess(conversionData: MutableMap<String, Any>?) {
                        val mergedData = mergeAttributionData(
                            values = conversionData.orEmpty(),
                            source = DEEP_LINK_SOURCE_CONVERSION
                        )
                        logDebug("onConversionDataSuccess ${mergedData.toDeepLinkLogSummary()}")
                        resumeWithAttributionData(mergedData)
                    }

                    override fun onConversionDataFail(errorMessage: String?) {
                        logDebug("onConversionDataFail ${errorMessage.orEmpty()}")
                        resumeWithAttributionData(currentAttributionData())
                    }

                    override fun onAppOpenAttribution(attributionData: MutableMap<String, String>?) {
                        val mergedData = mergeAttributionData(
                            values = attributionData.orEmpty(),
                            source = DEEP_LINK_SOURCE_APP_OPEN_ATTRIBUTION
                        )
                        logDebug("onAppOpenAttribution ${mergedData.toDeepLinkLogSummary()}")
                        resumeWithAttributionData(mergedData)
                    }

                    override fun onAttributionFailure(errorMessage: String?) {
                        logDebug("onAttributionFailure ${errorMessage.orEmpty()}")
                    }
                }

                logDebug("AppsFlyer Dev Key configured ${devKey.isNotBlank()}")
                AppsFlyerLib.getInstance().init(devKey, listener, appContext)
                AppsFlyerLib.getInstance().subscribeForDeepLink { deepLinkResult ->
                    logDebug("DeepLinkListener status=${deepLinkResult.status}")
                    if (deepLinkResult.status == DeepLinkResult.Status.FOUND) {
                        val deepLinkData = mutableMapOf<String, Any?>()
                        deepLinkResult.deepLink?.let { deepLink ->
                            deepLink.clickEvent?.let { clickEvent ->
                                clickEvent.keys().forEach { key ->
                                    deepLinkData[key] = clickEvent.opt(key)
                                }
                            }
                            deepLink.getDeepLinkValue()
                                ?.takeIf { it.isNotBlank() }
                                ?.let { value -> deepLinkData[KEY_DEEP_LINK_VALUE] = value }
                            DEEP_LINK_SUB_KEYS.forEach { key ->
                                deepLink.getStringValue(key)
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { value -> deepLinkData[key] = value }
                            }
                        }
                        val mergedData = mergeAttributionData(
                            values = deepLinkData,
                            source = DEEP_LINK_SOURCE_UDL
                        )
                        logDebug("DeepLinkListener clickEvent ${mergedData.toDeepLinkLogSummary()}")
                        resumeWithAttributionData(mergedData)
                    }
                }
                AppsFlyerLib.getInstance().start(appContext)
            }
        } ?: currentAttributionData()
    }

    private fun mergeAttributionData(
        values: Map<String, Any?>,
        source: String
    ): Map<String, Any?> {
        return synchronized(attributionDataLock) {
            val mergedData = cachedConversionData.orEmpty().toMutableMap()
            values.forEach { (key, value) ->
                if (value.isNonBlankAttributionValue() || !mergedData.containsKey(key)) {
                    mergedData[key] = value
                }
            }
            if (values.hasAnyNonBlankDeepLinkParam()) {
                cachedDeepLinkSource = source
            }
            cachedConversionData = mergedData
            mergedData.toMap()
        }
    }

    private fun currentAttributionData(): Map<String, Any?> {
        return synchronized(attributionDataLock) {
            cachedConversionData.orEmpty()
        }
    }

    private fun currentDeepLinkSource(): String {
        return synchronized(attributionDataLock) {
            cachedDeepLinkSource
        }
    }

    private companion object {
        const val CONVERSION_DATA_TIMEOUT_MS = 4_000L
        const val KEY_AF_ID = "af_id"
        const val KEY_DEEP_LINK_VALUE = "deep_link_value"
        const val KEY_DEEP_LINK_SUB_PREFIX = "deep_link_sub"
        val DEEP_LINK_SUB_KEYS = (1..10).map { "$KEY_DEEP_LINK_SUB_PREFIX$it" }
    }
}

private fun Any?.isNonBlankAttributionValue(): Boolean {
    return this != null && this != JSONObject.NULL && toString().isNotBlank()
}

private fun Map<String, Any?>.toDeepLinkLogSummary(): String {
    val deepLinkValuePresent = this["deep_link_value"].isNonBlankAttributionValue()
    val deepLinkSubKeys = keys
        .filter { it.startsWith("deep_link_sub") && this[it].isNonBlankAttributionValue() }
        .sorted()
    return "deep_link_value=$deepLinkValuePresent deep_link_sub_keys=$deepLinkSubKeys keys=${keys.sorted()}"
}

private fun Map<String, Any?>.hasAnyNonBlankDeepLinkParam(): Boolean {
    return any { (key, value) ->
        (key == "deep_link_value" || key.startsWith("deep_link_sub")) &&
            value.isNonBlankAttributionValue()
    }
}

private fun logDebug(message: String) {
    if (BuildConfig.DEBUG) {
        runCatching {
            Log.d("AppsFlyerAttribution", message)
        }
    }
}

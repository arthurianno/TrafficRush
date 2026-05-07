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
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AppsFlyerAttributionService(
    context: Context,
    private val devKey: String = AppConstants.APPSFLYER_DEV_KEY
) : AttributionProvider {
    private val appContext = context.applicationContext
    @Volatile private var cachedConversionData: Map<String, Any?>? = null

    override suspend fun getConversionData(): AttributionData = withContext(Dispatchers.IO) {
        val conversionData = cachedConversionData ?: waitForConversionData().also {
            cachedConversionData = it
        }

        val values = conversionData.toMutableMap()
        val appsFlyerUid = AppsFlyerLib.getInstance().getAppsFlyerUID(appContext)
        logDebug("AppsFlyer UID loaded: ${appsFlyerUid.orEmpty()}")
        appsFlyerUid
            ?.takeIf { it.isNotBlank() }
            ?.let { appsFlyerUid ->
                values[KEY_AF_ID] = appsFlyerUid
            }

        AttributionData(values = values)
    }

    private suspend fun waitForConversionData(): Map<String, Any?> {
        return withTimeoutOrNull(CONVERSION_DATA_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : AppsFlyerConversionListener {
                    override fun onConversionDataSuccess(conversionData: MutableMap<String, Any>?) {
                        cachedConversionData = mergeAttributionData(conversionData.orEmpty())
                        if (continuation.isActive) {
                            continuation.resume(cachedConversionData.orEmpty())
                        }
                    }

                    override fun onConversionDataFail(errorMessage: String?) {
                        if (continuation.isActive) {
                            continuation.resume(emptyMap())
                        }
                    }

                    override fun onAppOpenAttribution(attributionData: MutableMap<String, String>?) {
                        cachedConversionData = mergeAttributionData(attributionData.orEmpty())
                    }

                    override fun onAttributionFailure(errorMessage: String?) = Unit
                }

                logDebug("AppsFlyer Dev Key configured ${devKey.isNotBlank()}")
                AppsFlyerLib.getInstance().init(devKey, listener, appContext)
                AppsFlyerLib.getInstance().subscribeForDeepLink { deepLinkResult ->
                    if (deepLinkResult.status == DeepLinkResult.Status.FOUND) {
                        val deepLinkData = mutableMapOf<String, Any?>()
                        deepLinkResult.deepLink?.clickEvent?.let { json ->
                            json.keys().forEach { key ->
                                deepLinkData[key] = json.opt(key)
                            }
                        }
                        cachedConversionData = mergeAttributionData(deepLinkData)
                    }
                }
                AppsFlyerLib.getInstance().start(appContext)
            }
        } ?: emptyMap()
    }

    private fun mergeAttributionData(values: Map<String, Any?>): Map<String, Any?> {
        return cachedConversionData.orEmpty() + values
    }

    private companion object {
        const val CONVERSION_DATA_TIMEOUT_MS = 4_000L
        const val KEY_AF_ID = "af_id"
    }
}

private fun logDebug(message: String) {
    if (BuildConfig.DEBUG) {
        runCatching {
            Log.d("AppsFlyerAttribution", message)
        }
    }
}

package com.games.playNewAdventure.service

import android.content.Context
import com.appsflyer.AppsFlyerConversionListener
import com.appsflyer.AppsFlyerLib
import com.games.playNewAdventure.AppConstants
import com.games.playNewAdventure.startup.AttributionData
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AppsFlyerAttributionService(
    context: Context,
    private val devKey: String = AppConstants.APPSFLYER_DEV_KEY
) : AttributionService {
    private val appContext = context.applicationContext
    @Volatile private var cachedConversionData: Map<String, Any?>? = null

    override suspend fun getConversionData(): AttributionData = withContext(Dispatchers.IO) {
        val conversionData = cachedConversionData ?: waitForConversionData().also {
            cachedConversionData = it
        }

        val values = conversionData.toMutableMap()
        AppsFlyerLib.getInstance().getAppsFlyerUID(appContext)
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
                        if (continuation.isActive) {
                            continuation.resume(conversionData.orEmpty())
                        }
                    }

                    override fun onConversionDataFail(errorMessage: String?) {
                        if (continuation.isActive) {
                            continuation.resume(emptyMap())
                        }
                    }

                    override fun onAppOpenAttribution(attributionData: MutableMap<String, String>?) = Unit

                    override fun onAttributionFailure(errorMessage: String?) = Unit
                }

                AppsFlyerLib.getInstance().init(devKey, listener, appContext)
                AppsFlyerLib.getInstance().start(appContext)
            }
        } ?: emptyMap()
    }

    private companion object {
        const val CONVERSION_DATA_TIMEOUT_MS = 4_000L
        const val KEY_AF_ID = "af_id"
    }
}

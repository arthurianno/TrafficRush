package com.games.playNewAdventure.service

import android.content.Context
import android.util.Log
import com.games.playNewAdventure.AppConstants
import com.games.playNewAdventure.BuildConfig
import com.games.playNewAdventure.startup.domain.PushData
import com.games.playNewAdventure.startup.domain.PushTokenProvider
import com.games.playNewAdventure.startup.domain.toTokenDebugInfo
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirebasePushService(
    context: Context
) : PushTokenProvider {
    private val appContext = context.applicationContext

    override suspend fun getPushDataOrNull(): PushData? = withContext(Dispatchers.IO) {
        val token = runCatching {
            Tasks.await(FirebaseMessaging.getInstance().token)
        }.getOrNull()?.takeIf { it.isNotBlank() }

        token?.let { FcmTokenStore.saveLatestToken(appContext, it) }

        logDebug("FCM token available ${token != null}; ${token.toPushTokenLogSummary()}")

        token?.let {
            PushData(
                pushToken = it,
                firebaseProjectId = AppConstants.FIREBASE_PROJECT_ID,
                firebaseProjectNumber = AppConstants.FIREBASE_PROJECT_NUMBER
            )
        }
    }
}

private fun String?.toPushTokenLogSummary(): String {
    val token = this?.takeIf { it.isNotBlank() } ?: return "length=0 hash=- last4=-"
    val info = token.toTokenDebugInfo()
    return "length=${info?.length ?: 0} hash=${info?.sha256 ?: "-"} last4=${info?.last4 ?: "-"}"
}

private fun logDebug(message: String) {
    if (BuildConfig.DEBUG) {
        runCatching {
            Log.d("FirebasePushService", message)
        }
    }
}

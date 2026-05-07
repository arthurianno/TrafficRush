package com.games.playNewAdventure.service

import android.util.Log
import com.games.playNewAdventure.AppConstants
import com.games.playNewAdventure.BuildConfig
import com.games.playNewAdventure.startup.domain.PushData
import com.games.playNewAdventure.startup.domain.PushTokenProvider
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirebasePushService : PushTokenProvider {
    override suspend fun getPushDataOrNull(): PushData? = withContext(Dispatchers.IO) {
        val token = runCatching {
            Tasks.await(FirebaseMessaging.getInstance().token)
        }.getOrNull()?.takeIf { it.isNotBlank() }

        logDebug("FCM token available ${token != null}")

        token?.let {
            PushData(
                pushToken = it,
                firebaseProjectId = AppConstants.FIREBASE_PROJECT_ID,
                firebaseProjectNumber = AppConstants.FIREBASE_PROJECT_NUMBER
            )
        }
    }
}

private fun logDebug(message: String) {
    if (BuildConfig.DEBUG) {
        runCatching {
            Log.d("FirebasePushService", message)
        }
    }
}

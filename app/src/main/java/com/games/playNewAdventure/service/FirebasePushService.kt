package com.games.playNewAdventure.service

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.games.playNewAdventure.AppConstants
import com.games.playNewAdventure.startup.PushData
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirebasePushService : PushService {
    override suspend fun getPushDataOrNull(): PushData? = withContext(Dispatchers.IO) {
        val token = runCatching {
            Tasks.await(FirebaseMessaging.getInstance().token)
        }.getOrNull()?.takeIf { it.isNotBlank() }

        token?.let {
            PushData(
                pushToken = it,
                firebaseProjectId = AppConstants.FIREBASE_PROJECT_ID,
                firebaseProjectNumber = AppConstants.FIREBASE_PROJECT_NUMBER
            )
        }
    }

    override suspend fun requestNotificationPermission(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        val alreadyGranted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            return true
        }

        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_POST_NOTIFICATIONS
        )

        return false
    }

    companion object {
        const val REQUEST_POST_NOTIFICATIONS = 10_200
    }
}

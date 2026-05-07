package com.games.playNewAdventure.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

interface NotificationPermissionRequester {
    fun requestNotificationPermission(onComplete: () -> Unit)

    fun handlePermissionResult(
        requestCode: Int,
        grantResults: IntArray
    ): Boolean
}

class AndroidNotificationPermissionRequester(
    private val activity: ComponentActivity,
    private val onPermissionResult: (Boolean) -> Unit
) : NotificationPermissionRequester {
    private var onCompleteCallback: (() -> Unit)? = null

    override fun requestNotificationPermission(onComplete: () -> Unit) {
        onCompleteCallback = onComplete
        
        val sdkInt = Build.VERSION.SDK_INT
        val targetSdk = activity.applicationInfo.targetSdkVersion
        android.util.Log.d("PushPermission", "Accept clicked. SDK_INT: $sdkInt, targetSdkVersion: $targetSdk")

        if (sdkInt < Build.VERSION_CODES.TIRAMISU) {
            android.util.Log.d("PushPermission", "API < 33, skip system request")
            onPermissionResult(true)
            onCompleteCallback?.invoke()
            onCompleteCallback = null
            return
        }

        val alreadyGranted = ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        
        val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.POST_NOTIFICATIONS
        )

        android.util.Log.d("PushPermission", "notification permission status before Accept -> granted: $alreadyGranted, shouldShowRequestPermissionRationale: $showRationale")

        if (alreadyGranted) {
            android.util.Log.d("PushPermission", "permission already granted, continue")
            onPermissionResult(true)
            onCompleteCallback?.invoke()
            onCompleteCallback = null
        } else {
            android.util.Log.d("PushPermission", "system permission request launched for POST_NOTIFICATIONS")
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_POST_NOTIFICATIONS
            )
        }
    }

    override fun handlePermissionResult(
        requestCode: Int,
        grantResults: IntArray
    ): Boolean {
        if (requestCode != REQUEST_POST_NOTIFICATIONS) {
            return false
        }

        val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        android.util.Log.d("PushPermission", "permission result granted/denied -> granted: $granted")
        
        onPermissionResult(granted)
        
        android.util.Log.d("PushPermission", "continue to WebView")
        onCompleteCallback?.invoke()
        onCompleteCallback = null
        
        return true
    }

    companion object {
        const val REQUEST_POST_NOTIFICATIONS = 10_200
    }
}

class DebugNotificationPermissionRequester(
    private val onPermissionResult: (Boolean) -> Unit
) : NotificationPermissionRequester {
    override fun requestNotificationPermission(onComplete: () -> Unit) {
        android.util.Log.d("PushPermission", "Debug requester: Accept clicked, auto-granting.")
        onPermissionResult(true)
        onComplete()
    }

    override fun handlePermissionResult(
        requestCode: Int,
        grantResults: IntArray
    ): Boolean {
        return false
    }
}

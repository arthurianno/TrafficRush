package com.games.playNewAdventure.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.games.playNewAdventure.AppConstants
import com.games.playNewAdventure.MainActivity
import com.games.playNewAdventure.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class TrafficRushMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Log.d(LOG_TAG, "FCM token refreshed")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val pushUrl = message.data.pushUrlOrNull()
        val title = message.notification?.title
            ?: message.data[KEY_TITLE]
            ?: AppConstants.APP_NAME
        val body = message.notification?.body
            ?: message.data[KEY_BODY]
            ?: ""

        val imageUrl = message.notification?.imageUrl?.toString()
            ?: message.data[KEY_IMAGE_URL]
            ?: message.data[KEY_IMAGE]
        if (!imageUrl.isNullOrBlank()) {
            Log.w(LOG_TAG, "Push image payload received but rich image rendering is not implemented yet")
        }

        showNotification(
            title = title,
            body = body,
            pushUrl = pushUrl
        )
    }

    private fun showNotification(
        title: String,
        body: String,
        pushUrl: String?
    ) {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            pushUrl?.let { putExtra(AppConstants.EXTRA_PUSH_URL, it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            PUSH_NOTIFICATION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, PUSH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = NotificationManagerCompat.from(this)
        if (notificationManager.areNotificationsEnabled()) {
            notifyPush(notificationManager, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyPush(
        notificationManager: NotificationManagerCompat,
        notification: android.app.Notification
    ) {
        notificationManager.notify(PUSH_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            PUSH_CHANNEL_ID,
            AppConstants.APP_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun Map<String, String>.pushUrlOrNull(): String? {
        return PUSH_URL_KEYS
            .firstNotNullOfOrNull { key -> this[key]?.takeIf(String::isNotBlank) }
    }

    private companion object {
        const val LOG_TAG = "TrafficRushPush"
        const val PUSH_CHANNEL_ID = "traffic_rush_push"
        const val PUSH_NOTIFICATION_ID = 10_500
        const val PUSH_NOTIFICATION_REQUEST_CODE = 10_501
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_IMAGE = "image"
        const val KEY_IMAGE_URL = "image_url"

        val PUSH_URL_KEYS = listOf(
            AppConstants.EXTRA_PUSH_URL,
            "url",
            "link",
            "deeplink",
            "deep_link",
            "deep_link_value"
        )
    }
}

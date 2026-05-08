package com.games.playNewAdventure.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.games.playNewAdventure.AppConstants
import com.games.playNewAdventure.BuildConfig
import com.games.playNewAdventure.MainActivity
import com.games.playNewAdventure.R
import com.games.playNewAdventure.startup.domain.toTokenDebugInfo
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.net.HttpURLConnection
import java.net.URL

class TrafficRushMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        FcmTokenStore.saveLatestToken(applicationContext, token)
        val tokenInfo = token.toTokenDebugInfo()
        Log.d(
            LOG_TAG,
            "FCM token refreshed length=${tokenInfo?.length ?: 0} " +
                "hash=${tokenInfo?.sha256 ?: "-"} last4=${tokenInfo?.last4 ?: "-"}"
        )
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
        showNotification(
            title = title,
            body = body,
            pushUrl = pushUrl,
            imageUrl = imageUrl
        )
    }

    private fun showNotification(
        title: String,
        body: String,
        pushUrl: String?,
        imageUrl: String?
    ) {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            pushUrl?.let { putExtra(AppConstants.EXTRA_PUSH_URL, it) }
        }
        if (BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "Push notification tap intent created with url = ${pushUrl ?: "-"}")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            PUSH_NOTIFICATION_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val image = imageUrl?.takeIf { it.isNotBlank() }?.let(::downloadBitmap)
        val notificationBuilder = NotificationCompat.Builder(this, PUSH_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        image?.let { bitmap ->
            notificationBuilder
                .setLargeIcon(bitmap)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as Bitmap?)
                        .setSummaryText(body)
                )
        }

        val notification = notificationBuilder.build()

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

    private fun downloadBitmap(rawUrl: String): Bitmap? {
        return runCatching {
            val connection = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = IMAGE_CONNECT_TIMEOUT_MS
                readTimeout = IMAGE_READ_TIMEOUT_MS
                instanceFollowRedirects = true
            }
            try {
                if (connection.responseCode in HTTP_SUCCESS_RANGE) {
                    connection.inputStream.use(BitmapFactory::decodeStream)
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { failure ->
            Log.w(LOG_TAG, "Failed to load push image: ${failure.message}")
            null
        }
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
        const val IMAGE_CONNECT_TIMEOUT_MS = 3_000
        const val IMAGE_READ_TIMEOUT_MS = 3_000
        val HTTP_SUCCESS_RANGE = 200..299

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

package com.dailylearn.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.webkit.JavascriptInterface
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Exposed to JavaScript as `AndroidNotifBridge`.
 *
 * The injected JS shim calls [showNotification] whenever the web app
 * tries to create a `new Notification(title, options)`.
 */
class NativeNotifBridge(private val ctx: Context) {

    init {
        ensureChannel()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "DailyLearn",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Your daily learning line reminders"
                enableVibration(true)
            }
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    @JavascriptInterface
    fun showNotification(title: String, body: String, tag: String) {
        val tapIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.take(80))          // Android caps title length
            .setContentText(body.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(tag.hashCode(), notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted yet — safe to ignore
        }
    }

    companion object {
        const val CHANNEL_ID = "dailylearn_channel"
    }
}

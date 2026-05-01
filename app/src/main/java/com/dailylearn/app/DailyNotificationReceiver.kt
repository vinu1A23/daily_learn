package com.dailylearn.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONException

/**
 * Receives the daily 08:00 AM alarm.
 *
 * Reads line data from the WebView's localStorage (mirrored into SharedPreferences
 * by the JS bridge on every save) and posts an Android notification.
 *
 * After firing it re-schedules itself for the next day so the alarm persists.
 */
class DailyNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        Log.i(TAG, "Daily alarm fired")

        ensureChannel(ctx)
        buildAndPostNotification(ctx)

        // Re-schedule for tomorrow — exact alarms are one-shot on modern Android
        NotificationScheduler.schedule(ctx)
    }

    // ── Notification content ────────────────────────────────────────────────

    private fun buildAndPostNotification(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val linesJson   = prefs.getString(KEY_LINES, null)
        val idx         = prefs.getInt(KEY_IDX, 0)
        val linesPerDay = prefs.getInt(KEY_LINES_PER_DAY, 1)
        val notifOn     = prefs.getBoolean(KEY_NOTIF_ENABLED, true)

        if (!notifOn) {
            Log.i(TAG, "Notifications disabled by user — skipping")
            return
        }

        val lineText = extractLine(linesJson, idx, linesPerDay)
            ?: run {
                // No content yet → generic reminder
                postNotification(
                    ctx,
                    title  = "📖 DailyLearn",
                    body   = "Open the app and import a PDF to start your daily learning journey!",
                    notifId = NOTIF_ID_GENERIC
                )
                return
            }

        postNotification(
            ctx,
            title   = "📖 DailyLearn — Your Line for Today",
            body    = lineText,
            notifId = NOTIF_ID_DAILY
        )
    }

    private fun extractLine(linesJson: String?, startIdx: Int, count: Int): String? {
        if (linesJson.isNullOrBlank()) return null
        return try {
            val arr   = JSONArray(linesJson)
            if (arr.length() == 0) return null
            val lines = mutableListOf<String>()
            for (i in startIdx until minOf(startIdx + count, arr.length())) {
                val obj = arr.getJSONObject(i)
                lines += obj.optString("txt", "")
            }
            lines.filter { it.isNotBlank() }.joinToString(" / ").takeIf { it.isNotBlank() }
        } catch (e: JSONException) {
            Log.w(TAG, "Could not parse lines JSON: ${e.message}")
            null
        }
    }

    private fun postNotification(ctx: Context, title: String, body: String, notifId: Int) {
        val tapIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx, notifId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, NativeNotifBridge.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(notifId, notification)
            Log.i(TAG, "Notification posted: $title")
        } catch (_: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS permission missing")
        }
    }

    // ── Channel ─────────────────────────────────────────────────────────────

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                NativeNotifBridge.CHANNEL_ID,
                "DailyLearn",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily learning line reminders"
                enableVibration(true)
            }
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    companion object {
        private const val TAG = "DailyLearnReceiver"

        // SharedPreferences — must match the keys written by LocalStorageSyncBridge
        const val PREFS_NAME          = "dailylearn_prefs"
        const val KEY_LINES           = "dl_lines"
        const val KEY_IDX             = "dl_idx"
        const val KEY_LINES_PER_DAY   = "dl_linesPerDay"
        const val KEY_NOTIF_ENABLED   = "dl_notifEnabled"

        private const val NOTIF_ID_DAILY   = 2001
        private const val NOTIF_ID_GENERIC = 2002
    }
}

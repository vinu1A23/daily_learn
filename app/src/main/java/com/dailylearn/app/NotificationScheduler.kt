package com.dailylearn.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * Schedules (or re-schedules) an exact daily alarm at 08:00 AM local time.
 * The alarm fires [DailyNotificationReceiver] which posts the notification.
 *
 * Call [schedule] from MainActivity.onCreate and from [BootReceiver] so the
 * alarm survives device restarts.
 */
object NotificationScheduler {

    private const val TAG = "DailyLearnScheduler"
    private const val REQUEST_CODE = 1001

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, DailyNotificationReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build next 08:00 AM trigger — if it's already past 08:00 today, aim for tomorrow
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    // Android 12+: need SCHEDULE_EXACT_ALARM or USE_EXACT_ALARM permission
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi
                        )
                        Log.i(TAG, "Exact alarm set for ${cal.time}")
                    } else {
                        // Fall back to inexact — still fires within ~15 minutes of 08:00
                        am.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi
                        )
                        Log.i(TAG, "Inexact alarm set (no exact-alarm permission)")
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi
                    )
                    Log.i(TAG, "Exact alarm (M) set for ${cal.time}")
                }
                else -> {
                    am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                    Log.i(TAG, "Exact alarm set for ${cal.time}")
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not schedule exact alarm: ${e.message}")
        }
    }

    fun cancel(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, DailyNotificationReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pi?.let { am.cancel(it) }
        Log.i(TAG, "Alarm cancelled")
    }
}

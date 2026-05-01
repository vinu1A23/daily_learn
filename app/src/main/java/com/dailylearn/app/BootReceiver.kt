package com.dailylearn.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Fires on device boot/reboot so the daily alarm is automatically
 * re-registered (alarms don't survive a power cycle).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i("DailyLearnBoot", "Boot detected — re-scheduling daily alarm")
            NotificationScheduler.schedule(ctx)
        }
    }
}

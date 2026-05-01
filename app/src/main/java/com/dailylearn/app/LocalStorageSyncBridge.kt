package com.dailylearn.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.webkit.JavascriptInterface

/**
 * Exposed to JavaScript as `AndroidStoreBridge`.
 *
 * The web app's localStorage is sandboxed inside the WebView process and
 * is NOT accessible from a BroadcastReceiver.  This bridge lets the JS
 * side push key values (lines JSON, index, settings) into SharedPreferences
 * so [DailyNotificationReceiver] can read them at 08:00 AM without
 * launching the app.
 */
class LocalStorageSyncBridge(ctx: Context) {

    private val prefs: SharedPreferences =
        ctx.getSharedPreferences(DailyNotificationReceiver.PREFS_NAME, Context.MODE_PRIVATE)

    /** Called by JS whenever the lines array changes (after PDF import). */
    @JavascriptInterface
    fun syncLines(linesJson: String) {
        prefs.edit().putString(DailyNotificationReceiver.KEY_LINES, linesJson).apply()
        Log.d(TAG, "Lines synced (${linesJson.length} chars)")
    }

    /** Called by JS whenever the current index advances. */
    @JavascriptInterface
    fun syncIndex(idx: Int) {
        prefs.edit().putInt(DailyNotificationReceiver.KEY_IDX, idx).apply()
        Log.d(TAG, "Index synced → $idx")
    }

    /** Called by JS whenever linesPerDay changes. */
    @JavascriptInterface
    fun syncLinesPerDay(n: Int) {
        prefs.edit().putInt(DailyNotificationReceiver.KEY_LINES_PER_DAY, n).apply()
        Log.d(TAG, "LinesPerDay synced → $n")
    }

    /** Called by JS when the notif toggle changes. */
    @JavascriptInterface
    fun syncNotifEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(DailyNotificationReceiver.KEY_NOTIF_ENABLED, enabled).apply()
        Log.d(TAG, "NotifEnabled synced → $enabled")
    }

    companion object {
        private const val TAG = "LocalStorageSyncBridge"
    }
}

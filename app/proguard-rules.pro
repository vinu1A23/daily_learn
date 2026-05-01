# Keep JavascriptInterface methods so they are not stripped by R8
-keepclassmembers class com.dailylearn.app.NativeNotifBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.dailylearn.app.LocalStorageSyncBridge {
    @android.webkit.JavascriptInterface <methods>;
}
# Keep BroadcastReceivers referenced in the manifest
-keep class com.dailylearn.app.DailyNotificationReceiver { *; }
-keep class com.dailylearn.app.BootReceiver { *; }

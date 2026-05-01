# DailyLearn Android App

A native Android wrapper for the DailyLearn HTML app, built in Kotlin.
Provides a home-screen launcher icon and true 8 AM daily notifications
that work even when the app is closed.

---

## What's inside

```
DailyLearn-Android/
├── app/
│   └── src/main/
│       ├── assets/
│       │   └── index.html                ← your original HTML app (unchanged)
│       ├── java/com/dailylearn/app/
│       │   ├── MainActivity.kt           ← WebView host + permission flow
│       │   ├── NativeNotifBridge.kt      ← JS→Android notification bridge
│       │   ├── LocalStorageSyncBridge.kt ← mirrors localStorage→SharedPrefs
│       │   ├── NotificationScheduler.kt  ← schedules exact 8 AM alarm
│       │   ├── DailyNotificationReceiver.kt ← fires at 8 AM, posts notification
│       │   └── BootReceiver.kt           ← re-schedules alarm after reboot
│       └── res/                          ← icons, layout, themes
├── build.gradle
└── settings.gradle
```

---

## How it works

| Feature | Implementation |
|---|---|
| Home screen icon | Standard Android launcher Activity with adaptive icon |
| PDF file picker | `<input type="file">` intercepted by `WebChromeClient.onShowFileChooser` |
| In-app notifications | `NativeNotifBridge` (JS interface) replaces browser `Notification` API |
| Daily 8 AM notification | `AlarmManager` exact alarm → `DailyNotificationReceiver` BroadcastReceiver |
| Survives reboot | `BootReceiver` re-registers the alarm on `BOOT_COMPLETED` |
| Background data access | `LocalStorageSyncBridge` mirrors every `localStorage.setItem` into `SharedPreferences` so the receiver reads current line data without waking the WebView |

---

## Prerequisites

| Tool | Version |
|---|---|
| Android Studio | Hedgehog (2023.1.1) or newer |
| JDK | 17 (bundled with Android Studio) |
| Android SDK | API 34 (installed via SDK Manager) |
| Build Tools | 34.0.0 |

> You do **not** need a physical device — the Android Emulator works fine.

---

## Build steps (Android Studio — easiest)

1. **Open** Android Studio → *File → Open* → select the `DailyLearn-Android/` folder.
2. Wait for Gradle sync to finish (first time downloads ~300 MB of dependencies).
3. **Run on emulator:**
   - In the toolbar choose *Device Manager* → create an AVD (e.g. Pixel 6, API 34).
   - Press the green ▶ Run button.
4. **Build a release APK:**
   - *Build → Build Bundle(s) / APK(s) → Build APK(s)*
   - APK lands at `app/build/outputs/apk/debug/app-debug.apk`

---

## Build steps (command line / no Android Studio)

```bash
# 1. Set ANDROID_HOME
export ANDROID_HOME=$HOME/Android/Sdk       # Linux/Mac
# set ANDROID_HOME=C:\Users\YOU\AppData\Local\Android\Sdk  # Windows

# 2. Install Gradle wrapper (one-time)
cd DailyLearn-Android
gradle wrapper --gradle-version 8.4         # or use the included wrapper

# 3. Build debug APK
./gradlew assembleDebug

# APK output:
#   app/build/outputs/apk/debug/app-debug.apk
```

---

## Install on your Android phone (sideload)

### Option A — USB cable (ADB)

```bash
# Enable Developer Options on phone:
#   Settings → About Phone → tap "Build Number" 7 times
#   Settings → Developer Options → enable "USB Debugging"

# Connect phone via USB, then:
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Option B — Wi-Fi transfer (no cable)

1. Copy `app-debug.apk` to your phone (email, Google Drive, WhatsApp, USB).
2. On the phone go to **Settings → Apps → Special app access → Install unknown apps**.
3. Allow your file manager or browser to install APKs.
4. Open the APK file and tap **Install**.

### Option C — Direct device build

Connect your phone via USB with USB debugging on, press **▶ Run** in Android Studio — it installs and launches automatically.

---

## First launch

1. The app asks for **notification permission** (Android 13+) — tap **Allow**.
2. Tap **Setup** tab → upload your PDF → pick pages → set lines per day → **Start Learning**.
3. The app automatically schedules an 8 AM daily notification.
4. The notification appears even when the app is fully closed.

---

## Notification schedule

- Fires at **08:00 AM** local time every day.
- The alarm re-schedules itself after each fire (AlarmManager exact alarms are one-shot).
- The alarm is also re-registered automatically after a device reboot.
- To test immediately: open the app → tap the 🔔 bell icon in the top-right corner.

---

## Android version notes

| Android | Behaviour |
|---|---|
| 8.0–11 (API 26–30) | Exact alarm, no extra permission required |
| 12 (API 31–32) | Needs `SCHEDULE_EXACT_ALARM` (granted automatically on install) |
| 13+ (API 33+) | Runtime notification permission popup on first launch |
| 12+ exact alarm | Falls back to inexact (~15 min window) if user revokes exact-alarm permission in Settings |

---

## Troubleshooting

| Problem | Fix |
|---|---|
| Gradle sync fails | *File → Invalidate Caches → Restart* |
| "SDK not found" | Open SDK Manager, install API 34 + Build Tools 34.0.0 |
| No notification at 8 AM | Check *Settings → Apps → DailyLearn → Notifications* is ON |
| Notification missing content | Open app first so JS can sync localStorage → SharedPrefs |
| PDF picker does nothing | Ensure you haven't denied the storage read permission |
| App crashes on load | Wipe app data (*Settings → Apps → DailyLearn → Clear Data*) and re-import |

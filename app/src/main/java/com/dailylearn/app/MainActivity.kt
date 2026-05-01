package com.dailylearn.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    // Holds the callback WebView expects after a file chooser returns
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    // ── Permission launcher (Android 13+) ──────────────────────────────────
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            NotificationScheduler.schedule(this)
            showToast("✓ Daily notifications enabled!")
        } else {
            showToast("Notification permission denied — enable in Settings")
        }
    }

    // ── File chooser launcher (PDF upload) ─────────────────────────────────
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uriArray: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { arrayOf(it) }
        } else null
        fileChooserCallback?.onReceiveValue(uriArray)
        fileChooserCallback = null
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        configureWebView()
        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.removeAllViews()
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    // ── WebView setup ───────────────────────────────────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        with(webView.settings) {
            javaScriptEnabled         = true   // required for the app logic
            domStorageEnabled         = true   // localStorage (stores lines, progress, etc.)
            databaseEnabled           = true
            allowFileAccessFromFileURLs      = true
            allowUniversalAccessFromFileURLs = true
            cacheMode                 = WebSettings.LOAD_DEFAULT
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls       = false
            displayZoomControls       = false
            setSupportZoom(false)
            userAgentString = "$userAgentString DailyLearnApp/1.0"
        }

        // Native notification bridge — JS calls AndroidNotifBridge.showNotification(...)
        webView.addJavascriptInterface(NativeNotifBridge(this), "AndroidNotifBridge")

        // Storage sync bridge — JS calls AndroidStoreBridge.sync*() to mirror
        // localStorage → SharedPreferences so the background alarm can read it
        webView.addJavascriptInterface(LocalStorageSyncBridge(this), "AndroidStoreBridge")

        webView.webChromeClient = object : WebChromeClient() {

            // Grant all WebView permission requests (e.g. clipboard, etc.)
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            // Handle <input type="file"> for PDF upload
            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                // Cancel any previous pending callback
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = callback

                val intent = params?.createIntent()
                    ?: Intent(Intent.ACTION_GET_CONTENT).apply { type = "application/pdf" }

                filePickerLauncher.launch(intent)
                return true
            }

            // Useful for debugging; remove in production if desired
            override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                msg?.let {
                    android.util.Log.d(
                        "DailyLearn[JS]",
                        "${it.message()} — ${it.sourceId()}:${it.lineNumber()}"
                    )
                }
                return true
            }
        }

        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
                // Inject shim that replaces the browser Notification API with
                // calls to our NativeNotifBridge Java interface
                injectNotificationShim()
                injectStorageSyncShim()
            }

            // Keep external links (e.g. Google Fonts) loading normally;
            // only intercept truly external navigation links
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return when {
                    // Let the WebView load its own assets and CDN resources
                    url.startsWith("file://")                  -> false
                    url.contains("cdnjs.cloudflare.com")       -> false
                    url.contains("fonts.googleapis.com")       -> false
                    url.contains("fonts.gstatic.com")          -> false
                    // Everything else → open in the system browser
                    else -> {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        true
                    }
                }
            }
        }

        webView.loadUrl("file:///android_asset/index.html")
    }

    /**
     * Injects a thin JavaScript shim that:
     *  1. Reports Notification.permission as "granted" so the web app skips its permission banner.
     *  2. Intercepts `new Notification(title, options)` and forwards it to [NativeNotifBridge].
     *  3. Intercepts Notification.requestPermission() and resolves it instantly.
     */
    private fun injectNotificationShim() {
        val js = """
            (function() {
                if (window.__dailylearnShim) return;
                window.__dailylearnShim = true;

                /* ---- replace the Notification API ---- */
                var _Native = window.Notification;

                function DailyLearnNotification(title, opts) {
                    opts = opts || {};
                    try {
                        AndroidNotifBridge.showNotification(
                            String(title || ''),
                            String(opts.body  || ''),
                            String(opts.tag   || 'dailylearn')
                        );
                    } catch(e) {
                        console.warn('DailyLearn: native bridge error', e);
                    }
                }
                DailyLearnNotification.permission = 'granted';
                DailyLearnNotification.requestPermission = function() {
                    return Promise.resolve('granted');
                };
                window.Notification = DailyLearnNotification;

                /* ---- tell the web app to hide the permission banner ---- */
                var banner = document.getElementById('notif-banner');
                if (banner) banner.style.display = 'none';

                /* ---- update settings status text ---- */
                var st = document.getElementById('notif-status-text');
                if (st) st.textContent = '✓ Granted (native Android)';

                console.log('DailyLearn: notification shim active');
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    // ── Permission handling ─────────────────────────────────────────────────
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Below Android 13, no runtime permission needed — just schedule
            NotificationScheduler.schedule(this)
            return
        }
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED -> {
                NotificationScheduler.schedule(this)
            }
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                AlertDialog.Builder(this)
                    .setTitle("Enable Daily Reminders")
                    .setMessage(
                        "DailyLearn sends you a notification at 8 AM each morning with your " +
                        "line for the day. Allow notifications to stay on track."
                    )
                    .setPositiveButton("Allow") { _, _ ->
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    .setNegativeButton("Not now", null)
                    .show()
            }
            else -> {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * Patches localStorage.setItem so every write is also mirrored to
     * SharedPreferences via [LocalStorageSyncBridge].  This lets the
     * background alarm read the current line without launching the WebView.
     */
    private fun injectStorageSyncShim() {
        val js = """
            (function() {
                if (window.__dlStorageShim) return;
                window.__dlStorageShim = true;

                var _origSet = Storage.prototype.setItem;
                Storage.prototype.setItem = function(key, value) {
                    _origSet.call(this, key, value);
                    try {
                        if (key === 'dl_lines') {
                            AndroidStoreBridge.syncLines(String(value));
                        } else if (key === 'dl_idx') {
                            AndroidStoreBridge.syncIndex(parseInt(value) || 0);
                        } else if (key === 'dl_notifEnabled') {
                            AndroidStoreBridge.syncNotifEnabled(value !== 'false');
                        }
                    } catch(e) { /* bridge not available */ }

                    // Sync linesPerDay from inside the setup object
                    if (key === 'dl_setup') {
                        try {
                            var obj = JSON.parse(value);
                            if (obj && obj.linesPerDay) {
                                AndroidStoreBridge.syncLinesPerDay(parseInt(obj.linesPerDay) || 1);
                            }
                        } catch(e) {}
                    }
                };
                console.log('DailyLearn: storage sync shim active');
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    // ── Helpers ─────────────────────────────────────────────────────────────
    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

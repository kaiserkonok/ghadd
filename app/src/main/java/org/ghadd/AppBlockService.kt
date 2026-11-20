package org.ghadd

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * Watches which app comes to the foreground. If it is one you blocked (and it is not
 * temporarily unlocked), the unblock screen is shown on top of it.
 * It never reads screen content - only the package name of the window that opened.
 */
class AppBlockService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    /** The app the user is actually in (keyboards and the notification shade don't count). */
    private var current: String? = null
    private var lastFilterCheck = 0L

    private var overlays: Set<String> = emptySet()
    private var overlaysAt = 0L

    /** Re-blocks an app when its temporary unlock runs out while it's still open. */
    private val expiry = Runnable { current?.let { check(it) } }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Android (re)connected us, e.g. after the phone killed Ghadd: bring the filter back too.
        KeepAlive.startFilter(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        // Watchdog: every 30s of phone use, make sure the porn filter is still running.
        val now = System.currentTimeMillis()
        if (now - lastFilterCheck > 30_000) {
            lastFilterCheck = now
            if (!AppState.vpnRunning) KeepAlive.startFilter(this)
        }

        // Keyboards, the notification shade and our own screens open on top of the app the
        // user is in. They must not count as "left the app", or opening the keyboard would
        // cancel the end of a temporary unlock.
        if (pkg == packageName || pkg in overlayPackages()) return

        current = pkg
        handler.removeCallbacks(expiry)
        check(pkg)
    }

    private fun check(pkg: String) {
        if (pkg !in Prefs.blockedApps) return
        // Never block the home screen: "go home" would land in a blocked app again, forever.
        if (pkg in Apps.homePackages(this)) return

        if (Prefs.isUnlocked(pkg)) {
            val wait = Prefs.unlockUntil(pkg) - System.currentTimeMillis() + 500
            handler.removeCallbacks(expiry)
            handler.postDelayed(expiry, wait)
            return
        }
        // Already on screen for this app. If the user only switched away from it (Recents),
        // fall through and bring it back to the front.
        if (UnblockActivity.showingFor == pkg && ScreenState.visible == "unblock") return

        // Show the unblock screen over the app. If the phone refuses to open it (Vivo and
        // others block background screens), get the user out of the blocked app anyway -
        // accessibility can always press Home - and show a full-screen alert instead.
        val label = Apps.label(this, pkg)
        Launcher.show(
            this,
            Intent(this, UnblockActivity::class.java).putExtra(UnblockActivity.EXTRA_PKG, pkg),
            key = "unblock",
            title = "$label is blocked",
            text = "Allah sees you. Tap to continue.",
            onRefused = { performGlobalAction(GLOBAL_ACTION_HOME) }
        )
    }

    /** Keyboards and the system UI, refreshed every minute (the user may install a keyboard). */
    private fun overlayPackages(): Set<String> {
        val now = System.currentTimeMillis()
        if (now - overlaysAt > 60_000) {
            overlaysAt = now
            val imes = runCatching {
                getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
            }.getOrDefault(emptyList())
            overlays = (imes + "com.android.systemui").toSet()
        }
        return overlays
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

/** Small helpers about installed apps. */
object Apps {
    fun label(ctx: Context, pkg: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault("This app")

    @Volatile
    private var home: Set<String>? = null

    /** Home-screen apps (launchers). These can never be blocked. */
    fun homePackages(ctx: Context): Set<String> {
        home?.let { return it }
        val found = runCatching {
            ctx.packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0
            ).map { it.activityInfo.packageName }.toSet()
        }.getOrDefault(emptySet())
        home = found
        return found
    }
}

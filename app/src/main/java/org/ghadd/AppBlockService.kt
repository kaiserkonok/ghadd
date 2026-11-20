package org.ghadd

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager

/**
 * An app the user unlocked through the unblock flow. It stays open for this one visit only:
 * as soon as they leave it (home screen, another app, screen off) it is locked again.
 * Kept in memory on purpose - if anything restarts Ghadd, the app is simply locked.
 */
object Unlock {
    @Volatile
    var pkg: String? = null
}

/**
 * Watches which app comes to the foreground. If it is one you blocked (and you didn't just
 * unlock it for this visit), the unblock screen is shown on top of it.
 * It never reads screen content - only the package name of the window that opened.
 */
class AppBlockService : AccessibilityService() {

    private var lastFilterCheck = 0L

    private var overlays: Set<String> = emptySet()
    private var overlaysAt = 0L
    private val launchable = HashMap<String, Boolean>()

    /** Turning the screen off ends an unlocked visit. */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Unlock.pkg = null
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Android (re)connected us, e.g. after the phone killed Ghadd: bring the filter back too.
        KeepAlive.startFilter(this)
        runCatching { registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
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

        if (pkg == packageName) {
            // Our block screens open on top of the app; Ghadd's own home screen means they left.
            val cls = event.className?.toString().orEmpty()
            if (cls == MainActivity::class.java.name) Unlock.pkg = null
            return
        }
        // Keyboards and the notification shade open on top of the app the user is in.
        if (pkg in overlayPackages()) return

        endVisitIfLeft(pkg)
        check(pkg)
    }

    /**
     * The unlocked app's visit ends when the user goes to the home screen or another app.
     * Share sheets, permission dialogs, sign-in pop-ups etc. come from apps without a home-screen
     * icon; they open from inside the app and don't count as leaving it.
     */
    private fun endVisitIfLeft(pkg: String) {
        val unlocked = Unlock.pkg ?: return
        if (pkg == unlocked) return
        val left = pkg in Apps.homePackages(this) ||
            launchable.getOrPut(pkg) { packageManager.getLaunchIntentForPackage(pkg) != null }
        if (left) Unlock.pkg = null
    }

    private fun check(pkg: String) {
        if (pkg !in Prefs.blockedApps) return
        // Never block the home screen: "go home" would land in a blocked app again, forever.
        if (pkg in Apps.homePackages(this)) return
        if (pkg == Unlock.pkg) return
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
        runCatching { unregisterReceiver(screenOff) }
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

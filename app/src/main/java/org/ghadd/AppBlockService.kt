package org.ghadd

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Watches which app comes to the foreground. If it is one you blocked (and it is not
 * temporarily unlocked), the unblock screen is shown on top of it.
 * It never reads screen content - only the package name of the window that opened.
 */
class AppBlockService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var current: String? = null
    private var lastFilterCheck = 0L

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

        if (pkg == packageName) return
        current = pkg
        handler.removeCallbacksAndMessages(null)
        check(pkg)
    }

    private fun check(pkg: String) {
        if (pkg !in Prefs.blockedApps) return

        if (Prefs.isUnlocked(pkg)) {
            // When the temporary unlock runs out and the app is still on screen, block again.
            val wait = Prefs.unlockUntil(pkg) - System.currentTimeMillis() + 500
            handler.postDelayed({ if (current == pkg) check(pkg) }, wait)
            return
        }
        if (UnblockActivity.showingFor == pkg) return

        startActivity(
            Intent(this, UnblockActivity::class.java)
                .putExtra(UnblockActivity.EXTRA_PKG, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onInterrupt() {}
}

package org.ghadd

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings

object Perms {
    fun overlayGranted(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun accessibilityEnabled(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val me = ComponentName(ctx, AppBlockService::class.java)
        return enabled.split(':').any {
            it.equals(me.flattenToString(), true) || it.equals(me.flattenToShortString(), true)
        }
    }

    fun overlaySettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))

    fun goHome(ctx: Context) {
        ctx.startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Called by the VPN service whenever a porn domain is looked up. */
object SiteBlockEvents {
    private var last = 0L

    @Synchronized
    fun onBlocked(ctx: Context, domain: String) {
        Prefs.countSiteBlock()
        // Browsers fire many lookups per page - only pop the screen up every few seconds.
        val now = SystemClock.elapsedRealtime()
        if (now - last < 4000) return
        last = now
        if (ScreenState.visible == "block") return
        Launcher.show(
            ctx,
            Intent(ctx, BlockActivity::class.java),
            key = "block",
            title = "Stop. Allah sees you.",
            text = "That site is blocked. Tap to listen."
        )
    }
}

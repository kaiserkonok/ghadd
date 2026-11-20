package org.ghadd

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Everything that keeps Ghadd alive in the background. Phone makers like Vivo, Xiaomi, Oppo
 * kill background apps aggressively; a foreground service plus these settings stop that.
 */
object KeepAlive {
    private const val TAG = "KeepAlive"

    /** Starts the porn filter if the user turned it on and VPN consent is still ours. */
    fun startFilter(ctx: Context) {
        if (!Prefs.filterWanted) return
        if (VpnService.prepare(ctx) != null) return // consent missing or another VPN took over
        val intent = Intent(ctx, BlockerVpnService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
            else ctx.startService(intent)
        } catch (e: Exception) {
            Log.w(TAG, "could not start filter", e)
        }
    }

    fun ignoringBatteryOptimizations(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    /** One-tap system dialog: "Let Ghadd always run in background?" */
    @Suppress("BatteryLife")
    fun batteryIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))

    val isVivo: Boolean
        get() = Build.MANUFACTURER.equals("vivo", true) || Build.BRAND.equals("vivo", true) ||
            Build.BRAND.equals("iqoo", true)

    /**
     * Vivo's own per-app permission page, where "Display pop-up windows in background" lives.
     * Falls back to the app info page.
     */
    fun openPopupPermission(ctx: Context) {
        val pkg = ctx.packageName
        val candidates = listOf(
            Intent("permission.intent.action.softPermissionDetail").putExtra("packagename", pkg),
            Intent().setComponent(
                ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity")
            ).putExtra("packagename", pkg),
            Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", pkg)
        )
        for (i in candidates) {
            try {
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Phones with their own extra "background" permissions that Android can't report on. */
    val hasOemRestrictions: Boolean
        get() = Build.MANUFACTURER.lowercase() in setOf("vivo", "iqoo", "xiaomi", "redmi", "poco", "oppo", "realme", "oneplus", "huawei", "honor")

    /**
     * Opens the phone maker's "autostart / background" screen. Screen names differ between
     * OS versions, so several known ones are tried; falls back to the app's info page.
     */
    fun openBackgroundSettings(ctx: Context) {
        val candidates = listOf(
            // Vivo / iQOO (Funtouch OS, OriginOS)
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
            ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
            // Xiaomi, Oppo/Realme, Huawei
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        )
        for (c in candidates) {
            try {
                ctx.startActivity(Intent().setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
                // not on this phone, try the next
            }
        }
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

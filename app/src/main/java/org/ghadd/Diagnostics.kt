package org.ghadd

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build

/**
 * Plain-language status of everything that decides whether blocking works on this phone.
 * Shown on the home screen so one screenshot is enough to diagnose a problem.
 */
object Diagnostics {

    /** Apps that are known to route around on-device DNS filters. */
    private val riskyApps = mapOf(
        "com.opera.mini.native" to "Opera Mini",
        "com.opera.browser" to "Opera",
        "com.opera.gx" to "Opera GX",
        "com.UCMobile.intl" to "UC Browser",
        "com.uc.browser.en" to "UC Browser Mini",
        "org.mozilla.firefox" to "Firefox",
        "org.mozilla.focus" to "Firefox Focus",
        "com.brave.browser" to "Brave",
        "org.torproject.torbrowser" to "Tor Browser",
        "org.torproject.android" to "Orbot",
        "com.cloudflare.onedotonedotone" to "1.1.1.1 / WARP",
        "com.psiphon3" to "Psiphon"
    )

    data class Row(val ok: Boolean?, val text: String)

    fun collect(ctx: Context): List<Row> {
        val rows = ArrayList<Row>()
        val now = System.currentTimeMillis()

        rows += Row(AppState.vpnRunning, if (AppState.vpnRunning) "Filter is running" else "Filter is NOT running")
        rows += Row(DomainFilter.size > 0, "Blocklist: ${"%,d".format(DomainFilter.size)} porn sites")

        val last = AppState.lastQueryAt
        rows += Row(
            last > 0 && now - last < 5 * 60_000,
            if (last == 0L) "No website lookups seen yet"
            else "Website lookups checked: ${AppState.queries.get()} (last ${(now - last) / 1000}s ago)"
        )

        if (VpnService.prepare(ctx) != null && Prefs.filterWanted) {
            rows += Row(false, "Another VPN app has taken over. Ghadd's filter is off")
        }

        runCatching { privateDns(ctx) }.getOrNull()?.let {
            rows += Row(false, "Private DNS is set to \"$it\". On some phones this skips Ghadd")
        }

        rows += Row(Perms.accessibilityEnabled(ctx), "App blocking: " + if (Perms.accessibilityEnabled(ctx)) "on" else "OFF")
        rows += Row(Perms.overlayGranted(ctx), "Show over apps: " + if (Perms.overlayGranted(ctx)) "on" else "OFF")
        rows += Row(
            KeepAlive.ignoringBatteryOptimizations(ctx),
            "Battery: " + if (KeepAlive.ignoringBatteryOptimizations(ctx)) "unrestricted" else "RESTRICTED"
        )

        val installed = riskyApps.filterKeys { installed(ctx, it) }.values
        val otherVpns = otherVpnApps(ctx)
        val risky = (installed + otherVpns).distinct()
        if (risky.isNotEmpty()) rows += Row(false, "Apps that can get around filters: ${risky.joinToString()}")

        rows += Row(null, "Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}")
        return rows
    }

    private fun installed(ctx: Context, pkg: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    /** Other apps that contain a VPN (only one VPN can run at a time). */
    private fun otherVpnApps(ctx: Context): List<String> = runCatching {
        val pm = ctx.packageManager
        pm.queryIntentServices(Intent(VpnService.SERVICE_INTERFACE), 0)
            .map { it.serviceInfo.packageName }
            .filter { it != ctx.packageName }
            .distinct()
            .map { pkg -> runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg) }
    }.getOrDefault(emptyList())

    /** The hostname if Private DNS is in "strict" mode on the real network, else null. */
    fun privateDns(ctx: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        @Suppress("DEPRECATION")
        for (n in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
            val name = cm.getLinkProperties(n)?.privateDnsServerName
            if (!name.isNullOrBlank()) return name
        }
        return null
    }
}

package org.ghadd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

/** Brings the porn filter back after a reboot or after the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Prefs.filterWanted) return
        // If VPN consent was revoked we can't start silently; the app asks again when opened.
        if (VpnService.prepare(context) != null) return
        context.startService(Intent(context, BlockerVpnService::class.java))
    }
}

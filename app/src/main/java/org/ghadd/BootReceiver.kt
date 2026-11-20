package org.ghadd

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Brings the porn filter back after a reboot or after the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        KeepAlive.startFilter(context)
    }
}

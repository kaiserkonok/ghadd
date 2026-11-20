package org.ghadd

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/** Which of our block screens is currently on screen ("block", "unblock" or null). */
object ScreenState {
    @Volatile
    var visible: String? = null
}

/**
 * Shows a block screen from the background. Some phones (Vivo, Xiaomi, ...) silently refuse
 * to open screens for background apps. So: try to open it directly, and if it isn't on
 * screen shortly after, fall back to a full-screen alert (like an alarm clock) that opens it.
 */
object Launcher {
    private const val TAG = "Launcher"
    private const val CHANNEL = "alerts"
    const val ALERT_ID = 2

    /**
     * [onRefused] runs if the phone didn't let the screen open (e.g. to throw the user out of
     * a blocked app). It must not run before the start attempt: pressing Home makes Android
     * refuse background screen starts for a few seconds.
     */
    fun show(
        ctx: Context, intent: Intent, key: String, title: String, text: String,
        onRefused: () -> Unit = {}
    ) {
        val app = ctx.applicationContext
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            app.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "direct start failed", e)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            if (ScreenState.visible != key) {
                Log.i(TAG, "screen '$key' did not appear - using full-screen alert")
                onRefused()
                alert(app, intent, key, title, text)
            }
        }, 1500)
    }

    private fun alert(ctx: Context, intent: Intent, key: String, title: String, text: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Block screens", NotificationManager.IMPORTANCE_HIGH)
        )
        val pi = PendingIntent.getActivity(
            ctx, key.hashCode(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(ALERT_ID, n)
        } catch (e: Exception) {
            Log.w(TAG, "alert failed", e)
        }
    }

    fun clearAlert(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).cancel(ALERT_ID)
    }
}

/** Every 15 minutes: if the phone killed the filter, start it again. */
object Watchdog {
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, WatchdogReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            pi
        )
    }
}

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!AppState.vpnRunning) KeepAlive.startFilter(context)
    }
}

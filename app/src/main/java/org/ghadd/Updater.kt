package org.ghadd

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Self-updater. The newest version lives as a GitHub Release on the project repo
 * (free, no server of our own). Ghadd checks it, downloads the APK and installs it.
 *
 * Android 12+: once Ghadd has installed itself one time, later updates are silent.
 * Older Android: the system shows one "Install" confirmation per update.
 */
object Updater {
    private const val TAG = "Updater"
    private const val RELEASES = "https://github.com/kaiserkonok/ghadd/releases"
    private const val LATEST_URL = "$RELEASES/latest"

    /** Every release must attach the APK under exactly this name (tools/release.sh does). */
    private const val APK_NAME = "ghadd.apk"

    /** Human-readable status for the home screen. */
    var status by mutableStateOf("")

    @Volatile
    private var busy = false

    private data class Release(val version: String, val apkUrl: String)

    fun currentVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /** Checks GitHub and installs a newer version if there is one. Safe to call from any thread. */
    fun checkAndInstall(ctx: Context) {
        if (busy) return
        busy = true
        val app = ctx.applicationContext
        thread(name = "updater") {
            try {
                Prefs.lastUpdateCheck = System.currentTimeMillis()
                status = "Checking for updates…"
                val current = currentVersion(app)
                val rel = latest()
                if (rel == null || !isNewer(rel.version, current)) {
                    status = "Up to date (v$current)"
                    return@thread
                }
                if (!canInstall(app)) {
                    status = "v${rel.version} is available. Allow Ghadd to install updates (button below)."
                    return@thread
                }
                status = "Downloading v${rel.version}…"
                val apk = download(app, rel.apkUrl)
                status = "Installing v${rel.version}…"
                install(app, apk)
            } catch (e: Exception) {
                Log.w(TAG, "update failed", e)
                status = "Couldn't check for updates (${e.javaClass.simpleName}). Will retry later."
            } finally {
                busy = false
            }
        }
    }

    /**
     * Uses GitHub's plain web redirect instead of the REST API: the API allows only 60
     * requests/hour per IP, which shared mobile/ISP addresses exceed easily.
     * /releases/latest  ->  302 Location: .../releases/tag/v1.2.3
     */
    private fun latest(): Release? {
        val conn = URL(LATEST_URL).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", "ghadd-updater")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        try {
            val code = conn.responseCode
            if (code !in 300..399) return null // no release published yet
            val location = conn.getHeaderField("Location") ?: return null
            val tag = location.substringAfterLast("/tag/", "")
            if (tag.isEmpty()) return null
            return Release(tag.removePrefix("v"), "$RELEASES/download/$tag/$APK_NAME")
        } finally {
            conn.disconnect()
        }
    }

    /** "0.2.1" > "0.2.0" */
    internal fun isNewer(candidate: String, current: String): Boolean {
        val a = candidate.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun download(ctx: Context, url: String): File {
        val out = File(ctx.cacheDir, "update.apk")
        val conn = URL(url).openConnection() as HttpURLConnection // GitHub redirects https->https; followed
        conn.setRequestProperty("User-Agent", "ghadd-updater")
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        try {
            conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
        } finally {
            conn.disconnect()
        }
        return out
    }

    private fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("ghadd.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(
                ctx, id, Intent(ctx, InstallResultReceiver::class.java), flags
            )
            session.commit(pending.intentSender)
        }
    }
}

/** Receives the result of an update install. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android wants the user to confirm (older Android, or first self-update).
                val confirm: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }
                Updater.status = "Tap Install to finish the update."
                confirm?.let {
                    it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(it)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.status = "Updated."
            else -> Updater.status =
                "Update failed: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"}"
        }
    }
}

package org.ghadd

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    // Bumped in onResume so the screen re-reads permission / blocked-app state.
    private var tick by mutableIntStateOf(0)

    private val vpnConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startVpn()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GhaddTheme {
                var showApps by remember { mutableStateOf(false) }
                if (showApps) {
                    AppsScreen(tick, onBack = { showApps = false }, onTryRemove = ::askRemove)
                } else {
                    HomeScreen(
                        tick = tick,
                        onEnableFilter = ::enableFilter,
                        onOverlay = { startActivity(Perms.overlaySettingsIntent(this)) },
                        onAccessibility = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onVpnSettings = { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) },
                        onPickApps = { showApps = true },
                        onAllowUpdates = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    android.net.Uri.parse("package:$packageName")
                                )
                            )
                        },
                        onCheckUpdates = { Updater.checkAndInstall(this) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tick++
        // The filter was turned on before but isn't running (killed, crashed, updated): restart it.
        if (Prefs.filterWanted && !AppState.vpnRunning && VpnService.prepare(this) == null) startVpn()
        // Check for a new version at most once an hour when the app is opened.
        if (System.currentTimeMillis() - Prefs.lastUpdateCheck > 60 * 60_000L) Updater.checkAndInstall(this)
    }

    private fun enableFilter() {
        val consent = VpnService.prepare(this)
        if (consent != null) vpnConsent.launch(consent) else startVpn()
    }

    private fun startVpn() {
        Prefs.filterWanted = true
        startService(Intent(this, BlockerVpnService::class.java))
    }

    /** Un-ticking a blocked app is an unblock, so it goes through the full flow. */
    private fun askRemove(pkg: String) {
        startActivity(
            Intent(this, UnblockActivity::class.java)
                .putExtra(UnblockActivity.EXTRA_PKG, pkg)
                .putExtra(UnblockActivity.EXTRA_REMOVE, true)
        )
    }
}

@Composable
private fun HomeScreen(
    tick: Int,
    onEnableFilter: () -> Unit,
    onOverlay: () -> Unit,
    onAccessibility: () -> Unit,
    onVpnSettings: () -> Unit,
    onPickApps: () -> Unit,
    onAllowUpdates: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // reading `tick` makes this block re-run on resume
    val canUpdate = remember(tick) { Updater.canInstall(ctx) }
    val overlay = remember(tick) { Perms.overlayGranted(ctx) }
    val accessibility = remember(tick) { Perms.accessibilityEnabled(ctx) }
    val blockedCount = remember(tick) { Prefs.blockedApps.size }
    val log = remember(tick) { Prefs.log().take(5) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        val version = remember {
            runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ghadd", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text("v$version", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
        }

        // ---- stats ----
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("${remember(tick) { Prefs.streakDays() }}", "day streak", Modifier.weight(1f))
            Stat("${remember(tick) { Prefs.siteBlocksToday() }}", "sites blocked today", Modifier.weight(1f))
            Stat("${remember(tick) { Prefs.siteBlocksTotal() }}", "sites blocked total", Modifier.weight(1f))
        }

        // ---- step 1: porn filter ----
        Section("1. Porn filter", done = AppState.vpnRunning) {
            if (AppState.vpnRunning) {
                Line("Active. Every website is checked, including in incognito. There is no off switch in this app.")
            } else {
                Line("Blocks porn sites in every browser and app, including incognito. Once on, it can't be turned off from here.")
                Button(onClick = onEnableFilter, modifier = Modifier.fillMaxWidth()) { Text("Turn on filter") }
            }
        }

        // ---- step 2: overlay ----
        Section("2. Show over other apps", done = overlay) {
            Line("Needed so the Quran and reminder screen can appear on top of a browser or a blocked app.")
            if (!overlay) Button(onClick = onOverlay, modifier = Modifier.fillMaxWidth()) { Text("Allow") }
        }

        // ---- step 3: accessibility ----
        Section("3. App blocking", done = accessibility) {
            Line("Turn on \"Ghadd\" in Accessibility so we can tell when an app you blocked is opened. It only sees the app's name, never its content.")
            if (!accessibility) Button(onClick = onAccessibility, modifier = Modifier.fillMaxWidth()) { Text("Open Accessibility settings") }
        }

        // ---- step 4: updates ----
        Section("4. Automatic updates", done = canUpdate) {
            Line(
                if (canUpdate) "On. Ghadd updates itself from GitHub."
                else "Allow Ghadd to install apps so it can update itself. You only do this once."
            )
            if (!canUpdate) Button(onClick = onAllowUpdates, modifier = Modifier.fillMaxWidth()) { Text("Allow updates") }
            if (Updater.status.isNotEmpty()) Line(Updater.status)
            OutlinedButton(onClick = onCheckUpdates, modifier = Modifier.fillMaxWidth()) { Text("Check for updates now") }
        }

        // ---- apps ----
        Section("Blocked apps", done = null) {
            Line("$blockedCount app(s) blocked. Removing one goes through the unblock flow.")
            Button(onClick = onPickApps, modifier = Modifier.fillMaxWidth()) { Text("Choose apps to block") }
        }

        // ---- optional: always-on VPN ----
        Section("Recommended: make the filter permanent", done = null) {
            Line("In VPN settings, tap the gear next to Ghadd and turn on \"Always-on VPN\" and \"Block connections without VPN\". Then it restarts by itself after a reboot and nothing can bypass it.")
            OutlinedButton(onClick = onVpnSettings, modifier = Modifier.fillMaxWidth()) { Text("Open VPN settings") }
        }

        // ---- log ----
        if (log.isNotEmpty()) {
            Section("Recent unblocks", done = null) {
                val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
                log.forEach {
                    val what = if (it.minutes == 0) "removed" else "${it.minutes} min"
                    val why = if (it.kind == Prefs.KIND_DESIRE) "desire" else "need"
                    Line("${fmt.format(Date(it.time))}  ·  ${it.app}  ·  $what  ·  $why")
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Section(title: String, done: Boolean?, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (done == true) "✓ $title" else title,
                style = MaterialTheme.typography.titleMedium,
                color = if (done == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
private fun Line(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private data class AppItem(val pkg: String, val label: String, val icon: Bitmap?)

@Composable
private fun AppsScreen(tick: Int, onBack: () -> Unit, onTryRemove: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val apps by produceState<List<AppItem>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != ctx.packageName }
                .map { pkg ->
                    val info = pm.getApplicationInfo(pkg, 0)
                    AppItem(
                        pkg,
                        pm.getApplicationLabel(info).toString(),
                        runCatching { pm.getApplicationIcon(info).toBitmap(96, 96) }.getOrNull()
                    )
                }
                .sortedBy { it.label.lowercase() }
        }
    }
    // re-read after returning from the unblock flow
    var blocked by remember(tick) { mutableStateOf(Prefs.blockedApps) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Choose apps to block", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            OutlinedButton(onClick = onBack) { Text("Done") }
        }
        val list = apps
        if (list == null) {
            Text("Loading…", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.pkg }) { app ->
                    val isBlocked = app.pkg in blocked
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        app.icon?.let { Image(it.asImageBitmap(), null, Modifier.size(40.dp)) }
                        Text(
                            app.label,
                            Modifier.weight(1f).padding(start = 12.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Checkbox(
                            checked = isBlocked,
                            onCheckedChange = { wantBlocked ->
                                if (wantBlocked) {
                                    Prefs.blockedApps = Prefs.blockedApps + app.pkg
                                    blocked = Prefs.blockedApps
                                } else {
                                    onTryRemove(app.pkg) // goes through the unblock flow
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

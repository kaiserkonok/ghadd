package org.ghadd

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.net.VpnService
import android.os.Build
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
import androidx.compose.material3.TextButton
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
            tick++
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    /**
     * Everything Ghadd needs, in order. The app walks through these one at a time on its own,
     * so the user only ever has to tap "Continue" and then "Allow" / switch ON.
     */
    private fun setupSteps(): List<SetupStep> = buildList {
        add(SetupStep(
            "filter", "Turn on the porn filter", "On the next screen, tap OK.",
            done = { Prefs.filterWanted && VpnService.prepare(this@MainActivity) == null },
            open = ::enableFilter
        ))
        if (Build.VERSION.SDK_INT >= 33) add(SetupStep(
            "notifications", "Allow Ghadd's alerts", "On the next screen, tap Allow.",
            done = {
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            },
            open = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        ))
        add(SetupStep(
            "overlay", "Let Ghadd show the Quran screen",
            "On the next screen, find Ghadd and turn the switch ON. Then press back.",
            done = { Perms.overlayGranted(this@MainActivity) },
            open = { runCatching { startActivity(Perms.overlaySettingsIntent(this@MainActivity)) } }
        ))
        add(SetupStep(
            "accessibility", "Turn on app blocking",
            "On the next screen, tap \"Ghadd\" (it may be under \"Downloaded apps\" or \"Installed services\"), " +
                "turn it ON and tap Allow. Then press back.",
            done = { Perms.accessibilityEnabled(this@MainActivity) },
            open = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        ))
        add(SetupStep(
            "updates", "Allow automatic updates", "On the next screen, turn the switch ON. Then press back.",
            done = { Updater.canInstall(this@MainActivity) },
            open = {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            }
        ))
        add(SetupStep(
            "battery", "Keep Ghadd running", "On the next screen, tap Allow.",
            done = { KeepAlive.ignoringBatteryOptimizations(this@MainActivity) },
            open = { runCatching { startActivity(KeepAlive.batteryIntent(this@MainActivity)) } }
        ))
        if (KeepAlive.hasOemRestrictions) {
            add(SetupStep(
                "popup", "Let Ghadd pop up over other apps",
                "On the next screen, find \"Display pop-up windows\" (or \"Pop-up in background\") and set it to Allow. Then press back.",
                done = { Prefs.setupDone("popup") },
                open = { Prefs.markSetupDone("popup"); KeepAlive.openPopupPermission(this@MainActivity) }
            ))
            add(SetupStep(
                "autostart", "Let Ghadd start by itself",
                "On the next screen, find Ghadd and turn it ON (Autostart / Background startup). Then press back.",
                done = { Prefs.setupDone("autostart") },
                open = { Prefs.markSetupDone("autostart"); KeepAlive.openBackgroundSettings(this@MainActivity) }
            ))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GhaddTheme {
                var showApps by remember { mutableStateOf(false) }
                var skipped by remember { mutableStateOf(setOf<String>()) }
                val steps = remember(tick) { setupSteps() }
                val next = steps.firstOrNull { !it.done() && it.id !in skipped }
                if (next != null) {
                    SetupScreen(
                        number = steps.indexOf(next) + 1,
                        total = steps.size,
                        step = next,
                        onSkip = { skipped = skipped + next.id }
                    )
                } else if (showApps) {
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
                        onCheckUpdates = { Updater.checkAndInstall(this) },
                        onBattery = {
                            runCatching { startActivity(KeepAlive.batteryIntent(this)) }
                        },
                        onBackgroundSettings = { KeepAlive.openBackgroundSettings(this) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        tick++
        // The filter was turned on before but isn't running (killed, crashed, updated): restart it.
        if (!AppState.vpnRunning) KeepAlive.startFilter(this)
        // Check for a new version at most once an hour when the app is opened.
        if (System.currentTimeMillis() - Prefs.lastUpdateCheck > 60 * 60_000L) Updater.checkAndInstall(this)
    }

    private fun enableFilter() {
        val consent = VpnService.prepare(this)
        if (consent != null) vpnConsent.launch(consent) else startVpn()
    }

    private fun startVpn() {
        Prefs.filterWanted = true
        KeepAlive.startFilter(this)
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

private class SetupStep(
    val id: String,
    val title: String,
    val hint: String,
    val done: () -> Boolean,
    val open: () -> Unit
)

/** One permission at a time, one big button. */
@Composable
private fun SetupScreen(number: Int, total: Int, step: SetupStep, onSkip: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Setup $number of $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(step.title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(step.hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(32.dp))
        Button(onClick = step.open, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("Continue") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
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
    onCheckUpdates: () -> Unit,
    onBattery: () -> Unit,
    onBackgroundSettings: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // reading `tick` makes this block re-run on resume
    val canUpdate = remember(tick) { Updater.canInstall(ctx) }
    val battery = remember(tick) { KeepAlive.ignoringBatteryOptimizations(ctx) }
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

        // ---- warning: the phone force-closed Ghadd, which also switches app blocking off ----
        if (!accessibility && blockedCount > 0) {
            Section("⚠ App blocking was switched off", done = null) {
                Line("Your phone force-closed Ghadd, and Android turned app blocking off. Turn it back on, then do step 5 so it doesn't happen again.")
                Button(onClick = onAccessibility, modifier = Modifier.fillMaxWidth()) { Text("Turn app blocking back on") }
            }
        }

        // ---- status: one screenshot of this tells exactly what's wrong ----
        val status = remember(tick) {
            runCatching { Diagnostics.collect(ctx) }
                .getOrElse { listOf(Diagnostics.Row(null, "Status unavailable: ${it.javaClass.simpleName}")) }
        }
        Section("Status", done = null) {
            status.forEach { row ->
                val mark = when (row.ok) { true -> "✓ "; false -> "✗ "; null -> "" }
                Text(
                    mark + row.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (row.ok) {
                        false -> MaterialTheme.colorScheme.error
                        true -> MaterialTheme.colorScheme.primary
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
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

        // ---- step 5: keep running in the background ----
        Section("5. Keep Ghadd running", done = null) {
            Line("Your phone closes background apps to save battery. Do these once so Ghadd keeps working even when you close all apps:")

            Line(if (battery) "✓ Battery: unrestricted" else "a) Let Ghadd run in the background:")
            if (!battery) Button(onClick = onBattery, modifier = Modifier.fillMaxWidth()) { Text("Allow background running") }

            Line(
                if (KeepAlive.isVivo) "b) In the screen that opens, find Ghadd and turn it ON (Autostart / Background startup / High background power usage):"
                else "b) If your phone has an Autostart or background-apps screen, allow Ghadd there:"
            )
            OutlinedButton(onClick = onBackgroundSettings, modifier = Modifier.fillMaxWidth()) { Text("Open autostart settings") }

            Line("c) In VPN settings, tap the gear next to Ghadd and turn on \"Always-on VPN\" so the filter starts with the phone. Leave \"Block connections without VPN\" OFF: it cuts your whole internet with Ghadd.")
            OutlinedButton(onClick = onVpnSettings, modifier = Modifier.fillMaxWidth()) { Text("Open VPN settings") }

            Line("d) Open recent apps, then lock Ghadd's card (pull it down or tap the lock icon) so \"Clear all\" doesn't close it.")
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

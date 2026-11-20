package org.ghadd

import android.Manifest
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    // Bumped in onResume (and after permission prompts) so the screen re-reads phone state.
    private var tick by mutableIntStateOf(0)

    private val vpnConsent =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) startVpn()
            tick++
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }

    private enum class Screen { Home, Apps, Details }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GhaddTheme {
                val t = tick
                val steps = remember(t) {
                    buildSetupSteps(this, ::enableFilter) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                var setupFinished by remember { mutableStateOf(Prefs.setupFinished) }
                var started by rememberSaveable { mutableStateOf(false) }
                var tried by rememberSaveable { mutableStateOf(listOf<String>()) }
                var screen by rememberSaveable { mutableStateOf(Screen.Home) }

                if (!setupFinished) {
                    val skipped = remember(t) { Prefs.skippedSteps }
                    val next = steps.firstOrNull { !it.done() && it.id !in skipped }
                    when {
                        !started -> WelcomeScreen(onStart = { started = true })
                        next != null -> SetupStepScreen(
                            number = steps.indexOf(next) + 1,
                            total = steps.size,
                            step = next,
                            triedAlready = next.id in tried,
                            onContinue = { tried = tried + next.id; next.open() },
                            onSkip = { Prefs.skipStep(next.id); tick++ }
                        )
                        else -> {
                            val finish = { Prefs.setupFinished = true; setupFinished = true }
                            SetupDoneScreen(
                                missingEssential = steps.count { it.essential && !it.done() },
                                onChooseApps = { finish(); screen = Screen.Apps },
                                onFinish = finish
                            )
                        }
                    }
                    return@GhaddTheme
                }

                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
                when (screen) {
                    Screen.Apps -> AppsScreen(t, onBack = { screen = Screen.Home }, onTryRemove = ::askRemove)
                    Screen.Details -> DetailsScreen(t, onBack = { screen = Screen.Home })
                    Screen.Home -> HomeScreen(
                        tick = t,
                        issues = remember(t, AppState.vpnRunning) { collectIssues(this, steps, ::enableFilter) },
                        onPickApps = { screen = Screen.Apps },
                        onDetails = { screen = Screen.Details }
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
        val consent = try {
            VpnService.prepare(this)
        } catch (_: Exception) {
            null
        }
        if (consent == null) {
            startVpn()
            return
        }
        try {
            vpnConsent.launch(consent)
        } catch (_: Exception) {
            // Some phones have no VPN consent screen; nothing more we can do here.
        }
    }

    private fun startVpn() {
        Prefs.filterWanted = true
        KeepAlive.startFilter(this)
        tick++
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

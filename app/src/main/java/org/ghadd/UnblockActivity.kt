package org.ghadd

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The unblock flow. Opened either because a blocked app was launched (open it for this one
 * visit), or because you tried to remove an app from the blocked list (permanent removal).
 *
 *  1. Quran plays and a reminder is shown, with a short wait.
 *  2. Choose: "I'm choosing my desire over Allah's rule"  or  "I genuinely need it".
 *  3a. Desire: a long wait, then access. Logged honestly and resets your streak.
 *  3b. Need: type the pledge, a short wait, then access.
 *
 * Access lasts for one visit: the app locks again as soon as you leave it (see [Unlock]).
 */
class UnblockActivity : ComponentActivity() {
    private lateinit var player: QuranPlayer
    private lateinit var pkg: String
    private lateinit var label: String
    private var removeMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val p = intent.getStringExtra(EXTRA_PKG)
        if (p == null) {
            finish()
            return
        }
        pkg = p
        removeMode = intent.getBooleanExtra(EXTRA_REMOVE, false)
        label = appLabel(pkg)
        showingFor = pkg

        player = QuranPlayer(this).also { it.start() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = stayBlocked()
        })

        setContent {
            GhaddTheme {
                UnblockScreen(
                    app = label,
                    removeMode = removeMode,
                    player = player,
                    onStay = ::stayBlocked,
                    onGrant = ::grant
                )
            }
        }
    }

    private fun appLabel(pkg: String): String = Apps.label(this, pkg)

    private fun stayBlocked() {
        // Coming from a blocked app: send them home. Coming from the app list: just close.
        if (!removeMode) Perms.goHome(this)
        finish()
    }

    private fun grant(kind: String) {
        if (removeMode) {
            Prefs.blockedApps = Prefs.blockedApps - pkg
            Prefs.addLog(kind, label, 0)
            finish()
            return
        }
        Prefs.addLog(kind, label, Prefs.OPENED_ONCE)
        // Unlocked for this visit only. Set before the app opens so it isn't blocked again.
        Unlock.pkg = pkg
        // Bring the app to the front (it may be behind us, or we may have sent them home).
        val launch = packageManager.getLaunchIntentForPackage(pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val opened = launch != null && runCatching { startActivity(launch) }.isSuccess
        if (!opened) {
            // App can't be opened from here (uninstalled, no icon): still unlocked, just close.
            Log.w("UnblockActivity", "could not open $pkg")
        }
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Already open (single instance). If it's for another app now, start over for that app.
        val p = intent.getStringExtra(EXTRA_PKG) ?: return
        if (p != pkgOrNull() || intent.getBooleanExtra(EXTRA_REMOVE, false) != removeMode) {
            setIntent(intent)
            recreate()
        }
    }

    override fun onStart() {
        super.onStart()
        if (::player.isInitialized) player.resume()
    }

    override fun onStop() {
        if (::player.isInitialized) player.pause()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        ScreenState.visible = "unblock"
        Launcher.clearAlert(this)
    }

    override fun onPause() {
        if (ScreenState.visible == "unblock") ScreenState.visible = null
        super.onPause()
    }

    override fun onDestroy() {
        if (::player.isInitialized) player.release()
        if (showingFor == pkgOrNull()) showingFor = null
        super.onDestroy()
    }

    private fun pkgOrNull(): String? = if (::pkg.isInitialized) pkg else null

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_REMOVE = "remove"

        /** Package currently being shown, so the accessibility service doesn't stack screens. */
        @Volatile
        var showingFor: String? = null
    }
}

private enum class Phase { Reminder, Choose, Desire, Need }

private const val REMINDER_SECONDS = 15
private const val DESIRE_SECONDS = 90
private const val NEED_SECONDS = 20

@Composable
private fun rememberCountdown(key: Any, seconds: Int): Int {
    var left by remember(key) { mutableIntStateOf(seconds) }
    LaunchedEffect(key) {
        left = seconds
        while (left > 0) {
            delay(1000)
            left--
        }
    }
    return left
}

@Composable
private fun UnblockScreen(
    app: String,
    removeMode: Boolean,
    player: QuranPlayer,
    onStay: () -> Unit,
    onGrant: (kind: String) -> Unit
) {
    var phase by remember { mutableStateOf(Phase.Reminder) }
    val reminder = remember { Content.reminders.random() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            if (removeMode) "Remove $app from your blocked list?" else "$app is blocked",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        VerseCard(player.current)

        when (phase) {
            Phase.Reminder -> {
                val left = rememberCountdown("reminder", REMINDER_SECONDS)
                Body(reminder)
                Body("You blocked this yourself, for a reason. Take a breath.")
                OutlinedButton(
                    onClick = { phase = Phase.Choose },
                    enabled = left == 0,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (left > 0) "Wait $left s…" else "I still want to unblock it") }
            }

            Phase.Choose -> {
                Body("Be honest with yourself. Why do you want this unblocked?")
                OutlinedButton(
                    onClick = { phase = Phase.Desire },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("I'm choosing my desire over Allah's rule") }
                OutlinedButton(
                    onClick = { phase = Phase.Need },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("I genuinely need it (work, a client, something important)") }
            }

            Phase.Desire -> {
                val left = rememberCountdown("desire", DESIRE_SECONDS)
                Body(
                    "You are admitting that you are choosing your desire over Allah's rule. " +
                        "This will be logged and your streak resets. Use this time to change your mind."
                )
                Button(
                    onClick = { onGrant(Prefs.KIND_DESIRE) },
                    enabled = left == 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        when {
                            left > 0 -> "Wait $left s…"
                            removeMode -> "Remove it anyway"
                            else -> "Open $app anyway"
                        }
                    )
                }
            }

            Phase.Need -> {
                var typed by remember { mutableStateOf("") }
                val ok = Content.pledgeMatches(typed)
                // Close the keyboard as soon as the pledge is right so it doesn't hide the buttons.
                val focus = LocalFocusManager.current
                LaunchedEffect(ok) { if (ok) focus.clearFocus() }
                Body("Write this sentence exactly, and mean it:")
                Text(
                    Content.PLEDGE,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Type it here") },
                    // Autocorrect "fixes" words like "haram" and makes the pledge impossible to type.
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (ok) {
                    val left = rememberCountdown("need", NEED_SECONDS)
                    if (left > 0) {
                        Body("Wait $left s…")
                    } else if (removeMode) {
                        Button(
                            onClick = { onGrant(Prefs.KIND_NEED) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Remove it from the blocked list") }
                    } else {
                        Body("$app will open now. It locks again as soon as you leave it.")
                        Button(
                            onClick = { onGrant(Prefs.KIND_NEED) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Open $app") }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onStay, modifier = Modifier.fillMaxWidth()) { Text("Stay blocked") }
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

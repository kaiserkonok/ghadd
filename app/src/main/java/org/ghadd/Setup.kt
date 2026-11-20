package org.ghadd

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** One thing Ghadd needs from the phone. */
class SetupStep(
    val id: String,
    val icon: ImageVector,
    /** Short name, used in the "needs attention" list. */
    val title: String,
    /** One sentence: why Ghadd needs this, in plain words. */
    val why: String,
    /** What to do on the screen that opens. */
    val how: List<String>,
    /** Without this, the main protection doesn't work. */
    val essential: Boolean,
    val done: () -> Boolean,
    val open: () -> Unit,
    /** Extra help, shown under the steps (e.g. "switch is greyed out?"). */
    val help: (@Composable () -> Unit)? = null
)

/** Tries each intent in turn until one opens. */
fun Context.startFirst(vararg intents: Intent): Boolean {
    for (i in intents) {
        try {
            startActivity(i)
            return true
        } catch (_: Exception) {
        }
    }
    return false
}

private fun Context.appDetails() =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

object Intents {
    /** Accessibility settings, asking Settings to scroll to and highlight Ghadd's entry. */
    fun accessibility(ctx: Context): Intent {
        val me = ComponentName(ctx, AppBlockService::class.java).flattenToString()
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(":settings:fragment_args_key", me)
            .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", me) })
    }

    fun openAccessibility(ctx: Context) {
        ctx.startFirst(accessibility(ctx), Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openOverlay(ctx: Context) {
        ctx.startFirst(
            Perms.overlaySettingsIntent(ctx),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            ctx.appDetails()
        )
    }

    fun openBattery(ctx: Context) {
        ctx.startFirst(
            KeepAlive.batteryIntent(ctx),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            ctx.appDetails()
        )
    }

    fun openInstallUpdates(ctx: Context) {
        ctx.startFirst(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")),
            ctx.appDetails()
        )
    }

    fun openNotificationSettings(ctx: Context) {
        ctx.startFirst(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
            ctx.appDetails()
        )
    }

    fun openFullScreen(ctx: Context) {
        if (Build.VERSION.SDK_INT < 34) return
        ctx.startFirst(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")),
            ctx.appDetails()
        )
    }

    fun openVpnSettings(ctx: Context) {
        ctx.startFirst(Intent(Settings.ACTION_VPN_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }

    fun openNetworkSettings(ctx: Context) {
        ctx.startFirst(Intent(Settings.ACTION_WIRELESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
    }

    fun openAppDetails(ctx: Context) {
        ctx.startFirst(ctx.appDetails())
    }
}

fun canUseFullScreen(ctx: Context): Boolean =
    Build.VERSION.SDK_INT < 34 || ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

/**
 * Everything Ghadd needs, in the order the guided setup asks for it.
 * The guided setup walks through these one at a time; the home screen lists any still missing.
 */
fun buildSetupSteps(
    activity: ComponentActivity,
    enableFilter: () -> Unit,
    requestNotifications: () -> Unit
): List<SetupStep> = buildList {
    val ctx: Context = activity

    add(SetupStep(
        id = "filter", icon = Icons.Filled.Lock, essential = true,
        title = "Turn on the porn filter",
        why = "This blocks porn websites in every browser and app on your phone, even in incognito mode.",
        how = listOf(
            "Tap Continue.",
            "Android asks if Ghadd may set up a VPN connection. Tap OK."
        ),
        done = { Prefs.filterWanted && VpnService.prepare(ctx) == null },
        open = enableFilter,
        help = {
            BodyText(
                "Android calls this a \"VPN\", but nothing leaves your phone. Ghadd only checks the names " +
                    "of websites, right here on your phone. Your browsing is never sent anywhere."
            )
        }
    ))

    add(SetupStep(
        id = "accessibility", icon = Icons.Filled.Face, essential = true,
        title = "Turn on app blocking",
        why = "This lets Ghadd notice when you open an app you chose to block. It only sees the app's name, never what's on the screen.",
        how = listOf(
            "Tap Continue.",
            "Find \"Ghadd\" in the list. It may be under \"Downloaded apps\" or \"Installed apps\".",
            "Tap it, turn the switch ON, then tap Allow.",
            "Press the back button to return here."
        ),
        done = { Perms.accessibilityEnabled(ctx) },
        open = { Intents.openAccessibility(ctx) },
        help = if (Build.VERSION.SDK_INT >= 33) {
            { RestrictedSettingHelp(ctx) }
        } else null
    ))

    add(SetupStep(
        id = "overlay", icon = Icons.Filled.Info, essential = true,
        title = "Let Ghadd show its reminder screen",
        why = "When you open a blocked site or app, Ghadd shows the Quran reminder on top of it.",
        how = listOf(
            "Tap Continue.",
            "If you see a list, tap \"Ghadd\".",
            "Turn \"Allow display over other apps\" ON.",
            "Press the back button to return here."
        ),
        done = { Perms.overlayGranted(ctx) },
        open = { Intents.openOverlay(ctx) }
    ))

    if (Build.VERSION.SDK_INT >= 33) add(SetupStep(
        id = "notifications", icon = Icons.Filled.Notifications, essential = false,
        title = "Allow Ghadd's notifications",
        why = "Some phones only let the reminder screen open through a notification. Ghadd never sends ads or spam.",
        how = if (Prefs.notificationsAsked >= 2) listOf(
            "Tap Continue.",
            "Turn \"Allow notifications\" (or \"All Ghadd notifications\") ON.",
            "Press the back button to return here."
        ) else listOf("Tap Continue.", "Tap Allow."),
        done = {
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        },
        open = {
            // Android stops showing the prompt after it's been refused twice.
            if (Prefs.notificationsAsked >= 2) Intents.openNotificationSettings(ctx)
            else {
                Prefs.notificationsAsked = Prefs.notificationsAsked + 1
                requestNotifications()
            }
        }
    ))

    add(SetupStep(
        id = "battery", icon = Icons.Filled.Star, essential = false,
        title = "Keep Ghadd running",
        why = "Phones close apps in the background to save battery. This stops your phone from switching Ghadd off. It uses almost no battery.",
        how = listOf(
            "Tap Continue.",
            "Tap Allow. (If you see a list instead, find Ghadd and choose \"Don't optimise\" or \"Unrestricted\".)"
        ),
        done = { KeepAlive.ignoringBatteryOptimizations(ctx) },
        open = { Intents.openBattery(ctx) }
    ))

    if (Build.VERSION.SDK_INT >= 34) add(SetupStep(
        id = "fullscreen", icon = Icons.Filled.Warning, essential = false,
        title = "Allow full-screen reminders",
        why = "If your phone blocks the reminder screen, Ghadd shows it like an alarm instead.",
        how = listOf("Tap Continue.", "Turn the switch ON.", "Press the back button to return here."),
        done = { canUseFullScreen(ctx) },
        open = { Intents.openFullScreen(ctx) }
    ))

    if (KeepAlive.hasOemRestrictions) {
        add(SetupStep(
            id = "popup", icon = Icons.Filled.Home, essential = false,
            title = "Let Ghadd pop up over other apps",
            why = "Your phone (${Build.MANUFACTURER}) has its own extra setting that stops apps from opening screens.",
            how = listOf(
                "Tap Continue.",
                "Find \"Display pop-up windows\" or \"Pop-up in background\" (it may be under \"Other permissions\").",
                "Set it to Allow.",
                "Press the back button to return here."
            ),
            done = { Prefs.setupDone("popup") },
            open = { Prefs.markSetupDone("popup"); KeepAlive.openPopupPermission(ctx) }
        ))
        add(SetupStep(
            id = "autostart", icon = Icons.Filled.PlayArrow, essential = false,
            title = "Let Ghadd start by itself",
            why = "Your phone (${Build.MANUFACTURER}) stops apps from restarting after it closes them, unless you allow it.",
            how = listOf(
                "Tap Continue.",
                "Find Ghadd and turn it ON (it may be called Autostart, Auto-launch or Background startup).",
                "Press the back button to return here."
            ),
            done = { Prefs.setupDone("autostart") },
            open = { Prefs.markSetupDone("autostart"); KeepAlive.openBackgroundSettings(ctx) }
        ))
    }

    add(SetupStep(
        id = "updates", icon = Icons.Filled.Refresh, essential = false,
        title = "Allow automatic updates",
        why = "Ghadd fixes problems and adds new blocked sites through updates. This lets it update itself.",
        how = listOf(
            "Tap Continue.",
            "Turn \"Allow from this source\" ON.",
            "Press the back button to return here."
        ),
        done = { Updater.canInstall(ctx) },
        open = { Intents.openInstallUpdates(ctx) }
    ))
}

/**
 * Android 13+ greys out the accessibility switch for apps installed from a file
 * ("Restricted setting"). The fix is hidden in a menu, so explain it step by step.
 */
@Composable
private fun RestrictedSettingHelp(ctx: Context) {
    var open by remember { mutableStateOf(false) }
    SectionCard(container = MaterialTheme.colorScheme.surfaceVariant) {
        TextButton(onClick = { open = !open }, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (open) "Hide help" else "Is the switch grey, or does it say \"Restricted setting\"?",
                textAlign = TextAlign.Center
            )
        }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BodyText("Android does this for apps installed from a file. You can unlock it:")
                NumberedSteps(
                    listOf(
                        "First try to turn Ghadd on once (so Android shows the \"Restricted setting\" message), then come back.",
                        "Tap \"Open Ghadd's app info\" below.",
                        "Tap the ⋮ menu in the top-right corner.",
                        "Tap \"Allow restricted settings\" and confirm with your PIN or fingerprint.",
                        "Come back here and tap Continue again."
                    )
                )
                SecondaryButton("Open Ghadd's app info", onClick = { Intents.openAppDetails(ctx) })
            }
        }
    }
}

// ---- screens ------------------------------------------------------------------------------

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Page {
        Spacer(Modifier.height(40.dp))
        Text(
            "غضّ",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Welcome to Ghadd",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "\"Tell the believing men to lower their gaze.\" (Qur'an 24:30)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        SectionCard {
            Feature(Icons.Filled.Lock, "Blocks porn websites", "In every browser and app, even incognito.")
            Feature(Icons.Filled.Face, "Blocks apps you choose", "Opening one plays Quran and asks you to pause and think.")
            Feature(Icons.Filled.Star, "Tracks your streak", "Everything stays on your phone. No account, no ads.")
        }
        SectionCard(container = MaterialTheme.colorScheme.surfaceVariant) {
            BodyText(
                "Setup takes about 2 minutes. Ghadd will open a few of your phone's settings screens " +
                    "and tell you exactly what to tap on each one.",
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(8.dp))
        BigButton("Start setup", onClick = onStart)
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            BodyText(text)
        }
    }
}

@Composable
fun SetupStepScreen(
    number: Int,
    total: Int,
    step: SetupStep,
    triedAlready: Boolean,
    onContinue: () -> Unit,
    onSkip: () -> Unit
) {
    Page {
        Spacer(Modifier.height(12.dp))
        Text(
            "Step $number of $total",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { (number - 1f) / total },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        IconBadge(step.icon, MaterialTheme.colorScheme.primary, size = 64)
        Text(
            step.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(step.why, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (triedAlready) {
            SectionCard(container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)) {
                Text(
                    "This isn't turned on yet. Tap Continue and follow the steps below once more.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        SectionCard(title = "What to do") { NumberedSteps(step.how) }
        step.help?.invoke()

        Spacer(Modifier.height(4.dp))
        BigButton("Continue", onClick = onContinue)
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text(
                if (step.essential) "Skip (Ghadd won't fully work)" else "Skip for now",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SetupDoneScreen(missingEssential: Int, onChooseApps: () -> Unit, onFinish: () -> Unit) {
    Page {
        Spacer(Modifier.height(48.dp))
        val ok = missingEssential == 0
        IconBadge(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
            size = 88
        )
        Text(
            if (ok) "You're protected" else "Almost done",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        BodyText(
            if (ok) "The porn filter is on. May Allah make it easy for you."
            else "You skipped $missingEssential important step${if (missingEssential > 1) "s" else ""}. " +
                "You can finish ${if (missingEssential > 1) "them" else "it"} from the home screen at any time."
        )
        SectionCard(title = "Next: choose apps to block") {
            BodyText(
                "Social media, games or any app that leads you somewhere you don't want to go. " +
                    "Opening one will play Quran and ask you to stop and think."
            )
            BigButton("Choose apps to block", onClick = onChooseApps)
        }
        SectionCard(title = "Tip", container = MaterialTheme.colorScheme.surfaceVariant) {
            BodyText(
                "Open your recent apps, find Ghadd and lock it (tap its icon and choose Lock, or pull the card " +
                    "down). Then \"Clear all\" won't close it.",
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Go to home screen") }
    }
}

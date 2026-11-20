package org.ghadd

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import android.net.VpnService
import java.text.DateFormat
import java.util.Date

/** Something that needs the user's attention, with a one-tap fix. */
class Issue(val title: String, val text: String, val essential: Boolean, val button: String, val fix: () -> Unit)

fun collectIssues(ctx: Context, steps: List<SetupStep>, enableFilter: () -> Unit): List<Issue> {
    val out = ArrayList<Issue>()

    // Another VPN app took the filter's place.
    if (Prefs.filterWanted && VpnService.prepare(ctx) != null) {
        out += Issue(
            "The porn filter was switched off",
            "Another VPN app took over. Only one VPN can run at a time. Turn Ghadd back on.",
            true, "Turn back on", enableFilter
        )
    }

    for (s in steps) {
        if (s.id == "filter" && Prefs.filterWanted) continue // covered above, or running fine
        if (s.done()) continue
        val text = when {
            s.id == "accessibility" && Prefs.blockedApps.isNotEmpty() && Prefs.setupFinished ->
                "Your phone switched app blocking off, so blocked apps open freely. Turn it back on."
            else -> s.why
        }
        out += Issue(s.title, text, s.essential, "Fix", s.open)
    }

    runCatching { Diagnostics.privateDns(ctx) }.getOrNull()?.let { host ->
        out += Issue(
            "Turn off Private DNS",
            "Private DNS is set to \"$host\", which can skip Ghadd's filter. In the screen that opens, " +
                "find Private DNS and choose Off or Automatic.",
            true, "Open settings"
        ) { Intents.openNetworkSettings(ctx) }
    }
    return out.sortedByDescending { it.essential }
}

@Composable
fun HomeScreen(
    tick: Int,
    issues: List<Issue>,
    onPickApps: () -> Unit,
    onDetails: () -> Unit
) {
    val ctx = LocalContext.current
    val blocked = remember(tick) { Prefs.blockedApps }
    val log = remember(tick) { Prefs.log().take(5) }
    val streak = remember(tick) { Prefs.streakDays() }
    val today = remember(tick) { Prefs.siteBlocksToday() }
    val total = remember(tick) { Prefs.siteBlocksTotal() }

    Page(
        title = "Ghadd",
        actions = {
            IconButton(onClick = onDetails) {
                Icon(Icons.Filled.Info, "Status and help", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    ) {
        StatusHero(filterOn = AppState.vpnRunning, essentialMissing = issues.count { it.essential }, other = issues.count { !it.essential })

        if (issues.isNotEmpty()) {
            SectionCard(title = if (issues.size == 1) "1 thing to fix" else "${issues.size} things to fix") {
                issues.forEachIndexed { i, issue ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    IssueRow(issue)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("$streak", if (streak == 1) "day clean" else "days clean", Modifier.weight(1f))
            Stat("$today", "blocked today", Modifier.weight(1f))
            Stat("$total", "blocked total", Modifier.weight(1f))
        }

        SectionCard(title = "Blocked apps") {
            if (blocked.isEmpty()) {
                BodyText("No apps blocked yet. Block apps that pull you towards things you want to avoid.")
            } else {
                val names = remember(blocked) { blocked.map { Apps.label(ctx, it) }.sorted() }
                BlockedIcons(ctx, blocked)
                BodyText(names.joinToString(", "))
            }
            BigButton(if (blocked.isEmpty()) "Choose apps to block" else "Change blocked apps", onClick = onPickApps)
        }

        if (log.isNotEmpty()) {
            SectionCard(title = "Recent unblocks") {
                val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
                log.forEach {
                    val what = when (it.minutes) {
                        0 -> "removed from list"
                        Prefs.OPENED_ONCE -> "opened once"
                        else -> "${it.minutes} min"
                    }
                    val why = if (it.kind == Prefs.KIND_DESIRE) "desire" else "need"
                    Column {
                        Text(
                            "${it.app}  ·  $what",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${fmt.format(Date(it.time))}  ·  $why",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (it.kind == Prefs.KIND_DESIRE) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusHero(filterOn: Boolean, essentialMissing: Int, other: Int) {
    val (icon, color, title, text) = when {
        essentialMissing > 0 || !filterOn && Prefs.filterWanted -> Hero(
            Icons.Filled.Warning, MaterialTheme.colorScheme.error,
            "You're not fully protected",
            if (!filterOn && Prefs.filterWanted && essentialMissing == 0) "The filter is starting…"
            else "Fix the ${if (essentialMissing == 1) "item" else "items"} below. It only takes a moment."
        )
        other > 0 -> Hero(
            Icons.Filled.CheckCircle, MaterialTheme.colorScheme.primary,
            "You're protected",
            "The filter is on. A few optional settings below make Ghadd more reliable."
        )
        else -> Hero(
            Icons.Filled.Lock, MaterialTheme.colorScheme.primary,
            "You're protected",
            "The filter is on and Ghadd is set up. There is no off switch, by design."
        )
    }
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.14f))
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color, size = 56)
            Column(Modifier.padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = color)
                BodyText(text, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

private data class Hero(val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: Color, val title: String, val text: String)

@Composable
private fun IssueRow(issue: Issue) {
    Column(Modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Warning, null,
                tint = if (issue.essential) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                issue.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        BodyText(issue.text)
        Button(
            onClick = issue.fix,
            shape = RoundedCornerShape(14.dp),
            colors = if (issue.essential) androidx.compose.material3.ButtonDefaults.buttonColors() else WarningButtonColors()
        ) { Text(issue.button) }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Card(
        modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun BlockedIcons(ctx: Context, pkgs: Set<String>) {
    val icons = remember(pkgs) {
        pkgs.take(8).mapNotNull { pkg ->
            runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
    }
    if (icons.isEmpty()) return
    Row {
        icons.forEach {
            Image(it, null, Modifier.size(36.dp))
            Spacer(Modifier.width(8.dp))
        }
    }
}

/** Status details, background-running tips and updates. Meant for troubleshooting. */
@Composable
fun DetailsScreen(tick: Int, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val status = remember(tick) {
        runCatching { Diagnostics.collect(ctx) }
            .getOrElse { listOf(Diagnostics.Row(null, "Status unavailable: ${it.javaClass.simpleName}")) }
    }
    val canUpdate = remember(tick) { Updater.canInstall(ctx) }
    val version = remember { Updater.currentVersion(ctx) }

    Page(title = "Status and help", onBack = onBack) {
        SectionCard(title = "Status") {
            BodyText("If something isn't working, a screenshot of this card helps whoever is helping you.")
            status.forEach { row ->
                Row(verticalAlignment = Alignment.Top) {
                    val (mark, color) = when (row.ok) {
                        true -> "✓" to MaterialTheme.colorScheme.primary
                        false -> "✗" to MaterialTheme.colorScheme.error
                        null -> "•" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(mark, color = color, modifier = Modifier.width(20.dp))
                    Text(row.text, style = MaterialTheme.typography.bodyMedium, color = color)
                }
            }
        }

        SectionCard(title = "Start the filter with the phone") {
            BodyText(
                "In VPN settings, tap the gear ⚙ next to Ghadd and turn on \"Always-on VPN\". " +
                    "Leave \"Block connections without VPN\" OFF, or your internet stops whenever Ghadd restarts."
            )
            SecondaryButton("Open VPN settings", onClick = { Intents.openVpnSettings(ctx) })
        }

        SectionCard(title = "Stop the phone closing Ghadd") {
            BodyText("Open your recent apps, find Ghadd and lock it (tap its icon and choose Lock, or pull the card down).")
            if (KeepAlive.hasOemRestrictions) {
                BodyText("Your phone also has an autostart setting. Find Ghadd there and turn it on.")
                SecondaryButton("Open autostart settings", onClick = { KeepAlive.openBackgroundSettings(ctx) })
            }
            SecondaryButton("Open battery settings", onClick = { Intents.openBattery(ctx) })
        }

        SectionCard(title = "Updates") {
            BodyText(
                if (canUpdate) "Ghadd updates itself automatically. You have version $version."
                else "Automatic updates are off. You have version $version."
            )
            if (Updater.status.isNotEmpty()) BodyText(Updater.status, color = MaterialTheme.colorScheme.onSurface)
            if (!canUpdate) BigButton("Allow automatic updates", onClick = { Intents.openInstallUpdates(ctx) })
            SecondaryButton("Check for updates now", onClick = { Updater.checkAndInstall(ctx) })
        }
    }
}

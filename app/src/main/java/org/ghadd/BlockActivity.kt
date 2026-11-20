package org.ghadd

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Shown when a porn site is opened. Plays Quran. There is deliberately no unblock option. */
class BlockActivity : ComponentActivity() {
    private lateinit var player: QuranPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = QuranPlayer(this).also { it.start() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })

        setContent {
            GhaddTheme {
                BlockScreen(player, onLeave = ::leave)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ScreenState.visible = "block"
        Launcher.clearAlert(this)
    }

    override fun onPause() {
        if (ScreenState.visible == "block") ScreenState.visible = null
        super.onPause()
    }

    private fun leave() {
        Perms.goHome(this)
        finish()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@Composable
private fun BlockScreen(player: QuranPlayer, onLeave: () -> Unit) {
    val reminder = remember { Content.reminders.random() }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Text(
            "Stop. Allah sees you.",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        VerseCard(player.current)
        Text(
            reminder,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onLeave, modifier = Modifier.fillMaxWidth()) {
            Text("I'm walking away")
        }
    }
}

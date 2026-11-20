package org.ghadd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun GhaddTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF7FD1AE),
            onPrimary = Color(0xFF06231A),
            background = Color(0xFF0E1A17),
            onBackground = Color(0xFFE6F2EE),
            surface = Color(0xFF162420),
            onSurface = Color(0xFFE6F2EE),
            surfaceVariant = Color(0xFF1E322C),
            onSurfaceVariant = Color(0xFFB8CCC5),
            outline = Color(0xFF3A524A),
            // Amber: "needs attention"
            tertiary = Color(0xFFF2C46D),
            onTertiary = Color(0xFF2B1D00),
            error = Color(0xFFFFB4A8),
            onError = Color(0xFF5C1008)
        ),
        content = content
    )
}

/** Shows the verse being recited: Arabic, translation and reference. */
@Composable
fun VerseCard(verse: Verse?) {
    if (verse == null) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                verse.arabic,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                verse.translation,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Qur'an ${verse.ref}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

package io.github.m1nexoff.themerift.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.m1nexoff.themerift.ThemeRift
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RAPID_SWITCHES = 3
private const val RAPID_SWITCH_GAP_MILLIS = 300L

/**
 * The whole demo: a settings-style screen whose theme switches through [ThemeRift]. [onThemeShown]
 * fires once the new theme is on screen, for platform chrome such as the status bar icons.
 */
@Composable
fun DemoApp(onThemeShown: (DemoTheme) -> Unit = {}) {
    var theme by rememberSaveable { mutableStateOf(DemoTheme.Light) }
    val scope = rememberCoroutineScope()

    ThemeRift(
        targetState = theme,
        colors = DemoTheme::riftColors,
        modifier = Modifier.fillMaxSize(),
    ) { shown ->
        LaunchedEffect(shown) { onThemeShown(shown) }
        MaterialTheme(colorScheme = shown.colorScheme) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                DemoScreen(
                    selected = shown,
                    onSelect = { theme = it },
                    onRapidFire = {
                        scope.launch {
                            repeat(RAPID_SWITCHES) {
                                theme = theme.next()
                                delay(RAPID_SWITCH_GAP_MILLIS)
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DemoScreen(
    selected: DemoTheme,
    onSelect: (DemoTheme) -> Unit,
    onRapidFire: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Theme Rift", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "Pick a theme. The screen tears open from where you tapped.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ThemePicker(selected, onSelect)
        NowPlayingCard()
        StatsRow()
        SettingsCard()
        OutlinedButton(onClick = onRapidFire, modifier = Modifier.fillMaxWidth()) {
            Text("Switch three times, fast")
        }
    }
}

// Each option is drawn in its own theme, so the picker previews what a tap will tear open.
@Composable
private fun ThemePicker(selected: DemoTheme, onSelect: (DemoTheme) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DemoTheme.entries.forEach { theme ->
            val isSelected = theme == selected
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(theme.colorScheme.background)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = shape,
                    )
                    .clickable { onSelect(theme) }
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    Swatch(theme.colorScheme.primary)
                    Swatch(theme.colorScheme.surface)
                    Swatch(theme.colorScheme.onBackground)
                }
                Text(
                    theme.label,
                    color = theme.colorScheme.onBackground,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

@Composable
private fun NowPlayingCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "NOW PLAYING",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Spring campaign",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                "Lobby screens · 14 of 38 slides",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { 0.37f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surface,
            )
        }
    }
}

@Composable
private fun StatsRow() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("128", "Screens", Modifier.weight(1f))
        StatTile("97%", "Online", Modifier.weight(1f))
        StatTile("12", "Playlists", Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Real, remembered state: it survives every switch, since the screen is composed once, not twice.
@Composable
private fun SettingsCard() {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            SettingRow("Notifications", "Alerts when a screen goes offline", initial = true)
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingRow("Autoplay", "Start the next playlist on its own", initial = false)
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingRow("Wi-Fi only", "Download media on Wi-Fi only", initial = true)
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, initial: Boolean) {
    var checked by remember { mutableStateOf(initial) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { checked = !checked }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = { checked = it })
    }
}

package io.github.m1nexoff.themetransitions.demo

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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.m1nexoff.themetransitions.AnimatedTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RAPID_SWITCHES = 3
private const val RAPID_SWITCH_GAP_MILLIS = 300L

/**
 * The whole demo: a screen whose theme switches through [AnimatedTheme], with a playground to pick the
 * transition. [onThemeShown] fires once the new theme is on screen, for platform chrome such as the
 * status bar icons. [bench] names a [BenchCase] to run instead of waiting for taps.
 */
@Composable
fun DemoApp(bench: String? = null, onThemeShown: (DemoTheme) -> Unit = {}) {
    var theme by rememberSaveable { mutableStateOf(DemoTheme.Light) }
    var playground by rememberSaveable(stateSaver = PlaygroundSaver) { mutableStateOf(Playground()) }
    val benchCase = remember(bench) { BenchCase.of(bench) }
    val scope = rememberCoroutineScope()
    var showcase by remember { mutableStateOf<Job?>(null) }

    if (benchCase != null) {
        LaunchedEffect(benchCase) { runBench(benchCase, bench!!) { theme = theme.next() } }
    }

    AnimatedTheme(
        targetState = theme,
        modifier = Modifier.fillMaxSize(),
        transition = benchCase?.transition(theme) ?: playground.transition(theme),
    ) { shown ->
        LaunchedEffect(shown) { onThemeShown(shown) }
        MaterialTheme(colorScheme = shown.colorScheme) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (BenchCase.isPlain(bench)) return@Surface
                DemoScreen(
                    selected = shown,
                    onSelect = { theme = it },
                    playground = playground,
                    onPlaygroundChange = { playground = it },
                    onRapidFire = {
                        scope.launch {
                            repeat(RAPID_SWITCHES) {
                                theme = theme.next()
                                delay(RAPID_SWITCH_GAP_MILLIS)
                            }
                        }
                    },
                    playingAll = showcase != null,
                    onPlayAll = {
                        val running = showcase
                        if (running != null) {
                            running.cancel()
                        } else {
                            val before = playground
                            showcase = scope.launch {
                                playShowcase(
                                    show = { playground = it?.playground ?: before },
                                    switch = { theme = theme.next() },
                                )
                            }.also { job -> job.invokeOnCompletion { showcase = null } }
                        }
                    },
                )
            }
        }
    }
}

/**
 * The demo screen in [theme], switching through what [playground] is set to, with its controls left
 * unwired: for stills and recordings.
 */
@Composable
fun PreviewDemo(theme: DemoTheme, playground: Playground) {
    AnimatedTheme(targetState = theme, modifier = Modifier.fillMaxSize(), transition = playground.transition(theme)) { shown ->
        MaterialTheme(colorScheme = shown.colorScheme) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                DemoScreen(
                    selected = shown,
                    onSelect = {},
                    playground = playground,
                    onPlaygroundChange = {},
                    onRapidFire = {},
                    playingAll = false,
                    onPlayAll = {},
                )
            }
        }
    }
}

private val PlaygroundSaver = Saver<Playground, List<Any>>(
    save = { listOf(it.reveal.ordinal, it.effects, it.durationMillis, it.angleDegrees, it.tileSizeDp) },
    restore = {
        Playground(
            reveal = Reveal.entries[it[0] as Int],
            effects = it[1] as Int,
            durationMillis = it[2] as Int,
            angleDegrees = it[3] as Float,
            tileSizeDp = it[4] as Float,
        )
    },
)

@Composable
private fun DemoScreen(
    selected: DemoTheme,
    onSelect: (DemoTheme) -> Unit,
    playground: Playground,
    onPlaygroundChange: (Playground) -> Unit,
    onRapidFire: () -> Unit,
    playingAll: Boolean,
    onPlayAll: () -> Unit,
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
            Text("Theme Transitions", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(
                "Pick a theme. The transition starts where you tap.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ThemePicker(selected, onSelect)
        PlaygroundCard(playground, onPlaygroundChange)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRapidFire, modifier = Modifier.weight(1f)) {
                Text("Three times, fast")
            }
            // Every transition and its variants through all three themes, the playground following along.
            Button(onClick = onPlayAll, modifier = Modifier.weight(1f)) {
                Text(if (playingAll) "Stop" else "Play all")
            }
        }
        ProfileCard()
        StatsRow()
        SettingsCard()
    }
}

// Each option is drawn in its own theme, so the picker previews what a tap will switch to.
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
                Text(theme.label, color = theme.colorScheme.onBackground, style = MaterialTheme.typography.labelLarge)
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
private fun ProfileCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("AM", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Alex Morgan",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "Weekly goal · 5 of 7 days",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Button(onClick = {}) { Text("Edit") }
            }
            Spacer(Modifier.height(2.dp))
            LinearProgressIndicator(
                progress = { 5f / 7f },
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
        StatTile("8,412", "Steps", Modifier.weight(1f))
        StatTile("7h 20m", "Sleep", Modifier.weight(1f))
        StatTile("1.6 L", "Water", Modifier.weight(1f))
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Real, remembered state: it survives every switch, since the screen is composed once, not twice.
@Composable
private fun SettingsCard() {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(vertical = 8.dp)) {
            SettingRow("Notifications", "Reminders and replies", initial = true)
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingRow("Sound effects", "Taps and toggles", initial = false)
            HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SettingRow("Auto-update", "Install updates on Wi-Fi", initial = true)
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

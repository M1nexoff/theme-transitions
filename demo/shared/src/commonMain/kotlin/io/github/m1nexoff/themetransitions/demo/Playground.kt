package io.github.m1nexoff.themetransitions.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.m1nexoff.themetransitions.EdgeEffect
import io.github.m1nexoff.themetransitions.Effects
import io.github.m1nexoff.themetransitions.RadialEffect
import io.github.m1nexoff.themetransitions.ThemeTransition
import io.github.m1nexoff.themetransitions.Transitions
import io.github.m1nexoff.themetransitions.UniversalEffect
import kotlin.math.roundToInt

// Which effects a reveal takes, mirroring the library's effect types.
enum class EffectFit { Radial, Edge, Universal, Fixed }

enum class Reveal(val label: String, val defaultMillis: Int, val fit: EffectFit) {
    Circle("Circle", 650, EffectFit.Radial),
    Flood("Flood", 900, EffectFit.Radial),
    Wipe("Wipe", 600, EffectFit.Edge),
    Fade("Fade", 450, EffectFit.Universal),
    Dissolve("Dissolve", 700, EffectFit.Universal),
    Tear("Tear", 2_000, EffectFit.Radial),
    Rift("Rift", 2_000, EffectFit.Fixed),
    None("None", 0, EffectFit.Fixed),
}

enum class Effect(val label: String, val fit: EffectFit) {
    Ring("Ring", EffectFit.Edge),
    Sparks("Sparks", EffectFit.Radial),
    Lightning("Lightning", EffectFit.Radial),
    Shake("Shake", EffectFit.Universal),
    Dim("Dim", EffectFit.Universal),
    Glow("Border glow", EffectFit.Universal),
    ;

    fun fits(reveal: Reveal): Boolean = when (reveal.fit) {
        EffectFit.Radial -> true
        EffectFit.Edge -> fit != EffectFit.Radial
        EffectFit.Universal -> fit == EffectFit.Universal
        EffectFit.Fixed -> false
    }
}

// Everything the playground lets the user pick. Plain values, so it saves across configuration changes.
data class Playground(
    val reveal: Reveal = Reveal.Circle,
    // A bit per Effect, by ordinal.
    val effects: Int = 0,
    val durationMillis: Int = Reveal.Circle.defaultMillis,
    val angleDegrees: Float = 90f,
    val tileSizeDp: Float = 12f,
) {
    fun has(effect: Effect): Boolean = effects and (1 shl effect.ordinal) != 0

    fun toggle(effect: Effect): Playground = copy(effects = effects xor (1 shl effect.ordinal))

    fun withReveal(reveal: Reveal): Playground = copy(reveal = reveal, durationMillis = reveal.defaultMillis)

    // Built for the theme being switched to, so the colours suit it.
    fun transition(theme: DemoTheme): ThemeTransition {
        val chosen = Effect.entries.filter { has(it) && it.fits(reveal) }
        return when (reveal) {
            Reveal.Circle -> Transitions.circle(durationMillis, effects = chosen.map { radial(it, theme) })
            Reveal.Flood -> Transitions.flood(theme.colorScheme.primary, durationMillis, effects = chosen.map { radial(it, theme) })
            Reveal.Wipe -> Transitions.wipe(angleDegrees, durationMillis, effects = chosen.mapNotNull { edge(it, theme) })
            Reveal.Fade -> Transitions.fade(durationMillis, effects = chosen.mapNotNull { universal(it, theme) })
            Reveal.Dissolve -> Transitions.dissolve(tileSizeDp.dp, durationMillis, effects = chosen.mapNotNull { universal(it, theme) })
            Reveal.Tear -> Transitions.tear(durationMillis, effects = chosen.map { radial(it, theme) })
            Reveal.Rift -> theme.rift()
            Reveal.None -> Transitions.none()
        }
    }
}

fun DemoTheme.rift(): ThemeTransition = Transitions.rift(
    glow = colorScheme.onBackground,
    backdrop = colorScheme.background,
    lightning = accent,
)

private fun universal(effect: Effect, theme: DemoTheme): UniversalEffect? = when (effect) {
    Effect.Shake -> Effects.shake(backdrop = theme.colorScheme.background)
    Effect.Dim -> Effects.dim()
    Effect.Glow -> Effects.borderGlow(theme.colorScheme.onBackground)
    else -> null
}

private fun edge(effect: Effect, theme: DemoTheme): EdgeEffect? =
    universal(effect, theme) ?: if (effect == Effect.Ring) Effects.ring(theme.accent) else null

private fun radial(effect: Effect, theme: DemoTheme): RadialEffect = edge(effect, theme) ?: when (effect) {
    Effect.Sparks -> Effects.sparks(theme.accent)
    Effect.Lightning -> Effects.lightning(theme.accent)
    else -> error("$effect has no radial form")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaygroundCard(playground: Playground, onChange: (Playground) -> Unit) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Transition", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Reveal.entries.forEach { reveal ->
                    FilterChip(
                        selected = playground.reveal == reveal,
                        onClick = { onChange(playground.withReveal(reveal)) },
                        label = { Text(reveal.label) },
                    )
                }
            }

            val fitting = Effect.entries.filter { it.fits(playground.reveal) }
            if (fitting.isNotEmpty()) {
                Text("Effects", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    fitting.forEach { effect ->
                        FilterChip(
                            selected = playground.has(effect),
                            onClick = { onChange(playground.toggle(effect)) },
                            label = { Text(effect.label) },
                        )
                    }
                }
            } else if (playground.reveal == Reveal.Rift) {
                Text(
                    "A preset: tear with dim, ring, sparks, lightning, shake and border glow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (playground.reveal.fit != EffectFit.Fixed) {
                LabelledSlider(
                    label = "Duration",
                    value = "${playground.durationMillis} ms",
                    current = playground.durationMillis.toFloat(),
                    range = 150f..3_000f,
                    onChange = { onChange(playground.copy(durationMillis = it.roundToInt())) },
                )
            }
            if (playground.reveal == Reveal.Wipe) {
                LabelledSlider(
                    label = "Angle",
                    value = "${playground.angleDegrees.roundToInt()}°",
                    current = playground.angleDegrees,
                    range = 0f..360f,
                    onChange = { onChange(playground.copy(angleDegrees = it)) },
                )
            }
            if (playground.reveal == Reveal.Dissolve) {
                LabelledSlider(
                    label = "Tile size",
                    value = "${playground.tileSizeDp.roundToInt()} dp",
                    current = playground.tileSizeDp,
                    range = 4f..48f,
                    onChange = { onChange(playground.copy(tileSizeDp = it)) },
                )
            }
        }
    }
}

@Composable
private fun LabelledSlider(
    label: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = current, onValueChange = onChange, valueRange = range)
    }
}

package io.github.m1nexoff.themetransitions

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.m1nexoff.themetransitions.geometry.REFERENCE_DENSITY
import io.github.m1nexoff.themetransitions.reveal.CircleSpec
import io.github.m1nexoff.themetransitions.reveal.DissolveSpec
import io.github.m1nexoff.themetransitions.reveal.FadeSpec
import io.github.m1nexoff.themetransitions.reveal.FloodSpec
import io.github.m1nexoff.themetransitions.reveal.RevealSpec
import io.github.m1nexoff.themetransitions.reveal.TearSpec
import io.github.m1nexoff.themetransitions.reveal.WipeSpec

/**
 * How [AnimatedTheme] switches from one theme to the next: one reveal, and the effects layered over
 * it. Make one with [Transitions].
 */
@Immutable
public class ThemeTransition internal constructor(
    // Null switches at once.
    internal val reveal: RevealSpec?,
    internal val effects: List<RadialEffect>,
)

/**
 * The transitions [AnimatedTheme] can play. Each reveal takes effects from [Effects]; the type of its
 * `effects` parameter says which fit.
 */
public object Transitions {

    /** A hot red that reads on light and dark themes alike, the rift's lightning. */
    public val RiftLightning: Color = Color(0xFFFF1744)

    private val None = ThemeTransition(reveal = null, effects = emptyList())

    /**
     * The new theme grows in a circle from [origin], by default the user's last touch. The default
     * transition.
     */
    public fun circle(
        durationMillis: Int = 650,
        easing: Easing = FastOutSlowInEasing,
        origin: TransitionOrigin = TransitionOrigin.Touch,
        effects: List<RadialEffect> = emptyList(),
    ): ThemeTransition = ThemeTransition(CircleSpec(durationMillis, easing, origin), effects)

    /**
     * A circle of [color] grows from [origin] until it covers the screen, then shrinks back into it,
     * uncovering the new theme. Each half takes half of [durationMillis] and runs through the whole
     * [easing]. The incoming theme's primary colour reads well.
     */
    public fun flood(
        color: Color,
        durationMillis: Int = 900,
        easing: Easing = FastOutSlowInEasing,
        origin: TransitionOrigin = TransitionOrigin.Touch,
        effects: List<RadialEffect> = emptyList(),
    ): ThemeTransition = ThemeTransition(FloodSpec(color, durationMillis, easing, origin), effects)

    /**
     * A straight edge sweeps across the screen, the new theme behind it.
     *
     * @param angleDegrees the direction the edge travels in, clockwise from the right: 0 sweeps left to
     * right, 90 top to bottom, 45 from the top-left corner to the bottom-right.
     */
    public fun wipe(
        angleDegrees: Float = 90f,
        durationMillis: Int = 600,
        easing: Easing = FastOutSlowInEasing,
        effects: List<EdgeEffect> = emptyList(),
    ): ThemeTransition = ThemeTransition(WipeSpec(angleDegrees, durationMillis, easing), effects)

    /** The old theme fades out over the new one. */
    public fun fade(
        durationMillis: Int = 450,
        easing: Easing = FastOutSlowInEasing,
        effects: List<UniversalEffect> = emptyList(),
    ): ThemeTransition = ThemeTransition(FadeSpec(durationMillis, easing), effects)

    /**
     * The old theme breaks into square tiles of [tileSize] that vanish in random order. Before Android
     * 10 the tiles are drawn one by one, so there they grow as needed to stay few.
     */
    public fun dissolve(
        tileSize: Dp = 12.dp,
        durationMillis: Int = 700,
        easing: Easing = LinearEasing,
        effects: List<UniversalEffect> = emptyList(),
    ): ThemeTransition = ThemeTransition(DissolveSpec(tileSize, durationMillis, easing), effects)

    /**
     * A ragged hole that wobbles as it grows, the rift without its effects.
     */
    public fun tear(
        durationMillis: Int = 2_000,
        easing: Easing = LinearOutSlowInEasing,
        origin: TransitionOrigin = TransitionOrigin.Touch,
        effects: List<RadialEffect> = emptyList(),
    ): ThemeTransition = ThemeTransition(TearSpec(durationMillis, easing, origin), effects)

    /**
     * The screen tears open where the user tapped: [tear] with lightning crackling off the edge, sparks
     * spiralling in, the screen dimming and shaking, and a glow rattling round the border.
     *
     * @param glow the border glow; the incoming theme's content colour reads best.
     * @param backdrop the incoming theme's background, for the strips the shake uncovers.
     * @param lightning the bolts, the sparks and the edge.
     * @param scrim dims the screen while the rift opens, so the lightning reads on a light theme too.
     */
    public fun rift(
        glow: Color,
        backdrop: Color,
        lightning: Color = RiftLightning,
        scrim: Color = Color.Black,
        origin: TransitionOrigin = TransitionOrigin.Touch,
    ): ThemeTransition = tear(
        origin = origin,
        effects = listOf(
            Effects.dim(scrim),
            Effects.ring(lightning, width = (6f / REFERENCE_DENSITY).dp, flicker = true),
            Effects.sparks(lightning),
            Effects.lightning(lightning),
            Effects.shake(strength = (8f / REFERENCE_DENSITY).dp, backdrop = backdrop),
            Effects.borderGlow(glow),
        ),
    )

    /** Switches at once, with nothing drawn. */
    public fun none(): ThemeTransition = None
}

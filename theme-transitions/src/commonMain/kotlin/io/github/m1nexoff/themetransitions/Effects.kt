package io.github.m1nexoff.themetransitions

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.m1nexoff.themetransitions.effect.BorderGlowRun
import io.github.m1nexoff.themetransitions.effect.DimRun
import io.github.m1nexoff.themetransitions.effect.EffectRun
import io.github.m1nexoff.themetransitions.effect.LightningRun
import io.github.m1nexoff.themetransitions.effect.RingRun
import io.github.m1nexoff.themetransitions.effect.ShakeRun
import io.github.m1nexoff.themetransitions.effect.SparksRun
import io.github.m1nexoff.themetransitions.reveal.RunSetup

/**
 * An effect for a reveal that opens from a point — [Transitions.circle], [Transitions.flood],
 * [Transitions.tear] — which accept every effect.
 *
 * The three effect types nest so that each reveal accepts exactly the effects it can draw:
 * [UniversalEffect]s work on any reveal, [EdgeEffect]s need a line between the themes, and the rest
 * need a centre to orbit too. Make effects with [Effects].
 */
public sealed interface RadialEffect

/** An effect that follows the line between the themes, so any reveal but a fade or a dissolve takes it. */
public sealed interface EdgeEffect : RadialEffect

/** An effect that works on any reveal. */
public sealed interface UniversalEffect : EdgeEffect

/**
 * Effects to layer over a reveal, in [Transitions]' `effects` parameter. Each draws on its own layer —
 * dim, then the ring, sparks and lightning, then the border glow — whatever order they are listed in.
 */
public object Effects {

    /**
     * A line along the edge between the themes.
     *
     * @param flicker whether it flickers and fades as the reveal opens, as on [Transitions.rift].
     */
    public fun ring(color: Color, width: Dp = 3.dp, flicker: Boolean = false): EdgeEffect =
        Ring(color, width, flicker)

    /** [count] sparks spiralling in towards the edge from outside it. */
    public fun sparks(color: Color, count: Int = 100): RadialEffect = Sparks(color, count)

    /** [bolts] bolts of lightning crackling outward off the edge, with branches. */
    public fun lightning(color: Color, bolts: Int = 12): RadialEffect = Lightning(color, bolts)

    /**
     * Shakes the screen, hardest midway through the reveal.
     *
     * @param backdrop fills the thin strips the shake uncovers at the screen's edges. The incoming
     * theme's background reads best.
     */
    public fun shake(strength: Dp = 3.dp, backdrop: Color = Color.Black): UniversalEffect =
        Shake(strength, backdrop)

    /**
     * Dims the screen as the reveal starts and lifts it as the reveal covers the screen, so bright
     * effects read on a light theme too.
     */
    public fun dim(color: Color = Color.Black, maxAlpha: Float = 0.7f): UniversalEffect = Dim(color, maxAlpha)

    /**
     * A glow rattling round the screen's edge. The incoming theme's content colour reads best: light
     * on the way to a dark theme, dark on the way to a light one.
     */
    public fun borderGlow(color: Color): UniversalEffect = BorderGlow(color)
}

// The public types only say where an effect fits; these say what it is.
internal sealed interface EffectSpec {
    fun start(setup: RunSetup): EffectRun
}

internal val RadialEffect.spec: EffectSpec get() = this as EffectSpec

@Immutable
private class Ring(val color: Color, val width: Dp, val flicker: Boolean) : EdgeEffect, EffectSpec {
    init {
        require(width >= 0.dp) { "width must not be negative, was $width" }
    }

    override fun start(setup: RunSetup) = RingRun(color, with(setup.density) { width.toPx() }, flicker)
}

@Immutable
private class Sparks(val color: Color, val count: Int) : RadialEffect, EffectSpec {
    init {
        require(count >= 0) { "count must not be negative, was $count" }
    }

    override fun start(setup: RunSetup) = SparksRun(color, count, setup.density)
}

@Immutable
private class Lightning(val color: Color, val bolts: Int) : RadialEffect, EffectSpec {
    init {
        require(bolts >= 0) { "bolts must not be negative, was $bolts" }
    }

    override fun start(setup: RunSetup) = LightningRun(color, bolts, setup.density)
}

@Immutable
private class Shake(val strength: Dp, val backdrop: Color) : UniversalEffect, EffectSpec {
    init {
        require(strength >= 0.dp) { "strength must not be negative, was $strength" }
    }

    override fun start(setup: RunSetup) = ShakeRun(with(setup.density) { strength.toPx() }, backdrop)
}

@Immutable
private class Dim(val color: Color, val maxAlpha: Float) : UniversalEffect, EffectSpec {
    init {
        require(maxAlpha in 0f..1f) { "maxAlpha must be within 0..1, was $maxAlpha" }
    }

    override fun start(setup: RunSetup) = DimRun(color, maxAlpha)
}

@Immutable
private class BorderGlow(val color: Color) : UniversalEffect, EffectSpec {
    override fun start(setup: RunSetup) = BorderGlowRun(color, setup.density)
}

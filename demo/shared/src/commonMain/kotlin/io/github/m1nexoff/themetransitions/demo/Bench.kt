package io.github.m1nexoff.themetransitions.demo

import io.github.m1nexoff.themetransitions.Effects
import io.github.m1nexoff.themetransitions.ThemeTransition
import io.github.m1nexoff.themetransitions.TransitionOrigin
import io.github.m1nexoff.themetransitions.Transitions
import kotlinx.coroutines.delay

private const val SWITCHES = 10
private const val SETTLE_MILLIS = 1_500L
// Long enough that scripts/bench.sh, which splits transitions on pauses of 0.6 s, never takes a slow
// switch for the pause between two.
private const val GAP_MILLIS = 1_000L

// A switch from code has no touch behind it; this stands in for a tap on the theme picker.
private val BenchOrigin = TransitionOrigin.Fraction(0.5f, 0.25f)

// The transitions scripts/bench.sh measures, by the name it passes in.
enum class BenchCase(val id: String, val durationMillis: Int) {
    Circle("circle", 650),
    CircleEffects("circle-fx", 650),
    Flood("flood", 900),
    Wipe("wipe", 600),
    WipeDiagonal("wipe45", 600),
    Fade("fade", 450),
    Dissolve("dissolve", 700),
    Tear("tear", 2_000),
    Rift("rift", 2_000),

    // Redraws the live screen every frame for as long as a circle would run, with no old frame and no
    // effects: the cost of the screen under a transition, alone.
    Redraw("redraw", 650),
    ;

    fun transition(theme: DemoTheme): ThemeTransition = when (this) {
        Circle -> Transitions.circle(origin = BenchOrigin)
        CircleEffects -> Transitions.circle(
            origin = BenchOrigin,
            effects = listOf(Effects.ring(theme.accent), Effects.sparks(theme.accent), Effects.lightning(theme.accent), Effects.dim()),
        )
        Flood -> Transitions.flood(theme.colorScheme.primary, origin = BenchOrigin)
        Wipe -> Transitions.wipe()
        WipeDiagonal -> Transitions.wipe(angleDegrees = 45f)
        Fade -> Transitions.fade()
        Dissolve -> Transitions.dissolve()
        Tear -> Transitions.tear(origin = BenchOrigin)
        Rift -> Transitions.rift(theme.colorScheme.onBackground, theme.colorScheme.background, theme.accent, origin = BenchOrigin)
        Redraw -> Transitions.fade(durationMillis, easing = { 1f })
    }

    companion object {
        // "<case>-plain" runs the case over a flat background instead of the demo screen, which separates
        // what the transition costs from what redrawing the screen under it costs.
        fun of(id: String?): BenchCase? = entries.firstOrNull { it.id == id?.removeSuffix(PLAIN) }

        fun isPlain(id: String?): Boolean = id?.endsWith(PLAIN) == true

        private const val PLAIN = "-plain"
    }
}

// Waits for the screen to settle, switches theme ten times, each after the last has finished, and
// logs a line the bench script waits for.
suspend fun runBench(case: BenchCase, id: String, switch: () -> Unit) {
    delay(SETTLE_MILLIS)
    repeat(SWITCHES) {
        switch()
        delay(case.durationMillis + GAP_MILLIS)
    }
    println("ThemeTransitionsBench done $id")
}

package io.github.m1nexoff.themetransitions.demo

import io.github.m1nexoff.themetransitions.Effects
import io.github.m1nexoff.themetransitions.ThemeTransition
import io.github.m1nexoff.themetransitions.TransitionOrigin
import io.github.m1nexoff.themetransitions.Transitions
import kotlinx.coroutines.delay

private const val SWITCHES = 10
private const val SETTLE_MILLIS = 1_500L
private const val GAP_MILLIS = 500L

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
    }

    companion object {
        fun of(id: String?): BenchCase? = entries.firstOrNull { it.id == id }
    }
}

// Waits for the screen to settle, switches theme ten times, each after the last has finished, and
// logs a line the bench script waits for.
suspend fun runBench(case: BenchCase, switch: () -> Unit) {
    delay(SETTLE_MILLIS)
    repeat(SWITCHES) {
        switch()
        delay(case.durationMillis + GAP_MILLIS)
    }
    println("ThemeTransitionsBench done ${case.id}")
}

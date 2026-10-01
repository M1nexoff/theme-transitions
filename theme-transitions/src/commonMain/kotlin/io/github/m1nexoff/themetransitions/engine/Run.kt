package io.github.m1nexoff.themetransitions.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.m1nexoff.themetransitions.ThemeTransition
import io.github.m1nexoff.themetransitions.effect.EffectLayer
import io.github.m1nexoff.themetransitions.effect.EffectRun
import io.github.m1nexoff.themetransitions.effect.ShakeRun
import io.github.m1nexoff.themetransitions.geometry.TWO_PI
import io.github.m1nexoff.themetransitions.reveal.RevealRun
import io.github.m1nexoff.themetransitions.reveal.RunSetup
import io.github.m1nexoff.themetransitions.spec

// Every sin/cos the effects draw runs on one clock that turns once per this many milliseconds.
private const val CLOCK_PERIOD_MILLIS = 2_000f

// How long a transition that a newer one replaced takes to fade its effects out.
private const val RETIRE_MILLIS = 150f

// One switch: its reveal, its effects and its clock.
internal class Run(transition: ThemeTransition, val setup: RunSetup) {
    private val revealSpec = checkNotNull(transition.reveal) { "a transition that switches at once has no run" }
    val reveal: RevealRun = revealSpec.start(setup)

    // Sorted by layer, so the engine can draw a layer's effects in one pass.
    val effects: List<EffectRun> = transition.effects.map { it.spec.start(setup) }.sortedBy { it.layer }
    val shake: ShakeRun? = effects.firstOrNull { it is ShakeRun } as ShakeRun?

    // Milliseconds since the switch. Snapshot state: every tick redraws whatever reads it.
    var clockMillis by mutableFloatStateOf(0f)
        private set

    private var revealStart = Float.NaN
    private var retiredAt = Float.NaN

    // The old frame is let go: covered, baked into a newer run's frame, or dropped.
    var released = false
        private set

    // The old theme is captured and the reveal is moving. Until then the old theme is still live.
    val frozen: Boolean get() = !revealStart.isNaN()

    val retiring: Boolean get() = !retiredAt.isNaN()

    // This run still has an old frame to draw.
    val hasOld: Boolean get() = frozen && !released && !reveal.covered

    val linear: Float
        get() = when {
            !frozen -> 0f
            revealSpec.durationMillis == 0 -> 1f
            else -> ((clockMillis - revealStart) / revealSpec.durationMillis).coerceIn(0f, 1f)
        }

    val progress: Float get() = if (frozen) revealSpec.easing.transform(linear) else 0f

    val time: Float get() = clockMillis / CLOCK_PERIOD_MILLIS * TWO_PI

    // 1, or falling to 0 over RETIRE_MILLIS once a newer transition has replaced this one.
    val alpha: Float
        get() = if (!retiring) 1f else (1f - (clockMillis - retiredAt) / RETIRE_MILLIS).coerceIn(0f, 1f)

    val finished: Boolean get() = if (retiring) alpha <= 0f else frozen && linear >= 1f

    init {
        reveal.update(0f, 0f, time)
    }

    fun advance(deltaMillis: Float) {
        clockMillis += deltaMillis
        // A retired reveal stays where it was: the newer run's frozen frame already holds it.
        if (!retiring) reveal.update(linear, progress, time)
        // Covered, the old frame shows nowhere any more; the effects may run on for a while yet.
        if (frozen && !released && reveal.covered) release()
    }

    fun freeze(image: ImageBitmap) {
        revealStart = clockMillis
        reveal.freeze(image)
        reveal.update(linear, progress, time)
    }

    fun retire() {
        retiredAt = clockMillis
    }

    fun release() {
        if (released) return
        released = true
        reveal.release()
    }

    fun DrawScope.drawEffects(layer: EffectLayer) {
        val alpha = alpha
        for (i in effects.indices) {
            val effect = effects[i]
            if (effect.layer == layer) with(effect) { draw(this@Run, alpha) }
        }
    }
}

package io.github.m1nexoff.themetransitions.effect

import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.m1nexoff.themetransitions.engine.Run

// Drawn in this order, whatever order the effects were listed in: the dim goes over the themes so
// that what follows reads on a light theme too, and the glow goes over everything.
internal enum class EffectLayer { Content, Dim, Ring, Sparks, Lightning, Frame }

// One switch's state for one effect. [alpha] fades a retiring transition's effects out.
internal abstract class EffectRun(val layer: EffectLayer) {
    abstract fun DrawScope.draw(run: Run, alpha: Float)
}

// Up from nothing, a hold, and back to nothing by the end, so nothing is still moving when the
// transition is dropped.
internal fun intensity(progress: Float): Float = when {
    progress < 0.3f -> progress / 0.3f
    progress < 0.7f -> 1f
    else -> (1f - progress) / 0.3f
}.coerceIn(0f, 1f)

package io.github.m1nexoff.themetransitions.effect

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.m1nexoff.themetransitions.engine.Run
import kotlin.math.max
import kotlin.math.min

// Eased in rather than switched on, so a second transition does not flash the screen dark again.
private const val DIM_IN_MILLIS = 150f

// Gone by the time the reveal has covered most of the screen.
private const val DIM_OUT_AT = 0.85f

internal class DimRun(private val color: Color, private val maxAlpha: Float) : EffectRun(EffectLayer.Dim) {

    fun alpha(run: Run): Float =
        maxAlpha * min(run.clockMillis / DIM_IN_MILLIS, 1f) * max(0f, 1f - run.progress / DIM_OUT_AT)

    override fun DrawScope.draw(run: Run, alpha: Float) {
        val dim = alpha(run) * alpha
        if (dim > 0f) drawRect(color, alpha = dim)
    }

    // The strips the shake uncovers sit outside the shaking layer, so they are dimmed on their own.
    fun DrawScope.drawOn(topLeft: Offset, size: Size, run: Run, alpha: Float) {
        val dim = alpha(run) * alpha
        if (dim > 0f) drawRect(color, topLeft, size, alpha = dim)
    }
}

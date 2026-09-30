package io.github.m1nexoff.themetransitions.effect

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.m1nexoff.themetransitions.engine.Run
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Moves the content rather than drawing over it; the engine reads offset() for its layer.
internal class ShakeRun(private val strengthPx: Float, val backdrop: Color) : EffectRun(EffectLayer.Content) {

    fun strength(run: Run, alpha: Float): Float = intensity(run.progress) * alpha

    // Whole pixels: a fractional translation of the entire screen softens every line of text.
    fun offset(run: Run, strength: Float): Offset {
        val px = strengthPx * strength
        return Offset(
            (sin(run.time * 15f) * px).roundToInt().toFloat(),
            (cos(run.time * 12f) * px * 0.75f).roundToInt().toFloat(),
        )
    }

    override fun DrawScope.draw(run: Run, alpha: Float) = Unit
}

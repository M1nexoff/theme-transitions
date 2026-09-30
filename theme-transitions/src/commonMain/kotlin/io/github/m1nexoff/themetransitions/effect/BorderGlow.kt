package io.github.m1nexoff.themetransitions.effect

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import io.github.m1nexoff.themetransitions.engine.Run
import io.github.m1nexoff.themetransitions.geometry.ref
import kotlin.math.cos
import kotlin.math.sin

private const val SHAKE_PX = 8f

// A rattling glow around the screen's edge.
internal class BorderGlowRun(private val color: Color, density: Density) : EffectRun(EffectLayer.Frame) {
    private val jitterPx = density.ref(SHAKE_PX) * 0.6f
    private val inset = density.ref(14f)
    private val corner = CornerRadius(density.ref(28f))
    private val wide = Stroke(density.ref(30f))
    private val narrow = Stroke(density.ref(6f))

    override fun DrawScope.draw(run: Run, alpha: Float) {
        val intensity = intensity(run.progress) * alpha
        if (intensity <= 0f) return
        val time = run.time
        val shown = intensity * (0.6f + sin(time * 9f) * 0.4f)
        val jitter = jitterPx * intensity
        val topLeft = Offset(inset + sin(time * 23f) * jitter, inset + cos(time * 19f) * jitter)
        val frameSize = Size(size.width - inset * 2f, size.height - inset * 2f)
        drawRoundRect(color, topLeft, frameSize, corner, style = wide, alpha = (0.22f * shown).coerceIn(0f, 1f))
        drawRoundRect(color, topLeft, frameSize, corner, style = narrow, alpha = (0.9f * shown).coerceIn(0f, 1f))
    }
}

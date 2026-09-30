package io.github.m1nexoff.themetransitions.effect

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import io.github.m1nexoff.themetransitions.engine.Run
import kotlin.math.sin

// A line along the reveal's edge. Each reveal draws its own outline, the cheapest way its shape allows.
internal class RingRun(color: Color, widthPx: Float, private val flicker: Boolean) : EffectRun(EffectLayer.Ring) {

    // Reused every frame, so drawing the ring allocates nothing.
    private val paint = Paint().apply {
        this.color = color
        style = PaintingStyle.Stroke
        strokeWidth = widthPx
    }
    private val baseAlpha = color.alpha

    override fun DrawScope.draw(run: Run, alpha: Float) {
        val edge = run.reveal.edge ?: return
        if (!edge.visible) return
        val shown = if (flicker) (0.7f + sin(run.time * 8f) * 0.3f) * (1f - run.progress * 0.4f) else 1f
        paint.alpha = (baseAlpha * shown * alpha).coerceIn(0f, 1f)
        drawIntoCanvas { edge.drawOutline(it, paint) }
    }
}

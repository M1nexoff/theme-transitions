package io.github.m1nexoff.themetransitions.effect

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import io.github.m1nexoff.themetransitions.engine.Run
import io.github.m1nexoff.themetransitions.geometry.TWO_PI
import io.github.m1nexoff.themetransitions.geometry.ref
import kotlin.math.cos
import kotlin.math.sin

// Bolts crackling outward off the edge, with branches and twigs off the brightest.
internal class LightningRun(
    private val color: Color,
    private val bolts: Int,
    density: Density,
) : EffectRun(EffectLayer.Lightning) {

    // Reused every frame and every bolt, so drawing allocates nothing.
    private val bolt = Path()
    private val branch = Path()
    private val twig = Path()
    private val boltStroke = Stroke(density.ref(4.5f))
    private val branchStroke = Stroke(density.ref(3f))
    private val twigStroke = Stroke(density.ref(2f))
    private val branchOffset = density.ref(80f)
    private val twigLength = density.ref(30f)

    override fun DrawScope.draw(run: Run, alpha: Float) {
        val edge = run.reveal.radial ?: return
        val progress = run.progress
        if (progress >= 0.75f) return
        val time = run.time
        val fade = (1f - progress) * alpha
        for (i in 0 until bolts) {
            val flicker = sin(time * 7f + i * 2.1f)
            if (flicker < -0.2f) continue

            val angle = i.toFloat() / bolts * TWO_PI + time * 0.6f
            val startRadius = edge.distanceAt(angle) * 0.99f
            val startX = edge.center.x + cos(angle) * startRadius
            val startY = edge.center.y + sin(angle) * startRadius

            var x = startX
            var y = startY
            bolt.reset()
            bolt.moveTo(x, y)
            val segments = 16 + (sin(time + i) * 5).toInt()
            for (segment in 1..segments) {
                val heading = angle + sin(time * 3f + segment * 1.7f + i * 0.9f) * 1.2f
                val length = ref(50f + sin(time * 4f + segment * 2.3f) * 25f)
                x += cos(heading) * length
                y += sin(heading) * length
                bolt.lineTo(x, y)
            }
            drawPath(bolt, color, alpha = (0.9f * fade * (0.5f + flicker * 0.5f)).coerceIn(0f, 1f), style = boltStroke)

            if (flicker > 0.1f) drawBranch(angle, startX, startY, time, fade, flicker)
        }
    }

    private fun DrawScope.drawBranch(
        angle: Float,
        startX: Float,
        startY: Float,
        time: Float,
        fade: Float,
        flicker: Float,
    ) {
        var x = startX + cos(angle + 0.3f) * branchOffset
        var y = startY + sin(angle + 0.3f) * branchOffset
        branch.reset()
        branch.moveTo(x, y)
        for (step in 1..8) {
            val heading = angle + sin(time * 5f + step) * 1.4f + 0.5f
            val length = ref(35f + sin(time * 3f + step * 1.5f) * 18f)
            x += cos(heading) * length
            y += sin(heading) * length
            branch.lineTo(x, y)
        }
        drawPath(branch, color, alpha = (0.7f * fade).coerceIn(0f, 1f), style = branchStroke)

        if (flicker <= 0.3f) return
        twig.reset()
        twig.moveTo(x, y)
        for (step in 1..6) {
            val heading = angle + sin(time * 6f + step * 2f) * 1.5f + 1f
            x += cos(heading) * twigLength
            y += sin(heading) * twigLength
            twig.lineTo(x, y)
        }
        drawPath(twig, color, alpha = (0.5f * fade).coerceIn(0f, 1f), style = twigStroke)
    }
}


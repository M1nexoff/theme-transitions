package io.github.m1nexoff.themetransitions.effect

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import io.github.m1nexoff.themetransitions.engine.Run
import io.github.m1nexoff.themetransitions.geometry.TWO_PI
import io.github.m1nexoff.themetransitions.geometry.ref
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val TRAIL = 3

// Below this a spark adds nothing a viewer could see.
private const val MIN_VISIBLE_ALPHA = 0.004f

private class Particle(val angle: Float, val distance: Float, val speed: Float, val radius: Float)

// Sparks spiralling in towards the edge from outside it.
internal class SparksRun(private val color: Color, count: Int, density: Density) : EffectRun(EffectLayer.Sparks) {

    private val particles = List(count) {
        Particle(
            angle = Random.nextFloat() * TWO_PI,
            distance = Random.nextFloat(),
            speed = 0.15f + Random.nextFloat() * 0.6f,
            radius = density.ref(1.5f + Random.nextFloat() * 4f),
        )
    }

    override fun DrawScope.draw(run: Run, alpha: Float) {
        val edge = run.reveal.radial ?: return
        val progress = run.progress
        if (progress >= 0.9f) return
        val time = run.time
        val radius = edge.radius
        val fade = (1f - progress) * alpha
        for (i in particles.indices) {
            val particle = particles[i]
            val orbit = radius * (1.4f + particle.distance * 0.6f)
            val travelled = ((time * particle.speed + particle.angle) % TWO_PI) / TWO_PI
            if (orbit * (1f - travelled) <= radius * 0.1f) continue

            val baseAngle = particle.angle + time * 0.5f
            val sparkAlpha = (1f - travelled) * fade * 0.7f
            for (t in 0 until TRAIL) {
                val trail = travelled - t * 0.04f
                if (trail < 0f) break
                val trailAlpha = sparkAlpha * (1f - t * 0.35f)
                if (trailAlpha < MIN_VISIBLE_ALPHA) continue
                val distance = orbit * (1f - trail)
                val spiral = baseAngle + trail * 2f
                val sparkRadius = particle.radius * (1f - t * 0.25f)
                val x = edge.center.x + cos(spiral) * distance
                val y = edge.center.y + sin(spiral) * distance
                // Once the edge is wide most orbits run off screen; recording those is most of the cost.
                if (x < -sparkRadius || y < -sparkRadius || x > size.width + sparkRadius || y > size.height + sparkRadius) {
                    continue
                }
                drawCircle(color, sparkRadius, Offset(x, y), alpha = trailAlpha.coerceAtMost(1f))
            }
        }
    }
}

package io.github.m1nexoff.themetransitions

import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.unit.dp
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.DARK
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.HEIGHT
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.WIDTH
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue

// Each effect, part way through a transition: it shows, and where it belongs. The themes are flat
// light and dark greys, so anything red is an effect.
class EffectRenderTest {

    @Test
    fun dimDarkensTheOldTheme() = withScene(transition = { Transitions.tear(effects = listOf(Effects.dim())) }) { scene ->
        scene.tap(TOUCH)
        scene.theme = DARK
        scene.advance(150)
        val outside = samples(scene.frame(), where = farFrom(TOUCH, 450f))
        assertMostly(outside, "the old, light theme should be dimmed") { it.luminance() < 0.75f }
    }

    @Test
    fun borderGlowHugsTheEdge() = withScene(transition = { Transitions.tear(effects = listOf(Effects.borderGlow(RED))) }) { scene ->
        scene.tap(TOUCH)
        scene.theme = DARK
        scene.advance(250)
        // It flickers, so look over a few frames.
        val frames = List(6) { scene.advance(40); scene.frame() }
        val atEdge = frames.maxOf { redPixels(it, FAINT) { x, y -> edgeDistance(x, y) < 40 } }
        val inside = frames.sumOf { redPixels(it, FAINT) { x, y -> edgeDistance(x, y) > 70 } }
        assertTrue(atEdge > 200, "the glow should run round the edge: $atEdge red pixels")
        assertTrue(inside == 0, "the glow should keep to the edge: $inside red pixels inside")
    }

    @Test
    fun shakeUncoversTheBackdrop() = withScene(
        transition = { Transitions.tear(effects = listOf(Effects.shake(strength = 6.dp, backdrop = RED))) },
    ) { scene ->
        scene.tap(TOUCH)
        scene.theme = DARK
        scene.advance(400)
        val pixels = scene.frame()
        val border = (0 until WIDTH).flatMap { listOf(pixels[it, 0], pixels[it, HEIGHT - 1]) } +
            (0 until HEIGHT).flatMap { listOf(pixels[0, it], pixels[WIDTH - 1, it]) }
        assertTrue(border.any { it == RED }, "a strip the shake uncovers should show the backdrop")
    }

    @Test
    fun sparksFlyOutsideTheCircle() = outsideTheCircle(Effects.sparks(RED), "sparks")

    @Test
    fun lightningCracklesOutsideTheCircle() = outsideTheCircle(Effects.lightning(RED), "lightning")

    @Test
    fun ringSitsOnTheCircle() = withScene(
        transition = { Transitions.circle(1_500, LinearEasing, effects = listOf(Effects.ring(RED, width = 4.dp))) },
    ) { scene ->
        scene.tap(TOUCH)
        scene.theme = DARK
        scene.advance(300)
        val distances = red(scene.frame()).map { (x, y) -> hypot(x - TOUCH.x, y - TOUCH.y) }
        assertTrue(distances.size > 50, "the ring should show: ${distances.size} red pixels")
        val spread = distances.max() - distances.min()
        // 4 dp at this density is 10.5 px, plus anti-aliasing on both sides.
        assertTrue(spread < 16f, "every red pixel should be the same distance from the tap, but they spread over $spread px")
    }

    // Sparks and bolts orbit and crackle beyond the edge, and stay on screen.
    private fun outsideTheCircle(effect: RadialEffect, name: String) = withScene(
        transition = { Transitions.circle(1_500, LinearEasing, effects = listOf(effect)) },
    ) { scene ->
        scene.tap(TOUCH)
        scene.theme = DARK
        scene.advance(300)
        val pixels = scene.frame()
        // About a fifth of the way: the circle is some 150 px across the reach of 760.
        val beyond = redPixels(pixels) { x, y -> hypot(x - TOUCH.x, y - TOUCH.y) > 200f }
        assertTrue(beyond > 20, "$name should show beyond the circle: $beyond red pixels")
    }

    private fun red(
        pixels: PixelMap,
        threshold: Float = 0.25f,
        where: (Int, Int) -> Boolean = { _, _ -> true },
    ): List<Pair<Int, Int>> = buildList {
        for (y in 0 until HEIGHT) {
            for (x in 0 until WIDTH) {
                val c = pixels[x, y]
                if (c.red - c.green > threshold && where(x, y)) add(x to y)
            }
        }
    }

    private fun redPixels(pixels: PixelMap, threshold: Float = 0.25f, where: (Int, Int) -> Boolean): Int =
        red(pixels, threshold, where).size

    private fun edgeDistance(x: Int, y: Int): Int = minOf(x, y, WIDTH - 1 - x, HEIGHT - 1 - y)

    private companion object {
        val TOUCH = Offset(100f, 700f)
        val RED = Color(0xFFFF1744)

        // A red too faint for the strict threshold: the glow at the low point of its flicker.
        const val FAINT = 0.12f
    }
}

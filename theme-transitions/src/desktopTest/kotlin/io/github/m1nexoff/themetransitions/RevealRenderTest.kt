package io.github.m1nexoff.themetransitions

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.DARK
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.FRAME_MILLIS
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.HEIGHT
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.LIGHT
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.WIDTH
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// Each reveal, part way: the new theme where it has opened, the old one where it has not. Linear
// easing, so where the edge should be is plain arithmetic; the switch takes a frame or two to freeze,
// hence the range.
class RevealRenderTest {

    @Test
    fun circleOpensFromTheTouch() = withScene(transition = { Transitions.circle(easing = LinearEasing) }) { scene ->
        scene.tap(TOUCH)
        scene.theme = DARK
        val (low, high) = progressAfter(scene, 260, 650)
        val pixels = scene.frame()

        val reach = hypot(300f, 700f)
        assertAll(samples(pixels, where = near(TOUCH, reach * low * 0.9f)), DARK, "inside the circle")
        assertAll(samples(pixels, where = farFrom(TOUCH, reach * high * 1.1f)), LIGHT, "outside the circle")
    }

    @Test
    fun floodCoversThenUncovers() = withScene(transition = { Transitions.flood(RED, easing = LinearEasing) }) { scene ->
        scene.tap(TOUCH)
        scene.theme = DARK
        progressAfter(scene, 225, 900)
        val covering = scene.frame()
        assertAll(samples(covering, where = near(TOUCH, 50f)), RED, "the flood near the tap")
        assertAll(samples(covering, where = near(FAR_CORNER, 15f)), LIGHT, "the old theme beyond the flood")

        scene.advance(450)
        val uncovering = scene.frame()
        assertAll(samples(uncovering, where = near(TOUCH, 30f)), RED, "the flood, shrinking back to the tap")
        assertAll(samples(uncovering, where = near(FAR_CORNER, 15f)), DARK, "the new theme it uncovered")
    }

    @Test
    fun wipeDownSweepsFromTheTop() = withScene(transition = { Transitions.wipe(easing = LinearEasing) }) { scene ->
        scene.theme = DARK
        val (low, high) = progressAfter(scene, 300, 600)
        val pixels = scene.frame()

        assertAll(samples(pixels) { _, y -> y < HEIGHT * low * 0.95f }, DARK, "above the line")
        assertAll(samples(pixels) { _, y -> y > HEIGHT * high * 1.05f }, LIGHT, "below the line")
    }

    @Test
    fun diagonalWipeSweepsFromTheCorner() = withScene(
        transition = { Transitions.wipe(angleDegrees = 45f, easing = LinearEasing) },
    ) { scene ->
        scene.theme = DARK
        progressAfter(scene, 300, 600)
        val pixels = scene.frame()

        assertAll(samples(pixels, where = near(Offset(0f, 0f), 80f)), DARK, "the corner it started from")
        assertAll(samples(pixels, where = near(Offset(WIDTH.toFloat(), HEIGHT.toFloat()), 80f)), LIGHT, "the corner it heads for")
    }

    @Test
    fun fadeBlendsTheThemes() = withScene(transition = { Transitions.fade(easing = LinearEasing) }) { scene ->
        scene.theme = DARK
        progressAfter(scene, 225, 450)
        val all = samples(scene.frame()) { _, _ -> true }

        val dark = DARK.luminance()
        val light = LIGHT.luminance()
        assertTrue(all.all { it.luminance() in (dark + 0.05f)..(light - 0.05f) }, "every pixel should be a blend: ${all.take(3)}")
    }

    @Test
    fun dissolveMixesWholeTiles() = withScene(transition = { Transitions.dissolve(easing = LinearEasing) }) { scene ->
        scene.theme = DARK
        val (low, high) = progressAfter(scene, 350, 700)
        val all = samples(scene.frame()) { _, _ -> true }

        assertTrue(all.all { it == DARK || it == LIGHT }, "every pixel is one theme or the other")
        val dark = all.count { it == DARK }.toFloat() / all.size
        assertTrue(dark in (low - 0.15f)..(high + 0.15f), "dark share $dark, progress $low..$high")
    }

    @Test
    fun everyRevealEndsClean() {
        val reveals = listOf(
            Transitions.circle(),
            Transitions.flood(RED),
            Transitions.wipe(),
            Transitions.wipe(angleDegrees = 200f),
            Transitions.fade(),
            Transitions.dissolve(),
        )
        for (reveal in reveals) {
            withScene(transition = { reveal }) { scene ->
                scene.tap(TOUCH)
                scene.theme = DARK
                scene.advance(1_100)
                assertAll(samples(scene.frame()) { _, _ -> true }, DARK, "after ${reveal.reveal}")
            }
        }
    }

    @Test
    fun overshootingEasingStillEndsClean() {
        val reveals = listOf(
            Transitions.circle(easing = OVERSHOOT),
            Transitions.flood(RED, easing = OVERSHOOT),
            Transitions.wipe(easing = OVERSHOOT),
            Transitions.fade(easing = OVERSHOOT),
            Transitions.dissolve(easing = OVERSHOOT),
            Transitions.tear(durationMillis = 800, easing = OVERSHOOT),
        )
        for (reveal in reveals) {
            withScene(transition = { reveal }) { scene ->
                scene.tap(TOUCH)
                scene.theme = DARK
                scene.advance(1_100)
                assertAll(samples(scene.frame()) { _, _ -> true }, DARK, "after ${reveal.reveal}")
            }
        }
    }

    @Test
    fun originOutsideTheBoxStillCovers() = withScene(
        transition = { Transitions.circle(origin = TransitionOrigin.Fraction(1.2f, -0.1f), easing = LinearEasing) },
    ) { scene ->
        scene.theme = DARK
        progressAfter(scene, 325, 650)
        assertAll(samples(scene.frame(), where = near(Offset(WIDTH.toFloat(), 0f), 20f)), DARK, "the corner nearest the origin")

        scene.advance(600)
        assertAll(samples(scene.frame()) { _, _ -> true }, DARK, "the whole screen once it ends")
    }

    @Test
    fun effectsDrawOnEveryReveal() {
        val all = listOf(
            Effects.dim(),
            Effects.ring(RED),
            Effects.sparks(RED),
            Effects.lightning(RED),
            Effects.shake(backdrop = DARK),
            Effects.borderGlow(RED),
        )
        val reveals = listOf(
            Transitions.circle(effects = all),
            Transitions.flood(RED, effects = all),
            Transitions.tear(durationMillis = 800, effects = all),
            Transitions.wipe(effects = listOf(Effects.ring(RED), Effects.shake(), Effects.dim())),
            Transitions.wipe(angleDegrees = 30f, effects = listOf(Effects.ring(RED))),
            Transitions.fade(effects = listOf(Effects.shake(), Effects.dim(), Effects.borderGlow(RED))),
            Transitions.dissolve(effects = listOf(Effects.shake(), Effects.borderGlow(RED))),
        )
        for (reveal in reveals) {
            withScene(transition = { reveal }) { scene ->
                scene.tap(TOUCH)
                scene.theme = DARK
                repeat(8) { scene.advance(60); scene.frame() }
                scene.advance(1_100)
                assertAll(samples(scene.frame()) { _, _ -> true }, DARK, "after ${reveal.reveal}")
            }
        }
    }

    @Test
    fun ringFollowsTheCircle() = withScene(
        transition = { Transitions.circle(easing = LinearEasing, effects = listOf(Effects.ring(RED, width = 4.dp))) },
    ) { scene ->
        scene.tap(TOUCH)
        scene.theme = DARK
        progressAfter(scene, 260, 650)
        val pixels = scene.frame()
        val red = samples(pixels) { _, _ -> true }.count { it.red > 0.8f && it.green < 0.3f }
        assertTrue(red > 0, "the ring should show somewhere")
    }

    @Test
    fun nonPositiveTileSizeIsRejected() {
        assertFailsWith<IllegalArgumentException> { Transitions.dissolve(tileSize = 0.dp) }
        assertFailsWith<IllegalArgumentException> { Transitions.flood(RED, durationMillis = -5) }
    }

    // Advances [millis] from the switch and returns the range the reveal's linear progress can be in at
    // the next frame, given the frame or two it takes to freeze.
    private fun progressAfter(scene: RenderTestScene, millis: Long, duration: Int): Pair<Float, Float> {
        scene.advance(millis)
        val elapsed = millis + FRAME_MILLIS
        val low = ((elapsed - FRAME_MILLIS * 4) / duration.toFloat()).coerceIn(0f, 1f)
        val high = (elapsed / duration.toFloat()).coerceIn(0f, 1f)
        return low to high
    }

    private companion object {
        val TOUCH = Offset(100f, 700f)
        val FAR_CORNER = Offset(390f, 10f)
        val RED = Color(0xFFFF1744)
        val OVERSHOOT: Easing = CubicBezierEasing(0.3f, -0.5f, 0.7f, 1.5f)
    }
}

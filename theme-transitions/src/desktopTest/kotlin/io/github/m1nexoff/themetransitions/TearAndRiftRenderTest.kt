package io.github.m1nexoff.themetransitions

import androidx.compose.ui.geometry.Offset
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.DARK
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.HEIGHT
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.LIGHT
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.WIDTH
import kotlin.test.Test

// The old theme has to hold outside the hole while the new one shows inside it, and nothing may be
// left once the transition is over.
class TearAndRiftRenderTest {

    private val rift = { target: androidx.compose.ui.graphics.Color ->
        Transitions.rift(glow = if (target == DARK) LIGHT else DARK, backdrop = target)
    }

    @Test
    fun riftShowsTheNewThemeInsideTheHoleAndTheOldOneOutside() = withScene(transition = rift) { scene ->
        scene.tap(TOUCH)
        scene.theme = DARK
        scene.advance(MIDWAY_MILLIS)
        val pixels = scene.frame()

        val inside = samples(pixels) { x, y -> clearOfTheGlow(x, y) && near(TOUCH, 150f)(x, y) }
        val outside = samples(pixels) { x, y -> clearOfTheGlow(x, y) && farFrom(TOUCH, 450f)(x, y) }
        assertMostly(inside, "inside the hole should be the new, dark theme") { it.luminance() < 0.1f }
        assertMostly(outside, "outside the hole should still be the old, light theme") { it.luminance() > 0.35f }
    }

    @Test
    fun riftLeavesNothingBehindOnceItEnds() = withScene(transition = rift) { scene ->
        scene.tap(TOUCH)
        scene.theme = DARK
        scene.advance(OVER_MILLIS)

        assertAll(samples(scene.frame()) { _, _ -> true }, DARK, "the whole screen should be the new theme")
    }

    @Test
    fun tearAloneShowsTheHole() = withScene(transition = { Transitions.tear() }) { scene ->
        scene.tap(TOUCH)
        scene.theme = DARK
        scene.advance(MIDWAY_MILLIS)
        val pixels = scene.frame()

        // No lightning or sparks: every sample is one theme or the other, exactly.
        assertAll(samples(pixels, where = near(TOUCH, 150f)), DARK, "inside the hole")
        assertAll(samples(pixels, where = farFrom(TOUCH, 450f)), LIGHT, "outside the hole")
    }

    @Test
    fun tearLeavesNothingBehindOnceItEnds() = withScene(transition = { Transitions.tear() }) { scene ->
        scene.tap(TOUCH)
        scene.theme = DARK
        scene.advance(OVER_MILLIS)

        assertAll(samples(scene.frame()) { _, _ -> true }, DARK, "the whole screen should be the new theme")
    }

    // The glow rattles round the screen's edge over both themes.
    private fun clearOfTheGlow(x: Float, y: Float): Boolean =
        x in GLOW_MARGIN..(WIDTH - GLOW_MARGIN) && y in GLOW_MARGIN..(HEIGHT - GLOW_MARGIN)

    private companion object {
        const val GLOW_MARGIN = 60f
        const val MIDWAY_MILLIS = 300L
        const val OVER_MILLIS = 2_600L
        val TOUCH = Offset(100f, 700f)
    }
}

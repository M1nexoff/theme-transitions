package io.github.m1nexoff.themetransitions

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.DARK
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.DUSK
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.FRAME_MILLIS
import io.github.m1nexoff.themetransitions.RenderTestScene.Companion.LIGHT
import kotlin.test.Test

// What the engine does around any reveal: switching at once, switching mid-transition, resizing.
class EngineRenderTest {

    private val tear = { _: androidx.compose.ui.graphics.Color -> Transitions.tear() }

    @Test
    fun switchesAtOnceWithAnimationsOff() = withScene(AnimationsOff, tear) { scene ->
        scene.theme = DARK
        scene.advance(FRAME_MILLIS * 2)

        assertAll(samples(scene.frame()) { _, _ -> true }, DARK, "with animations off the switch should be instant")
    }

    @Test
    fun noneSwitchesAtOnce() = withScene(transition = { Transitions.none() }) { scene ->
        scene.theme = DARK
        scene.advance(FRAME_MILLIS * 2)

        assertAll(samples(scene.frame()) { _, _ -> true }, DARK, "none() should switch at once")
    }

    @Test
    fun zeroDurationSwitchesAtOnce() = withScene(transition = { Transitions.tear(durationMillis = 0) }) { scene ->
        scene.theme = DARK
        scene.advance(FRAME_MILLIS * 2)

        assertAll(samples(scene.frame()) { _, _ -> true }, DARK, "a zero duration should switch at once")
    }

    // The second switch freezes what is on screen — the first hole included — and tears open over it.
    @Test
    fun rapidSwitchesEndOnTheLastTheme() = withScene(transition = tear) { scene ->
        scene.tap(FIRST)
        scene.theme = DARK
        scene.advance(120)
        scene.tap(SECOND)
        scene.theme = DUSK
        scene.advance(120)
        val pixels = scene.frame()

        assertAll(samples(pixels, where = near(SECOND, 60f)), DUSK, "inside the second hole")
        assertAll(samples(pixels, where = near(FIRST, 60f)), DARK, "inside the first hole, outside the second")
        assertAll(samples(pixels, where = near(Offset(380f, 400f), 15f)), LIGHT, "outside both holes")

        scene.advance(2_600)
        assertAll(samples(scene.frame()) { _, _ -> true }, DUSK, "the last theme, and nothing else")
    }

    // Three in a row: each freeze bakes in the one before, which itself baked in the first.
    @Test
    fun threeRapidSwitchesKeepEveryHoleAndEndOnTheLastTheme() = withScene(transition = tear) { scene ->
        scene.tap(FIRST)
        scene.theme = DARK
        scene.advance(120)
        scene.tap(SECOND)
        scene.theme = DUSK
        scene.advance(120)
        scene.tap(THIRD)
        scene.theme = LIGHT
        scene.advance(120)
        val pixels = scene.frame()

        assertAll(samples(pixels, where = near(THIRD, 60f)), LIGHT, "inside the third hole")
        assertAll(samples(pixels, where = near(SECOND, 60f)), DUSK, "inside the second hole, outside the third")
        assertAll(samples(pixels, where = near(FIRST, 60f)), DARK, "inside the first hole, outside the others")

        scene.advance(2_600)
        assertAll(samples(scene.frame()) { _, _ -> true }, LIGHT, "the last theme, and nothing else")
    }

    @Test
    fun switchingBackAtOnceEndsOnTheFirstTheme() = withScene(transition = tear) { scene ->
        scene.tap(FIRST)
        scene.theme = DARK
        scene.advance(FRAME_MILLIS)
        scene.theme = LIGHT
        scene.advance(2_600)

        assertAll(samples(scene.frame()) { _, _ -> true }, LIGHT, "back on the first theme, and nothing else")
    }

    // A frozen frame no longer fits a resized box, so the transition ends.
    @Test
    fun resizeEndsTheTransition() = withScene(transition = tear) { scene ->
        scene.tap(FIRST)
        scene.theme = DARK
        scene.advance(200)
        scene.boxSize = IntSize(360, 700)
        scene.advance(FRAME_MILLIS * 2)

        val inside = samples(scene.frame(), width = 360, height = 700) { _, _ -> true }
        assertAll(inside, DARK, "the resized box should show the new theme alone")
    }

    // A frame that comes late — the app recomposing in the new theme, say — must not make the
    // transition skip ahead: it picks up where it was, a frame or two on.
    @Test
    fun aLateFrameDoesNotSkipTheTransitionAhead() = withScene(
        transition = { Transitions.circle(easing = androidx.compose.animation.core.LinearEasing) },
    ) { scene ->
        scene.tap(FIRST)
        scene.theme = DARK
        scene.advance(FRAME_MILLIS * 4)
        scene.stall(400)
        val pixels = scene.frame()

        // 400 ms into 650 would have opened the circle past 60% of its reach.
        val reach = kotlin.math.hypot(300f, 700f)
        assertAll(samples(pixels, where = farFrom(FIRST, reach * 0.3f)), LIGHT, "the old theme beyond the circle")
    }

    private companion object {
        val FIRST = Offset(100f, 700f)
        val SECOND = Offset(300f, 100f)
        val THIRD = Offset(380f, 380f)
    }
}

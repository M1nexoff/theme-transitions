package io.github.m1nexoff.themerift

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertTrue

// Draws the effect through Skia, off screen, and reads back what it put on the pixels: the old theme
// has to hold outside the hole while the new one shows inside it, and nothing may be left once the
// rift is gone.
class ThemeRiftRenderTest {

    @Test
    fun riftShowsTheNewThemeInsideTheHoleAndTheOldOneOutside() = withScene { scene ->
        scene.tap(TOUCH)
        dark = true
        scene.advance(RIFT_MIDWAY_MILLIS)
        val pixels = scene.frame()

        val inside = samples(pixels) { x, y -> clearOfTheGlow(x, y) && hypot(x - TOUCH.x, y - TOUCH.y) < 150f }
        val outside = samples(pixels) { x, y -> clearOfTheGlow(x, y) && hypot(x - TOUCH.x, y - TOUCH.y) > 450f }
        assertMostly(inside, "inside the hole should be the new, dark theme") { it.luminance() < 0.1f }
        assertMostly(outside, "outside the hole should still be the old, light theme") { it.luminance() > 0.35f }
    }

    @Test
    fun riftLeavesNothingBehindOnceItEnds() = withScene { scene ->
        scene.tap(TOUCH)
        dark = true
        scene.advance(RIFT_OVER_MILLIS)
        val pixels = scene.frame()

        val all = samples(pixels) { _, _ -> true }
        assertTrue(all.all { it == DARK }, "the whole screen should be the new theme and nothing else")
    }

    @Test
    fun switchesAtOnceWithAnimationsOff() = withScene(AnimationsOff) { scene ->
        dark = true
        scene.advance(FRAME_MILLIS * 2)
        val pixels = scene.frame()

        val all = samples(pixels) { _, _ -> true }
        assertTrue(all.all { it == DARK }, "with animations off the switch should skip the rift")
    }

    private var dark by mutableStateOf(false)

    private var clock = 0L

    private fun withScene(context: CoroutineContext = EmptyCoroutineContext, block: (ImageComposeScene) -> Unit) {
        val scene = ImageComposeScene(
            width = WIDTH,
            height = HEIGHT,
            density = Density(REFERENCE_DENSITY),
            coroutineContext = context,
        ) {
            ThemeRift(
                targetState = dark,
                colors = { if (it) RiftColors(DARK, LIGHT) else RiftColors(LIGHT, DARK) },
                modifier = Modifier.fillMaxSize(),
            ) { isDark ->
                Box(Modifier.fillMaxSize().background(if (isDark) DARK else LIGHT))
            }
        }
        try {
            scene.advance(FRAME_MILLIS * 3)
            block(scene)
        } finally {
            scene.close()
        }
    }

    private fun ImageComposeScene.tap(at: Offset) {
        sendPointerEvent(PointerEventType.Press, at)
        sendPointerEvent(PointerEventType.Release, at)
    }

    private fun ImageComposeScene.advance(millis: Long) {
        Snapshot.sendApplyNotifications()
        val end = clock + millis
        while (clock < end) {
            clock += FRAME_MILLIS
            render(clock * 1_000_000)
        }
    }

    private fun ImageComposeScene.frame(): PixelMap {
        clock += FRAME_MILLIS
        return render(clock * 1_000_000).toComposeImageBitmap().toPixelMap()
    }

    private fun samples(pixels: PixelMap, where: (Float, Float) -> Boolean): List<Color> = buildList {
        for (y in SAMPLE_STEP / 2 until HEIGHT step SAMPLE_STEP) {
            for (x in SAMPLE_STEP / 2 until WIDTH step SAMPLE_STEP) {
                if (where(x.toFloat(), y.toFloat())) add(pixels[x, y])
            }
        }
    }

    // The glow rattles round the screen's edge over both themes.
    private fun clearOfTheGlow(x: Float, y: Float): Boolean =
        x in GLOW_MARGIN..(WIDTH - GLOW_MARGIN) && y in GLOW_MARGIN..(HEIGHT - GLOW_MARGIN)

    // Sparks and bolts fly across either region, so a few samples are allowed to miss.
    private fun assertMostly(colors: List<Color>, message: String, predicate: (Color) -> Boolean) {
        assertTrue(colors.size >= 20, "too few samples to judge: ${colors.size}")
        val share = colors.count(predicate).toFloat() / colors.size
        assertTrue(share >= 0.9f, "$message — only ${(share * 100).toInt()}% of ${colors.size} samples were")
    }

    private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

    private object AnimationsOff : MotionDurationScale {
        override val scaleFactor = 0f
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 800
        const val SAMPLE_STEP = 10
        const val GLOW_MARGIN = 60f

        // The density the effect's sizes were drawn at, so pixels here match its own.
        const val REFERENCE_DENSITY = 2.625f

        const val FRAME_MILLIS = 16L
        const val RIFT_MIDWAY_MILLIS = 300L
        const val RIFT_OVER_MILLIS = 2_600L

        val TOUCH = Offset(100f, 700f)
        val LIGHT = Color(0xFFFAFAFC)
        val DARK = Color(0xFF15151F)
    }
}

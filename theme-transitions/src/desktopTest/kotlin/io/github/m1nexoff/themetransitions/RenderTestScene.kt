package io.github.m1nexoff.themetransitions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.hypot
import kotlin.test.assertTrue

// Draws AnimatedTheme through Skia, off screen. The theme is a flat colour, so every pixel says which
// theme it belongs to.
internal class RenderTestScene(
    context: CoroutineContext = EmptyCoroutineContext,
    private val transition: (target: Color) -> ThemeTransition,
) : AutoCloseable {

    var theme by mutableStateOf(LIGHT)

    // Null fills the scene.
    var boxSize by mutableStateOf<IntSize?>(null)

    private var clock = 0L

    private val scene = ImageComposeScene(
        width = WIDTH,
        height = HEIGHT,
        density = Density(REFERENCE_DENSITY),
        // Effects resume on the thread that renders, as they do on Android's main thread. With no
        // dispatcher here they would run on Dispatchers.Default and race the draw.
        coroutineContext = Dispatchers.Unconfined + context,
    ) {
        val size = boxSize
        val modifier = if (size == null) {
            Modifier.fillMaxSize()
        } else {
            with(LocalDensity.current) { Modifier.size(size.width.toDp(), size.height.toDp()) }
        }
        AnimatedTheme(targetState = theme, modifier = modifier, transition = transition(theme)) { shown ->
            Box(Modifier.fillMaxSize().background(shown))
        }
    }

    init {
        advance(FRAME_MILLIS * 3)
    }

    fun tap(at: Offset) {
        scene.sendPointerEvent(PointerEventType.Press, at)
        scene.sendPointerEvent(PointerEventType.Release, at)
    }

    fun advance(millis: Long) {
        Snapshot.sendApplyNotifications()
        val end = clock + millis
        while (clock < end) {
            clock += FRAME_MILLIS
            scene.render(clock * 1_000_000)
        }
    }

    // One frame that arrives [millis] late, as after a long recomposition.
    fun stall(millis: Long) {
        Snapshot.sendApplyNotifications()
        clock += millis
        scene.render(clock * 1_000_000)
    }

    fun frame(): PixelMap {
        Snapshot.sendApplyNotifications()
        clock += FRAME_MILLIS
        return scene.render(clock * 1_000_000).toComposeImageBitmap().toPixelMap()
    }

    override fun close() = scene.close()

    companion object {
        const val WIDTH = 400
        const val HEIGHT = 800
        const val SAMPLE_STEP = 10
        const val FRAME_MILLIS = 16L

        // The density the effects' sizes were drawn at, so pixels here match their own.
        const val REFERENCE_DENSITY = 2.625f

        val LIGHT = Color(0xFFFAFAFC)
        val DARK = Color(0xFF15151F)
        val DUSK = Color(0xFF2A1B3D)
    }
}

internal fun withScene(
    context: CoroutineContext = EmptyCoroutineContext,
    transition: (target: Color) -> ThemeTransition,
    block: (RenderTestScene) -> Unit,
) {
    RenderTestScene(context, transition).use(block)
}

internal object AnimationsOff : MotionDurationScale {
    override val scaleFactor = 0f
}

internal fun samples(
    pixels: PixelMap,
    width: Int = RenderTestScene.WIDTH,
    height: Int = RenderTestScene.HEIGHT,
    where: (x: Float, y: Float) -> Boolean,
): List<Color> = buildList {
    val step = RenderTestScene.SAMPLE_STEP
    for (y in step / 2 until height step step) {
        for (x in step / 2 until width step step) {
            if (where(x.toFloat(), y.toFloat())) add(pixels[x, y])
        }
    }
}

internal fun near(point: Offset, radius: Float): (Float, Float) -> Boolean =
    { x, y -> hypot(x - point.x, y - point.y) < radius }

internal fun farFrom(point: Offset, radius: Float): (Float, Float) -> Boolean =
    { x, y -> hypot(x - point.x, y - point.y) > radius }

// Sparks and bolts fly across either region, so a few samples are allowed to miss.
internal fun assertMostly(colors: List<Color>, message: String, share: Float = 0.9f, predicate: (Color) -> Boolean) {
    assertTrue(colors.size >= 8, "too few samples to judge: ${colors.size}")
    val actual = colors.count(predicate).toFloat() / colors.size
    assertTrue(actual >= share, "$message — only ${(actual * 100).toInt()}% of ${colors.size} samples were")
}

internal fun assertAll(colors: List<Color>, expected: Color, message: String) {
    assertTrue(colors.isNotEmpty(), "no samples")
    val wrong = colors.filter { it != expected }
    assertTrue(wrong.isEmpty(), "$message — ${wrong.size} of ${colors.size} samples differ, e.g. ${wrong.take(3)}")
}

internal fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

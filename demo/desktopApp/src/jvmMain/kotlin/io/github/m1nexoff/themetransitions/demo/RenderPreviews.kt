package io.github.m1nexoff.themetransitions.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private const val WIDTH = 800
private const val HEIGHT = 1_600
private const val DENSITY = 2f
private const val FRAME_MILLIS = 16L
private const val CELL_WIDTH = 200
private const val CELL_HEIGHT = 400
private const val LABEL_WIDTH = 150

// Where the Dark option of the theme picker sits, in pixels: the transitions open from a tap there.
private val TAP = Offset(400f, 310f)

// One of each reveal, from the showcase the demo plays.
private val rows = listOf("Circle + ring", "Flood", "Wipe 45° + ring", "Fade", "Dissolve", "Tear + ring, shake", "Rift")
    .map { label -> Showcase.first { it.label == label } }

// Moments through each transition, as fractions of its duration: early, since most easings do most of
// their moving early.
private val moments = listOf(0.06f, 0.14f, 0.25f, 0.4f, 0.65f)

/**
 * Renders docs/preview.png: each row a transition from the light theme to the dark one, five moments
 * through it, drawn off screen by the same code the apps run. Run with `./gradlew renderPreviews`.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "docs/preview.png")
    val sheet = BufferedImage(LABEL_WIDTH + CELL_WIDTH * moments.size, CELL_HEIGHT * rows.size, BufferedImage.TYPE_INT_RGB)
    val g = sheet.createGraphics().apply {
        setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        color = Color.WHITE
        fillRect(0, 0, sheet.width, sheet.height)
        font = Font(Font.SANS_SERIF, Font.BOLD, 20)
    }
    rows.forEachIndexed { r, row ->
        g.color = Color(0x1E213F)
        g.drawString(row.label.substringBefore(" "), 16, r * CELL_HEIGHT + CELL_HEIGHT / 2)
        frames(row).forEachIndexed { m, frame ->
            g.drawImage(frame, LABEL_WIDTH + m * CELL_WIDTH, r * CELL_HEIGHT, CELL_WIDTH, CELL_HEIGHT, null)
        }
    }
    g.dispose()
    out.parentFile?.mkdirs()
    ImageIO.write(sheet, "png", out)
    println("Wrote ${out.path}")
}

private fun frames(row: ShowcaseItem): List<BufferedImage> {
    var theme by mutableStateOf(DemoTheme.Light)
    var clock = 0L
    val scene = ImageComposeScene(WIDTH, HEIGHT, Density(DENSITY), coroutineContext = Dispatchers.Unconfined) {
        PreviewDemo(theme, row.playground)
    }
    fun render() = scene.render(clock * 1_000_000).also { clock += FRAME_MILLIS }
    try {
        repeat(4) { render() }
        scene.sendPointerEvent(PointerEventType.Press, TAP)
        scene.sendPointerEvent(PointerEventType.Release, TAP)
        theme = DemoTheme.Dark
        Snapshot.sendApplyNotifications()
        // The switch freezes the old theme over a frame or two before the transition's clock starts.
        repeat(2) { render() }
        val start = clock
        return moments.map { moment ->
            val at = start + (row.durationMillis * moment).toLong()
            var image = render()
            while (clock <= at) image = render()
            image.toComposeImageBitmap().toAwtImage()
        }
    } finally {
        scene.close()
    }
}

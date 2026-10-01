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
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.File
import java.io.OutputStream
import kotlin.math.roundToInt

// 4:5 portrait, the shape a video gets the most room in on LinkedIn's feed, at 1080 px wide.
private const val VIDEO_WIDTH = 1_080
private const val VIDEO_HEIGHT = 1_350
private const val CAPTION_HEIGHT = 180
private const val APP_HEIGHT = VIDEO_HEIGHT - CAPTION_HEIGHT
// 450 dp across: enough height below the picker to show the playground's effect chips too.
private const val DENSITY = 2.4f
private const val FPS = 60
private const val GIF_WIDTH = 300

// The showcase items that also get a looping GIF of their own, for the README.
private val GifItems = listOf(
    "Circle", "Circle + ring", "Circle + ring, sparks, lightning, dim",
    "Flood", "Wipe 45° + ring", "Fade",
    "Dissolve", "Tear + ring, shake", "Rift",
)

private val CaptionBackground = Color(0x10, 0x10, 0x18)
private val CaptionText = Color(0xEC, 0xEC, 0xF4)
private val CaptionSubtle = Color(0x9A, 0x9A, 0xAD)

/**
 * Renders the showcase — every transition and its variants, each through all three themes — off screen
 * at 60 fps: showreel.mp4 for sharing, and a looping GIF of a few of them for the README.
 *
 * `./gradlew renderShowreel -Pffmpeg=<path to ffmpeg>`
 */
fun main(args: Array<String>) {
    val ffmpeg = args.argument("--ffmpeg") ?: error("pass --ffmpeg=<path to ffmpeg>")
    val out = File(args.argument("--out") ?: "docs/showreel").apply { mkdirs() }
    Reel(ffmpeg, out).use { it.render() }
}

private fun Array<String>.argument(name: String): String? =
    firstOrNull { it.startsWith("$name=") }?.substringAfter('=')

private class Reel(private val ffmpeg: String, private val out: File) : AutoCloseable {
    private var theme by mutableStateOf(DemoTheme.Light)
    private var playground by mutableStateOf(Playground())
    private var nanos = 0L
    private var frame = 0

    // Where the last tap went down, and on which frame, for the marker that shows it.
    private var tapAt: Offset? = null
    private var tapFrame = 0

    private val scene = ImageComposeScene(VIDEO_WIDTH, APP_HEIGHT, Density(DENSITY), coroutineContext = Dispatchers.Unconfined) {
        PreviewDemo(theme, playground)
    }
    private val video = encoder(
        VIDEO_WIDTH,
        VIDEO_HEIGHT,
        File(out, "showreel.mp4"),
        "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-pix_fmt", "yuv420p", "-movflags", "+faststart",
    )
    private var gif: Process? = null
    private val canvas = BufferedImage(VIDEO_WIDTH, VIDEO_HEIGHT, BufferedImage.TYPE_3BYTE_BGR)
    private val app = canvas.getSubimage(0, 0, VIDEO_WIDTH, APP_HEIGHT)
    private val appBytes = BufferedImage(VIDEO_WIDTH, APP_HEIGHT, BufferedImage.TYPE_3BYTE_BGR)

    fun render() {
        caption("Theme Transitions", "Theme switch animations for Compose Multiplatform")
        hold(1_400)
        for (item in Showcase) {
            playground = item.playground
            val makesGif = item.label in GifItems
            if (makesGif) gif = encoder(VIDEO_WIDTH, APP_HEIGHT, File(out, "${item.label.slug()}.gif"), *gifFilter())
            caption(item.label, "${Showcase.indexOf(item) + 1} of ${Showcase.size}")
            for (next in listOf(DemoTheme.Dark, DemoTheme.Dusk, DemoTheme.Light)) {
                hold(SHOWCASE_PAUSE_MILLIS.toInt())
                tap(next)
                theme = next
                hold(item.durationMillis + 120)
            }
            hold(SHOWCASE_PAUSE_MILLIS.toInt())
            gif?.finish()
            gif = null
        }
        caption("Theme Transitions", "github.com/M1nexoff/theme-transitions")
        hold(1_800)
    }

    private var title = ""
    private var subtitle = ""

    private fun caption(title: String, subtitle: String) {
        this.title = title
        this.subtitle = subtitle
    }

    // Taps the picker's option for [theme], which is where the transition opens from.
    private fun tap(theme: DemoTheme) {
        val at = Offset(chipCenterDp(theme.ordinal) * DENSITY, CHIP_ROW_DP * DENSITY)
        scene.sendPointerEvent(PointerEventType.Press, at)
        scene.sendPointerEvent(PointerEventType.Release, at)
        tapAt = at
        tapFrame = frame
    }

    private fun hold(millis: Int) {
        repeat((millis * FPS / 1_000f).roundToInt()) { step() }
    }

    private fun step() {
        Snapshot.sendApplyNotifications()
        val image = scene.render(nanos).toComposeImageBitmap().toAwtImage()
        nanos += 1_000_000_000L / FPS
        val g = canvas.createGraphics()
        g.drawImage(image, 0, 0, null)
        g.drawTap()
        g.drawCaption()
        g.dispose()
        video.write(canvas)
        gif?.let { encoder ->
            appBytes.createGraphics().apply { drawImage(app, 0, 0, null); dispose() }
            encoder.write(appBytes)
        }
        frame++
    }

    // A ring that grows and fades where the finger went down.
    private fun Graphics2D.drawTap() {
        val at = tapAt ?: return
        val age = (frame - tapFrame) / (FPS * 0.35f)
        if (age > 1f) return
        setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val radius = 34f + 40f * age
        val alpha = ((1f - age) * 200).toInt().coerceIn(0, 255)
        color = Color(255, 255, 255, alpha / 2)
        fillOval((at.x - radius).toInt(), (at.y - radius).toInt(), (radius * 2).toInt(), (radius * 2).toInt())
        color = Color(30, 33, 63, alpha)
        stroke = BasicStroke(5f)
        drawOval((at.x - radius).toInt(), (at.y - radius).toInt(), (radius * 2).toInt(), (radius * 2).toInt())
    }

    private fun Graphics2D.drawCaption() {
        setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        color = CaptionBackground
        fillRect(0, APP_HEIGHT, VIDEO_WIDTH, CAPTION_HEIGHT)
        color = CaptionText
        font = Font(Font.SANS_SERIF, Font.BOLD, 54)
        drawString(title, 56, APP_HEIGHT + 88)
        color = CaptionSubtle
        font = Font(Font.SANS_SERIF, Font.PLAIN, 32)
        drawString(subtitle, 56, APP_HEIGHT + 140)
    }

    private fun encoder(width: Int, height: Int, file: File, vararg output: String): Process =
        ProcessBuilder(
            ffmpeg, "-y", "-loglevel", "error",
            "-f", "rawvideo", "-pix_fmt", "bgr24", "-s", "${width}x$height", "-r", "$FPS", "-i", "-",
            *output, file.path,
        ).redirectError(ProcessBuilder.Redirect.INHERIT).start()

    // Half the frame rate and a palette built from the clip itself keep a GIF small and its colours true.
    private fun gifFilter(): Array<String> = arrayOf(
        "-vf",
        "fps=30,scale=$GIF_WIDTH:-1:flags=lanczos,split[a][b];" +
            "[a]palettegen=max_colors=128:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=4:diff_mode=rectangle",
        "-loop", "0",
    )

    override fun close() {
        video.finish()
        gif?.finish()
        scene.close()
    }

    private companion object {
        // The theme picker: three options across the screen, 20 dp in from each side and 12 dp apart.
        const val CHIP_ROW_DP = 155f

        fun chipCenterDp(index: Int): Float {
            val width = VIDEO_WIDTH / DENSITY
            val chip = (width - 2 * 20f - 2 * 12f) / 3f
            return 20f + chip / 2f + index * (chip + 12f)
        }
    }
}

private fun Process.write(image: BufferedImage) {
    val out: OutputStream = outputStream
    out.write((image.raster.dataBuffer as DataBufferByte).data)
}

private fun Process.finish() {
    outputStream.close()
    check(waitFor() == 0) { "ffmpeg failed" }
}

private fun String.slug(): String =
    lowercase().replace("°", "").replace(Regex("[^a-z0-9]+"), "-").trim('-')

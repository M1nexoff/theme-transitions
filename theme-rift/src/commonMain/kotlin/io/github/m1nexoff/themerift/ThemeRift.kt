package io.github.m1nexoff.themerift

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val DURATION_MILLIS = 2_200

// The reveal starts late — only once the new theme is on screen — so it gets a little less than
// the whole effect and still ends inside it.
private const val REVEAL_MILLIS = 2_000

// Every sin/cos below runs on one clock that turns once per this many milliseconds.
private const val CLOCK_PERIOD_MILLIS = 2_000f

private const val TWO_PI = 2f * PI.toFloat()

private const val EDGE_SEGMENTS = 64
private const val SEGMENT_ANGLE = TWO_PI / EDGE_SEGMENTS

// The furthest wobble() ever pulls the edge in: its three amplitudes added up.
private const val WOBBLE_DEPTH = 0.07f

// The progress by which the edge has passed every corner. The easing crawls from here to the end,
// so a corner that waited for 1.0 would stay in the old theme for most of the last second.
private const val COVERED_AT = 0.85f

private const val MAX_DIMMING = 0.7f

// Eased in rather than switched on, so a second rift does not flash the screen dark again.
private const val DIM_IN_MILLIS = 150f

private const val MAX_RIFTS = 3

private const val BOLT_COUNT = 12
private const val PARTICLE_COUNT = 100
private const val PARTICLE_TRAIL = 3

// Below this a spark adds nothing a viewer could see.
private const val MIN_VISIBLE_ALPHA = 0.004f

// The effect's sizes were tuned as raw pixels on a 420dpi phone. ref() gives them the same physical
// size on any screen.
private const val REFERENCE_DENSITY = 2.625f
private const val SHAKE_PX = 8f

/**
 * Switches [content] between themes through a rift that tears open where the user last touched —
 * usually the theme toggle they just tapped — with lightning crackling off its edge. A switch with
 * no touch behind it, such as the system going dark, opens from the centre.
 *
 * Put it at the root, around your theme, and build the theme from the state [content] is given,
 * never from [targetState] directly: [content] keeps the old state for one frame after a switch,
 * while that frame is frozen to be torn open over the new one.
 *
 * ```
 * ThemeRift(
 *     targetState = darkTheme,
 *     colors = { dark -> if (dark) DarkRift else LightRift },
 * ) { dark ->
 *     AppTheme(dark) { App() }
 * }
 * ```
 *
 * [content] is composed once, in one state at a time; the outgoing theme is a bitmap, not a second
 * composition. It must paint its own opaque background, since that bitmap is what the rift tears.
 *
 * A switch made while a rift is still open does not cut it short: the new rift tears open from the
 * new touch, and the older one keeps growing wherever the new one has not reached yet. With a
 * [MotionDurationScale] of 0 — on Android, animations turned off in the system settings — the theme
 * changes at once.
 *
 * @param targetState the theme to show. Anything compared by `equals`: a Boolean, an enum, a
 * palette object.
 * @param colors the colours of the rift that opens *into* a given state. Called once per switch.
 * @param modifier applied to the box that holds [content] and draws the rift over it.
 * @param content the UI, drawn in the state it is given.
 */
@Composable
public fun <T> ThemeRift(
    targetState: T,
    colors: (T) -> RiftColors,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val target by rememberUpdatedState(targetState)
    val colorsFor by rememberUpdatedState(colors)
    var shown by remember { mutableStateOf(targetState) }
    val rifts = remember { mutableStateListOf<Rift>() }
    val lastTouch = remember { TouchPoint() }
    val pen = remember { Pen() }
    val frame = rememberGraphicsLayer()

    LaunchedEffect(Unit) {
        // One switch at a time as far as the freeze, since each has to find the previous one's
        // theme underneath; the rifts themselves then run side by side.
        snapshotFlow { target }.conflate().collect { next ->
            if (next == shown) return@collect
            val animationsOff = currentCoroutineContext()[MotionDurationScale]?.scaleFactor == 0f
            if (animationsOff || frame.size == IntSize.Zero) {
                rifts.clear()
                shown = next
                return@collect
            }
            // Every open rift holds a full-screen bitmap. Past the cap the oldest goes, and the
            // corner only it still covered jumps a theme ahead.
            if (rifts.size == MAX_RIFTS) rifts.removeAt(0)

            val center = Offset(frame.size.width / 2f, frame.size.height / 2f)
            val opening = Rift(lastTouch.position ?: center, frame.size, colorsFor(next))
            rifts += opening
            launch {
                animate(
                    initialValue = 0f,
                    targetValue = DURATION_MILLIS.toFloat(),
                    animationSpec = tween(DURATION_MILLIS, easing = LinearEasing),
                ) { value, _ ->
                    opening.clock = value
                    if (!opening.covered && opening.progress >= COVERED_AT) {
                        opening.cover()
                        // Everything older is behind it now.
                        repeat(rifts.indexOf(opening)) { rifts.removeAt(0) }
                    }
                }
                rifts.remove(opening)
            }
            withFrameNanos {}
            opening.freeze(frame.toImageBitmap())
            shown = next
        }
    }

    Box(
        modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes
                            .firstOrNull { it.changedToDownIgnoreConsumed() }
                            ?.let { lastTouch.position = it.position }
                    }
                }
            }
            .drawWithContent {
                if (rifts.isNotEmpty()) drawShakeBackdrop(rifts)
                drawContent()
            }
            .graphicsLayer {
                val shake = if (rifts.isEmpty()) Offset.Zero else rifts.shake(this)
                translationX = shake.x
                translationY = shake.y
            }
            .drawWithContent {
                // `frame` is what the next switch freezes: the live theme alone.
                frame.record { this@drawWithContent.drawContent() }
                if (rifts.isEmpty()) {
                    drawLayer(frame)
                } else {
                    for (rift in rifts) rift.growEdge()
                    drawLive(rifts) { drawLayer(frame) }
                    drawRifts(rifts, pen)
                }
            }
            // The draw above runs every frame of a rift; without this boundary each of those would
            // re-record the whole screen below as well.
            .graphicsLayer(),
    ) {
        content(shown)
    }
}

// Written on every touch and read only when a rift opens, so it is deliberately not snapshot state:
// nothing should recompose or redraw because a finger went down.
private class TouchPoint {
    var position: Offset? = null
}

// Reused every frame, so drawing the rim allocates nothing.
private class Pen {
    val rim = Paint()
}

private class Particle(val angle: Float, val distance: Float, val speed: Float, val size: Float)

@Stable
private class Rift(
    val origin: Offset,
    size: IntSize,
    val colors: RiftColors,
) {
    // Milliseconds since the rift opened.
    var clock by mutableFloatStateOf(0f)

    // The frame of the theme this rift replaces.
    var frozen by mutableStateOf<ImageBitmap?>(null)
        private set
    var painter: FrozenFramePainter? = null
        private set

    // The edge has passed every corner: nothing of the frozen frame, or of any older rift, shows.
    var covered by mutableStateOf(false)
        private set

    // The new theme is underneath and the hole is growing. Until then the old one is still live.
    val opened: Boolean get() = frozen != null || covered

    private var revealStart = 0f

    fun freeze(bitmap: ImageBitmap) {
        revealStart = clock
        painter = FrozenFramePainter(bitmap)
        frozen = bitmap
    }

    fun cover() {
        covered = true
        frozen = null
        painter = null
    }

    // Decelerating: the hole bursts open and then slows. An ease-in leaves it at 3% of its size a
    // fifth of a second in, which reads as a late response to the tap.
    val progress: Float
        get() {
            if (!opened) return 0f
            return LinearOutSlowInEasing.transform(((clock - revealStart) / REVEAL_MILLIS).coerceIn(0f, 1f))
        }

    val time: Float get() = clock / CLOCK_PERIOD_MILLIS * TWO_PI

    // Ramps up, holds, and settles to nothing by the end, so nothing is still moving when the rift
    // is dropped.
    val intensity: Float
        get() {
            val p = progress
            return when {
                p < 0.3f -> p / 0.3f
                p < 0.7f -> 1f
                else -> (1f - p) / 0.3f
            }.coerceIn(0f, 1f)
        }

    val dimming: Float
        get() {
            val arrival = (clock / DIM_IN_MILLIS).coerceAtMost(1f)
            return MAX_DIMMING * arrival * (1f - progress / 0.85f).coerceAtLeast(0f)
        }

    // Far enough that the edge passes the furthest corner by COVERED_AT even where the wobble pulls
    // it in and between two of its vertices.
    val reach = hypot(max(origin.x, size.width - origin.x), max(origin.y, size.height - origin.y)) /
        ((1f - WOBBLE_DEPTH) * cos(SEGMENT_ANGLE / 2f) * COVERED_AT)

    // Each vertex keeps the furthest it has reached, so the new theme only ever gains ground. Drawn
    // from scratch every frame, the wobble outpaces the growth once the easing slows it, and pulls
    // parts of the edge back.
    private val edge = FloatArray(EDGE_SEGMENTS)
    val hole = Path()

    // The rim as a closed run of line segments; stroking `hole` as a Path is what cost the GPU.
    val rim = FloatArray((EDGE_SEGMENTS + 1) * 2)

    // A circle round the whole hole, which the GPU clips without a mask.
    val bounds = Path()

    val mesh = OutsideMesh(origin.x, origin.y, size.width.toFloat(), size.height.toFloat(), EDGE_SEGMENTS)

    val bolt = Path()
    val branch = Path()
    val twig = Path()

    val particles = List(PARTICLE_COUNT) {
        Particle(
            angle = Random.nextFloat() * TWO_PI,
            distance = Random.nextFloat(),
            speed = 0.15f + Random.nextFloat() * 0.6f,
            size = 1.5f + Random.nextFloat() * 4f,
        )
    }

    // Once per frame, before anything reads the edge.
    fun growEdge() {
        hole.reset()
        bounds.reset()
        if (!opened) return
        val radius = reach * progress
        if (radius > 0f) {
            val time = time
            var furthest = 0f
            for (i in 0 until EDGE_SEGMENTS) {
                val angle = i * SEGMENT_ANGLE
                edge[i] = max(edge[i], radius * wobble(angle, time))
                furthest = max(furthest, edge[i])
                val x = origin.x + cos(angle) * edge[i]
                val y = origin.y + sin(angle) * edge[i]
                if (i == 0) hole.moveTo(x, y) else hole.lineTo(x, y)
                rim[i * 2] = x
                rim[i * 2 + 1] = y
            }
            hole.close()
            rim[EDGE_SEGMENTS * 2] = rim[0]
            rim[EDGE_SEGMENTS * 2 + 1] = rim[1]
            bounds.addOval(Rect(origin, furthest + 1f))
        }
        if (painter?.usesMesh == true) mesh.build(edge)
    }

    fun edgeAt(angle: Float): Float = edge[(angle / SEGMENT_ANGLE).toInt().mod(EDGE_SEGMENTS)]

    // Whole pixels: a fractional translation of the entire screen softens every line of text.
    fun shake(density: Density, intensity: Float): Offset {
        val strength = density.ref(SHAKE_PX) * intensity
        return Offset(
            (sin(time * 15f) * strength).roundToInt().toFloat(),
            (cos(time * 12f) * strength * 0.75f).roundToInt().toFloat(),
        )
    }
}

private fun List<Rift>.shake(density: Density): Offset = last().shake(density, maxOf { it.intensity })

private fun wobble(angle: Float, time: Float): Float = 1f +
    sin(angle * 7f + time * 1.2f) * 0.03f +
    cos(angle * 5f - time * 1.9f) * 0.025f +
    sin(angle * 11f + time * 3f) * 0.015f

private fun Density.ref(px: Float): Float = px * density / REFERENCE_DENSITY

private fun flicker(time: Float): Float = 0.7f + sin(time * 8f) * 0.3f

// What the shake uncovers along two edges, dimmed like the rest. Only those strips: a full-screen
// backdrop under the whole content is two more layers of fill every frame.
private fun DrawScope.drawShakeBackdrop(rifts: List<Rift>) {
    val newest = rifts.last()
    val shake = rifts.shake(this)
    val dimming = rifts.maxOf { it.dimming }
    fun strip(left: Float, top: Float, right: Float, bottom: Float) {
        if (right <= left || bottom <= top) return
        val topLeft = Offset(left, top)
        val stripSize = Size(right - left, bottom - top)
        drawRect(newest.colors.background, topLeft, stripSize)
        if (dimming > 0f) drawRect(newest.colors.scrim, topLeft, stripSize, alpha = dimming)
    }
    if (shake.x > 0f) strip(0f, 0f, shake.x, size.height) else strip(size.width + shake.x, 0f, size.width, size.height)
    if (shake.y > 0f) strip(0f, 0f, size.width, shake.y) else strip(0f, size.height + shake.y, size.width, size.height)
}

// Outside the newest open hole the live theme is covered by frozen frames, so it need not be drawn
// there.
private inline fun DrawScope.drawLive(rifts: List<Rift>, draw: DrawScope.() -> Unit) {
    val newestOpen = rifts.lastOrNull { it.opened }
    if (newestOpen == null || newestOpen.covered) draw() else clipPath(newestOpen.bounds, block = draw)
}

// Oldest first in [rifts]. The themes go down first and the dimming over them, then the lightning
// on top, so it stays bright.
private fun DrawScope.drawRifts(rifts: List<Rift>, pen: Pen) {
    val newest = rifts.last()
    drawFrozen(rifts, rifts.lastIndex)
    val dimming = rifts.maxOf { it.dimming }
    if (dimming > 0f) drawRect(newest.colors.scrim, alpha = dimming)
    drawEffects(rifts, rifts.lastIndex, pen)
    drawFrame(newest, rifts.maxOf { it.intensity })
}

// Rift [index] shows its frozen frame outside its hole; every older rift is drawn inside that same
// outside, so it carries on only where the newer ones have not reached.
private fun DrawScope.drawFrozen(rifts: List<Rift>, index: Int) {
    if (index < 0) return
    val rift = rifts[index]
    if (!rift.opened) return drawFrozen(rifts, index - 1)
    val painter = rift.painter ?: return
    painter.draw(this, rift.mesh, rift.hole)
    if (index > 0) clipPath(rift.hole, ClipOp.Difference) { drawFrozen(rifts, index - 1) }
}

// Clipped the same way as drawFrozen.
private fun DrawScope.drawEffects(rifts: List<Rift>, index: Int, pen: Pen) {
    if (index < 0) return
    val rift = rifts[index]
    val progress = rift.progress
    val time = rift.time
    val radius = rift.reach * progress
    if (!rift.hole.isEmpty) {
        pen.rim.color = rift.colors.lightning
        pen.rim.alpha = flicker(time) * (1f - progress * 0.4f)
        pen.rim.strokeWidth = ref(6f)
        drawIntoCanvas { it.drawRawPoints(PointMode.Polygon, rift.rim, pen.rim) }
    }
    drawParticles(rift, radius, progress, time)
    drawLightning(rift, progress, time)

    when {
        !rift.opened -> drawEffects(rifts, index - 1, pen)
        !rift.covered -> clipPath(rift.hole, ClipOp.Difference) { drawEffects(rifts, index - 1, pen) }
    }
}

private fun DrawScope.drawParticles(rift: Rift, radius: Float, progress: Float, time: Float) {
    if (progress >= 0.9f) return
    val fade = 1f - progress
    for (particle in rift.particles) {
        val orbit = radius * (1.4f + particle.distance * 0.6f)
        val travelled = ((time * particle.speed + particle.angle) % TWO_PI) / TWO_PI
        if (orbit * (1f - travelled) <= radius * 0.1f) continue

        val baseAngle = particle.angle + time * 0.5f
        val alpha = (1f - travelled) * fade * 0.7f
        for (t in 0 until PARTICLE_TRAIL) {
            val trail = travelled - t * 0.04f
            if (trail < 0f) break
            val sparkAlpha = alpha * (1f - t * 0.35f)
            if (sparkAlpha < MIN_VISIBLE_ALPHA) continue
            val distance = orbit * (1f - trail)
            val spiral = baseAngle + trail * 2f
            val sparkRadius = ref(particle.size) * (1f - t * 0.25f)
            val x = rift.origin.x + cos(spiral) * distance
            val y = rift.origin.y + sin(spiral) * distance
            // Once the rift is wide most orbits run off screen; recording those is most of the cost.
            if (x < -sparkRadius || y < -sparkRadius || x > size.width + sparkRadius || y > size.height + sparkRadius) {
                continue
            }
            drawCircle(
                color = rift.colors.lightning,
                radius = sparkRadius,
                center = Offset(x, y),
                alpha = sparkAlpha,
            )
        }
    }
}

private fun DrawScope.drawLightning(rift: Rift, progress: Float, time: Float) {
    if (progress >= 0.75f) return
    val fade = 1f - progress
    for (i in 0 until BOLT_COUNT) {
        val flicker = sin(time * 7f + i * 2.1f)
        if (flicker < -0.2f) continue

        val angle = i.toFloat() / BOLT_COUNT * TWO_PI + time * 0.6f
        val startRadius = rift.edgeAt(angle) * 0.99f
        val start = rift.origin + Offset(cos(angle) * startRadius, sin(angle) * startRadius)

        var x = start.x
        var y = start.y
        rift.bolt.reset()
        rift.bolt.moveTo(x, y)
        val segments = 16 + (sin(time + i) * 5).toInt()
        for (segment in 1..segments) {
            val heading = angle + sin(time * 3f + segment * 1.7f + i * 0.9f) * 1.2f
            val length = ref(50f + sin(time * 4f + segment * 2.3f) * 25f)
            x += cos(heading) * length
            y += sin(heading) * length
            rift.bolt.lineTo(x, y)
        }
        drawPath(
            path = rift.bolt,
            color = rift.colors.lightning,
            alpha = (0.9f * fade * (0.5f + flicker * 0.5f)).coerceIn(0f, 1f),
            style = Stroke(ref(4.5f)),
        )

        if (flicker > 0.1f) drawBranch(rift, angle, start, time, fade, flicker)
    }
}

private fun DrawScope.drawBranch(
    rift: Rift,
    angle: Float,
    start: Offset,
    time: Float,
    fade: Float,
    flicker: Float,
) {
    var x = start.x + cos(angle + 0.3f) * ref(80f)
    var y = start.y + sin(angle + 0.3f) * ref(80f)
    rift.branch.reset()
    rift.branch.moveTo(x, y)
    for (step in 1..8) {
        val heading = angle + sin(time * 5f + step) * 1.4f + 0.5f
        val length = ref(35f + sin(time * 3f + step * 1.5f) * 18f)
        x += cos(heading) * length
        y += sin(heading) * length
        rift.branch.lineTo(x, y)
    }
    drawPath(rift.branch, rift.colors.lightning, alpha = 0.7f * fade, style = Stroke(ref(3f)))

    if (flicker <= 0.3f) return
    rift.twig.reset()
    rift.twig.moveTo(x, y)
    for (step in 1..6) {
        val heading = angle + sin(time * 6f + step * 2f) * 1.5f + 1f
        x += cos(heading) * ref(30f)
        y += sin(heading) * ref(30f)
        rift.twig.lineTo(x, y)
    }
    drawPath(rift.twig, rift.colors.lightning, alpha = 0.5f * fade, style = Stroke(ref(2f)))
}

// A rattling glow around the screen in the incoming theme's glow colour.
private fun DrawScope.drawFrame(rift: Rift, intensity: Float) {
    if (intensity <= 0f) return
    val time = rift.time
    val alpha = intensity * (0.6f + sin(time * 9f) * 0.4f)
    val jitter = ref(SHAKE_PX) * 0.6f * intensity
    val inset = ref(14f)
    val topLeft = Offset(inset + sin(time * 23f) * jitter, inset + cos(time * 19f) * jitter)
    val frameSize = Size(size.width - inset * 2f, size.height - inset * 2f)
    val corner = CornerRadius(ref(28f))

    drawRoundRect(
        color = rift.colors.glow,
        topLeft = topLeft,
        size = frameSize,
        cornerRadius = corner,
        style = Stroke(ref(30f)),
        alpha = 0.22f * alpha,
    )
    drawRoundRect(
        color = rift.colors.glow,
        topLeft = topLeft,
        size = frameSize,
        cornerRadius = corner,
        style = Stroke(ref(6f)),
        alpha = 0.9f * alpha,
    )
}

package io.github.m1nexoff.themetransitions.reveal

import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import io.github.m1nexoff.themetransitions.TransitionOrigin
import io.github.m1nexoff.themetransitions.geometry.OutsideMesh
import io.github.m1nexoff.themetransitions.geometry.TWO_PI
import io.github.m1nexoff.themetransitions.platform.OutsideMeshPainter
import io.github.m1nexoff.themetransitions.platform.createOutsideMeshPainter
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

private const val EDGE_SEGMENTS = 64
private const val SEGMENT_ANGLE = TWO_PI / EDGE_SEGMENTS

// The furthest wobble() ever pulls the edge in: its three amplitudes added up.
private const val WOBBLE_DEPTH = 0.07f

// The progress by which the edge has passed every corner. An easing that crawls to the end would
// otherwise leave a corner in the old theme for most of the last second.
private const val COVERED_AT = 0.85f

// Thin enough to stay invisible, wide enough to cover the mesh's stair-stepped edge.
private const val SMOOTHING_WIDTH_PX = 1.5f

internal class TearSpec(durationMillis: Int, easing: Easing, origin: TransitionOrigin) :
    RevealSpec(durationMillis, easing, origin) {
    override fun start(setup: RunSetup): RevealRun = TearRun(setup)
}

// A ragged hole that wobbles as it grows.
private class TearRun(setup: RunSetup) : RevealRun(), RadialEdge {
    override val center: Offset = setup.origin
    override var radius = 0f
        private set
    override var covered = false
        private set
    override val radial: RadialEdge get() = this

    // Far enough that the edge passes the furthest corner by COVERED_AT even where the wobble pulls it
    // in and between two of its vertices.
    private val reach = hypot(
        max(center.x, setup.size.width - center.x),
        max(center.y, setup.size.height - center.y),
    ) / ((1f - WOBBLE_DEPTH) * cos(SEGMENT_ANGLE / 2f) * COVERED_AT)

    // Each vertex keeps the furthest it has reached, so the new theme only ever gains ground. Drawn
    // from scratch every frame, the wobble outpaces the growth once the easing slows it, and pulls parts
    // of the edge back.
    private val distances = FloatArray(EDGE_SEGMENTS)
    private val hole = Path()

    // The edge as a closed run of line segments; stroking `hole` as a Path is what cost the GPU.
    private val rim = FloatArray((EDGE_SEGMENTS + 1) * 2)

    // A circle round the whole hole, which the GPU clips without a mask.
    private val bounds = Path()

    private val mesh = OutsideMesh(
        center.x,
        center.y,
        setup.size.width.toFloat(),
        setup.size.height.toFloat(),
        EDGE_SEGMENTS,
    )

    private var image: ImageBitmap? = null
    private var painter: OutsideMeshPainter? = null
    private var smoothing: Paint? = null

    override val visible: Boolean get() = !hole.isEmpty

    override fun update(linear: Float, progress: Float, time: Float) {
        if (progress >= COVERED_AT) covered = true
        hole.reset()
        bounds.reset()
        if (image == null && !covered) return
        radius = reach * progress.coerceAtLeast(0f)
        if (radius > 0f) {
            var furthest = 0f
            for (i in 0 until EDGE_SEGMENTS) {
                val angle = i * SEGMENT_ANGLE
                distances[i] = max(distances[i], radius * wobble(angle, time))
                furthest = max(furthest, distances[i])
                val x = center.x + cos(angle) * distances[i]
                val y = center.y + sin(angle) * distances[i]
                if (i == 0) hole.moveTo(x, y) else hole.lineTo(x, y)
                rim[i * 2] = x
                rim[i * 2 + 1] = y
            }
            hole.close()
            rim[EDGE_SEGMENTS * 2] = rim[0]
            rim[EDGE_SEGMENTS * 2 + 1] = rim[1]
            bounds.addOval(Rect(center, furthest + 1f))
        }
        if (painter != null && !covered) mesh.build(distances)
    }

    override fun distanceAt(angle: Float): Float = distances[(angle / SEGMENT_ANGLE).toInt().mod(EDGE_SEGMENTS)]

    override fun drawOutline(canvas: Canvas, paint: Paint) {
        canvas.drawRawPoints(PointMode.Polygon, rim, paint)
    }

    override fun freeze(image: ImageBitmap) {
        this.image = image
        painter = createOutsideMeshPainter(image)?.also {
            smoothing = Paint().apply {
                shader = ImageShader(image)
                strokeWidth = SMOOTHING_WIDTH_PX
            }
        }
    }

    override fun release() {
        image = null
        painter = null
        smoothing = null
    }

    override fun DrawScope.drawLive(live: GraphicsLayer) {
        if (image == null || covered) drawLayer(live) else clipPath(bounds) { drawLayer(live) }
    }

    override fun DrawScope.drawOld() {
        val image = image ?: return
        if (covered) return
        val painter = painter
        if (painter == null) {
            clipPath(hole, ClipOp.Difference) { drawImage(image) }
            return
        }
        painter.draw(this, mesh)
        // The mesh is not anti-aliased. A thin line of the old frame itself along the edge smooths it
        // without showing.
        val smoothing = smoothing ?: return
        if (!hole.isEmpty) drawIntoCanvas { it.drawRawPoints(PointMode.Polygon, rim, smoothing) }
    }
}

private fun wobble(angle: Float, time: Float): Float = 1f +
    sin(angle * 7f + time * 1.2f) * 0.03f +
    cos(angle * 5f - time * 1.9f) * 0.025f +
    sin(angle * 11f + time * 3f) * 0.015f

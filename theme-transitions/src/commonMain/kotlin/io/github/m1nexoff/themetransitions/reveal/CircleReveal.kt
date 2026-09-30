package io.github.m1nexoff.themetransitions.reveal

import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntSize
import io.github.m1nexoff.themetransitions.TransitionOrigin
import kotlin.math.hypot
import kotlin.math.max

internal class CircleSpec(durationMillis: Int, easing: Easing, origin: TransitionOrigin) :
    RevealSpec(durationMillis, easing, origin) {
    override fun start(setup: RunSetup): RevealRun = CircleRun(setup)
}

// The new theme grows in a circle.
private class CircleRun(setup: RunSetup) : RevealRun(), RadialEdge {
    // Never `center` inside a DrawScope: there that name is the screen's centre.
    private val origin: Offset = setup.origin
    override val center: Offset get() = origin
    override var radius = 0f
        private set
    override var covered = false
        private set
    override val radial: RadialEdge get() = this

    private val reach = reach(origin, setup.size)
    private val outside = OutsideCircle(reach)
    private val bounds = Path()
    private var brush: Brush? = null

    override val visible: Boolean get() = radius > 0f

    override fun update(linear: Float, progress: Float, time: Float) {
        radius = reach * progress.coerceAtLeast(0f)
        if (progress >= 1f) covered = true
        bounds.reset()
        bounds.addOval(Rect(origin, radius + 1f))
    }

    override fun distanceAt(angle: Float): Float = radius

    override fun drawOutline(canvas: Canvas, paint: Paint) {
        canvas.drawCircle(origin, radius, paint)
    }

    override fun freeze(image: ImageBitmap) {
        brush = ShaderBrush(ImageShader(image))
    }

    override fun release() {
        brush = null
    }

    // A circle clip is analytic on the GPU, no mask. The pixel of slack sits under the old frame's
    // anti-aliased edge, so no gap opens between the two.
    override fun DrawScope.drawLive(live: GraphicsLayer) {
        if (brush == null || covered) drawLayer(live) else clipPath(bounds) { drawLayer(live) }
    }

    override fun DrawScope.drawOld() {
        val brush = brush ?: return
        if (!covered) with(outside) { draw(brush, origin, radius) }
    }
}

// How far the furthest corner is from [center], which may lie outside the screen.
internal fun reach(center: Offset, size: IntSize): Float {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    return max(
        max(hypot(center.x, center.y), hypot(w - center.x, center.y)),
        max(hypot(center.x, h - center.y), hypot(w - center.x, h - center.y)),
    )
}

// Paints everything outside a circle as one very thick circle stroke: its inner edge is the circle,
// its outer edge beyond every corner. An anti-aliased circle is a cheap analytic draw on any GPU, where
// clipping a frame to the outside of a circle can cost a full-screen mask.
internal class OutsideCircle(reach: Float) {
    // Wide enough to reach every corner from any radius, so it is made once.
    private val stroke = Stroke(width = reach + 2f)

    fun DrawScope.draw(brush: Brush, origin: Offset, radius: Float) {
        drawCircle(brush, radius = radius + stroke.width / 2f, center = origin, style = stroke)
    }
}

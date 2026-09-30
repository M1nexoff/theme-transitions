package io.github.m1nexoff.themetransitions.reveal

import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import io.github.m1nexoff.themetransitions.TransitionOrigin

internal class FloodSpec(
    private val color: Color,
    durationMillis: Int,
    easing: Easing,
    origin: TransitionOrigin,
) : RevealSpec(durationMillis, easing, origin) {
    override fun start(setup: RunSetup): RevealRun = FloodRun(setup, color, easing)
}

// A circle of one colour grows over the old theme until it covers the screen, then shrinks back into
// its origin over the new one. Each half runs through the whole easing.
private class FloodRun(setup: RunSetup, private val color: Color, private val easing: Easing) :
    RevealRun(), RadialEdge {
    // Never `center` inside a DrawScope: there that name is the screen's centre.
    private val origin: Offset = setup.origin
    override val center: Offset get() = origin
    override var radius = 0f
        private set
    override var covered = false
        private set
    override val radial: RadialEdge get() = this

    private val reach = reach(origin, setup.size) + 1f
    private val outside = OutsideCircle(reach)
    private val opaque = color.alpha >= 1f
    private var brush: Brush? = null
    private var receding = false

    override val visible: Boolean get() = radius > 0f

    override fun update(linear: Float, progress: Float, time: Float) {
        if (linear < 0.5f) {
            radius = reach * easing.transform(linear * 2f).coerceAtLeast(0f)
        } else {
            // The old theme is gone from here on.
            receding = true
            brush = null
            radius = reach * (1f - easing.transform((linear - 0.5f) * 2f)).coerceAtLeast(0f)
        }
        if (linear >= 1f) covered = true
    }

    override fun distanceAt(angle: Float): Float = radius

    override fun drawOutline(canvas: Canvas, paint: Paint) {
        canvas.drawCircle(origin, radius, paint)
    }

    override fun freeze(image: ImageBitmap) {
        if (!receding) brush = ShaderBrush(ImageShader(image))
    }

    override fun release() {
        brush = null
    }

    // While the colour grows, it and the old frame around it cover everything.
    override fun DrawScope.drawLive(live: GraphicsLayer) {
        if (receding || brush == null) drawLayer(live)
    }

    override fun DrawScope.drawOld() {
        if (covered) return
        val brush = brush
        if (!receding && brush != null) {
            // A see-through colour shows the old theme under it, not a hole.
            if (opaque) with(outside) { draw(brush, origin, radius) } else drawRect(brush)
        }
        // Over the old frame the colour reaches a pixel past it, under the frame's anti-aliased inner edge,
        // so no seam opens between them.
        if (radius > 0f) drawCircle(color, if (receding) radius else radius + 1f, origin)
    }
}

package io.github.m1nexoff.themetransitions.reveal

import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import io.github.m1nexoff.themetransitions.geometry.WipeGeometry

internal class WipeSpec(private val angleDegrees: Float, durationMillis: Int, easing: Easing) :
    RevealSpec(durationMillis, easing, origin = null) {
    override fun start(setup: RunSetup): RevealRun = WipeRun(setup, angleDegrees)
}

// A straight edge sweeps across the screen.
private class WipeRun(setup: RunSetup, angleDegrees: Float) : RevealRun(), Edge {
    private val geometry = WipeGeometry(setup.size.width.toFloat(), setup.size.height.toFloat(), angleDegrees)
    private val old = Path()
    private var brush: Brush? = null

    override val edge: Edge get() = this
    override var covered = false
        private set
    override val visible: Boolean get() = geometry.edgeVisible

    override fun update(linear: Float, progress: Float, time: Float) {
        geometry.update(progress)
        if (geometry.covered) covered = true
        old.reset()
        val points = geometry.oldPoints
        if (points < 3) return
        val polygon = geometry.oldPolygon
        old.moveTo(polygon[0], polygon[1])
        for (i in 1 until points) old.lineTo(polygon[i * 2], polygon[i * 2 + 1])
        old.close()
    }

    override fun drawOutline(canvas: Canvas, paint: Paint) {
        canvas.drawLine(geometry.edgeStart, geometry.edgeEnd, paint)
    }

    override fun freeze(image: ImageBitmap) {
        brush = ShaderBrush(ImageShader(image))
    }

    override fun release() {
        brush = null
    }

    // A rect clip is free, so a straight horizontal or vertical sweep spares drawing the live theme
    // under the old one. It reaches a pixel past the line, under the old side's anti-aliased edge, so no
    // gap opens between them.
    override fun DrawScope.drawLive(live: GraphicsLayer) {
        if (brush == null || covered || !geometry.axisAligned) {
            drawLayer(live)
            return
        }
        val clip = geometry.liveClip.inflate(1f)
        clipRect(clip.left, clip.top, clip.right, clip.bottom) { drawLayer(live) }
    }

    // A convex polygon filled with the frozen frame: no mask, unlike a clip to the same shape.
    override fun DrawScope.drawOld() {
        val brush = brush ?: return
        if (!old.isEmpty) drawPath(old, brush)
    }
}

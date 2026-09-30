package io.github.m1nexoff.themetransitions.reveal

import androidx.compose.animation.core.Easing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import io.github.m1nexoff.themetransitions.TransitionOrigin

// How the new theme replaces the old one. A spec is the user's choice; start() makes the per-switch
// state that draws it.
internal abstract class RevealSpec(
    val durationMillis: Int,
    val easing: Easing,
    // Null for reveals that do not open from a point.
    val origin: TransitionOrigin?,
) {
    abstract fun start(setup: RunSetup): RevealRun
}

internal class RunSetup(val size: IntSize, val origin: Offset, val density: Density)

// One switch's reveal. The engine calls update() once a frame, before anything draws, and freeze()
// once the old theme has been captured; until then the old theme is still live and nothing is drawn
// over it.
internal abstract class RevealRun {
    // The line between the themes, for effects that follow it. Null when there is none.
    open val edge: Edge? get() = radial

    // The same, for reveals that open from a point.
    open val radial: RadialEdge? get() = null

    // The old frame can no longer show anywhere. The engine then lets go of it.
    abstract val covered: Boolean

    // [linear] is the time through the reveal, 0 to 1; [progress] is the same through its easing,
    // which may overshoot either end. [time] is the effects' clock in radians.
    abstract fun update(linear: Float, progress: Float, time: Float)

    abstract fun freeze(image: ImageBitmap)

    abstract fun release()

    // The live, new theme, wherever the old frame may not cover it.
    open fun DrawScope.drawLive(live: GraphicsLayer) {
        drawLayer(live)
    }

    // The old theme's frame, wherever the new one has not reached yet.
    abstract fun DrawScope.drawOld()
}

internal interface Edge {
    val visible: Boolean

    fun drawOutline(canvas: Canvas, paint: Paint)
}

internal interface RadialEdge : Edge {
    val center: Offset

    // How far the edge is from the centre, roughly; sparks orbit relative to it.
    val radius: Float

    fun distanceAt(angle: Float): Float
}

package io.github.m1nexoff.themetransitions.geometry

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// Within this of a right angle, the line counts as axis-aligned and the live theme gets a rect clip.
private const val AXIS_TOLERANCE_DEGREES = 0.01f

// A straight line sweeping across a width × height screen in the direction [angleDegrees] (0 towards
// the right, 90 downwards). What it has passed is the new theme; the rest, the old one, is a convex
// polygon of at most five points: the screen cut by one straight line.
internal class WipeGeometry(private val width: Float, private val height: Float, angleDegrees: Float) {

    private val dx: Float
    private val dy: Float

    // The line's position, measured along the direction, at the start and at the end of the sweep.
    private val start: Float
    private val end: Float

    val axisAligned: Boolean

    init {
        val radians = angleDegrees / 180f * PI.toFloat()
        val c = cos(radians)
        val s = sin(radians)
        val nearestRightAngle = (angleDegrees / 90f).let { kotlin.math.round(it) * 90f }
        axisAligned = abs(angleDegrees - nearestRightAngle) < AXIS_TOLERANCE_DEGREES
        // Exact for right angles, so a rect clip lines up with whole pixels.
        dx = if (axisAligned) kotlin.math.round(c) else c
        dy = if (axisAligned) kotlin.math.round(s) else s
        val projections = floatArrayOf(0f, width * dx, height * dy, width * dx + height * dy)
        start = projections.min()
        end = projections.max()
    }

    // x, y pairs.
    val oldPolygon = FloatArray(MAX_POINTS * 2)
    var oldPoints = 0
        private set

    // Where the swept part is: for an axis-aligned line, a rect the live theme can be clipped to.
    var liveClip = Rect.Zero
        private set

    var edgeStart = Offset.Zero
        private set
    var edgeEnd = Offset.Zero
        private set
    var edgeVisible = false
        private set

    var covered = false
        private set

    fun update(progress: Float) {
        val sweep = start + (end - start) * progress.coerceIn(0f, 1f)
        covered = progress >= 1f
        oldPoints = 0
        edgeVisible = false
        var edgePoints = 0
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        // Sutherland–Hodgman against one half-plane: walk the screen's corners, keep those ahead of the
        // line, and add a point wherever an edge of the screen crosses it.
        for (i in 0 until CORNERS) {
            val ax = cornerX(i)
            val ay = cornerY(i)
            val bx = cornerX((i + 1) % CORNERS)
            val by = cornerY((i + 1) % CORNERS)
            val a = ax * dx + ay * dy - sweep
            val b = bx * dx + by * dy - sweep
            if (a > 0f) {
                add(ax, ay)
            } else {
                // Behind the line: part of the swept side.
                minX = minOf(minX, ax); minY = minOf(minY, ay); maxX = maxOf(maxX, ax); maxY = maxOf(maxY, ay)
            }
            if ((a > 0f) != (b > 0f)) {
                val t = a / (a - b)
                val x = ax + (bx - ax) * t
                val y = ay + (by - ay) * t
                add(x, y)
                minX = minOf(minX, x); minY = minOf(minY, y); maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
                if (edgePoints == 0) edgeStart = Offset(x, y) else edgeEnd = Offset(x, y)
                edgePoints++
            }
        }
        if (covered) oldPoints = 0
        edgeVisible = edgePoints == 2 && !covered
        liveClip = if (minX > maxX) Rect.Zero else Rect(minX, minY, maxX, maxY)
    }

    private fun add(x: Float, y: Float) {
        oldPolygon[oldPoints * 2] = x
        oldPolygon[oldPoints * 2 + 1] = y
        oldPoints++
    }

    private fun cornerX(i: Int): Float = if (i == 1 || i == 2) width else 0f

    private fun cornerY(i: Int): Float = if (i >= 2) height else 0f

    private companion object {
        const val CORNERS = 4
        const val MAX_POINTS = 5
    }
}

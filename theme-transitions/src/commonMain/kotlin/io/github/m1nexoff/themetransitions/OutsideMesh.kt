package io.github.m1nexoff.themetransitions

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// Triangles covering the part of a width × height screen that lies outside a hole: a closed polygon
// whose vertices sit on evenly spaced rays from [originX], [originY], at the distances given to
// [build]. The frozen frame is drawn through these instead of through a clip, because a clip this
// shape costs the GPU a full-screen coverage mask every frame — measured on a Helio G99, it was what
// pushed the frame time past budget.
//
// The screen is cut into wedges between consecutive rays, plus one ray through each screen corner so
// no wedge meets two screen edges. In a wedge, the uncovered part is where the hole edge is nearer
// than the screen edge: all of it, a triangle where the hole edge crosses the screen edge, or none.
internal class OutsideMesh(
    private val originX: Float,
    private val originY: Float,
    private val width: Float,
    private val height: Float,
    private val rays: Int,
) {
    private val step = TWO_PI / rays

    // Rays in angular order: the hole's own, then the four corner rays, each after the hole ray
    // below it.
    private val raySegment = IntArray(rays + CORNERS)
    private val rayIsCorner = BooleanArray(rays + CORNERS)
    private val rayCos = FloatArray(rays + CORNERS)
    private val raySin = FloatArray(rays + CORNERS)
    private val rayReach = FloatArray(rays + CORNERS)

    // A corner exactly on a hole ray adds no wedge of its own, so this can fall short of the arrays.
    private val rayCount: Int

    private val holeX = FloatArray(rays + CORNERS)
    private val holeY = FloatArray(rays + CORNERS)
    private val holeDistance = FloatArray(rays + CORNERS)

    // x, y pairs, three vertices per triangle.
    val positions = FloatArray((rays + CORNERS) * MAX_TRIANGLES_PER_WEDGE * 6)
    var size = 0
        private set

    init {
        val corners = floatArrayOf(width, height, 0f, height, 0f, 0f, width, 0f)
        val cornerAngles = FloatArray(CORNERS) { c ->
            normalized(atan2(corners[c * 2 + 1] - originY, corners[c * 2] - originX))
        }
        var r = 0
        for (i in 0 until rays) {
            add(r++, i * step, i, corner = false)
            val next = (i + 1) * step
            cornerAngles.indices
                .filter { cornerAngles[it] > i * step && cornerAngles[it] < next }
                .sortedBy { cornerAngles[it] }
                .forEach { add(r++, cornerAngles[it], i, corner = true) }
        }
        rayCount = r
    }

    private fun add(index: Int, angle: Float, segment: Int, corner: Boolean) {
        raySegment[index] = segment
        rayIsCorner[index] = corner
        val c = cos(angle)
        val s = sin(angle)
        rayCos[index] = c
        raySin[index] = s
        rayReach[index] = minOf(
            if (c > EPSILON) (width - originX) / c else if (c < -EPSILON) -originX / c else Float.MAX_VALUE,
            if (s > EPSILON) (height - originY) / s else if (s < -EPSILON) -originY / s else Float.MAX_VALUE,
        )
    }

    // [distances] holds the hole's distance from the origin along each of its [rays].
    fun build(distances: FloatArray) {
        for (r in 0 until rayCount) {
            val segment = raySegment[r]
            if (!rayIsCorner[r]) {
                val d = distances[segment]
                holeX[r] = originX + rayCos[r] * d
                holeY[r] = originY + raySin[r] * d
                holeDistance[r] = d
                continue
            }
            // Where this corner ray crosses the hole edge between the two hole vertices around it.
            val d0 = distances[segment]
            val d1 = distances[(segment + 1) % rays]
            val ax = cos(segment * step) * d0
            val ay = sin(segment * step) * d0
            val ex = cos((segment + 1) * step) * d1 - ax
            val ey = sin((segment + 1) * step) * d1 - ay
            val denominator = rayCos[r] * ey - raySin[r] * ex
            val d = if (denominator == 0f) 0f else (ax * ey - ay * ex) / denominator
            holeX[r] = originX + rayCos[r] * d
            holeY[r] = originY + raySin[r] * d
            holeDistance[r] = d
        }

        size = 0
        for (r in 0 until rayCount) {
            val n = (r + 1) % rayCount
            wedge(r, n)
        }
    }

    private fun wedge(a: Int, b: Int) {
        val aInside = holeDistance[a] < rayReach[a]
        val bInside = holeDistance[b] < rayReach[b]
        if (!aInside && !bInside) return

        val ax = originX + rayCos[a] * rayReach[a]
        val ay = originY + raySin[a] * rayReach[a]
        val bx = originX + rayCos[b] * rayReach[b]
        val by = originY + raySin[b] * rayReach[b]

        if (aInside && bInside) {
            triangle(holeX[a], holeY[a], holeX[b], holeY[b], bx, by)
            triangle(holeX[a], holeY[a], bx, by, ax, ay)
            return
        }

        // The hole edge leaves the screen inside this wedge: find where it crosses the screen edge.
        val hx = holeX[b] - holeX[a]
        val hy = holeY[b] - holeY[a]
        val sx = bx - ax
        val sy = by - ay
        val denominator = hx * sy - hy * sx
        if (denominator == 0f) return
        val t = ((ax - holeX[a]) * sy - (ay - holeY[a]) * sx) / denominator
        val cx = holeX[a] + hx * t
        val cy = holeY[a] + hy * t
        if (aInside) {
            triangle(holeX[a], holeY[a], cx, cy, ax, ay)
        } else {
            triangle(cx, cy, holeX[b], holeY[b], bx, by)
        }
    }

    private fun triangle(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float) {
        positions[size++] = x0
        positions[size++] = y0
        positions[size++] = x1
        positions[size++] = y1
        positions[size++] = x2
        positions[size++] = y2
    }

    private companion object {
        const val CORNERS = 4
        const val MAX_TRIANGLES_PER_WEDGE = 2
        const val EPSILON = 1e-6f
        val TWO_PI = (2 * PI).toFloat()

        fun normalized(angle: Float): Float = if (angle < 0f) angle + TWO_PI else angle
    }
}

package io.github.m1nexoff.themerift

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

// The mesh stands in for a clip, so a mistake shows as the old palette where the new one belongs, or
// the reverse. Every sampled pixel must be covered exactly once outside the hole and never inside it.
class OutsideMeshTest {

    private val width = 1080f
    private val height = 2400f
    private val rays = 64

    @Test
    fun emptyHoleCoversTheWholeScreen() {
        assertCoverage(originX = 540f, originY = 1137f, distances = FloatArray(rays))
    }

    @Test
    fun holeInsideTheScreenLeavesItsOutsideCovered() {
        assertCoverage(originX = 697f, originY = 1137f, distances = wobbly(radius = 300f, seed = 1))
    }

    @Test
    fun holeCrossingTheScreenEdgesIsCutAtThem() {
        assertCoverage(originX = 405f, originY = 1137f, distances = wobbly(radius = 900f, seed = 2))
        assertCoverage(originX = 100f, originY = 200f, distances = wobbly(radius = 1500f, seed = 3))
    }

    @Test
    fun holePastEveryCornerLeavesNothing() {
        val mesh = OutsideMesh(540f, 1137f, width, height, rays)
        mesh.build(FloatArray(rays) { 5_000f })
        assertEquals(0, mesh.size)
    }

    @Test
    fun randomHolesAndOriginsKeepExactCoverage() {
        val random = Random(42)
        repeat(40) {
            assertCoverage(
                originX = random.nextFloat() * width,
                originY = random.nextFloat() * height,
                distances = wobbly(radius = random.nextFloat() * 2_800f, seed = random.nextInt()),
            )
        }
    }

    // The same ragged edge the rift grows: a circle pushed in and out by a few percent.
    private fun wobbly(radius: Float, seed: Int): FloatArray {
        val random = Random(seed)
        return FloatArray(rays) { radius * (0.93f + random.nextFloat() * 0.14f) }
    }

    private fun assertCoverage(originX: Float, originY: Float, distances: FloatArray) {
        val mesh = OutsideMesh(originX, originY, width, height, rays)
        mesh.build(distances)
        val holeX = FloatArray(rays) { originX + cos(it * STEP).toFloat() * distances[it] }
        val holeY = FloatArray(rays) { originY + sin(it * STEP).toFloat() * distances[it] }

        var y = 0.5f
        while (y < height) {
            var x = 0.5f
            while (x < width) {
                if (!nearHoleEdge(x, y, holeX, holeY) && !nearTriangleEdge(x, y, mesh)) {
                    val expected = if (insideHole(x, y, holeX, holeY)) 0 else 1
                    val covered = coverage(x, y, mesh)
                    if (covered != expected) {
                        fail("($x, $y) from origin ($originX, $originY): covered $covered times, expected $expected")
                    }
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }
    }

    private fun coverage(x: Float, y: Float, mesh: OutsideMesh): Int {
        var count = 0
        var i = 0
        val p = mesh.positions
        while (i < mesh.size) {
            if (inTriangle(x, y, p[i], p[i + 1], p[i + 2], p[i + 3], p[i + 4], p[i + 5])) count++
            i += 6
        }
        return count
    }

    private fun inTriangle(x: Float, y: Float, x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        val d0 = cross(x1 - x0, y1 - y0, x - x0, y - y0)
        val d1 = cross(x2 - x1, y2 - y1, x - x1, y - y1)
        val d2 = cross(x0 - x2, y0 - y2, x - x2, y - y2)
        val anyNegative = d0 < 0 || d1 < 0 || d2 < 0
        val anyPositive = d0 > 0 || d1 > 0 || d2 > 0
        return !(anyNegative && anyPositive)
    }

    private fun insideHole(x: Float, y: Float, hx: FloatArray, hy: FloatArray): Boolean {
        var inside = false
        var j = rays - 1
        for (i in 0 until rays) {
            if ((hy[i] > y) != (hy[j] > y) &&
                x < (hx[j] - hx[i]) * (y - hy[i]) / (hy[j] - hy[i]) + hx[i]
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    private fun nearHoleEdge(x: Float, y: Float, hx: FloatArray, hy: FloatArray): Boolean {
        var j = rays - 1
        for (i in 0 until rays) {
            if (distanceToSegment(x, y, hx[j], hy[j], hx[i], hy[i]) < TOLERANCE) return true
            j = i
        }
        return false
    }

    private fun nearTriangleEdge(x: Float, y: Float, mesh: OutsideMesh): Boolean {
        val p = mesh.positions
        var i = 0
        while (i < mesh.size) {
            if (distanceToSegment(x, y, p[i], p[i + 1], p[i + 2], p[i + 3]) < TOLERANCE ||
                distanceToSegment(x, y, p[i + 2], p[i + 3], p[i + 4], p[i + 5]) < TOLERANCE ||
                distanceToSegment(x, y, p[i + 4], p[i + 5], p[i], p[i + 1]) < TOLERANCE
            ) {
                return true
            }
            i += 6
        }
        return false
    }

    private fun distanceToSegment(x: Float, y: Float, x0: Float, y0: Float, x1: Float, y1: Float): Float {
        val dx = x1 - x0
        val dy = y1 - y0
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return hypot(x - x0, y - y0)
        val t = (((x - x0) * dx + (y - y0) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(x - (x0 + t * dx), y - (y0 + t * dy))
    }

    private fun cross(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val c = ax * by - ay * bx
        return if (abs(c) < 1e-3f) 0f else c
    }

    private companion object {
        const val SAMPLE_STEP = 37f
        const val TOLERANCE = 0.75f
        val STEP = 2 * PI / 64
    }
}

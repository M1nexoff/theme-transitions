package io.github.m1nexoff.themetransitions.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

// The polygon stands in for a clip: a point belongs to the old theme exactly when the sweeping line
// has not reached it yet.
class WipeGeometryTest {

    private val width = 300f
    private val height = 500f

    @Test
    fun oldSideIsExactlyWhatTheLineHasNotReached() {
        for (angle in ANGLES) {
            for (progress in PROGRESSES) {
                val wipe = WipeGeometry(width, height, angle)
                wipe.update(progress)
                assertOldSide(wipe, angle, progress)
            }
        }
    }

    @Test
    fun nothingIsSweptAtTheStartAndEverythingAtTheEnd() {
        for (angle in ANGLES) {
            val wipe = WipeGeometry(width, height, angle)
            wipe.update(0f)
            assertEquals(width * height, area(wipe), width * height * 1e-4f, "at 0, angle $angle")
            assertFalse(wipe.covered, "at 0, angle $angle")
            wipe.update(1f)
            assertEquals(0f, area(wipe), 1e-2f, "at 1, angle $angle")
            assertTrue(wipe.covered, "at 1, angle $angle")
        }
    }

    @Test
    fun overshootingProgressClampsToTheScreen() {
        val wipe = WipeGeometry(width, height, 90f)
        wipe.update(-0.4f)
        assertEquals(width * height, area(wipe), 1f)
        wipe.update(1.7f)
        assertEquals(0f, area(wipe), 1e-2f)
        assertTrue(wipe.covered)
    }

    @Test
    fun onlyMultiplesOfNinetyDegreesAreAxisAligned() {
        assertTrue(WipeGeometry(width, height, 0f).axisAligned)
        assertTrue(WipeGeometry(width, height, 90f).axisAligned)
        assertTrue(WipeGeometry(width, height, -270f).axisAligned)
        assertTrue(WipeGeometry(width, height, 720f).axisAligned)
        assertFalse(WipeGeometry(width, height, 45f).axisAligned)
        assertFalse(WipeGeometry(width, height, 91f).axisAligned)
    }

    @Test
    fun axisAlignedLiveClipIsTheSweptRect() {
        val down = WipeGeometry(width, height, 90f)
        down.update(0.25f)
        assertEquals(0f, down.liveClip.top, 1e-3f)
        assertEquals(125f, down.liveClip.bottom, 1e-3f)
        assertEquals(width, down.liveClip.width, 1e-3f)

        val left = WipeGeometry(width, height, 180f)
        left.update(0.5f)
        assertEquals(150f, left.liveClip.left, 1e-3f)
        assertEquals(width, left.liveClip.right, 1e-3f)
        assertEquals(height, left.liveClip.height, 1e-3f)
    }

    @Test
    fun edgeRunsAlongTheLineFromSideToSide() {
        val wipe = WipeGeometry(width, height, 90f)
        wipe.update(0.5f)
        assertTrue(wipe.edgeVisible)
        assertEquals(250f, wipe.edgeStart.y, 1e-3f)
        assertEquals(250f, wipe.edgeEnd.y, 1e-3f)
        assertEquals(width, abs(wipe.edgeEnd.x - wipe.edgeStart.x), 1e-3f)

        wipe.update(1f)
        assertFalse(wipe.edgeVisible)
    }

    private fun assertOldSide(wipe: WipeGeometry, angle: Float, progress: Float) {
        val radians = angle / 180f * PI.toFloat()
        val dx = cos(radians)
        val dy = sin(radians)
        val projections = listOf(0f, width * dx, height * dy, width * dx + height * dy)
        val low = projections.min()
        val high = projections.max()
        val sweep = low + (high - low) * progress.coerceIn(0f, 1f)
        var y = 3.5f
        while (y < height) {
            var x = 3.5f
            while (x < width) {
                val along = x * dx + y * dy
                // Points on the line itself could go either way.
                if (abs(along - sweep) > 0.5f) {
                    val expected = along > sweep
                    if (contains(wipe, x, y) != expected) {
                        fail("angle $angle, progress $progress: ($x, $y) should ${if (expected) "" else "not "}be old")
                    }
                }
                x += 7f
            }
            y += 7f
        }
    }

    private fun contains(wipe: WipeGeometry, x: Float, y: Float): Boolean {
        val n = wipe.oldPoints
        if (n < 3) return false
        val p = wipe.oldPolygon
        var sign = 0
        for (i in 0 until n) {
            val ax = p[i * 2]
            val ay = p[i * 2 + 1]
            val bx = p[((i + 1) % n) * 2]
            val by = p[((i + 1) % n) * 2 + 1]
            val cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax)
            if (abs(cross) < 1e-3f) continue
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    private fun area(wipe: WipeGeometry): Float {
        val n = wipe.oldPoints
        val p = wipe.oldPolygon
        var twice = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            twice += p[i * 2] * p[j * 2 + 1] - p[j * 2] * p[i * 2 + 1]
        }
        return abs(twice) / 2f
    }

    private companion object {
        val ANGLES = listOf(0f, 30f, 90f, 135f, 180f, 225f, 270f, 333f, -45f, 720f)
        val PROGRESSES = listOf(0f, 0.25f, 0.5f, 1f)
    }
}

package io.github.m1nexoff.themetransitions.geometry

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

// The tiles stand in for the old frame: a gap shows the new theme early, an overlap is wasted fill.
class TileGridTest {

    @Test
    fun tilesCoverEveryPixelExactlyOnce() {
        val width = 301
        val height = 517
        val grid = TileGrid(width, height, tilePx = 16, maxTiles = 10_000, random = Random(7))
        val coverage = IntArray(width * height)
        for (i in 0 until grid.count) {
            for (y in grid.top[i] until grid.bottom[i]) {
                for (x in grid.left[i] until grid.right[i]) coverage[y * width + x]++
            }
        }
        coverage.forEachIndexed { index, times ->
            if (times != 1) fail("pixel (${index % width}, ${index / width}) covered $times times")
        }
    }

    @Test
    fun trianglesMatchTheTilesInTheSameOrder() {
        val grid = TileGrid(64, 48, tilePx = 16, maxTiles = 10_000, random = Random(3))
        assertEquals(grid.count * 12, grid.triangles.size)
        for (i in 0 until grid.count) {
            val t = grid.triangles
            val o = i * 12
            assertEquals(grid.left[i].toFloat(), t[o])
            assertEquals(grid.top[i].toFloat(), t[o + 1])
            assertEquals(grid.right[i].toFloat(), t[o + 4])
            assertEquals(grid.bottom[i].toFloat(), t[o + 5])
        }
    }

    @Test
    fun tileSizeGrowsToStayUnderTheCap() {
        val grid = TileGrid(1080, 2340, tilePx = 4, maxTiles = 600, random = Random(1))
        assertTrue(grid.count <= 600, "count ${grid.count}")
        assertTrue(grid.tilePx > 4, "tile ${grid.tilePx}")
    }

    @Test
    fun thresholdsAscend() {
        val grid = TileGrid(400, 800, tilePx = 20, maxTiles = 10_000, random = Random(5))
        for (i in 1 until grid.count) assertTrue(grid.thresholds[i - 1] <= grid.thresholds[i])
        assertTrue(grid.thresholds.all { it >= 0f && it < 1f })
    }

    @Test
    fun remainingTilesAreTheOnesNotYetDue() {
        val grid = TileGrid(400, 800, tilePx = 20, maxTiles = 10_000, random = Random(9))
        assertEquals(0, grid.firstRemaining(0f))
        assertEquals(grid.count, grid.firstRemaining(1f))
        assertEquals(0, grid.firstRemaining(-0.4f))
        assertEquals(grid.count, grid.firstRemaining(1.7f))
        val first = grid.firstRemaining(0.5f)
        for (i in 0 until first) assertTrue(grid.thresholds[i] < 0.5f)
        for (i in first until grid.count) assertTrue(grid.thresholds[i] >= 0.5f)
    }
}

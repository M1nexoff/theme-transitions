package io.github.m1nexoff.themetransitions.geometry

import kotlin.random.Random

// A width × height screen cut into square tiles of whole pixels, each given a random moment, 0 to 1,
// at which it vanishes. The tiles are sorted by that moment, so those still showing at any progress
// are always the last ones: drawing them is one run from an offset to the end, with nothing to
// rebuild from frame to frame.
internal class TileGrid(width: Int, height: Int, tilePx: Int, maxTiles: Int, random: Random) {

    // Raised from the size asked for until the tiles fit under maxTiles.
    val tilePx: Int
    val count: Int

    val thresholds: FloatArray
    val left: IntArray
    val top: IntArray
    val right: IntArray
    val bottom: IntArray

    // Two triangles per tile, x, y pairs, in the same order: 12 floats a tile.
    val triangles: FloatArray

    init {
        require(width > 0 && height > 0) { "the screen must not be empty: $width × $height" }
        require(tilePx > 0) { "tiles must be at least a pixel: $tilePx" }
        require(maxTiles > 0) { "maxTiles must be positive: $maxTiles" }
        var size = tilePx
        while (columns(width, size) * rows(height, size) > maxTiles) size++
        this.tilePx = size
        val columns = columns(width, size)
        count = columns * rows(height, size)

        val moments = FloatArray(count) { random.nextFloat() }
        val order = (0 until count).sortedBy { moments[it] }
        thresholds = FloatArray(count)
        left = IntArray(count)
        top = IntArray(count)
        right = IntArray(count)
        bottom = IntArray(count)
        triangles = FloatArray(count * 12)
        order.forEachIndexed { i, tile ->
            thresholds[i] = moments[tile]
            val l = (tile % columns) * size
            val t = (tile / columns) * size
            val r = minOf(l + size, width)
            val b = minOf(t + size, height)
            left[i] = l
            top[i] = t
            right[i] = r
            bottom[i] = b
            val o = i * 12
            triangles[o] = l.toFloat(); triangles[o + 1] = t.toFloat()
            triangles[o + 2] = r.toFloat(); triangles[o + 3] = t.toFloat()
            triangles[o + 4] = r.toFloat(); triangles[o + 5] = b.toFloat()
            triangles[o + 6] = l.toFloat(); triangles[o + 7] = t.toFloat()
            triangles[o + 8] = r.toFloat(); triangles[o + 9] = b.toFloat()
            triangles[o + 10] = l.toFloat(); triangles[o + 11] = b.toFloat()
        }
    }

    // The first tile still showing at [progress]: a tile shows until progress reaches its moment. A
    // binary search, since an easing may overshoot and run progress backwards.
    fun firstRemaining(progress: Float): Int {
        var low = 0
        var high = count
        while (low < high) {
            val mid = (low + high) ushr 1
            if (thresholds[mid] < progress) low = mid + 1 else high = mid
        }
        return low
    }

    private fun columns(width: Int, size: Int) = (width + size - 1) / size

    private fun rows(height: Int, size: Int) = (height + size - 1) / size
}

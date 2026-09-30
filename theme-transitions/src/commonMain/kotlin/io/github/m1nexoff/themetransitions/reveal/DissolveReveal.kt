package io.github.m1nexoff.themetransitions.reveal

import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.m1nexoff.themetransitions.geometry.TileGrid
import io.github.m1nexoff.themetransitions.platform.TilePainter
import io.github.m1nexoff.themetransitions.platform.createTilePainter
import io.github.m1nexoff.themetransitions.platform.dissolveTileLimit
import kotlin.math.roundToInt
import kotlin.random.Random

internal class DissolveSpec(private val tileSize: Dp, durationMillis: Int, easing: Easing) :
    RevealSpec(durationMillis, easing, origin = null) {
    init {
        require(tileSize > 0.dp) { "tileSize must be positive, was $tileSize" }
    }

    override fun start(setup: RunSetup): RevealRun {
        val tilePx = with(setup.density) { tileSize.toPx() }.roundToInt().coerceAtLeast(1)
        return DissolveRun(TileGrid(setup.size.width, setup.size.height, tilePx, dissolveTileLimit, Random))
    }
}

// The old theme breaks into tiles that vanish one by one, in random order.
private class DissolveRun(private val grid: TileGrid) : RevealRun() {
    private var image: ImageBitmap? = null
    private var painter: TilePainter? = null
    private var first = 0
    override var covered = false
        private set

    override fun update(linear: Float, progress: Float, time: Float) {
        first = grid.firstRemaining(progress)
        if (first >= grid.count) covered = true
    }

    override fun freeze(image: ImageBitmap) {
        this.image = image
        painter = createTilePainter(image)
    }

    override fun release() {
        image = null
        painter = null
    }

    override fun DrawScope.drawOld() {
        val image = image ?: return
        if (covered) return
        val painter = painter
        if (painter != null) {
            painter.draw(this, grid, first)
            return
        }
        for (i in first until grid.count) {
            val offset = IntOffset(grid.left[i], grid.top[i])
            val size = IntSize(grid.right[i] - grid.left[i], grid.bottom[i] - grid.top[i])
            drawImage(image, srcOffset = offset, srcSize = size, dstOffset = offset, dstSize = size)
        }
    }
}

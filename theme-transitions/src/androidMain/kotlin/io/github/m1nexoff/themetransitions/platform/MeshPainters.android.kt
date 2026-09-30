package io.github.m1nexoff.themetransitions.platform

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import io.github.m1nexoff.themetransitions.geometry.OutsideMesh
import io.github.m1nexoff.themetransitions.geometry.TileGrid

// A hardware-accelerated canvas only draws vertices from Android 10; before that the call is dropped,
// and the old theme would vanish the moment the transition opened.
private val drawsVertices = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

internal actual fun createOutsideMeshPainter(image: ImageBitmap): OutsideMeshPainter? =
    if (drawsVertices) VertexOutsideMeshPainter(texturePaint(image)) else null

internal actual fun createTilePainter(image: ImageBitmap): TilePainter? =
    if (drawsVertices) VertexTilePainter(texturePaint(image)) else null

// A mesh redraws all its tiles in one call, so they can be small. One drawImage each before Android 10
// costs a draw op per tile, so they are kept few there.
internal actual val dissolveTileLimit: Int = if (drawsVertices) 20_000 else 600

private fun texturePaint(image: ImageBitmap): Paint = Paint().apply {
    shader = BitmapShader(image.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
}

private class VertexOutsideMeshPainter(private val paint: Paint) : OutsideMeshPainter {
    override fun draw(scope: DrawScope, mesh: OutsideMesh) {
        if (mesh.size == 0) return
        scope.drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawTriangles(mesh.positions, 0, mesh.size, paint)
        }
    }
}

private class VertexTilePainter(private val paint: Paint) : TilePainter {
    override fun draw(scope: DrawScope, grid: TileGrid, first: Int) {
        val remaining = grid.count - first
        if (remaining <= 0) return
        scope.drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawTriangles(grid.triangles, first * FLOATS_PER_TILE, remaining * FLOATS_PER_TILE, paint)
        }
    }
}

private const val FLOATS_PER_TILE = 12

// Each vertex's position is also its texture coordinate.
private fun android.graphics.Canvas.drawTriangles(positions: FloatArray, offset: Int, count: Int, paint: Paint) {
    drawVertices(
        Canvas.VertexMode.TRIANGLES,
        count,
        positions,
        offset,
        positions,
        offset,
        null,
        0,
        null,
        0,
        0,
        paint,
    )
}

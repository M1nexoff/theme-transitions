package io.github.m1nexoff.themerift

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

// A hardware-accelerated canvas only draws vertices from Android 10; before that the call is
// dropped, and the old theme would vanish the moment the rift opened.
internal actual fun FrozenFramePainter(image: ImageBitmap): FrozenFramePainter =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MeshFramePainter(image) else ClipFramePainter(image)

// The frame is the mesh's texture, mapped 1:1, so the mesh's coordinates double as texture
// coordinates. The mesh is not anti-aliased; the rift's rim is drawn over its edge.
private class MeshFramePainter(image: ImageBitmap) : FrozenFramePainter {
    private val paint = Paint().apply {
        shader = BitmapShader(image.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }

    override val usesMesh = true

    override fun draw(scope: DrawScope, mesh: OutsideMesh, hole: Path) {
        if (mesh.size == 0) return
        scope.drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawVertices(
                Canvas.VertexMode.TRIANGLES,
                mesh.size,
                mesh.positions,
                0,
                mesh.positions,
                0,
                null,
                0,
                null,
                0,
                0,
                paint,
            )
        }
    }
}

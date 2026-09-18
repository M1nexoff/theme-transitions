package io.github.m1nexoff.themerift

import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath

// Draws a rift's frozen frame everywhere outside its hole. How is the platform's call: through
// OutsideMesh where drawing a textured mesh is cheap, through a clip where it is not known to be.
internal interface FrozenFramePainter {
    // Whether draw() reads the mesh; when it does not, nobody needs to build it.
    val usesMesh: Boolean

    fun draw(scope: DrawScope, mesh: OutsideMesh, hole: Path)
}

internal expect fun FrozenFramePainter(image: ImageBitmap): FrozenFramePainter

// Correct everywhere, but it costs the GPU a coverage mask the size of the screen every frame.
internal class ClipFramePainter(private val image: ImageBitmap) : FrozenFramePainter {
    override val usesMesh = false

    override fun draw(scope: DrawScope, mesh: OutsideMesh, hole: Path) {
        scope.clipPath(hole, ClipOp.Difference) { drawImage(image) }
    }
}

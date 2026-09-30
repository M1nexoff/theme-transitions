package io.github.m1nexoff.themetransitions.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.m1nexoff.themetransitions.geometry.OutsideMesh

// Draws a frozen frame through an OutsideMesh: the frame is the mesh's texture, mapped 1:1, so the
// mesh's coordinates double as texture coordinates. The mesh is not anti-aliased.
internal interface OutsideMeshPainter {
    fun draw(scope: DrawScope, mesh: OutsideMesh)
}

// Null where drawing a textured mesh is not known to be cheap, or not possible; the caller then clips.
internal expect fun createOutsideMeshPainter(image: ImageBitmap): OutsideMeshPainter?

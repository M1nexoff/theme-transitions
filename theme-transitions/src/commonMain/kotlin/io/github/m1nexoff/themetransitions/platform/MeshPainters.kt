package io.github.m1nexoff.themetransitions.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import io.github.m1nexoff.themetransitions.geometry.OutsideMesh
import io.github.m1nexoff.themetransitions.geometry.TileGrid

// Draws a frozen frame through an OutsideMesh: the frame is the mesh's texture, mapped 1:1, so the
// mesh's coordinates double as texture coordinates. The mesh is not anti-aliased.
internal interface OutsideMeshPainter {
    fun draw(scope: DrawScope, mesh: OutsideMesh)
}

// Null where drawing a textured mesh is not known to be cheap, or not possible; the caller then clips.
internal expect fun createOutsideMeshPainter(image: ImageBitmap): OutsideMeshPainter?

// Draws a frozen frame's tiles from [first] to the end of a TileGrid, as one textured mesh.
internal interface TilePainter {
    fun draw(scope: DrawScope, grid: TileGrid, first: Int)
}

// Null where the tiles are drawn one image call each instead.
internal expect fun createTilePainter(image: ImageBitmap): TilePainter?

// How many tiles a dissolve may cut the screen into here: many as one mesh, few one draw call each.
internal expect val dissolveTileLimit: Int

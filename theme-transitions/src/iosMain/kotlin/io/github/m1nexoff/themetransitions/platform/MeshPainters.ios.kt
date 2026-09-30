package io.github.m1nexoff.themetransitions.platform

import androidx.compose.ui.graphics.ImageBitmap

// Not measured on iOS, so it keeps the clip, which is known to draw correctly there.
internal actual fun createOutsideMeshPainter(image: ImageBitmap): OutsideMeshPainter? = null

// Skia takes a mesh's vertices whole, with no offset into them, so tiles are drawn one image call each.
internal actual fun createTilePainter(image: ImageBitmap): TilePainter? = null

internal actual val dissolveTileLimit: Int = 2_500

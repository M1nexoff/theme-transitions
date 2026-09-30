package io.github.m1nexoff.themetransitions.platform

import androidx.compose.ui.graphics.ImageBitmap

// Not measured on iOS, so it keeps the clip, which is known to draw correctly there.
internal actual fun createOutsideMeshPainter(image: ImageBitmap): OutsideMeshPainter? = null

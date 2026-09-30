package io.github.m1nexoff.themetransitions

import androidx.compose.ui.graphics.ImageBitmap

// Not measured on desktop yet, so it keeps the clip, which is known to draw correctly there.
internal actual fun FrozenFramePainter(image: ImageBitmap): FrozenFramePainter = ClipFramePainter(image)

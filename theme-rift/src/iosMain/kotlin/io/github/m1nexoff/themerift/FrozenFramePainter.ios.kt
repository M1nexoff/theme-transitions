package io.github.m1nexoff.themerift

import androidx.compose.ui.graphics.ImageBitmap

// Not measured on iOS yet, so it keeps the clip, which is known to draw correctly there.
internal actual fun FrozenFramePainter(image: ImageBitmap): FrozenFramePainter = ClipFramePainter(image)

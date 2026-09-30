package io.github.m1nexoff.themetransitions.geometry

import androidx.compose.ui.unit.Density
import kotlin.math.PI

internal const val TWO_PI = 2f * PI.toFloat()

// The rift's sizes were tuned as raw pixels on a 420dpi phone. ref() gives them the same physical size
// on any screen.
internal const val REFERENCE_DENSITY = 2.625f

internal fun Density.ref(px: Float): Float = px * density / REFERENCE_DENSITY

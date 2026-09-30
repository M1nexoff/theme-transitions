package io.github.m1nexoff.themetransitions.reveal

import androidx.compose.animation.core.Easing
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope

internal class FadeSpec(durationMillis: Int, easing: Easing) : RevealSpec(durationMillis, easing, origin = null) {
    override fun start(setup: RunSetup): RevealRun = FadeRun()
}

// The old theme fades out over the new one.
private class FadeRun : RevealRun() {
    private var image: ImageBitmap? = null
    private var alpha = 1f
    override var covered = false
        private set

    override fun update(linear: Float, progress: Float, time: Float) {
        alpha = (1f - progress).coerceIn(0f, 1f)
        if (progress >= 1f) covered = true
    }

    override fun freeze(image: ImageBitmap) {
        this.image = image
    }

    override fun release() {
        image = null
    }

    override fun DrawScope.drawOld() {
        val image = image ?: return
        if (alpha > 0f) drawImage(image, alpha = alpha)
    }
}

package io.github.m1nexoff.themetransitions.engine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first

// Retired runs only fade their effects out, but a burst of switches should not pile them up.
private const val MAX_RETIRING = 2

// The transitions on screen: the current one, and those a newer switch replaced, fading out.
internal class TransitionEngine {

    var current: Run? by mutableStateOf(null)
        private set

    // Oldest first. Replaced as a whole, never mutated, so a frame can read it once and keep it.
    var retiring: List<Run> by mutableStateOf(emptyList())
        private set

    val active: Boolean get() = current != null || retiring.isNotEmpty()

    private var lastFrameNanos = -1L

    // The run already on screen retires: its reveal stops where it is, and its effects fade out. Its
    // old frame stays until the new run freezes, which captures it along with the live theme.
    fun begin(run: Run) {
        val replaced = current
        if (replaced != null) {
            replaced.retire()
            val all = retiring + replaced
            all.dropLast(MAX_RETIRING).forEach { it.release() }
            retiring = all.takeLast(MAX_RETIRING)
        }
        current = run
    }

    fun freeze(run: Run, image: ImageBitmap) {
        run.freeze(image)
        // Whatever they still showed of older themes is in this frame now.
        retiring.forEach { it.release() }
    }

    fun clear() {
        current?.release()
        retiring.forEach { it.release() }
        current = null
        retiring = emptyList()
        lastFrameNanos = -1L
    }

    // Ticks every frame while anything is on screen; idles otherwise.
    suspend fun runFrames() {
        while (true) {
            snapshotFlow { active }.first { it }
            while (active) {
                val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
                if (scale == 0f) {
                    clear()
                    break
                }
                withFrameNanos { tick(it, scale) }
            }
            lastFrameNanos = -1L
        }
    }

    private fun tick(frameNanos: Long, scale: Float) {
        val delta = if (lastFrameNanos < 0L) 0f else (frameNanos - lastFrameNanos) / 1_000_000f / scale
        lastFrameNanos = frameNanos

        val run = current
        if (run != null) {
            run.advance(delta)
            if (run.finished) {
                run.release()
                current = null
            }
        }
        val old = retiring
        if (old.isNotEmpty()) {
            for (i in old.indices) old[i].advance(delta)
            if (old.any { it.finished }) {
                old.filter { it.finished }.forEach { it.release() }
                retiring = old.filterNot { it.finished }
            }
        }
    }
}

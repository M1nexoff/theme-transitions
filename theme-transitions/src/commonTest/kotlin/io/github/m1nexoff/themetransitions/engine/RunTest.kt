package io.github.m1nexoff.themetransitions.engine

import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import io.github.m1nexoff.themetransitions.Transitions
import io.github.m1nexoff.themetransitions.reveal.RunSetup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// The clock a transition runs on, and what it holds on to while it runs.
class RunTest {

    private val setup = RunSetup(IntSize(100, 200), Offset(50f, 100f), Density(1f))

    private fun circle(durationMillis: Int = 1_000) =
        Run(Transitions.circle(durationMillis = durationMillis, easing = LinearEasing), setup)

    @Test
    fun nothingMovesUntilTheOldThemeIsFrozen() {
        val run = circle()
        run.advance(300f)
        assertFalse(run.frozen)
        assertEquals(0f, run.progress)
        assertFalse(run.finished)
    }

    @Test
    fun progressCountsFromTheFreeze() {
        val run = circle()
        run.advance(100f)
        run.freeze(ImageBitmap(100, 200))
        run.advance(250f)
        assertEquals(0.25f, run.progress, 1e-4f)
        assertEquals(350f, run.clockMillis, 1e-4f)
    }

    @Test
    fun theOldFrameIsLetGoOnceTheRevealCoversTheScreen() {
        val run = circle()
        run.freeze(ImageBitmap(100, 200))
        run.advance(500f)
        assertFalse(run.released, "half way, the old frame still shows")
        run.advance(500f)
        assertTrue(run.reveal.covered)
        assertTrue(run.released, "covered, the old frame should be let go before the run ends")
        assertFalse(run.hasOld)
    }

    @Test
    fun aRetiredRunFadesOutItsEffectsOverAHundredAndFiftyMilliseconds() {
        val run = circle()
        run.freeze(ImageBitmap(100, 200))
        run.advance(100f)
        run.retire()
        assertEquals(1f, run.alpha)
        run.advance(75f)
        assertEquals(0.5f, run.alpha, 1e-4f)
        assertFalse(run.finished)
        run.advance(75f)
        assertTrue(run.finished)
    }
}

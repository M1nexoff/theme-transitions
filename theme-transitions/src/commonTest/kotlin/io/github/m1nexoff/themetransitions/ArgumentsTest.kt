package io.github.m1nexoff.themetransitions

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFailsWith

// A wrong argument fails where it is written, not mid-animation.
class ArgumentsTest {

    @Test
    fun invalidArgumentsAreRejected() {
        assertFailsWith<IllegalArgumentException> { Transitions.tear(durationMillis = -1) }
        assertFailsWith<IllegalArgumentException> { Effects.sparks(Color.Red, count = -1) }
        assertFailsWith<IllegalArgumentException> { Effects.lightning(Color.Red, bolts = -1) }
        assertFailsWith<IllegalArgumentException> { Effects.dim(maxAlpha = 1.5f) }
        assertFailsWith<IllegalArgumentException> { Effects.dim(maxAlpha = -0.1f) }
        assertFailsWith<IllegalArgumentException> { Effects.ring(Color.Red, width = (-1).dp) }
        assertFailsWith<IllegalArgumentException> { Effects.shake(strength = (-1).dp) }
    }
}

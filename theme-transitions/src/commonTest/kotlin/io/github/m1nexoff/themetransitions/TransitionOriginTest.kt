package io.github.m1nexoff.themetransitions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TransitionOriginTest {

    // Equal fractions must hash alike, or a HashSet or remember key sees two origins where there is one.
    @Test
    fun equalFractionsHashAlike() {
        val fractions = listOf(0f, -0f, 0.5f, 1.2f, Float.NaN)
        for (x in fractions) {
            for (y in fractions) {
                val a = TransitionOrigin.Fraction(x, y)
                for (x2 in fractions) {
                    for (y2 in fractions) {
                        val b = TransitionOrigin.Fraction(x2, y2)
                        if (a == b) assertEquals(a.hashCode(), b.hashCode(), "$a and $b are equal")
                    }
                }
            }
        }
        assertEquals(TransitionOrigin.Fraction(0.5f, 1f), TransitionOrigin.Fraction(0.5f, 1f))
        assertNotEquals(TransitionOrigin.Fraction(0.5f, 1f), TransitionOrigin.Fraction(1f, 0.5f))
    }
}

package io.github.m1nexoff.themetransitions

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/**
 * Where a radial transition — [Transitions.circle], [Transitions.flood], [Transitions.tear] — opens
 * from.
 */
@Immutable
public sealed interface TransitionOrigin {

    /**
     * Where the user's finger last went down inside [AnimatedTheme], usually on the toggle they just
     * tapped. A switch with no recent touch behind it, such as the system going dark, opens from the
     * centre.
     */
    public data object Touch : TransitionOrigin

    /** The centre of [AnimatedTheme]. */
    public data object Center : TransitionOrigin

    /**
     * A fixed point, as fractions of [AnimatedTheme]'s width and height: `Fraction(1f, 0f)` is the
     * top-right corner. Values outside `0..1` lie outside the screen, and the transition sweeps in from
     * there.
     */
    public class Fraction(public val x: Float, public val y: Float) : TransitionOrigin {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Fraction && x == other.x && y == other.y)

        override fun hashCode(): Int = 31 * x.hashCode() + y.hashCode()

        override fun toString(): String = "Fraction(x=$x, y=$y)"
    }
}

internal fun TransitionOrigin.resolve(size: IntSize, touch: Offset?): Offset {
    val center = Offset(size.width / 2f, size.height / 2f)
    return when (this) {
        TransitionOrigin.Touch -> touch ?: center
        TransitionOrigin.Center -> center
        is TransitionOrigin.Fraction -> Offset(size.width * x, size.height * y)
    }
}

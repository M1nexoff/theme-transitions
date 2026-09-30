package io.github.m1nexoff.themetransitions

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The colours of a rift opening into one theme. [ThemeRift] asks for them once per switch, for the
 * theme being switched *to*.
 *
 * @property background the incoming theme's background. The screen shakes while the rift opens, and
 * this fills the thin strips the shake uncovers at its edges.
 * @property glow the rattling frame around the screen's edge. The incoming theme's content colour
 * reads best: light on the way to a dark theme, dark on the way to a light one.
 * @property lightning the bolts, the sparks and the rim of the hole.
 * @property scrim dims the whole screen while the rift opens, so the lightning reads on a light
 * theme too.
 */
@Immutable
public class RiftColors(
    public val background: Color,
    public val glow: Color,
    public val lightning: Color = DefaultLightning,
    public val scrim: Color = DefaultScrim,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RiftColors) return false
        return background == other.background &&
            glow == other.glow &&
            lightning == other.lightning &&
            scrim == other.scrim
    }

    override fun hashCode(): Int {
        var result = background.hashCode()
        result = 31 * result + glow.hashCode()
        result = 31 * result + lightning.hashCode()
        result = 31 * result + scrim.hashCode()
        return result
    }

    override fun toString(): String =
        "RiftColors(background=$background, glow=$glow, lightning=$lightning, scrim=$scrim)"

    public companion object {
        /** A hot red that reads on light and dark themes alike. */
        public val DefaultLightning: Color = Color(0xFFFF1744)

        public val DefaultScrim: Color = Color.Black
    }
}

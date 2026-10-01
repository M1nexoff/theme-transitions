package io.github.m1nexoff.themetransitions.demo

import kotlinx.coroutines.delay

// The pause on each theme before the next switch, so the eye can settle.
const val SHOWCASE_PAUSE_MILLIS = 450L

// One variant of a transition: what the playground is set to while it plays.
class ShowcaseItem(val label: String, val playground: Playground) {
    val durationMillis: Int get() = playground.durationMillis
}

private fun item(label: String, reveal: Reveal, vararg effects: Effect, angle: Float = 90f, tileSize: Float = 12f) =
    ShowcaseItem(
        label,
        effects.fold(Playground().withReveal(reveal).copy(angleDegrees = angle, tileSizeDp = tileSize)) { p, e -> p.toggle(e) },
    )

// Every transition, and the variants that show what its options do. Each plays through all three
// themes.
val Showcase: List<ShowcaseItem> = listOf(
    item("Circle", Reveal.Circle),
    item("Circle + ring", Reveal.Circle, Effect.Ring),
    item("Circle + ring, sparks, lightning, dim", Reveal.Circle, Effect.Ring, Effect.Sparks, Effect.Lightning, Effect.Dim),
    item("Flood", Reveal.Flood),
    item("Flood + ring, sparks", Reveal.Flood, Effect.Ring, Effect.Sparks),
    item("Wipe", Reveal.Wipe),
    item("Wipe 45° + ring", Reveal.Wipe, Effect.Ring, angle = 45f),
    item("Wipe 180° + ring, shake", Reveal.Wipe, Effect.Ring, Effect.Shake, angle = 180f),
    item("Fade", Reveal.Fade),
    item("Fade + dim, border glow", Reveal.Fade, Effect.Dim, Effect.Glow),
    item("Dissolve", Reveal.Dissolve),
    item("Dissolve, 32 dp tiles", Reveal.Dissolve, tileSize = 32f),
    item("Tear", Reveal.Tear),
    item("Tear + ring, shake", Reveal.Tear, Effect.Ring, Effect.Shake),
    item("Rift", Reveal.Rift),
)

// Plays every item through the three themes in turn: [show] sets the playground, [switch] moves to the
// next theme.
suspend fun playShowcase(show: (ShowcaseItem?) -> Unit, switch: () -> Unit) {
    try {
        for (item in Showcase) {
            show(item)
            repeat(DemoTheme.entries.size) {
                delay(SHOWCASE_PAUSE_MILLIS)
                switch()
                delay(item.durationMillis.toLong())
            }
        }
        delay(SHOWCASE_PAUSE_MILLIS)
    } finally {
        show(null)
    }
}

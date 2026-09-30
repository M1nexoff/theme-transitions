# Theme Transitions — design

Date: 2026-10-01. Status: approved in conversation; the author delegated every remaining decision
("choose yourself best, don't stop until you make it").

## Goal

Turn Theme Rift, a library with one hard-wired effect, into **Theme Transitions**: a Compose
Multiplatform library of interchangeable, customizable theme-switch animations that is easy to use
and fast on budget phones.

What the author said:
- Ship circle reveal, colour flood, wipe/slide, fade/dissolve, alongside the existing rift.
- Fully customizable through mix-and-match layers, but no public "write your own transition" API:
  whoever needs that forks. The layers must cover most cases.
- Rename to a generic name.
- Every effect has to be optimized, measured on real phones (a Redmi Note 9 now, a second
  mid-range phone later), with older Android versions checked on emulators.

Assumptions (not stated, chosen as defaults):
- 0.1.0 of Theme Rift was never published (no git remote, no tags), so the rename breaks nobody
  and the new library ships as its own 0.1.0. No deprecated compatibility shims.
- Android, iOS and desktop stay supported, minSdk 23, as before.

## Success criteria

1. `AnimatedTheme(targetState) { shown -> ... }` works with no other argument (circle reveal).
2. Every reveal and effect in this document exists, is documented, and is covered by pixel tests.
3. Incompatible combinations (an edge effect on a reveal without an edge) do not compile.
4. On the Redmi Note 9 (Helio G85, Mali-G52 MC2, Android 12, 1080×2340, 60 Hz), in a release build,
   `circle`, `flood`, `wipe`, `fade` and `dissolve` hold a 90th-percentile frame time of at most
   16 ms over a scripted bench (`dumpsys gfxinfo`). `tear` and `rift` are measured, optimized as far
   as they go, and their numbers published in the README.
5. On API 23 and API 28 emulators every reveal draws correctly (checked by screenshots mid-way and
   at the end).
6. The rift preset still shows the new theme inside the hole, the old outside, and nothing after.

## Names

| | |
|---|---|
| Maven Central | `io.github.m1nexoff:theme-transitions` |
| JitPack | `com.github.M1nexoff.theme-transitions:theme-transitions` |
| Package | `io.github.m1nexoff.themetransitions` |
| Gradle module | `:theme-transitions` |
| Composable | `AnimatedTheme` |
| Factories | `Transitions`, `Effects` |

## Public API

```kotlin
@Composable
public fun <T> AnimatedTheme(
    targetState: T,
    modifier: Modifier = Modifier,
    transition: ThemeTransition = Transitions.circle(),
    content: @Composable (T) -> Unit,
)
```

`transition` is read at the moment of each switch. Colours that depend on the incoming theme are
therefore built from the caller's own state, `Transitions.flood(color = theme.primary)`, with no
extra API. The rule from Theme Rift stands: `content` builds its theme from the `shown` parameter,
never from `targetState`.

### Reveals

Exactly one per transition. Every factory takes `durationMillis: Int`, `easing: Easing` and
`effects`. Radial reveals also take `origin: TransitionOrigin` (`Touch` — the last pointer-down,
falling back to the centre — `Center`, or `Fraction(x, y)`).

| Factory | Behaviour | Own parameters | Defaults | Effects accepted |
|---|---|---|---|---|
| `circle()` | The new theme grows in a circle from the origin | — | 650 ms, FastOutSlowIn | `RadialEffect` |
| `flood(color)` | A circle of `color` covers the screen (first half), then shrinks back into the origin over the new theme (second half) | `color` | 900 ms, FastOutSlowIn | `RadialEffect` |
| `wipe()` | A straight edge sweeps across the screen | `angleDegrees` (0 = towards the right, 90 = downwards, clockwise) | 600 ms, FastOutSlowIn, 90° | `EdgeEffect` |
| `fade()` | Crossfade | — | 450 ms, FastOutSlowIn | `UniversalEffect` |
| `dissolve()` | The old theme breaks into tiles that vanish in random order | `tileSize: Dp` | 700 ms, Linear, 12 dp | `UniversalEffect` |
| `tear()` | The rift's ragged, wobbling hole and nothing else | — | 2000 ms, LinearOutSlowIn | `RadialEffect` |
| `rift(glow, backdrop, lightning, scrim)` | Preset: `tear` + dim + flickering ring + sparks + lightning + shake + border glow | colours | Theme Rift's look and timing | — |
| `none()` | Switches at once | — | — | — |

### Effects

| Factory | Type | What it draws |
|---|---|---|
| `ring(color, width = 3.dp, flicker = false)` | `EdgeEffect` | A line along the reveal's edge |
| `sparks(color, count = 100)` | `RadialEffect` | Sparks spiralling in towards the edge |
| `lightning(color, bolts = 12)` | `RadialEffect` | Bolts crackling outward off the edge, with branches |
| `shake(strength = 3.dp, backdrop = Color.Black)` | `UniversalEffect` | Shakes the screen; `backdrop` fills the strips it uncovers |
| `dim(color = Color.Black, maxAlpha = 0.7f)` | `UniversalEffect` | Dims the screen while the transition opens |
| `borderGlow(color)` | `UniversalEffect` | A rattling glow round the screen's edge |

The compatibility types nest the other way round from what one might expect, so that `List` variance
does the checking: `UniversalEffect : EdgeEffect : RadialEffect`. A radial reveal takes
`List<RadialEffect>` and so accepts every effect; `wipe` takes `List<EdgeEffect>` and rejects sparks
and lightning; `fade` and `dissolve` take `List<UniversalEffect>`. All three are sealed, so users can
only pass the library's own effects.

Layering is fixed by the effect's kind, not list order: themes, dim, edge effects (ring, sparks,
lightning), border glow. Shake moves everything but the backdrop strips.

`ThemeTransition`, `TransitionOrigin` and the effect types are opaque: public types with internal
contents. `TransitionOrigin.Fraction` is a plain class with `equals`/`hashCode`, not a data class.

Deliberately not included: a public custom-transition API, a `(from, to) -> ThemeTransition`
lambda, feathered wipe edges (an offscreen layer per frame), a modifier to mark a reveal origin.

## Engine

One running transition at a time, plus retiring ones whose effects are fading out.

Per switch:
1. `snapshotFlow { target }.conflate()` delivers the new state. Same as `shown`: nothing. Animations
   off (`MotionDurationScale` 0), `none()`, or a not-yet-laid-out box: every run is dropped and
   `shown` changes at once.
2. A new run starts. Its effects draw from this frame on, over the still-live old theme. A run that
   was already going retires: its reveal stops moving, and its effects fade out over 150 ms. At most
   two retiring runs are kept.
3. One frame later the screen's *theme composite* is frozen to a bitmap: `themes.toImageBitmap()`.
4. `shown` becomes the new state and the reveal starts its clock.
5. When the reveal reports that the old frame can no longer show (`covered`), the bitmap and its
   painter are released; effects carry on to the end.
6. The run ends when its reveal does. A size change mid-run ends every run at once, since the frozen
   frame no longer fits.

The theme composite is a `GraphicsLayer` (`themes`) recorded every frame: the live layer (`frame`,
the content alone) then the current run's old frame. Effects are drawn after it, outside it. So a
switch made mid-transition freezes exactly what is visibly theme — the live theme plus whatever the
running reveal still shows of the old one — without any lightning or glow. This "bake and restart"
replaces Theme Rift's three stacked rifts: one bitmap instead of three, and any reveal can follow any
other.

The draw code reads the current run and the retiring list into locals once per frame. The clock is a
manual `withFrameNanos` loop scaled by `MotionDurationScale`.

Modifier chain on the root box (outermost first):
1. `pointerInput`: remembers the last pointer-down, in the Initial pass, without consuming it.
2. `drawWithContent`: the shake backdrop strips, then the content.
3. `graphicsLayer`: the shake translation (whole pixels).
4. `drawWithContent`: records `frame` (content) and `themes` (composite), draws `themes`, then the
   effects by layer.
5. `graphicsLayer()`: keeps the content's display list from being re-recorded every frame.

## Drawing techniques

All `Path`, `Paint`, `Stroke`, brush and array objects are created once per run and reused.

| Reveal | Old frame | Live frame |
|---|---|---|
| circle | A circle *stroke* filled with an `ImageShader` of the old frame: inner radius = the hole, width reaching past the farthest corner. An analytic, anti-aliased circle draw; no clip, no mask. | Clipped to the hole's bounding circle |
| flood | Phase 1: as circle, under a solid circle of `color`. Phase 2: none (bitmap released). | Phase 1: not drawn (covered). Phase 2: full, under a shrinking `color` circle. |
| wipe | The old side of the line as a convex polygon (at most five points) filled with an `ImageShader` brush. | Clipped to a rect when the angle is axis-aligned, else full |
| fade | `drawImage` with alpha `1 − progress` | Full |
| dissolve | Tiles of whole pixels, each with a random vanish time, **sorted once** by that time, so the tiles still showing are always a suffix of one prebuilt triangle array. Android 10+: one `drawVertices` call with an offset into that array — no per-frame geometry work. Android 6–9 and Skia (iOS, desktop): one `drawImage(src, dst)` per remaining tile, the tile size raised so there are at most 600 tiles on Android 6–9. | Full |
| tear | Android 10+: `OutsideMesh` through `drawVertices`, then a hair-thin anti-aliased stroke along the edge painted with the old frame itself, which hides the mesh's aliasing. Elsewhere: a difference clip. | Clipped to the hole's bounding circle |

Edges for effects: each reveal draws its own outline the cheapest way — circle and flood with a
circle stroke, tear with `drawRawPoints` (never a stroked `Path`), wipe with one line. Radial reveals
also expose their centre, nominal radius and the edge distance at an angle, which sparks and
lightning read. Lightning bolts stay stroked paths unless the bench shows them over budget, in which
case they become raw-point polylines with round caps.

Density: effects' art constants stay in the reference pixels Theme Rift was tuned in (420 dpi,
`ref()`); public sizes are `Dp`.

## Code layout

`theme-transitions/src/commonMain/kotlin/io/github/m1nexoff/themetransitions/`:
- `AnimatedTheme.kt` — the composable and its modifier chain.
- `ThemeTransition.kt` — `ThemeTransition`, `Transitions`.
- `Effects.kt` — the sealed effect types, their internal spec classes, `Effects`.
- `TransitionOrigin.kt`.
- `engine/` — `Run`, `RunClock`, the frame state handed to reveals and effects.
- `reveal/` — one file per reveal, plus the `Reveal`/`RevealRun` and `Edge`/`RadialEdge` contracts.
- `effect/` — one file per effect renderer.
- `geometry/` — `OutsideMesh`, `TileGrid`, `WipePolygon`.
- `platform/` — `expect` mesh painters (`OutsideMeshPainter`, `TileMeshPainter`).

## Testing

- `commonTest`: geometry (`OutsideMesh` as today, `TileGrid` ordering and suffix coverage,
  `WipePolygon` coverage at any angle and progress), the run clock.
- `desktopTest` (Skia, off screen, deterministic `Dispatchers.Unconfined` — Theme Rift's tests
  raced on `Dispatchers.Default`): for every reveal, new theme where it has opened and old theme
  where it has not, mid-way; only the new theme once it ends; animations off; `none()`; a rapid
  second and third switch ending on the right theme with nothing left behind; the rift preset's
  three Theme Rift tests.
- Device bench: the demo takes `--es bench <reveal>` (Android) and runs ten switches; a script
  resets `dumpsys gfxinfo`, waits for the run's log line, and reads the 90th/95th percentiles and
  janky-frame share per reveal. Release build, minified, debug-signed.
- Emulators API 23 and 28: screenshots mid-transition and at the end for every reveal.

## Demo

A playground: three themes; a chip per reveal; toggles for each effect the chosen reveal accepts;
sliders for duration, wipe angle and tile size; "switch three times, fast". Bench mode as above.

## Docs and release

- README rewritten around `AnimatedTheme`, a reveal/effect reference, performance numbers per
  reveal and device, the three rules, platforms.
- `docs/preview.png` regenerated: one row per reveal, five moments each, rendered on desktop by a
  `renderPreviews` task in the desktop demo.
- CHANGELOG: one 0.1.0 entry for Theme Transitions. PUBLISHING.md, `jitpack.yml`, POM updated.
- The local folder stays `theme-rift`; the GitHub repository to create is `theme-transitions`.

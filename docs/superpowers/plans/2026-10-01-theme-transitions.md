# Theme Transitions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the single-effect Theme Rift library into Theme Transitions: `AnimatedTheme` with
interchangeable reveals (circle, flood, wipe, fade, dissolve, tear, rift preset, none) and
mix-and-match effects, fast on budget Android phones.

**Architecture:** One engine (`TransitionEngine`) owns switching, freezing, "bake and restart" and the
clock. A transition is one `RevealRun` (draws the old frame its cheapest way and exposes an edge) plus
`EffectRun`s drawn in fixed layers. Platform code only supplies mesh painters.

**Tech Stack:** Kotlin 2.4 (API/language 2.2), Compose Multiplatform 1.11.1, AGP 9, Skia off-screen
tests (`ImageComposeScene`), `dumpsys gfxinfo` on device.

**Spec:** `docs/superpowers/specs/2026-10-01-theme-transitions-design.md`

**Execution note:** the author delegated all decisions and asked for uninterrupted work. Native
execution (one implementer, this session) with a whole-branch review at the end. Method bodies are
specified by behaviour and by the contracts below rather than pasted in full; contracts and tests are
exact.

## Global Constraints

- Package `io.github.m1nexoff.themetransitions`; module `:theme-transitions`; artifact
  `io.github.m1nexoff:theme-transitions`, version 0.1.0.
- `explicitApi()`; Kotlin API/language 2.2, `coreLibrariesVersion = "2.2.20"`; JVM target 11.
- Targets: Android minSdk 23, iosArm64, iosSimulatorArm64, desktop JVM.
- No per-frame allocation in draw code: `Path`, `Paint`, `Stroke`, brushes, arrays created per run.
- Never stroke a `Path` for an edge; never clip the old frame to a non-oval, non-rect path on
  Android 10+ (mesh) — the clip fallback is for Android 6–9, iOS and desktop only.
- Public types with internal contents; no data classes in the public API.
- Commits: author M1nexoff only, no AI attribution lines, 1–2 hours apart in author/committer dates.
- Comments in the existing voice: explain *why*, in full sentences, sparingly.

## Review Focus

1. A custom easing that overshoots or runs backwards (`CubicBezierEasing(0.3f, -0.5f, 0.7f, 1.5f)`)
   — no crash, radii never negative, dissolve finds the remaining tiles by binary search, and the run
   still ends on the new theme. Test in Task 4 (`overshootingEasingStillEndsClean`).
2. An origin outside the box (`TransitionOrigin.Fraction(1.2f, -0.1f)`) — the circle still covers
   the whole screen by its end. Test in Task 4 (`originOutsideTheBoxStillCovers`).
3. `durationMillis = 0` behaves like `none()`; a negative duration, a non-positive tile size, a
   negative count fail at construction with a clear message. Test in Task 2
   (`zeroDurationSwitchesAtOnce`) and Task 3 (`invalidArgumentsAreRejected`).
4. The box resized mid-transition (rotation, desktop window) — every run ends at once; no stretched
   frozen frame. Test in Task 2 (`resizeEndsTheTransition`).
5. Switching there and back within a frame or two (A→B→A) — ends on A with nothing left behind.
   Test in Task 2 (`switchingBackAtOnceEndsOnTheFirstTheme`).

---

### Task 0: Spec and plan

- [ ] Commit `docs/superpowers/specs/...` and `docs/superpowers/plans/...`:
  `git commit -m "Design and plan for Theme Transitions"`.

### Task 1: Rename to Theme Transitions; deterministic test harness

**Files:**
- Move: `theme-rift/` → `theme-transitions/`; every `io/github/m1nexoff/themerift` source dir →
  `io/github/m1nexoff/themetransitions` (library and demo).
- Modify: `settings.gradle.kts` (rootProject `theme-transitions`, include `:theme-transitions`),
  `theme-transitions/build.gradle.kts` (namespace), `demo/shared/build.gradle.kts` (namespace,
  dependency, framework `ThemeTransitionsDemo`), `demo/androidApp/build.gradle.kts` (namespace,
  applicationId `io.github.m1nexoff.themetransitions.demo`), `demo/desktopApp/build.gradle.kts`
  (mainClass), `demo/androidApp/src/main/AndroidManifest.xml` (label), iOS `Config.xcconfig`,
  `project.pbxproj`, `ContentView.swift` (`ThemeTransitionsDemo`), `gradle.properties` (POM),
  `jitpack.yml` (`:theme-transitions:publishToMavenLocal`).
- Modify: `ThemeRiftRenderTest.kt` — scene context `Dispatchers.Unconfined` (and
  `Dispatchers.Unconfined + AnimationsOff`), the cause of the baseline's
  `IndexOutOfBoundsException` race.

- [ ] Move and rename; `grep -ri "themerift\|theme-rift\|ThemeRiftDemo"` returns only `ThemeRift`
  the composable, `README.md`, `CHANGELOG.md`, `PUBLISHING.md` and `docs/`.
- [ ] Run `./gradlew :theme-transitions:desktopTest` — expected: 8 tests pass, three times in a row.
- [ ] Run `./gradlew :demo:androidApp:assembleDebug :demo:desktopApp:compileKotlinJvm` — builds.
- [ ] Commit "Rename to Theme Transitions".

### Task 2: Engine, public API, tear reveal and the rift's effects

**Files:**
- Create `theme-transitions/src/commonMain/kotlin/io/github/m1nexoff/themetransitions/`:
  `AnimatedTheme.kt`, `ThemeTransition.kt`, `Effects.kt`, `TransitionOrigin.kt`,
  `engine/TransitionEngine.kt`, `engine/Run.kt`, `engine/Drawing.kt`,
  `reveal/Reveal.kt`, `reveal/TearReveal.kt`, `effect/EffectRun.kt`, `effect/Ring.kt`,
  `effect/Sparks.kt`, `effect/Lightning.kt`, `effect/Shake.kt`, `effect/Dim.kt`,
  `effect/BorderGlow.kt`, `geometry/OutsideMesh.kt` (moved), `geometry/Reference.kt` (`ref()`),
  `platform/MeshPainters.kt` (expect) + `androidMain`, `desktopMain`, `iosMain` actuals.
- Delete: `ThemeRift.kt`, `RiftColors.kt`, `FrozenFramePainter*.kt`.
- Tests: `desktopTest/.../RenderTestScene.kt` (harness), `desktopTest/.../TearAndRiftRenderTest.kt`,
  `desktopTest/.../EngineRenderTest.kt`; `commonTest/.../geometry/OutsideMeshTest.kt` (moved).

**Interfaces (Produces):**

```kotlin
// AnimatedTheme.kt
@Composable public fun <T> AnimatedTheme(
    targetState: T, modifier: Modifier = Modifier,
    transition: ThemeTransition = Transitions.circle(), content: @Composable (T) -> Unit)

// ThemeTransition.kt
@Immutable public class ThemeTransition internal constructor(
    internal val reveal: RevealSpec?,            // null: switch at once
    internal val effects: List<RadialEffect>)
public object Transitions { /* tear, rift, none here; the rest in Task 4 */ 
    public val RiftLightning: Color /* 0xFFFF1744 */ }

// TransitionOrigin.kt
@Immutable public sealed interface TransitionOrigin {
    public data object Touch : TransitionOrigin
    public data object Center : TransitionOrigin
    public class Fraction(public val x: Float, public val y: Float) : TransitionOrigin }

// Effects.kt
public sealed interface RadialEffect
public sealed interface EdgeEffect : RadialEffect
public sealed interface UniversalEffect : EdgeEffect
internal abstract class EffectSpec { abstract fun start(setup: RunSetup): EffectRun }
internal val RadialEffect.spec: EffectSpec get() = this as EffectSpec
public object Effects { ring, sparks, lightning, shake, dim, borderGlow }

// reveal/Reveal.kt
internal abstract class RevealSpec(val durationMillis: Int, val easing: Easing) {
    abstract fun start(setup: RunSetup): RevealRun }
internal class RunSetup(val size: IntSize, val origin: Offset, val density: Density)
internal abstract class RevealRun {
    open val edge: Edge? get() = null
    open val radial: RadialEdge? get() = null
    abstract val covered: Boolean                       // the old frame can no longer show
    abstract fun update(linear: Float, progress: Float, time: Float)
    abstract fun freeze(image: ImageBitmap)             // creates shaders/painters
    abstract fun release()                              // drops them
    abstract fun DrawScope.drawLive(live: GraphicsLayer)
    abstract fun DrawScope.drawOld()
}
internal interface Edge { val visible: Boolean; fun drawOutline(canvas: Canvas, paint: Paint) }
internal interface RadialEdge : Edge {
    val center: Offset; val radius: Float; fun distanceAt(angle: Float): Float }

// effect/EffectRun.kt
internal enum class EffectLayer { Dim, Edge, Frame }
internal abstract class EffectRun(val layer: EffectLayer) {
    abstract fun DrawScope.draw(run: Run, alpha: Float) }
internal class ShakeRun(...) : EffectRun(EffectLayer.Dim) // draws nothing; engine reads offset()

// engine/Run.kt
internal class Run(transition: ThemeTransition, val setup: RunSetup) {
    val reveal: RevealRun; val effects: List<EffectRun>; val shake: ShakeRun?
    var clockMillis: Float            // snapshot state: redraws
    val progress: Float; val linear: Float; val time: Float
    val frozen: Boolean; val hasOld: Boolean   // frozen && !covered && !released
    var retiredAtMillis: Float        // NaN while current
    val alpha: Float                  // 1, or the 150 ms retire fade
    val finished: Boolean
    fun freeze(image: ImageBitmap); fun release()
}
// engine/TransitionEngine.kt
internal class TransitionEngine {
    val current: Run?; val retiring: List<Run>; val active: Boolean
    fun begin(run: Run)   // retires current, keeps at most two retiring
    fun clear()
    fun tick(frameNanos: Long, scale: Float)
    fun frozen(run: Run, image: ImageBitmap) // freezes run, releases retiring runs' old frames
}
```

**Behaviour:** as the spec's Engine section. `time = clockMillis / 2000f * 2π`. Intensity envelope
(shake, border glow): `p/0.3` up to 1, hold until 0.7, `(1-p)/0.3` down. Dim: `maxAlpha *
min(clock/150, 1) * max(0, 1 - p/0.85)`. Ring with flicker: alpha `(0.7 + sin(8t)*0.3) * (1 -
0.4p)`. Sparks off at `p ≥ 0.9`, lightning off at `p ≥ 0.75`, both from Theme Rift unchanged with the
radius read from `RadialEdge.radius` and the bolt start from `distanceAt(angle) * 0.99f`. Rift preset:
`tear(2000, LinearOutSlowInEasing, effects = [dim(scrim, 0.7), ring(lightning, 6 ref px, flicker),
sparks(lightning), lightning(lightning), shake(8 ref px, backdrop), borderGlow(glow)])`.

**Tests** (`desktopTest`, harness: 400×800 at density 2.625, `Dispatchers.Unconfined`, LIGHT
`0xFFFAFAFC`, DARK `0xFF15151F`, DUSK `0xFF2A1B3D`; `tap(at)`; `advance(ms)`; `frame()`;
`samples(where)`; `assertMostly(share ≥ 0.9)`):

- [ ] `riftShowsTheNewThemeInsideTheHoleAndTheOldOneOutside` — Theme Rift's test with
  `Transitions.rift(...)`: 300 ms after tap at (100, 700), within 150 px of it dark, beyond 450 px
  light (60 px glow margin excluded).
- [ ] `riftLeavesNothingBehindOnceItEnds` — after 2600 ms every sample DARK.
- [ ] `tearAloneShowsTheHole` — `Transitions.tear()`, same regions as the rift test, no margin.
- [ ] `switchesAtOnceWithAnimationsOff`, `noneSwitchesAtOnce`, `zeroDurationSwitchesAtOnce`
  (`Transitions.tear(durationMillis = 0)`) — two frames after the switch every sample DARK.
- [ ] `rapidSwitchesEndOnTheLastTheme` — tap (100,700), DARK; +250 ms tap (300,100), DUSK;
  +250 ms: within 80 px of (300,100) DUSK, within 80 px of (100,700) DARK, at (380, 400) LIGHT;
  +2600 ms: every sample DUSK.
- [ ] `switchingBackAtOnceEndsOnTheFirstTheme` — DARK then, one frame later, LIGHT; +2600 ms every
  sample LIGHT.
- [ ] `resizeEndsTheTransition` — host box size in state; tap, DARK, +200 ms, resize to 360×700,
  +2 frames: every sample inside the box DARK.
- [ ] Run tests; then implement until they pass; `OutsideMeshTest` (moved) passes.
- [ ] Demo compiles against `AnimatedTheme(transition = Transitions.rift(...))`.
- [ ] Commit "Split the transition engine from the rift".

### Task 3: Geometry — wipe polygon and tile grid

**Files:** `geometry/WipeGeometry.kt`, `geometry/TileGrid.kt`; tests
`commonTest/.../geometry/WipeGeometryTest.kt`, `TileGridTest.kt`, `commonTest/.../ArgumentsTest.kt`.

**Interfaces (Produces):**

```kotlin
internal class WipeGeometry(width: Float, height: Float, angleDegrees: Float) {
    val axisAligned: Boolean
    fun update(progress: Float)          // 0: nothing swept, 1: everything
    val oldPath: Path                    // convex, ≤ 5 points; empty when covered
    val oldPolygon: FloatArray; val oldPoints: Int   // the same points, for tests
    val liveClip: Rect                   // valid when axisAligned
    val edgeStart: Offset; val edgeEnd: Offset; val edgeVisible: Boolean
    val covered: Boolean
}
internal class TileGrid(width: Int, height: Int, tilePx: Int, maxTiles: Int, random: Random) {
    val tilePx: Int                      // raised so count ≤ maxTiles
    val count: Int
    val thresholds: FloatArray           // ascending
    val left: IntArray; val top: IntArray; val right: IntArray; val bottom: IntArray // sorted with thresholds
    val triangles: FloatArray            // 12 floats per tile, same order
    fun firstRemaining(progress: Float): Int   // binary search: first index with threshold > progress
}
```

- [ ] `WipeGeometryTest`: for angles `0, 30, 90, 135, 180, 225, 270, 333, -45, 720` and progress
  `0, 0.25, 0.5, 1`, on a 300×500 rect sampled every 7 px: a point is inside `oldPolygon` exactly
  when `dot(p, dir) > sweep(progress)`; area at 0 equals the rect, at 1 is 0; `axisAligned` only for
  multiples of 90.
- [ ] `TileGridTest`: at progress 0 the tiles cover every pixel of 301×517 exactly once; `count ≤
  maxTiles` with `tilePx` raised (1080×2340, 4 px, cap 600); thresholds ascending;
  `firstRemaining` at 0 is 0, at 1 is `count`, at 1.7 and −0.4 clamps.
- [ ] `ArgumentsTest` (`invalidArgumentsAreRejected`): negative duration, `tileSize = 0.dp`,
  `sparks(count = -1)`, `lightning(bolts = -1)`, `dim(maxAlpha = 1.5f)` throw
  `IllegalArgumentException`.
- [ ] Run to fail, implement, run to pass. Commit "Wipe and tile geometry".

### Task 4: Circle, flood, wipe, fade and dissolve

**Files:** `reveal/CircleReveal.kt`, `reveal/FloodReveal.kt`, `reveal/WipeReveal.kt`,
`reveal/FadeReveal.kt`, `reveal/DissolveReveal.kt`; `platform/MeshPainters.kt` gains
`createTilePainter`; factories in `ThemeTransition.kt`. Test `desktopTest/.../RevealRenderTest.kt`.

**Interfaces (Produces):** `Transitions.circle/flood/wipe/fade/dissolve` with the spec's signatures
and defaults; `internal expect fun createTilePainter(image: ImageBitmap, grid: TileGrid):
TilePainter?`; `internal expect val perTileDrawLimit: Int` (Android 600, Skia 4000).

- [ ] `circleOpensFromTheTouch` — tap (100,700), 650 ms circle, at 40 % of the duration: points
  closer than 0.8·r dark, farther than 1.2·r light, r = reach·FastOutSlowIn(t).
- [ ] `floodCoversThenUncovers` — `flood(RED)`; at 25 %: within 50 px of the tap RED, at the far
  corner LIGHT; at 75 %: within 30 px RED, far corner DARK.
- [ ] `wipeDownSweepsFromTheTop` — at 50 %: y < 0.3·h DARK, y > 0.7·h LIGHT.
- [ ] `diagonalWipe` — 45°: near (0,0) DARK, near (w,h) LIGHT at 50 %.
- [ ] `fadeBlends` — at 50 % every sample's luminance strictly between DARK's and LIGHT's.
- [ ] `dissolveMixesTiles` — at 50 % every sample is LIGHT or DARK, DARK share within
  progress ± 0.15.
- [ ] `everyRevealEndsClean` — each reveal, after duration + 200 ms every sample DARK.
- [ ] `overshootingEasingStillEndsClean` — circle and dissolve with
  `CubicBezierEasing(0.3f, -0.5f, 0.7f, 1.5f)` end clean, no exception.
- [ ] `originOutsideTheBoxStillCovers` — circle with `Fraction(1.2f, -0.1f)` ends clean; at 50 %
  the corner nearest that origin is DARK.
- [ ] `effectsOnEveryReveal` — circle + ring/sparks/lightning/shake/dim/glow, wipe + ring, fade +
  shake: no exception, end clean.
- [ ] Run to fail, implement, run to pass. Commit "Circle, flood, wipe, fade and dissolve".

### Task 5: Demo playground and bench mode

**Files:** `demo/shared/.../DemoApp.kt`, `DemoTheme.kt`, new `Playground.kt` (reveal chips, effect
toggles limited to what the reveal accepts, duration / angle / tile sliders), `Bench.kt` (runs ten
switches of a named reveal, logs `ThemeTransitionsBench done <name>`), Android `MainActivity`
reads `bench` extra, desktop `Main.kt` reads `--bench=<name>`; `scripts/bench.sh`.

- [ ] `bench.sh <serial> <reveal...>`: `am start -W -n
  io.github.m1nexoff.themetransitions.demo/.MainActivity --es bench <reveal>`, `dumpsys gfxinfo
  <pkg> reset`, wait for the log line, `dumpsys gfxinfo <pkg>`, print frames, janky %, 50/90/95/99th.
- [ ] Desktop and Android release builds install and run. Commit "Demo playground and bench".

### Task 6: Measure and optimize

- [ ] `./gradlew :demo:androidApp:installRelease`; `scripts/bench.sh 1090aac20408 circle flood
  wipe wipe45 fade dissolve tear rift`; record the table.
- [ ] Any reveal over 16 ms p90 among circle/flood/wipe/fade/dissolve: profile
  (`gfxinfo framestats`, Perfetto if needed), fix, re-measure. Tear/rift: same, best effort; the
  lightning raw-point fallback is the first candidate.
- [ ] Emulators API 23 and 28 (create AVDs, debug build): screenshots of each reveal mid-way and at
  the end via `adb exec-out screencap -p`; inspect.
- [ ] Commit each optimization separately with the before/after numbers in the message.

### Task 7: Docs, previews, release metadata

**Files:** `README.md`, `CHANGELOG.md`, `PUBLISHING.md`, `docs/preview.png`,
`demo/desktopApp/src/jvmMain/.../RenderPreviews.kt` + `renderPreviews` Gradle task.

- [ ] `./gradlew :demo:desktopApp:renderPreviews` writes `docs/preview.png`: one row per reveal,
  five moments each.
- [ ] README: install, `AnimatedTheme` quick start, reveal table, effect table and compatibility,
  per-theme colours, rapid switches, animations off, performance table (device numbers from
  Task 6), how it works, platforms, demo, build, credits, licence.
- [ ] CHANGELOG 0.1.0 entry; PUBLISHING coordinates. Commit "Docs for Theme Transitions".

### Task 8: Whole-branch review

- [ ] Dispatch one fresh reviewer (most capable model) over the branch against the spec; fix what it
  confirms; re-run all tests; final commit.

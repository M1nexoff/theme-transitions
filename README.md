# Theme Transitions

[![JitPack](https://jitpack.io/v/M1nexoff/theme-transitions.svg)](https://jitpack.io/#M1nexoff/theme-transitions)

Theme switch animations for Compose Multiplatform. Wrap your theme in `AnimatedTheme`, pick a
transition, and the screen changes theme through a circle that grows from the user's tap, a flood
of colour, a wipe, a fade, a dissolve or a rift that tears the screen open. Layer rings, sparks,
lightning, a shake, a dim or a glow on top.

| Circle | Circle + ring | Circle + ring, sparks, lightning, dim |
|:---:|:---:|:---:|
| ![Circle](docs/showreel/circle.gif) | ![Circle with a ring](docs/showreel/circle-ring.gif) | ![Circle with a ring, sparks, lightning and dim](docs/showreel/circle-ring-sparks-lightning-dim.gif) |
| **Flood** | **Wipe 45° + ring** | **Fade** |
| ![Flood](docs/showreel/flood.gif) | ![Diagonal wipe with a ring](docs/showreel/wipe-45-ring.gif) | ![Fade](docs/showreel/fade.gif) |
| **Dissolve** | **Tear + ring, shake** | **Rift** |
| ![Dissolve](docs/showreel/dissolve.gif) | ![Tear with a ring and shake](docs/showreel/tear-ring-shake.gif) | ![Rift](docs/showreel/rift.gif) |

- **One composable.** No second copy of your UI is composed, and its state survives every switch.
- **Six reveals and a preset**, each with its own options, and **six effects** to mix over them.
  Combinations that cannot work do not compile.
- **Any theme state.** A `Boolean`, an enum, a palette object: anything you can compare with `equals`.
- **Android, iOS and desktop** from one `commonMain` dependency.
- **Measured on real phones.** Every transition keeps up with the display once it has started, at 60 fps
  on a budget Redmi Note 9 and at 120 fps on a mid-range Redmi Note 13 Pro. The switch itself holds the
  screen up for 12–52 ms on the Note 13 Pro and 67–130 ms on the Note 9, most of it your app's own
  recomposition (see [Performance](#performance)).

## Install

### JitPack

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

```kotlin
// build.gradle.kts of your shared (or Android) module
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.github.M1nexoff.theme-transitions:theme-transitions:0.1.0")
        }
    }
}
```

In an Android-only project, put the same line in `dependencies { implementation(...) }`.

### Maven Central

Once a release is on Maven Central (see [PUBLISHING.md](PUBLISHING.md)), no extra repository is
needed:

```kotlin
implementation("io.github.m1nexoff:theme-transitions:0.1.0")
```

## Use

Put `AnimatedTheme` at the root, around your theme:

```kotlin
@Composable
fun App() {
    var dark by rememberSaveable { mutableStateOf(false) }

    AnimatedTheme(
        targetState = dark,
        modifier = Modifier.fillMaxSize(),
    ) { isDark ->
        MaterialTheme(colorScheme = if (isDark) DarkColors else LightColors) {
            Surface(Modifier.fillMaxSize()) {
                Screen(onToggleTheme = { dark = !dark })
            }
        }
    }
}
```

That is a circle growing from wherever the user's finger last went down, which is usually the toggle
they just tapped. Pass `transition` for anything else:

```kotlin
AnimatedTheme(
    targetState = theme,
    transition = Transitions.wipe(angleDegrees = 45f, effects = listOf(Effects.ring(Color.Cyan))),
) { shown -> AppTheme(shown) { App() } }
```

### Three rules

1. **Build the theme from the lambda's parameter, never from `targetState`.** After a switch,
   `content` keeps the old state for one more frame so that frame can be frozen. A theme read straight
   from your own state would already be the new one, and the transition would reveal nothing.
2. **Paint an opaque background.** The transition reveals the frame your content drew. A `Surface` or
   `Modifier.background` filling the screen does it.
3. **Wrap the whole screen.** `AnimatedTheme` covers only its own bounds.

## Transitions

`Transitions` has one factory per reveal. Every reveal takes `durationMillis`, `easing` and
`effects`; those that open from a point also take `origin`.

| Reveal | What it does | Own options | Defaults |
|---|---|---|---|
| `circle()` | The new theme grows in a circle from the origin. **The default.** | | 650 ms, `FastOutSlowInEasing` |
| `flood(color)` | A circle of `color` covers the screen, then shrinks back into the origin over the new theme | `color` | 900 ms, `FastOutSlowInEasing` |
| `wipe()` | A straight edge sweeps across the screen | `angleDegrees`: 0 left to right, 90 top to bottom, any angle between | 600 ms, `FastOutSlowInEasing`, 90° |
| `fade()` | Crossfade | | 450 ms, `FastOutSlowInEasing` |
| `dissolve()` | The old theme breaks into tiles that vanish in random order | `tileSize` | 700 ms, `LinearEasing`, 12 dp |
| `tear()` | A ragged hole that wobbles as it grows | | 2 s, `LinearOutSlowInEasing` |
| `rift(glow, backdrop)` | **Preset:** `tear` with dim, a flickering ring, sparks, lightning, shake and a border glow | `lightning`, `scrim` | Same as `tear` |
| `none()` | Switches at once | | |

`origin` is `TransitionOrigin.Touch` by default: the last touch inside `AnimatedTheme`, or the centre
when there was none in the last five seconds, as when the system turns dark mode on. `Center` and
`Fraction(x, y)` fix it instead; a fraction outside `0..1` sweeps in from off screen.

## Effects

`Effects` makes the layers to put over a reveal:

| Effect | What it draws | Works with |
|---|---|---|
| `ring(color, width, flicker)` | A line along the edge between the themes | Every reveal but `fade` and `dissolve` |
| `sparks(color, count)` | Sparks spiralling in towards the edge | `circle`, `flood`, `tear` |
| `lightning(color, bolts)` | Bolts crackling outward off the edge | `circle`, `flood`, `tear` |
| `shake(strength, backdrop)` | Shakes the screen; `backdrop` fills the strips it uncovers | Every reveal |
| `dim(color, maxAlpha)` | Dims the screen as the reveal starts, so bright effects read on a light theme | Every reveal |
| `borderGlow(color)` | A glow rattling round the screen's edge | Every reveal |

Each effect draws on its own layer — dim, then ring, sparks and lightning, then the glow — whatever
order you list them in. The "works with" column is checked by the compiler: `effects` on `fade()`
only takes effects that need no edge, so `Transitions.fade(effects = listOf(Effects.lightning(c)))`
does not compile.

### Colours that follow the theme

`transition` is read at the moment of each switch, so build it from the same state as the theme and
its colours will suit the theme being switched *to*:

```kotlin
enum class AppTheme(val scheme: ColorScheme, val accent: Color) { Light(...), Dark(...), Dusk(...) }

var theme by rememberSaveable { mutableStateOf(AppTheme.Light) }

AnimatedTheme(
    targetState = theme,
    transition = Transitions.circle(
        effects = listOf(Effects.ring(theme.accent), Effects.sparks(theme.accent)),
    ),
) { shown ->
    MaterialTheme(colorScheme = shown.scheme) { /* ... */ }
}
```

The same goes for a different transition per theme: `transition = if (theme.isDark) ... else ...`.

### Behaviour worth knowing

- **Switching again mid-transition** freezes the screen as it stands, the running transition's frame
  included, and plays the new transition over it. The older one's effects fade out over 150 ms.
- **Animations off.** When the composition's `MotionDurationScale` is 0, which on Android means
  animations are turned off in the system settings, the theme changes at once. So it does with
  `none()`, a duration of 0, or when the box is resized mid-transition.
- **Late frames pause, they don't skip.** A frame that comes late, typically the one your app
  recomposes in the new theme on, moves the transition on by at most 34 ms, two frames at 60 Hz.
- **Wrong arguments fail early.** A negative duration, count, width or strength, a tile size of 0 or
  a dim outside `0..1` throws when the transition is built.

## Performance

Measured on two phones with the demo's release build and `scripts/bench.sh`, which reads from
SurfaceFlinger when each frame actually reached the screen. Ten switches per transition, over the
demo's Material 3 screen:

- **Redmi Note 9**: MediaTek Helio G85, Mali-G52 MC2, Android 12, 1080 × 2340, 60 Hz. A budget phone.
- **Redmi Note 13 Pro**: MediaTek Helio G99-Ultra, Mali-G57 MC2, Android 16, 1080 × 2400, at 60 Hz and at
  120 Hz. A mid-range one.

Each cell gives the share of the animation's vsyncs that had a new frame, then how long the switch
itself held the screen up:

| Transition | Note 9, 60 Hz | Note 13 Pro, 60 Hz | Note 13 Pro, 120 Hz |
|---|---:|---:|---:|
| circle | 97.6% · 115 ms | 100% · 13 ms | 99.6% · 32 ms |
| circle + ring, sparks, lightning, dim | 99.7% · 130 ms | 100% · 27 ms | 98.8% · 52 ms |
| flood | 99.4% · 112 ms | 100% · 17 ms | 100% · 37 ms |
| wipe | 99.0% · 67 ms | 100% · 12 ms | 99.8% · 25 ms |
| wipe 45° | 99.7% · 118 ms | 100% · 17 ms | 99.8% · 23 ms |
| fade | 99.1% · 105 ms | 100% · 18 ms | 100% · 26 ms |
| dissolve | 99.7% · 105 ms | 100% · 13 ms | 100% · 31 ms |
| tear | 99.7% · 100 ms | 100% · 15 ms | 99.9% · 33 ms |
| rift | 99.7% · 125 ms | 100% · 32 ms | 99.4% · 50 ms |
| *redraw: freeze and recompose, then repaint with no transition drawn* | *100% · 52 ms* | *100% · 18 ms* | *100% · 28 ms* |
| *redraw over a flat background* | *99.2% · 2 ms* | *100% · 0 ms* | *100% · 3 ms* |

**Once it starts, every transition keeps up with the display**: 60 fps on both phones, 120 fps on the
Note 13 Pro. What it misses is a single frame now and then, a few in the first, still cold transition
of a run, and at 120 Hz the tail of the switch, which runs past the four frames the bench counts as the
switch. The switch itself is where the cost is, in three parts:

- **Freezing the old theme** draws your whole screen once more into a bitmap: 15–30 ms on the Note 9,
  depending on the screen.
- **Your app recomposing in the new theme.** `MaterialTheme` hands out its colours through a static
  composition local, so everything below it recomposes on the frame the theme changes, transition or
  not: 60–120 ms for the demo's screen on the Note 9 while it is still being compiled, less after.
- **The transition's first frames**, which put the frozen frame on the GPU and wake it from idle: the
  difference between the `redraw` row and the others.

Over a flat background none of it shows, so it is the screen that sets the price: 12–52 ms of it on the
mid-range phone, 67–130 ms on the budget one. If a switch hitches on a slow phone, the recomposition is the one to
cut: provide your colours through a `compositionLocalOf` (dynamic), so only what reads a colour
recomposes, and ship a baseline profile.

## How it works

The UI is composed once. On a switch, `AnimatedTheme` freezes the screen into a bitmap, recomposes
the UI in the new theme, and draws the bitmap over it where the new theme has not reached yet. The
effects start on the very next frame, over the still-live old theme, so the tap gets an immediate
answer.

Each reveal draws the old frame the cheapest way its shape allows. Clipping a full-screen image to an
arbitrary shape costs the GPU a coverage mask the size of the screen every frame; measured on a Helio
G99, that alone took 25–30 ms. None of the reveals does it:

| Reveal | The old frame is drawn as |
|---|---|
| circle, flood | One very thick circle stroke filled with the frozen frame: inner edge the circle, outer edge past every corner. An anti-aliased circle is a cheap analytic draw on any GPU. |
| wipe | A convex polygon of at most five points filled with the frozen frame. A straight sweep also clips the live theme to a rect, which is free. |
| fade | The frozen frame with alpha. |
| dissolve | Android 10+: one textured mesh. The tiles are sorted by when they vanish, so the ones left are always the end of one prebuilt array, and a frame only moves an offset into it. Elsewhere one image call per tile, with tiles kept large before Android 10. |
| tear | Android 10+: a textured mesh covering exactly the outside of the hole, with a hair-thin line of the old frame itself along the edge to hide the mesh's jagged rim. Before Android 10, and on iOS and desktop, a clip. |

Edges are drawn as raw line segments rather than stroked paths, which cost 12 ms of GPU on the G99,
and the paths, paints, strokes and buffers a transition draws with are made once, not every frame.

`theme-transitions/src/desktopTest` renders every reveal off screen through Skia and checks the
pixels: the new theme where it has opened, the old one where it has not, and nothing left once a
transition ends, including after switches made mid-transition.

## Platforms

| | |
|---|---|
| Android | minSdk 23. Checked on Android 6, 9, 12 and 16, measured on two phones. |
| iOS | `iosArm64`, `iosSimulatorArm64`. Not measured on a device. |
| Desktop | JVM 11+ |
| Kotlin | 2.2.20 or newer |
| Compose Multiplatform | 1.11.1 or newer |

## Demo

`demo/` holds a screen with three themes, a playground that sets the reveal, its effects and options,
and **Play all**, which runs every transition and its variants through the three themes. The screen is
shared, with a thin host per platform:

| Platform | Run it |
|---|---|
| Desktop | `./gradlew :demo:desktopApp:run` |
| Android | `./gradlew :demo:androidApp:installDebug`. Judge smoothness on `installRelease` (minified, debug-signed), never on a debug build. |
| iOS | Open `demo/iosApp/iosApp.xcodeproj` in Xcode on a Mac and run. To run on a device, set your team in `demo/iosApp/Configuration/Config.xcconfig`. |

The Android SDK location goes in `local.properties` (`sdk.dir=...`), as in any Android project.

## Build

```sh
./gradlew :theme-transitions:desktopTest                  # geometry and rendering tests
./gradlew :theme-transitions:publishToMavenLocal          # every variant, iOS included, from any OS
./gradlew renderPreviews                                  # docs/preview.png: five moments of each transition
./gradlew renderShowreel -Pffmpeg=<path to ffmpeg>        # docs/showreel: an MP4 and the GIFs above
scripts/bench.sh <adb serial> [case...]                   # frame counts on a phone, release build installed
```

## Credits

The rift is inspired by *Rift Toggle* from [tbahlai/animations](https://github.com/tbahlai/animations),
a collection of Jetpack Compose experiments.

## License

```
Copyright 2026 M1nexoff

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

# Changelog

## 0.1.0

First release.

- `AnimatedTheme`: switches any theme state through a transition, composing the UI once.
- `Transitions`: `circle` (the default), `flood`, `wipe` at any angle, `fade`, `dissolve`, `tear`, the
  `rift` preset, and `none`, each with its duration, easing and, where it opens from a point, origin.
- `Effects`: `ring`, `sparks`, `lightning`, `shake`, `dim` and `borderGlow`, layered over any reveal
  that can draw them; the compiler rejects those that cannot.
- `TransitionOrigin`: the last touch, the centre, or a fixed point.
- A switch made mid-transition freezes the screen as it stands and plays over it.
- A `MotionDurationScale` of 0 (on Android, animations turned off in the system settings), `none()`, a
  zero duration or a resize mid-transition change the theme at once.
- Android (minSdk 23), iOS (arm64, simulator arm64) and desktop (JVM).

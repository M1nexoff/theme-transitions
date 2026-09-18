# Changelog

## 0.1.0

First release.

- `ThemeRift`: switches any theme state through a rift that tears open where the user last
  touched, with lightning, sparks, a screen shake and a glow round the edge.
- `RiftColors`: the rift's colours per incoming theme.
- Android, iOS (arm64, simulator arm64) and desktop (JVM).
- Overlapping switches: a new switch tears open over one still in progress instead of cutting it short.
- A `MotionDurationScale` of 0 (on Android, animations turned off in the system settings): the
  theme changes at once.

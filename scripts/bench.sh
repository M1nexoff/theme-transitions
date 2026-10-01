#!/usr/bin/env bash
# Measures transitions on a connected Android device with the demo's release build installed
# (./gradlew :demo:androidApp:installRelease): for each case, ten switches. Prints a Markdown table.
#
# What a viewer sees comes from SurfaceFlinger: when each frame actually reached the screen. For each
# case: frames shown during the transitions; vsyncs missed at the switch, in a transition's first four
# frames, where the old theme is frozen and the app recomposes in the new one; vsyncs missed during
# the animation after that; and the share of the animation's vsyncs that had a new frame. gfxinfo's
# janky share is there for reference only: its frame time runs from the intended vsync to the GPU
# finishing, so a phone that keeps up at 60 Hz with a frame of pipeline latency still reports some
# 17 ms per frame, and every such frame as janky.
#
# Usage: scripts/bench.sh <adb serial> [case...]
#   Cases: circle circle-fx flood wipe wipe45 fade dissolve tear rift (all of them when none given),
#   each also as <case>-plain, over a flat background instead of the demo screen.
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "usage: $0 <adb serial> [case...]" >&2
  exit 2
fi

serial=$1
shift
cases=("$@")
[ ${#cases[@]} -gt 0 ] || cases=(circle circle-fx flood wipe wipe45 fade dissolve tear rift)

package=io.github.m1nexoff.themetransitions.demo
layer="$package/$package.MainActivity#0"
# Git Bash on Windows would rewrite the device paths adb is given.
export MSYS_NO_PATHCONV=1

presents=$(mktemp)
trap 'rm -f "$presents"' EXIT
# Set KEEP_PRESENTS to a directory to keep each case's raw SurfaceFlinger present times there.
keep=${KEEP_PRESENTS:-}

device() { adb -s "$serial" "$@"; }

# SurfaceFlinger keeps only the last 128 frames, about two seconds, so the bench polls well within that.
collect() { device shell dumpsys SurfaceFlinger --latency "'$layer'" | tr -d '\r' >>"$presents"; }

# Splits the present times collected over a case into transitions — a pause of a fifth of a second or
# more is the idle time between switches — and counts the vsyncs each skipped.
smoothness() {
  awk 'NF == 3 && $2 > 0 && $2 < 9000000000000000000 { print $2 }' "$presents" | sort -n -u |
    awk -v period="$1" '
      NR > 1 {
        gap = $1 - last
        if (gap < 200000000) {
          shown++
          missed = int(gap / period + 0.5) - 1
          if (++interval <= 4) atswitch += missed; else { during += missed; animated++ }
        } else {
          interval = 0
        }
      }
      { last = $1 }
      END {
        smooth = animated + during ? 100 * animated / (animated + during) : 0
        printf "%d | %d | %d | %.1f%%", shown, atswitch, during, smooth
      }'
}

model=$(device shell getprop ro.product.model | tr -d '\r')
release=$(device shell getprop ro.build.version.release | tr -d '\r')
echo "Device: $model, Android $release"
echo
echo "| Case | Frames | Missed at switch | Missed during | Smooth during | gfxinfo janky |"
echo "|---|---:|---:|---:|---:|---:|"

# A screen that times out or locks mid-run stops the app drawing, and every number after it is noise.
awake() {
  device shell dumpsys power | grep -q "mWakefulness=Awake" &&
    ! device shell dumpsys window | grep -q "isKeyguardShowing=true"
}

for case in "${cases[@]}"; do
  if ! awake; then
    echo "The phone is asleep or locked: unlock it, and keep it awake with 'adb shell svc power stayon usb'." >&2
    exit 1
  fi
  device shell am force-stop "$package"
  device logcat -c
  device shell am start -W -n "$package/.MainActivity" --es bench "$case" >/dev/null
  # The demo waits a second and a half before its first switch; start counting here.
  device shell dumpsys gfxinfo "$package" reset >/dev/null
  device shell dumpsys SurfaceFlinger --latency-clear "'$layer'" >/dev/null
  : >"$presents"

  done=""
  for _ in $(seq 1 240); do
    collect
    if device logcat -d -s System.out:I | grep -q "ThemeTransitionsBench done $case"; then
      done=yes
      break
    fi
    sleep 0.5
  done
  if [ -z "$done" ]; then
    echo "| $case | timed out | | | | |"
    continue
  fi

  collect
  [ -z "$keep" ] || cp "$presents" "$keep/$case.presents"
  period=$(head -n 1 "$presents")
  stats=$(device shell dumpsys gfxinfo "$package" | tr -d '\r')
  janky=$(grep -m1 "^Janky frames:" <<<"$stats" | sed -E 's/.*\(([0-9.]+%)\).*/\1/')
  echo "| $case | $(smoothness "$period") | $janky |"
done

device shell am force-stop "$package"

#!/usr/bin/env bash
# Measures transitions on a connected Android device with the demo's release build installed
# (./gradlew :demo:androidApp:installRelease): for each case, ten switches, then the frame times
# `dumpsys gfxinfo` recorded over them. Prints a Markdown table.
#
# Usage: scripts/bench.sh <adb serial> [case...]
#   Cases: circle circle-fx flood wipe wipe45 fade dissolve tear rift (all of them when none given).
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
# Git Bash on Windows would rewrite the device paths adb is given.
export MSYS_NO_PATHCONV=1

device() { adb -s "$serial" "$@"; }

stat() { grep -m1 "$1" <<<"$2" | sed -E "s/.*$1[^0-9]*([0-9.]+).*/\1/"; }

model=$(device shell getprop ro.product.model | tr -d '\r')
release=$(device shell getprop ro.build.version.release | tr -d '\r')
echo "Device: $model, Android $release"
echo
echo "| Case | Frames | Janky | p50 | p90 | p95 | p99 |"
echo "|---|---:|---:|---:|---:|---:|---:|"

for case in "${cases[@]}"; do
  device shell am force-stop "$package"
  device logcat -c
  device shell am start -W -n "$package/.MainActivity" --es bench "$case" >/dev/null
  # The demo waits a second and a half before its first switch; start counting frames here.
  device shell dumpsys gfxinfo "$package" reset >/dev/null

  done=""
  for _ in $(seq 1 240); do
    if device logcat -d -s System.out:I | grep -q "ThemeTransitionsBench done $case"; then
      done=yes
      break
    fi
    sleep 0.5
  done
  if [ -z "$done" ]; then
    echo "| $case | timed out | | | | | |"
    continue
  fi

  stats=$(device shell dumpsys gfxinfo "$package" | tr -d '\r')
  frames=$(stat "Total frames rendered:" "$stats")
  janky=$(grep -m1 "^Janky frames:" <<<"$stats" | sed -E 's/.*\(([0-9.]+%)\).*/\1/')
  echo "| $case | $frames | $janky | $(stat "50th percentile:" "$stats") ms | $(stat "90th percentile:" "$stats") ms | $(stat "95th percentile:" "$stats") ms | $(stat "99th percentile:" "$stats") ms |"
done

device shell am force-stop "$package"

#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

"$repo_root/gradlew" -p "$repo_root" \
  -PlibreaacBundledOpenboardsDelivery=base \
  testDebugUnitTest \
  lint \
  assembleDebug

printf '%s\n' \
  "Debug APK: $repo_root/app/build/outputs/apk/debug/app-debug.apk"

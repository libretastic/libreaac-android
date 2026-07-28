#!/bin/sh
set -eu

repo_root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
unsigned_apk="$repo_root/app/build/outputs/apk/release/app-release-unsigned.apk"
unsigned_bundle="$repo_root/app/build/outputs/bundle/release/app-release.aab"

"$repo_root/gradlew" -p "$repo_root" \
  -PlibreaacReleaseSigningEnabled=false \
  testDebugUnitTest \
  lint \
  assembleRelease \
  bundleRelease

if [ ! -f "$unsigned_apk" ]; then
  printf '%s\n' "Expected unsigned APK was not produced: $unsigned_apk" >&2
  exit 1
fi

if [ ! -f "$unsigned_bundle" ]; then
  printf '%s\n' "Expected unsigned app bundle was not produced: $unsigned_bundle" >&2
  exit 1
fi

if jar tf "$unsigned_bundle" | grep -Eq '^META-INF/[^/]+\.(RSA|DSA|EC)$'; then
  printf '%s\n' "App bundle was unexpectedly signed: $unsigned_bundle" >&2
  exit 1
fi

printf '%s\n' \
  "Unsigned APK: $unsigned_apk" \
  "Unsigned app bundle: $unsigned_bundle"

#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 FRONTEND_DIST FRONTEND_REVISION" >&2
  exit 2
fi

frontend_dist=$1
frontend_revision=$2
asset_dir="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets"

if [[ ! -f "$frontend_dist/index.html" || ! -f "$frontend_dist/manifest.webmanifest" ]]; then
  echo "FRONTEND_DIST must contain a completed LibreAAC production build." >&2
  exit 1
fi

if [[ -z "$frontend_revision" ]]; then
  echo "FRONTEND_REVISION must identify the exact web release." >&2
  exit 1
fi

mkdir -p "$asset_dir"
find "$asset_dir" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
cp -R "$frontend_dist"/. "$asset_dir"/
{
  echo "frontend_revision=$frontend_revision"
  echo "sha256:"
  find "$asset_dir" -type f ! -name FRONTEND-RELEASE |
    sort |
    while IFS= read -r file; do
      relative=${file#"$asset_dir/"}
      digest=$(sha256sum "$file" | cut -d' ' -f1)
      echo "$digest  $relative"
    done
} > "$asset_dir/FRONTEND-RELEASE"

echo "Embedded LibreAAC web release $frontend_revision"


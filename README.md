# LibreAAC for Android

This repository packages LibreAAC as an offline Android application. It is a
small Kotlin WebView shell around a pinned production build from the sibling
`libreaac` web repository.

The shell:

- serves the bundled web files from Android app assets over a secure local
  origin;
- uses Android's document picker to open and save OBF/OBZ files;
- provides native text-to-speech, clipboard, and text sharing;
- retains the same browser IndexedDB library between launches;
- opens external HTTP/HTTPS links in the user's browser; and
- declares no `INTERNET` permission.

The repository contains no release-signing credentials. Debug and unsigned
release builds work independently; trusted private automation supplies signing
material only when producing distributable APKs or Google Play bundles.

Official builds stage the public communication-board selection declared by
the sibling `openboards` repository. These large generated assets are omitted
from Git history and are loaded into LibreAAC's local library only when the
user first opens a bundled board. Self-contained APKs carry them in the base
package; Google Play AABs use an install-time Play Asset Delivery pack.

It never fetches a moving web deployment. `app/src/main/assets/FRONTEND-RELEASE`
identifies the embedded web release and records a SHA-256 digest for every
asset.

## Quick start

Requires JDK 17 or later and Android SDK platform 36.

```sh
./gradlew testDebugUnitTest lint assembleDebug
```

The debug APK is created at
`app/build/outputs/apk/debug/app-debug.apk`. See [BUILD.md](BUILD.md) for
replacing the embedded frontend and producing release builds, and
[INSTALL.md](INSTALL.md) for device installation.

## Privacy and storage

Vocabulary working copies remain inside the app's WebView storage. Android
backup and device-transfer extraction are disabled so private communication
data is not silently copied. Files explicitly opened and saved through the
system picker remain under the user's chosen storage provider.

## License

Copyright © 2026 Luke Howson and LibreAAC contributors.

LibreAAC for Android is free software licensed under
[GNU GPL version 3 only](LICENSE).

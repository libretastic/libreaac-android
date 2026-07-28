# Installing LibreAAC for Android

LibreAAC supports Android 8.0 (API 26) and later.

## Debug build

Build the APK as described in [BUILD.md](BUILD.md), enable developer options and
USB debugging on the device, then run:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is signed with the local Android debug key and is intended only
for development.

## Release build

After configuring release signing and building as described in
[BUILD.md](BUILD.md), install the signed release APK with:

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

Do not install or distribute `app-release-unsigned.apk`; Android rejects APKs
without a signing certificate.

## Updates and data

Installing an update with the same application ID and signing key preserves the
local vocabulary library. Uninstalling the application removes that protected
local data. Save important vocabularies to user-controlled OBF/OBZ files before
uninstalling or clearing app storage.

No network connection is required to run the installed app.

Application source, issue tracking and build documentation are available from
the public
[LibreAAC for Android repository](https://github.com/libretastic/libreaac-android).

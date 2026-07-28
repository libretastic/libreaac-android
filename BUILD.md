# Building LibreAAC for Android

## Requirements

- JDK 17 or later
- Android SDK platform 36
- Android SDK build tools 36.0.0
- a completed LibreAAC web production build when updating the embedded release

The Gradle wrapper downloads Gradle 9.6.1. Android Gradle Plugin and library
versions are pinned by the build scripts.

Run all commands in this document from the `libreaac-android` repository root
unless a step says otherwise. Generated build outputs are placed under
`app/build/` and are not committed. This repository contains no private signing
material and can be built without access to release credentials.

## Development build

Run the unit tests and lint checks before assembling the debug APK:

```sh
scripts/build-debug.sh
```

Outputs:

- debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- lint report: `app/build/reports/lint-results-debug.html`
- unit-test results: `app/build/test-results/testDebugUnitTest/`

The debug APK is automatically signed with the workstation's Android debug key.
It is suitable for development only and cannot be upgraded in place to a
release-signed build.

## Unsigned release build

Build minified release APK and Android App Bundle artifacts without private
signing material:

```sh
scripts/build-unsigned-release.sh
```

Outputs:

- unsigned APK: `app/build/outputs/apk/release/app-release-unsigned.apk`
- unsigned app bundle: `app/build/outputs/bundle/release/app-release.aab`

These artifacts exercise the release build but are not suitable for
distribution. Signing is explicitly disabled by the script even if signing
environment variables happen to be present.

## Update the embedded web release

First verify and build the sibling web repository:

```sh
cd ../libreaac
npm ci
npm test
npm run typecheck
npm run lint
npm run build
```

Then, from this repository, embed it using a release tag or full commit ID:

```sh
scripts/sync-frontend.sh ../libreaac/dist WEB_RELEASE_OR_COMMIT
./gradlew testDebugUnitTest lint assembleDebug
```

The sync script replaces only `app/src/main/assets`, verifies that the source
looks like a completed production build, and writes asset checksums to
`FRONTEND-RELEASE`. Review that file in the same change as the generated assets.

## Configure release signing

Release signing is optional and external. No keystore or password file belongs
in this repository. A trusted private build environment must provide all four
signing values and explicitly enable signing:

| Gradle property | Environment variable |
| --- | --- |
| `libreaacReleaseStoreFile` | `LIBREAAC_RELEASE_STORE_FILE` |
| `libreaacReleaseStorePassword` | `LIBREAAC_RELEASE_STORE_PASSWORD` |
| `libreaacReleaseKeyAlias` | `LIBREAAC_RELEASE_KEY_ALIAS` |
| `libreaacReleaseKeyPassword` | `LIBREAAC_RELEASE_KEY_PASSWORD` |

Set `-PlibreaacReleaseSigningEnabled=true` only for a trusted signed build.
The build fails if signing is requested without a complete configuration.
Absolute keystore paths are supported.

## Build a signed release

For a release containing an updated web application, complete
[Update the embedded web release](#update-the-embedded-web-release) first.
Then, from a trusted environment that has supplied the external signing
configuration, run:

```sh
./gradlew -PlibreaacReleaseSigningEnabled=true \
  testDebugUnitTest lint assembleRelease
```

The signed APK is written to:

```text
app/build/outputs/apk/release/app-release.apk
```

The build has failed its release purpose if this file is absent. Private
automation should verify both the artifact signature and expected certificate
before distribution.

For Google Play, build a signed Android App Bundle instead:

```sh
./gradlew -PlibreaacReleaseSigningEnabled=true \
  testDebugUnitTest lint bundleRelease
```

The signed bundle is written to
`app/build/outputs/bundle/release/app-release.aab`.

## Verify the release APK

Use the Android SDK's `apksigner` to verify the completed artifact before
distribution:

```sh
apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

`apksigner` must report `Verifies`, one signer, and this LibreAAC release
certificate SHA-256 digest:

```text
aad2f12a9125a9f2f97b0070cb6b2dfd10b26ed989a84b4f12ff99bee66e52ba
```

Record the APK SHA-256 checksum alongside each distributed release. The APK
checksum changes when the application is rebuilt; the certificate digest above
must remain constant.

## Publish a GitLab release with `glab`

`glab` must be installed and authenticated against the GitLab host. Run release
commands inside this repository so `glab` automatically targets
`libreaac/libreaac-android`. When running elsewhere, add:

```sh
-R libreaac/libreaac-android
```

Publish only after the release merge request has been merged and local `master`
has been updated to exactly match `origin/master`. Set the version without the
leading `v`, verify that the APK version matches it, rebuild and verify the
signature, then create the release:

```sh
release_version=0.1.4
release_tag="v${release_version}"
release_apk="app/build/outputs/apk/release/app-release.apk"

git switch master
git pull origin master
./gradlew -PlibreaacReleaseSigningEnabled=true \
  testDebugUnitTest lint assembleRelease
apksigner verify --verbose --print-certs "${release_apk}"
sha256sum "${release_apk}"

glab release create "${release_tag}" \
  "${release_apk}#libreaac-v${release_version}.apk" \
  --ref master \
  --name "LibreAAC ${release_version}" \
  --notes "LibreAAC Android ${release_version}" \
  --no-update

glab release view "${release_tag}"
```

`--no-update` prevents an accidental second invocation from silently changing
an existing release. Without it, `glab release create` is an upsert. If the tag
does not already exist, `--ref master` creates it at the verified release
commit. The `#libreaac-v…apk` suffix sets the downloadable asset name without
renaming the local Gradle output.

Useful release operations are:

```sh
glab release list
glab release view
glab release view v0.1.4
glab release download v0.1.4 -n "libreaac-v0.1.4.apk"
glab release upload v0.1.4 ./additional-file
```

On `release download`, `-n` means an asset-name glob. On `release create`, `-n`
means the release name. Downloading without an asset filter also downloads the
automatically generated source archives. Deleting a release is destructive and
must be explicit:

```sh
glab release delete v0.1.4 -y
```

Add `--with-tag` only when the Git tag must also be deleted.

## Private release automation

The private LibreAAC secrets repository owns signing keys, credentials, signed
build launchers, and signature checks. Its scripts call this repository's
Gradle wrapper with an absolute external keystore path. Keep private and public
repositories as siblings when using those launchers, or set the documented
private-script override for the Android repository path.

Never commit keystores, passwords, generated `local.properties`, or encrypted
private-key exports to this public repository. Removing a secret in a later
commit does not remove it from existing Git history.

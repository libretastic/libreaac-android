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
`app/build/` and are not committed.

## Development build

Run the unit tests and lint checks before assembling the debug APK:

```sh
./gradlew testDebugUnitTest lint assembleDebug
```

Outputs:

- debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- lint report: `app/build/reports/lint-results-debug.html`
- unit-test results: `app/build/test-results/testDebugUnitTest/`

The debug APK is automatically signed with the workstation's Android debug key.
It is suitable for development only and cannot be upgraded in place to a
release-signed build.

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

The encrypted release keystore is versioned at
`signing/libreaac-release.keystore`. Its password is not versioned. Local release
builds read the password and other signing values from the ignored, locally
permission-restricted `signing/keystore.properties` file.

The file has this structure:

```properties
libreaacReleaseStoreFile=signing/libreaac-release.keystore
libreaacReleaseStorePassword=RELEASE_KEYSTORE_PASSWORD
libreaacReleaseKeyAlias=libreaac-release
libreaacReleaseKeyPassword=RELEASE_KEY_PASSWORD
```

Replace the two password placeholders with the actual credentials and restrict
the file to its owner:

```sh
chmod 600 signing/keystore.properties
```

Do not create a new release keystore when building a later version. Android
accepts an application update only when it is signed by the same key as the
installed version.

## Build a signed release

For a release containing an updated web application, complete
[Update the embedded web release](#update-the-embedded-web-release) first.
Then run:

```sh
./gradlew testDebugUnitTest lint assembleRelease
```

The signed APK is written to:

```text
app/build/outputs/apk/release/app-release.apk
```

The build has failed its release purpose if this file is absent. If Gradle
instead creates `app-release-unsigned.apk`, the signing configuration was
missing or incomplete; do not distribute that file.

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
release_version=0.1.1
release_tag="v${release_version}"
release_apk="app/build/outputs/apk/release/app-release.apk"

git switch master
git pull origin master
./gradlew testDebugUnitTest lint assembleRelease
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
glab release view v0.1.1
glab release download v0.1.1 -n "libreaac-v0.1.1.apk"
glab release upload v0.1.1 ./additional-file
```

On `release download`, `-n` means an asset-name glob. On `release create`, `-n`
means the release name. Downloading without an asset filter also downloads the
automatically generated source archives. Deleting a release is destructive and
must be explicit:

```sh
glab release delete v0.1.1 -y
```

Add `--with-tag` only when the Git tag must also be deleted.

## Non-local signing configuration

Release environments may use Gradle properties or environment variables instead
of the local properties file:

| Gradle or properties-file key | Environment variable |
| --- | --- |
| `libreaacReleaseStoreFile` | `LIBREAAC_RELEASE_STORE_FILE` |
| `libreaacReleaseStorePassword` | `LIBREAAC_RELEASE_STORE_PASSWORD` |
| `libreaacReleaseKeyAlias` | `LIBREAAC_RELEASE_KEY_ALIAS` |
| `libreaacReleaseKeyPassword` | `LIBREAAC_RELEASE_KEY_PASSWORD` |

`libreaacReleaseStoreFile` is resolved relative to the repository root. The
project value is `signing/libreaac-release.keystore`, and the key alias is
`libreaac-release`.

The release keystore and its password are both required to publish compatible
updates. Keep secure backups of the repository and the unversioned credentials.
Never commit `signing/keystore.properties`, passwords, or generated
`local.properties`.

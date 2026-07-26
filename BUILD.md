# Building LibreAAC for Android

## Requirements

- JDK 17 or later
- Android SDK platform 36
- Android SDK build tools 36.0.0
- a completed LibreAAC web production build when updating the embedded release

The Gradle wrapper downloads Gradle 9.6.1. Android Gradle Plugin and library
versions are pinned by the build scripts.

## Verify and build

```sh
./gradlew testDebugUnitTest lint assembleDebug
```

Outputs:

- debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- lint report: `app/build/reports/lint-results-debug.html`
- unit-test results: `app/build/test-results/testDebugUnitTest/`

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

## Release signing

The repository does not contain signing credentials. Configure a private Gradle
signing configuration in the release environment, then run:

```sh
./gradlew bundleRelease
```

Never commit keystores, passwords, or generated `local.properties`.


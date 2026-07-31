import groovy.json.JsonSlurper
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Sync

plugins {
    id("com.android.application")
}

val bundledOpenboardsRequired = providers
    .gradleProperty("libreaacBundledOpenboardsRequired")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false
val bundledOpenboardsDelivery = providers
    .gradleProperty("libreaacBundledOpenboardsDelivery")
    .orElse("base")
    .get()
require(bundledOpenboardsDelivery in setOf("base", "asset-pack", "none")) {
    "libreaacBundledOpenboardsDelivery must be base, asset-pack, or none"
}
val openboardsRepository = providers
    .gradleProperty("libreaacOpenboardsRepo")
    .orElse(providers.environmentVariable("LIBREAAC_OPENBOARDS_REPO"))
    .orNull
    ?.let(::file)
    ?: rootProject.file("upstream/openboards").takeIf {
        it.resolve("bundles/libreaac.json").isFile
    }
    ?: rootProject.file("../openboards")
val openboardsManifest = openboardsRepository.resolve("bundles/libreaac.json")
val generatedOpenboardsAssets = layout.buildDirectory.dir(
    "generated/bundledOpenboardsAssets"
)

@Suppress("UNCHECKED_CAST")
val bundledBoardEntries: List<Map<String, Any?>> =
    if (openboardsManifest.isFile) {
        val manifest = JsonSlurper().parse(openboardsManifest) as Map<String, Any?>
        require(manifest["schemaVersion"] == 1) {
            "Unsupported LibreAAC openboards bundle manifest"
        }
        require(manifest["bundle"] == "libreaac") {
            "Unexpected openboards bundle: ${manifest["bundle"]}"
        }
        manifest["boards"] as? List<Map<String, Any?>>
            ?: error("The LibreAAC openboards bundle has no boards array")
    } else {
        emptyList()
    }

if (!openboardsManifest.isFile && bundledOpenboardsRequired) {
    error(
        "Required openboards bundle manifest not found: " +
            openboardsManifest.absolutePath
    )
}
if (!openboardsManifest.isFile) {
    logger.warn(
        "Building without bundled openboards; no manifest found at {}",
        openboardsManifest.absolutePath
    )
}

val bundledBoardSources = bundledBoardEntries.map { entry ->
    val filename = entry["filename"] as? String
        ?: error("A bundled openboard has no filename")
    val sourcePath = entry["path"] as? String
        ?: error("A bundled openboard has no path")
    val source = openboardsRepository.resolve(sourcePath)
    require(source.isFile) {
        "Bundled openboard is missing: ${source.absolutePath}"
    }
    require(source.name == filename) {
        "Bundled openboard filename does not match its path: $filename"
    }
    source
}
val bundleManifestSource =
    if (openboardsManifest.isFile) {
        openboardsManifest
    } else {
        file("src/main/bundled-openboards-empty.json")
    }

val cleanBundledOpenboards = tasks.register<Delete>(
    "cleanBundledOpenboards"
) {
    delete(generatedOpenboardsAssets)
}

val prepareBundledOpenboards = tasks.register<Sync>(
    "prepareBundledOpenboards"
) {
    dependsOn(cleanBundledOpenboards)
    into(generatedOpenboardsAssets.map { it.dir("bundled-openboards") })
    if (bundledOpenboardsDelivery == "base") {
        from(bundleManifestSource) {
            rename { "manifest.json" }
        }
        from(bundledBoardSources)
    }
}

fun releaseSigningValue(propertyName: String, environmentName: String): String? =
    providers.gradleProperty(propertyName).orNull
        ?: providers.environmentVariable(environmentName).orNull

val releaseStoreFile = releaseSigningValue(
    "libreaacReleaseStoreFile",
    "LIBREAAC_RELEASE_STORE_FILE"
)
val releaseStorePassword = releaseSigningValue(
    "libreaacReleaseStorePassword",
    "LIBREAAC_RELEASE_STORE_PASSWORD"
)
val releaseKeyAlias = releaseSigningValue(
    "libreaacReleaseKeyAlias",
    "LIBREAAC_RELEASE_KEY_ALIAS"
)
val releaseKeyPassword = releaseSigningValue(
    "libreaacReleaseKeyPassword",
    "LIBREAAC_RELEASE_KEY_PASSWORD"
)
val releaseSigningConfigured = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }
val releaseSigningEnabled = providers
    .gradleProperty("libreaacReleaseSigningEnabled")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false

if (releaseSigningEnabled && !releaseSigningConfigured) {
    error(
        "Release signing was requested, but the external keystore path, " +
            "store password, key alias, and key password were not all supplied."
    )
}

android {
    namespace = "org.libreaac.app"
    compileSdk = 36
    assetPacks += listOf(":bundled_openboards")

    defaultConfig {
        applicationId = "org.libreaac.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "0.1.13"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    dependenciesInfo {
        // F-Droid rejects the encrypted SDK dependency metadata signing block.
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        create("release") {
            if (releaseSigningEnabled) {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // Absolute checkout paths make otherwise identical APKs differ.
            // Release tags and FRONTEND-RELEASE retain the source revisions.
            vcsInfo.include = false
            if (releaseSigningEnabled) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.all {
            it.useJUnit()
        }
    }

    sourceSets {
        getByName("main").assets.directories.add(
            generatedOpenboardsAssets.get().asFile.absolutePath
        )
    }

    androidResources {
        noCompress += "obz"
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareBundledOpenboards)
}

dependencies {
    implementation("androidx.webkit:webkit:1.16.0")

    testImplementation("junit:junit:4.13.2")
}

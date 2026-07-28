plugins {
    id("com.android.application")
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

    defaultConfig {
        applicationId = "org.libreaac.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

dependencies {
    implementation("androidx.webkit:webkit:1.16.0")

    testImplementation("junit:junit:4.13.2")
}

import groovy.json.JsonSlurper
import org.gradle.api.tasks.Sync

plugins {
    id("com.android.asset-pack")
}

val delivery = providers
    .gradleProperty("libreaacBundledOpenboardsDelivery")
    .orElse("base")
    .get()
val required = providers
    .gradleProperty("libreaacBundledOpenboardsRequired")
    .orNull
    ?.toBooleanStrictOrNull()
    ?: false
val openboardsRepository = providers
    .gradleProperty("libreaacOpenboardsRepo")
    .orElse(providers.environmentVariable("LIBREAAC_OPENBOARDS_REPO"))
    .orNull
    ?.let(::file)
    ?: rootProject.file("upstream/openboards").takeIf {
        it.resolve("bundles/libreaac.json").isFile
    }
    ?: rootProject.file("../openboards")
val manifestFile = openboardsRepository.resolve("bundles/libreaac.json")

if (delivery == "asset-pack" && !manifestFile.isFile && required) {
    error("Required openboards bundle manifest not found: ${manifestFile.absolutePath}")
}

@Suppress("UNCHECKED_CAST")
val boardSources =
    if (delivery == "asset-pack" && manifestFile.isFile) {
        val manifest = JsonSlurper().parse(manifestFile) as Map<String, Any?>
        require(manifest["schemaVersion"] == 1 && manifest["bundle"] == "libreaac") {
            "Invalid LibreAAC openboards bundle manifest"
        }
        val entries = manifest["boards"] as? List<Map<String, Any?>>
            ?: error("The LibreAAC openboards bundle has no boards array")
        entries.map { entry ->
            val filename = entry["filename"] as? String
                ?: error("A bundled openboard has no filename")
            val path = entry["path"] as? String
                ?: error("A bundled openboard has no path")
            val source = openboardsRepository.resolve(path)
            require(source.isFile && source.name == filename) {
                "Bundled openboard is missing or misnamed: ${source.absolutePath}"
            }
            source
        }
    } else {
        emptyList()
    }

val stageBundledOpenboards = tasks.register<Sync>("stageBundledOpenboards") {
    into(layout.projectDirectory.dir("src/main/assets"))
    if (delivery == "asset-pack" && manifestFile.isFile) {
        from(manifestFile) {
            into("bundled-openboards")
            rename { "manifest.json" }
        }
        from(boardSources) {
            into("bundled-openboards")
        }
    } else {
        from(rootProject.file("app/src/main/bundled-openboards-empty.json")) {
            rename { ".staging-mode" }
        }
    }
}

assetPack {
    packName.set("bundled_openboards")
    dynamicDelivery {
        deliveryType.set("install-time")
    }
}

tasks.named("generateAssetPackManifest").configure {
    dependsOn(stageBundledOpenboards)
}

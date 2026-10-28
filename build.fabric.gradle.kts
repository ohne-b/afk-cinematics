plugins { id("net.fabricmc.fabric-loom-remap") }

val minecraftVersion = property("minecraft_version").toString()
version = "${property("mod_version")}+mc$minecraftVersion-fabric"
group = property("maven_group").toString()
base.archivesName = "afk-cinematics"

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api")}")
    modCompileOnly("maven.modrinth:modmenu:${property("modmenu_version")}")
    implementation(project(":core"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

sourceSets.main { java.exclude("**/NeoForgeClient.java") }


if (providers.gradleProperty("smokeTest").isPresent) {
    apply(from = rootProject.file("gradle/smoke.gradle.kts"))
    loom.mods {
        register("ohneb_afk_cinematics") { sourceSet(sourceSets.main.get()) }
        register("ohneb_afk_cinematics_smoke") { sourceSet(sourceSets["smoke"]) }
    }
    loom.runs.named("client") {
        source(sourceSets["smoke"])
        vmArg("-Dafkcinematics.smoke.result=${rootProject.file("artifacts/smoke-${project.name}.txt").absolutePath}")
    }
}


if (minecraftVersion == "1.21.11") {
    fabricApi {
        configureTests {
            createSourceSet = true
            modId = "ohneb_afk_cinematics_tests"
            enableGameTests = false
            enableClientGameTests = true
            clearRunDirectory = false
            username = "CinematicTest"
        }
    }
    sourceSets.named("gametest") {
        java.setSrcDirs(listOf(rootProject.file("src/gametest/java")))
        resources.setSrcDirs(listOf(rootProject.file("src/gametest/resources")))
    }
    loom.runs.named("clientGameTest") {
        vmArg("-Dafkcinematics.test.artifacts=${rootProject.file("artifacts").absolutePath}")
        // Use real packet timing; Fabric's packet synchronizer can stall world startup.
        vmArg("-Dfabric.client.gametest.disableNetworkSynchronizer=true")
    }
}

stonecutter {
    replacements.string {
        direction = eval(current.version, ">1.21.10")
        replace("ResourceLocation", "Identifier")
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft", minecraftVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version, "minecraft" to minecraftVersion, "java" to 21)
    }
    exclude("META-INF/neoforge.mods.toml")
}

tasks.jar {
    dependsOn(":core:classes")
    from(project(":core").layout.buildDirectory.dir("classes/java/main"))
    from(rootProject.file("LICENSE.md"))
}

tasks.named<Jar>("sourcesJar") {
    from(rootProject.file("core/src/main/java"))
    from(rootProject.file("LICENSE.md"))
}

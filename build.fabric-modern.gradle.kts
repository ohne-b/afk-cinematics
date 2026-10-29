plugins { id("net.fabricmc.fabric-loom") }

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
    implementation("net.fabricmc:fabric-loader:${property("fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api")}")
    compileOnly("maven.modrinth:modmenu:${property("modmenu_version")}")
    implementation(project(":core"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

sourceSets.main { java.exclude("**/NeoForgeClient.java") }

if (minecraftVersion == "26.2" || minecraftVersion == "26.3") {
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
    stonecutter.tasks.configureSource(sourceSets["gametest"])
    loom.runs.named("clientGameTest") {
        vmArg("-Dafkcinematics.test.artifacts=${rootProject.file("artifacts/gametest-${project.name}").absolutePath}")
        providers.gradleProperty("naturalSeconds").orNull?.let { vmArg("-Dafkcinematics.test.naturalSeconds=$it") }
        providers.gradleProperty("cinematicCapture").orNull?.let { vmArg("-Dafkcinematics.test.capture=$it") }
        providers.gradleProperty("cinematicScene").orNull?.let { vmArg("-Dafkcinematics.test.scene=$it") }
        vmArg("-Dfabric.client.gametest.disableNetworkSynchronizer=true")
    }
}


if (providers.gradleProperty("smokeTest").isPresent) {
    apply(from = rootProject.file("gradle/smoke.gradle.kts"))
    stonecutter.tasks.configureSource(sourceSets["smoke"])
    loom.mods {
        register("ohneb_afk_cinematics") { sourceSet(sourceSets.main.get()) }
        register("ohneb_afk_cinematics_smoke") { sourceSet(sourceSets["smoke"]) }
    }
    loom.runs.named("client") {
        source(sourceSets["smoke"])
        vmArg("-Dafkcinematics.smoke.result=${rootProject.file("artifacts/smoke-${project.name}.txt").absolutePath}")
        providers.gradleProperty("sceneWorld").orNull?.let { vmArg("-Dafkcinematics.smoke.world=$it") }
    }
}

stonecutter {
    for ((old, currentName) in listOf(
        "keybinding.v1.KeyBindingHelper" to "keymapping.v1.KeyMappingHelper",
        "KeyBindingHelper.registerKeyBinding" to "KeyMappingHelper.registerKeyMapping"
    )) replacements.string { replace(old, currentName); direction = true }
    if (eval(current.version, ">=26.1")) {
        for ((old, currentName) in listOf(
            "GuiGraphics" to "GuiGraphicsExtractor", "\"render\"" to "\"extractRenderState\"",
            "render(" to "extractRenderState(", "drawString" to "text", "drawCenteredString" to "centeredText"
        )) replacements.string { replace(old, currentName); direction = true }
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft", minecraftVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version, "minecraft" to minecraftVersion, "java" to 25)
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

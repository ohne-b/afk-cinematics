plugins { id("net.neoforged.moddev") }

val minecraftVersion = property("minecraft_version").toString()
val neoForgeVersion = property("neoforge_version").toString()
version = "${property("mod_version")}+mc$minecraftVersion-neoforge"
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
    implementation(project(":core"))
    // The shared 1.21/1.21.1 jar needs WrapMethod, absent from NeoForge 21.0's bundled library.
    if (minecraftVersion == "1.21" || minecraftVersion == "1.21.1")
        implementation(jarJar("io.github.llamalad7:mixinextras-neoforge:0.5.5"))
}
evaluationDependsOn(":core")

java {
    toolchain.languageVersion = JavaLanguageVersion.of(if (minecraftVersion.startsWith("26.")) 25 else 21)
    withSourcesJar()
}

neoForge {
    version = neoForgeVersion
    runs {
        register("client") { client(); gameDirectory = file("run") }
    }
    mods {
        register("ohneb_afk_cinematics") {
            sourceSet(sourceSets.main.get())
            sourceSet(project(":core").extensions.getByType<SourceSetContainer>()["main"])
        }
    }
}

sourceSets.main { java.exclude("**/FabricClient.java", "**/ModMenuIntegration.java", "**/FabrishotMixin.java") }


if (providers.gradleProperty("smokeTest").isPresent) {
    apply(from = rootProject.file("gradle/smoke.gradle.kts"))
    stonecutter.tasks.configureSource(sourceSets["smoke"])
    neoForge.mods { register("ohneb_afk_cinematics_smoke") { sourceSet(sourceSets["smoke"]) } }
    neoForge.runs.named("client") {
        systemProperty("afkcinematics.smoke.result", rootProject.file("artifacts/smoke-${project.name}.txt").absolutePath)
        providers.gradleProperty("sceneWorld").orNull?.let { systemProperty("afkcinematics.smoke.world", it) }
    }
}

stonecutter {
    replacements.string {
        direction = eval(current.version, ">1.21.10")
        replace("ResourceLocation", "Identifier")
    }
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
    inputs.property("neoforge", neoForgeVersion)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version, "minecraft" to minecraftVersion, "neoforge" to neoForgeVersion,
            "logo_key" to if (stonecutter.eval(minecraftVersion, ">=26.2")) "iconFile" else "logoFile")
    }
    exclude("fabric.mod.json")
    exclude("ohneb_afk_cinematics.fabric.mixins.json")
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

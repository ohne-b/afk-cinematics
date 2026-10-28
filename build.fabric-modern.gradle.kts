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


stonecutter {
    for ((old, currentName) in listOf(
        "ClickType" to "ContainerInput", "handleInventoryMouseClick" to "handleContainerInput",
        "keybinding.v1.KeyBindingHelper" to "keymapping.v1.KeyMappingHelper",
        "KeyBindingHelper.registerKeyBinding" to "KeyMappingHelper.registerKeyMapping"
    )) replacements.string { replace(old, currentName); direction = true }
    if (eval(current.version, ">=26.1")) {
        for ((old, currentName) in listOf(
            "GuiGraphics" to "GuiGraphicsExtractor", "\"render\"" to "\"extractRenderState\"",
            "render(" to "extractRenderState(", "drawString" to "text", "drawCenteredString" to "centeredText",
            "renderWidget" to "extractWidgetRenderState", "renderContents" to "extractContents",
            "renderItemDecorations" to "itemDecorations", "renderItem(" to "item("
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

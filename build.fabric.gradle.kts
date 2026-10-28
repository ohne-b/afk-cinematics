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

val sourceSets = extensions.getByType<SourceSetContainer>()
val main = sourceSets.named("main").get()
val fabric = project.name.endsWith("-fabric")
val smoke = sourceSets.create("smoke") {
    java.setSrcDirs(listOf(rootProject.file("src/smoke/java")))
    java.exclude(if (fabric) "**/NeoForgeSmoke.java" else "**/FabricSmoke.java")
    resources.setSrcDirs(listOf(rootProject.file("src/smoke/resources")))
    compileClasspath += main.compileClasspath + main.output
    runtimeClasspath += main.runtimeClasspath
}
tasks.named<ProcessResources>(smoke.processResourcesTaskName) {
    exclude(if (fabric) "META-INF/neoforge.mods.toml" else "fabric.mod.json")
}

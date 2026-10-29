val sourceSets = extensions.getByType<SourceSetContainer>()
val main = sourceSets.named("main").get()
val fabric = project.name.endsWith("-fabric")
val smoke = sourceSets.create("smoke") {
    java.exclude(if (fabric) "**/NeoForgeSmoke.java" else "**/FabricSmoke.java")
    compileClasspath += main.compileClasspath + main.output
    runtimeClasspath += main.runtimeClasspath
}
tasks.named<ProcessResources>(smoke.processResourcesTaskName) {
    exclude(if (fabric) "META-INF/neoforge.mods.toml" else "fabric.mod.json")
}

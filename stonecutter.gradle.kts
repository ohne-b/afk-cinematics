plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom-remap") version "1.19.3" apply false
    id("net.fabricmc.fabric-loom") version "1.19.3" apply false
    id("net.neoforged.moddev") version "2.0.148" apply false
}

stonecutter active "1.21.11-fabric"

stonecutter parameters {
    constants.match(node.metadata.project.substringAfterLast('-'), "fabric", "neoforge")
}

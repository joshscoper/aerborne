plugins { id("net.fabricmc.fabric-loom") }
repositories {
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}
val sodium = "mc26.2-0.9.2-fabric"
val lithium = "mc26.2-0.25.3-fabric"
val ferriteCore = "9.0.0-fabric"
dependencies {
    minecraft("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.20.1")
    implementation("net.fabricmc.fabric-api:fabric-api:0.161.0+26.2")
    implementation(project(":protocol"))
    include(project(":protocol"))

    // Performance. Lithium (LGPL-3.0) and FerriteCore (MIT) travel inside the client jar. Sodium's licence
    // (PolyForm Shield) asks other mods not to embed it, so it runs here in development and is installed beside
    // the client (fabric.mod.json recommends it).
    include("maven.modrinth:lithium:$lithium")
    include("maven.modrinth:ferrite-core:$ferriteCore")
    runtimeOnly("maven.modrinth:lithium:$lithium")
    runtimeOnly("maven.modrinth:ferrite-core:$ferriteCore")
    runtimeOnly("maven.modrinth:sodium:$sodium")
}
tasks.jar { archiveBaseName.set("aerborne-client") }
tasks.processResources {
    val modVersion = project.version.toString()
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") { expand("version" to modVersion) }
}

plugins { id("net.fabricmc.fabric-loom") }
dependencies {
    minecraft("com.mojang:minecraft:26.2")
    implementation("net.fabricmc:fabric-loader:0.20.1")
    implementation("net.fabricmc.fabric-api:fabric-api:0.161.0+26.2")
    implementation(project(":protocol"))
    include(project(":protocol"))
}
tasks.jar { archiveBaseName.set("aerborne-client") }
tasks.processResources {
    val modVersion = project.version.toString()
    inputs.property("version", modVersion)
    filesMatching("fabric.mod.json") { expand("version" to modVersion) }
}

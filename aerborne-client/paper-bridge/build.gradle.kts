dependencies {
    implementation(project(":protocol"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
}
tasks.jar {
    archiveBaseName.set("aerborne-client-bridge")
    from(project(":protocol").sourceSets.main.get().output)
}
tasks.processResources {
    val bridgeVersion = project.version.toString()
    inputs.property("version", bridgeVersion)
    filesMatching("plugin.yml") { expand("version" to bridgeVersion) }
}

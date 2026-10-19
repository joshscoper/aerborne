plugins {
    id("com.gradleup.shadow")
}

dependencies {
    api(project(":nerdsmp-api"))
    compileOnly("io.papermc.paper:paper-api:26.2.build.121-stable")
    // The server already ships Netty; Watchdog reads its packet pipeline directly. Not bundled.
    compileOnly("io.netty:netty-transport:4.2.15.Final")
    // Paper ships these modules; the read-only API owns its bounded HTTP listener, not another Netty bundle.
    compileOnly("io.netty:netty-codec-http:4.2.15.Final")
    compileOnly("io.netty:netty-handler:4.2.15.Final")

    implementation("com.zaxxer:HikariCP:7.0.2")
    implementation("org.postgresql:postgresql:42.7.8")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:26.2.build.121-stable")
    testImplementation("io.netty:netty-transport:4.2.15.Final")
    testImplementation("io.netty:netty-codec-http:4.2.15.Final")
    testImplementation("io.netty:netty-handler:4.2.15.Final")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
    val properties = mapOf("version" to project.version)
    inputs.properties(properties)
    filesMatching("plugin.yml") {
        expand(properties)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    relocate("com.zaxxer.hikari", "net.nerdsmp.lib.hikari")
    mergeServiceFiles()
}

tasks.jar {
    enabled = false
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

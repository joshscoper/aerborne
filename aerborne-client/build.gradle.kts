plugins {
    base
    id("net.fabricmc.fabric-loom") version "1.19.3" apply false
}
allprojects {
    group = "net.aerborne.client"
    version = "0.3.0-alpha.1"
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
subprojects {
    apply(plugin = "java-library")
    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
    }
    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
}
tasks.named("build") { dependsOn(subprojects.map { "${it.path}:build" }) }

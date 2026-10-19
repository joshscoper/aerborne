plugins {
    base
    id("com.gradleup.shadow") version "9.2.2" apply false
}

allprojects {
    group = "net.nerdsmp"
    version = providers.gradleProperty("version").get()
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
        options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}

tasks.register("qualityGate") {
    group = "verification"
    description = "Runs every module verification task."
    dependsOn(subprojects.map { it.tasks.named("check") })
}


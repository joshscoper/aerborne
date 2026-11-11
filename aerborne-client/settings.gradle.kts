pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
    }
}
rootProject.name = "aerborne-client"
include("protocol", "fabric", "paper-bridge")

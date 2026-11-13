rootProject.name = "Modern Warfare Cubed"

pluginManagement {
    repositories {
        // локальные jar файлы
        flatDir {
            dirs("libs")
        }
        gradlePluginPortal()
        mavenCentral()
        mavenLocal()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.gtnewhorizons.retrofuturagradle") {
                useModule("com.gtnewhorizons:retrofuturagradle:2.0.2")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
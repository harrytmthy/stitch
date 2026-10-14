pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
    }
    repositories {
        exclusiveContent {
            forRepository { mavenLocal() }
            filter { includeGroup("io.github.harrytmthy") }
        }
        mavenCentral()
    }
}
rootProject.name = "stitch-kmp-consumer"
include(":bindings")

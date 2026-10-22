pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenLocal()
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenLocal()
        // Kotlin/JS downloads its Node.js and Yarn toolchains from Ivy distributions.
        ivy {
            url = uri("https://nodejs.org/dist")
            patternLayout { artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("org.nodejs", "node") }
        }
        ivy {
            url = uri("https://github.com/yarnpkg/yarn/releases/download")
            patternLayout { artifact("v[revision]/[artifact](-v[revision]).[ext]") }
            metadataSources { artifact() }
            content { includeModule("com.yarnpkg", "yarn") }
        }

        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "Stitch"
// Metro's Gradle plugin needs a JDK 21+ Gradle daemon, so builds on older JDKs skip the benchmark and samples.
if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
    include(":benchmark")
    include(":sample:shared", ":sample:none", ":sample:stitch", ":sample:dagger", ":sample:koin", ":sample:metro")
    project(":sample").projectDir = file("samples")
    project(":sample").children.forEach { it.projectDir = file("samples/${it.name}") }
}
include(":core")
include(":feature:home")
include(":stitch")
include(":stitch-annotations")
include(":stitch-ksp")

project(":core").projectDir = file("samples/core")
project(":feature").projectDir = file("samples/feature")
project(":feature:home").projectDir = file("samples/feature/home")

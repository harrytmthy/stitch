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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
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
    }
}

rootProject.name = "Stitch"
include(":app")
include(":core")
include(":feature:home")
include(":stitch")
include(":stitch-annotations")
include(":stitch-compiler")

project(":app").projectDir = file("samples/app")
project(":core").projectDir = file("samples/core")
project(":feature").projectDir = file("samples/feature")
project(":feature:home").projectDir = file("samples/feature/home")
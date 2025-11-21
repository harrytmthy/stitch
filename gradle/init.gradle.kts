val ktlintVersion = "1.8.0"

initscript {
    val spotlessVersion = "7.0.2"
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.diffplug.spotless:spotless-plugin-gradle:$spotlessVersion")
    }
}

rootProject {
    subprojects {
        apply<com.diffplug.gradle.spotless.SpotlessPlugin>()
        extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
            when (path) {
                ":app" -> {
                    kotlin {
                        target("**/StitchVsKoinBenchmark.kt")
                        targetExclude("**/build/**/*.kt")
                        ktlint(ktlintVersion).editorConfigOverride(mapOf("android" to "true"))
                        licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
                    }
                }
                else -> {
                    kotlin {
                        target("**/*.kt")
                        targetExclude("**/build/**/*.kt")
                        ktlint(ktlintVersion).editorConfigOverride(mapOf("android" to "true"))
                        licenseHeaderFile(rootProject.file("spotless/copyright.kt"))
                    }
                }
            }
            format("kts") {
                target("**/*.kts")
                targetExclude("**/build/**/*.kts")
                licenseHeaderFile(rootProject.file("spotless/copyright.kts"), "(^(?![\\/ ]\\*).*$)")
            }
        }
    }
}
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.ksp)
}

val stitchVersion = providers.gradleProperty("stitchVersion").get()

kotlin {
    jvm()
    js {
        nodejs()
        browser {
            testTask { useKarma { useChromeHeadless() } }
        }
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    sourceSets {
        commonMain {
            kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
            dependencies {
                implementation(project(":bindings"))
                implementation("io.github.harrytmthy:stitch:$stitchVersion")
                implementation("io.github.harrytmthy:stitch-annotations:$stitchVersion")
            }
        }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}

dependencies {
    add("kspCommonMainMetadata", "io.github.harrytmthy:stitch-ksp:$stitchVersion")
}

// Generate once in commonMain, then compile the same output on every target.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
}
tasks.matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }.configureEach {
    dependsOn("kspCommonMainKotlinMetadata")
}

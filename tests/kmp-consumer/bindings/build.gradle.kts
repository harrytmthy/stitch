plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
}

val stitchVersion = providers.gradleProperty("stitchVersion").get()

kotlin {
    jvm()
    js { nodejs() }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    sourceSets.commonMain {
        kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")
        dependencies {
            implementation("io.github.harrytmthy:stitch-annotations:$stitchVersion")
        }
    }
}

ksp { arg("stitch.moduleName", project.path) }
dependencies {
    add("kspCommonMainMetadata", "io.github.harrytmthy:stitch-ksp:$stitchVersion")
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    if (name != "kspCommonMainKotlinMetadata") dependsOn("kspCommonMainKotlinMetadata")
}

tasks.matching { it.name.startsWith("ksp") && it.name != "kspCommonMainKotlinMetadata" }.configureEach {
    dependsOn("kspCommonMainKotlinMetadata")
}

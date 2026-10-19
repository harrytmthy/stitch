plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp")
}

val stitchVersion = providers.gradleProperty("stitchVersion").get()

kotlin {
    jvm()
    sourceSets {
        jvmMain.dependencies {
            implementation("io.github.harrytmthy:stitch:$stitchVersion")
            implementation("io.github.harrytmthy:stitch-annotations:$stitchVersion")
            implementation(libs.javax.inject)
        }
        jvmTest.dependencies { implementation(kotlin("test")) }
    }
}

dependencies {
    add("kspJvm", "io.github.harrytmthy:stitch-ksp:$stitchVersion")
}

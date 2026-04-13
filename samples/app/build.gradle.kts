/*
 * Copyright 2025 Harry Timothy Tumalewa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.harrytmthy.stitch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.harrytmthy.stitch"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.output.enable"] = "true"
        testInstrumentationRunnerArguments["additionalTestOutputDir"] = "/sdcard/Download/benchmark"
    }

    flavorDimensions += "di"
    productFlavors {
        create("none") {
            dimension = "di"
            matchingFallbacks += "withNone"
        }
        create("stitch") {
            dimension = "di"
            matchingFallbacks += "withStitch"
        }
        create("koin") {
            dimension = "di"
            matchingFallbacks += "withNone"
        }
        create("dagger") {
            dimension = "di"
            matchingFallbacks += "withDagger"
        }
        create("all") {
            dimension = "di"
            matchingFallbacks += "withStitchAndDagger"
        }
    }
    buildTypes {
        debug {
            isDebuggable = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    ksp {
        arg("stitch.moduleName", "App")
    }
}

dependencies {
    // Shared across all flavors
    implementation(project(":core"))
    implementation(project(":feature:home"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    compileOnly(libs.javax.inject)

    // Stitch flavor only
    "stitchImplementation"(project(":stitch"))
    "stitchCompileOnly"(project(":stitch-annotations"))
    "kspStitch"(project(":stitch-compiler"))

    // Dagger flavor only
    "daggerImplementation"(libs.dagger)
    "kspDagger"(libs.dagger.compiler)

    // Koin flavor only
    "koinImplementation"(libs.koin.android)

    // All flavor (Stitch + Dagger + Koin)
    "allImplementation"(project(":stitch"))
    "allCompileOnly"(project(":stitch-annotations"))
    "allImplementation"(libs.dagger)
    "allImplementation"(libs.koin.android)
    "kspAll"(project(":stitch-compiler"))
    "kspAll"(libs.dagger.compiler)

    // Benchmark tests (all variant, since tests reference all 3 frameworks)
    debugImplementation(libs.androidx.benchmark.common)
    "androidTestAllImplementation"(libs.androidx.benchmark.junit4)
    "androidTestAllImplementation"(libs.androidx.test.ext)
    "androidTestAllImplementation"(libs.androidx.test.runner)
    "androidTestAllImplementation"(libs.androidx.test.rules)
    "androidTestAllImplementation"(libs.koin.android)
    "androidTestAllImplementation"(libs.dagger)
}
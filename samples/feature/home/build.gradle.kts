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

plugins {
    alias(libs.plugins.stitch.android.library)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.harrytmthy.stitch.feature.home"

    flavorDimensions += "di"
    productFlavors {
        create("withStitch") { dimension = "di" }
        create("withDagger") { dimension = "di" }
        create("withStitchAndDagger") { dimension = "di" }
        create("withNone") { dimension = "di" }
    }

    ksp {
        arg("stitch.moduleName", "FeatureHome")
    }
}

dependencies {
    compileOnly(libs.javax.inject)
    implementation(project(":core"))
    "withStitchImplementation"(project(":stitch"))
    "withStitchCompileOnly"(project(":stitch-annotations"))
    "withDaggerImplementation"(libs.dagger)
    "withStitchAndDaggerImplementation"(project(":stitch"))
    "withStitchAndDaggerCompileOnly"(project(":stitch-annotations"))
    "withStitchAndDaggerImplementation"(libs.dagger)
    implementation(libs.androidx.appcompat)
    "kspWithStitch"(project(":stitch-compiler"))
    "kspWithDagger"(libs.dagger.compiler)
    "kspWithStitchAndDagger"(project(":stitch-compiler"))
    "kspWithStitchAndDagger"(libs.dagger.compiler)
}
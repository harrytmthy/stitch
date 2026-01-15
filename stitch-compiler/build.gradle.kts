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
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.stitch.publishing)
    `java-gradle-plugin`
}

dependencies {
    implementation(project(":stitch-annotations"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)
    implementation(libs.ksp.api)

    // Support for Dagger annotations
    compileOnly(libs.javax.inject)
}

mavenPublishing {
    pom {
        name.set("Stitch Compiler")
        description.set("Stitch KSP compiler and Gradle plugin for module-aware code generation.")
    }
}

gradlePlugin {
    plugins {
        create("stitchPlugin") {
            id = "io.github.harrytmthy.stitch"
            implementationClass = "com.harrytmthy.stitch.compiler.plugin.StitchPlugin"
        }
    }
}
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
    alias(libs.plugins.stitch.jvm)
    alias(libs.plugins.stitch.publishing)
    alias(libs.plugins.kotlin.binary.compatibility)
}

kotlin {
    sourceSets {
        val main by getting { kotlin.setSrcDirs(listOf("src/commonMain/kotlin")) }
        val test by getting { kotlin.setSrcDirs(listOf("src/commonTest/kotlin")) }
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    testImplementation(libs.kotlin.test)
}

mavenPublishing {
    pom {
        name.set("Stitch")
        description.set("")
    }
}
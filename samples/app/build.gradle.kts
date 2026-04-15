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

// ---------------------------------------------------------------------------
// Fixture code generation (per-flavor)
// ---------------------------------------------------------------------------

val fixtureCount = (findProperty("fixture") as? String)?.toIntOrNull() ?: 10

abstract class GenerateFixturesTask : DefaultTask() {

    @get:Input
    abstract val count: Property<Int>

    @get:Input
    abstract val flavor: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val pkg = "com.harrytmthy.stitch.fixture"
        val pkgPath = pkg.replace('.', '/')
        val n = count.get()
        val fl = flavor.get()
        val baseDir = outputDir.get().asFile
        if (baseDir.exists()) baseDir.deleteRecursively()
        val dir = baseDir.resolve(pkgPath)
        dir.mkdirs()

        // Shared fixture
        dir.resolve("FixtureClasses.kt").writeText(buildString {
            appendLine("package $pkg")
            appendLine()
            if (fl == "stitch") {
                appendLine("import com.harrytmthy.stitch.annotations.Binds")
            }
            appendLine("import javax.inject.Inject")
            appendLine("import javax.inject.Singleton")
            appendLine()
            for (i in 1..n) {
                appendLine("interface Service$i")
                appendLine()
                val dep = if (i == 1) "" else "val dep: Service${i - 1}Impl"
                if (fl == "stitch") {
                    appendLine("@Binds(aliases = [Service${i}::class])")
                }
                appendLine("@Singleton")
                appendLine("class Service${i}Impl @Inject constructor($dep) : Service$i")
                appendLine()
            }
        })

        // InjectionTarget creation
        dir.resolve("InjectionTarget.kt").writeText(buildString {
            appendLine("package $pkg")
            appendLine()
            when (fl) {
                "koin" -> {
                    appendLine("import org.koin.core.component.KoinComponent")
                    appendLine("import org.koin.core.component.get")
                }
                "none" -> {}
                else -> appendLine("import javax.inject.Inject")
            }
            appendLine()
            val impl = if (fl == "koin") ": KoinComponent" else ""
            appendLine("class InjectionTarget$impl {")
            for (i in 1..n) {
                val annotation = if (fl in listOf("stitch", "dagger")) "@Inject " else ""
                appendLine("    ${annotation}lateinit var service$i: Service$i")
            }
            if (fl == "koin") {
                appendLine()
                appendLine("    fun inject() {")
                for (i in 1..n) {
                    appendLine("        service$i = get<Service$i>()")
                }
                appendLine("    }")
            }
            appendLine("}")
        })

        // Dagger wiring
        if (fl == "dagger") {
            dir.resolve("DaggerFixture.kt").writeText(buildString {
                appendLine("package $pkg")
                appendLine()
                appendLine("import dagger.Binds")
                appendLine("import dagger.Component")
                appendLine("import dagger.Module")
                appendLine("import javax.inject.Singleton")
                appendLine()
                appendLine("@Module")
                appendLine("interface DaggerFixtureModule {")
                for (i in 1..n) {
                    appendLine()
                    appendLine("    @Binds")
                    appendLine("    fun bindService$i(impl: Service${i}Impl): Service$i")
                }
                appendLine("}")
                appendLine()
                appendLine("@Singleton")
                appendLine("@Component(modules = [DaggerFixtureModule::class])")
                appendLine("interface DaggerFixtureComponent {")
                appendLine("    fun inject(target: InjectionTarget)")
                appendLine("}")
            })
        }

        // Koin wiring
        if (fl == "koin") {
            dir.resolve("KoinFixture.kt").writeText(buildString {
                appendLine("package $pkg")
                appendLine()
                appendLine("import org.koin.core.module.dsl.singleOf")
                appendLine("import org.koin.dsl.bind")
                appendLine("import org.koin.dsl.module")
                appendLine()
                appendLine("val fixtureModule = module {")
                for (i in 1..n) {
                    appendLine("    singleOf(::Service${i}Impl) bind Service${i}::class")
                }
                appendLine("}")
            })
        }
    }
}

androidComponents {
    onVariants { variant ->
        val flavorName = variant.productFlavors.firstOrNull()?.second ?: "none"
        val taskProvider = tasks.register<GenerateFixturesTask>(
            "generateFixtures${variant.name.replaceFirstChar { it.uppercase() }}"
        ) {
            count.set(fixtureCount)
            flavor.set(flavorName)
            outputDir.set(layout.buildDirectory.dir("generated/fixtures/${variant.name}"))
        }
        variant.sources.java?.addGeneratedSourceDirectory(
            taskProvider,
            GenerateFixturesTask::outputDir,
        )
    }
}

tasks.matching { it.name.startsWith("ksp") }
    .configureEach {
        val variantName = name.removePrefix("ksp").removeSuffix("Kotlin")
        val genTaskName = "generateFixtures$variantName"
        if (tasks.names.contains(genTaskName)) dependsOn(genTaskName)
    }

// ---------------------------------------------------------------------------
// Dependencies
// ---------------------------------------------------------------------------

val excludeOtherModules = (findProperty("excludeOtherModules") as? String)?.toBoolean() ?: false

dependencies {
    // Shared across all flavors
    if (!excludeOtherModules) {
        implementation(project(":core"))
        implementation(project(":feature:home"))
    }
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
}
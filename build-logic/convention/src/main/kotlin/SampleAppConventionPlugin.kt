/*
 * Copyright 2026 Harry Timothy Tumalewa
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

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

class SampleAppConventionPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        with(target) {
            // Each sample module is named after its DI framework, so every module shares one application shell.
            val di = name
            apply(plugin = "com.android.application")
            extensions.configure<ApplicationExtension> {
                namespace = "com.harrytmthy.stitch"
                compileSdk = 36
                defaultConfig {
                    applicationId = "com.harrytmthy.stitch"
                    minSdk = 23
                    targetSdk = 36
                    versionCode = 1
                    versionName = "1.0"
                    missingDimensionStrategy(
                        "di",
                        when (di) {
                            "stitch" -> "withStitch"
                            "dagger" -> "withDagger"
                            else -> "withNone"
                        },
                    )
                }
                buildTypes {
                    named("debug") {
                        isDebuggable = false
                    }
                    named("release") {
                        isMinifyEnabled = false
                    }
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_11
                    targetCompatibility = JavaVersion.VERSION_11
                }
            }
            extensions.configure<KotlinAndroidProjectExtension> {
                compilerOptions.jvmTarget = JvmTarget.JVM_11
            }

            val generateFixtures = tasks.register<GenerateFixturesTask>("generateFixtures") {
                count = (findProperty("fixture") as? String)?.toIntOrNull() ?: 10
                flavor = di
                outputDir = layout.buildDirectory.dir("generated/fixtures")
            }
            extensions.configure<ApplicationAndroidComponentsExtension> {
                onVariants { variant ->
                    variant.sources.java?.addGeneratedSourceDirectory(generateFixtures, GenerateFixturesTask::outputDir)
                }
            }
            tasks.matching { it.name.startsWith("ksp") }.configureEach { dependsOn(generateFixtures) }

            val excludeOtherModules = (findProperty("excludeOtherModules") as? String)?.toBoolean() ?: false
            dependencies {
                "implementation"(project(":sample:shared"))
                if (!excludeOtherModules) {
                    "implementation"(project(":core"))
                    "implementation"(project(":feature:home"))
                }
            }
        }
    }
}

@CacheableTask
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

        // Fixture classes (per-flavor annotations)
        dir.resolve("FixtureClasses.kt").writeText(buildString {
            appendLine("package $pkg")
            appendLine()
            when (fl) {
                "stitch" -> {
                    appendLine("import com.harrytmthy.stitch.annotations.Binds")
                    appendLine("import javax.inject.Inject")
                    appendLine("import javax.inject.Singleton")
                }
                "dagger" -> {
                    appendLine("import javax.inject.Inject")
                    appendLine("import javax.inject.Singleton")
                }
                "koin" -> {
                    appendLine("import org.koin.core.annotation.Single")
                }
                "metro" -> {
                    appendLine("import dev.zacsweers.metro.AppScope")
                    appendLine("import dev.zacsweers.metro.Inject")
                    appendLine("import dev.zacsweers.metro.SingleIn")
                }
            }
            appendLine()
            for (i in 1..n) {
                appendLine("interface Service$i")
                appendLine()
                val dep = if (i == 1) "" else "val dep: Service${i - 1}Impl"
                when (fl) {
                    "stitch" -> {
                        appendLine("@Binds(aliases = [Service${i}::class])")
                        appendLine("@Singleton")
                        appendLine("class Service${i}Impl @Inject constructor($dep) : Service$i")
                    }
                    "dagger" -> {
                        appendLine("@Singleton")
                        appendLine("class Service${i}Impl @Inject constructor($dep) : Service$i")
                    }
                    "koin" -> {
                        appendLine("@Single")
                        appendLine("class Service${i}Impl($dep) : Service$i")
                    }
                    "metro" -> {
                        appendLine("@SingleIn(AppScope::class)")
                        appendLine("class Service${i}Impl @Inject constructor($dep) : Service$i")
                    }
                    else -> {
                        appendLine("class Service${i}Impl($dep) : Service$i")
                    }
                }
                appendLine()
            }
        })

        // Injection targets: the fields are split across two targets, so neither exceeds the JVM's 255-parameter limit
        val half = (n + 1) / 2
        writeTarget(dir, pkg, fl, "InjectionTarget", 1..half)
        writeTarget(dir, pkg, fl, "InjectionTarget2", half + 1..n)

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
                appendLine("    fun inject(target: InjectionTarget2)")
                appendLine("}")
            })
        }

        // Metro wiring
        if (fl == "metro") {
            dir.resolve("MetroFixture.kt").writeText(buildString {
                appendLine("package $pkg")
                appendLine()
                appendLine("import dev.zacsweers.metro.AppScope")
                appendLine("import dev.zacsweers.metro.Binds")
                appendLine("import dev.zacsweers.metro.DependencyGraph")
                appendLine()
                appendLine("@DependencyGraph(AppScope::class)")
                appendLine("interface MetroFixtureGraph {")
                appendLine("    fun inject(target: InjectionTarget)")
                appendLine("    fun inject(target: InjectionTarget2)")
                for (i in 1..n) {
                    appendLine()
                    appendLine("    @Binds")
                    appendLine("    fun bindService$i(impl: Service${i}Impl): Service$i")
                }
                appendLine("}")
            })
        }

        // Koin module (annotation-based, auto-discovers @Single classes)
        if (fl == "koin") {
            dir.resolve("KoinFixtureModule.kt").writeText(buildString {
                appendLine("package $pkg")
                appendLine()
                appendLine("import org.koin.core.annotation.ComponentScan")
                appendLine("import org.koin.core.annotation.Module")
                appendLine()
                appendLine("@Module")
                appendLine("@ComponentScan")
                appendLine("class KoinFixtureModule")
            })
        }
    }

    private fun writeTarget(dir: java.io.File, pkg: String, fl: String, name: String, range: IntRange) {
        dir.resolve("$name.kt").writeText(buildString {
            appendLine("package $pkg")
            appendLine()
            when (fl) {
                "koin" -> {
                    appendLine("import org.koin.core.component.KoinComponent")
                    appendLine("import org.koin.core.component.get")
                }
                "stitch", "dagger" -> appendLine("import javax.inject.Inject")
                "metro" -> appendLine("import dev.zacsweers.metro.Inject")
            }
            appendLine()
            val impl = if (fl == "koin") ": KoinComponent" else ""
            appendLine("class $name$impl {")
            for (i in range) {
                val annotation = if (fl in listOf("stitch", "dagger", "metro")) "@Inject " else ""
                appendLine("    ${annotation}lateinit var service$i: Service$i")
            }
            when (fl) {
                "koin" -> {
                    appendLine()
                    appendLine("    fun inject() {")
                    for (i in range) {
                        appendLine("        service$i = get<Service$i>()")
                    }
                    appendLine("    }")
                }
                "none" -> {
                    appendLine()
                    // The second target continues the chain from the last service of the first target.
                    val previous = if (range.first == 1) "" else "previous: InjectionTarget"
                    appendLine("    fun inject($previous) {")
                    for (i in range) {
                        val owner = if (i - 1 < range.first) "previous." else ""
                        val dep = if (i == 1) "" else "${owner}service${i - 1} as Service${i - 1}Impl"
                        appendLine("        service$i = Service${i}Impl($dep)")
                    }
                    appendLine("    }")
                }
            }
            appendLine("}")
        })
    }
}

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

plugins {
    alias(libs.plugins.stitch.android.library)
    alias(libs.plugins.ksp)
}

val fixtureCount = (findProperty("fixture") as? String)?.toIntOrNull() ?: 10

android {
    namespace = "com.harrytmthy.stitch.benchmark"

    defaultConfig {
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.output.enable"] = "true"
        testInstrumentationRunnerArguments["additionalTestOutputDir"] = "/sdcard/Download/benchmark"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    testBuildType = "release"

    ksp {
        arg("stitch.moduleName", "Benchmark")
    }
}

abstract class GenerateFixturesTask : DefaultTask() {

    @get:Input
    abstract val count: Property<Int>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val pkg = "com.harrytmthy.stitch.benchmark.fixture"
        val pkgPath = pkg.replace('.', '/')
        val n = count.get()
        val baseDir = outputDir.get().asFile
        if (baseDir.exists()) baseDir.deleteRecursively()
        val dir = baseDir.resolve(pkgPath)
        dir.mkdirs()

        // Shared InjectionTarget
        dir.resolve("InjectionTarget.kt").writeText(buildString {
            appendLine("package $pkg")
            appendLine()
            appendLine("import com.harrytmthy.stitch.api.Stitch")
            appendLine("import javax.inject.Inject")
            appendLine("import org.koin.core.Koin")
            appendLine()
            appendLine("class InjectionTarget {")
            for (i in 1..n) {
                appendLine("    @Inject lateinit var service$i: Service$i")
            }
            appendLine()
            appendLine("    fun injectWithKoin(koin: Koin) {")
            for (i in 1..n) {
                appendLine("        service$i = koin.get()")
            }
            appendLine("    }")
            appendLine()
            appendLine("    fun injectWithStitch() {")
            for (i in 1..n) {
                appendLine("        service$i = Stitch.get()")
            }
            appendLine("    }")
            appendLine("}")
        })

        // Factory injection target (for cold-path factory benchmarks)
        dir.resolve("FactoryInjectionTarget.kt").writeText(buildString {
            appendLine("package $pkg")
            appendLine()
            appendLine("import com.harrytmthy.stitch.api.Stitch")
            appendLine("import javax.inject.Inject")
            appendLine("import org.koin.core.Koin")
            appendLine()
            appendLine("class FactoryInjectionTarget {")
            appendLine("    @Inject lateinit var service1: Service1Factory")
            appendLine()
            appendLine("    fun injectWithKoin(koin: Koin) {")
            appendLine("        service1 = koin.get()")
            appendLine("    }")
            appendLine()
            appendLine("    fun injectWithStitch() {")
            appendLine("        service1 = Stitch.get()")
            appendLine("    }")
            appendLine("}")
        })

        // Stitch precompiled path wiring
        dir.resolve("StitchPrecompiledFixture.kt").writeText(buildString {
            appendLine("package $pkg")
            appendLine()
            appendLine("import com.harrytmthy.stitch.annotations.Binds")
            appendLine("import javax.inject.Inject")
            appendLine("import javax.inject.Singleton")
            appendLine()
            for (i in 1..n) {
                appendLine("interface Service$i")
                appendLine()
                val dep = if (i == 1) "" else "val dep: Service${i - 1}Singleton"
                appendLine("@Binds(aliases = [Service${i}::class])")
                appendLine("@Singleton")
                appendLine("class Service${i}Singleton @Inject constructor($dep) : Service$i")
                appendLine()
            }
            // Factory classes: descending chain (Service1Factory depends on Service2Factory)
            // so resolving Service1Factory always traverses the full depth on every call
            for (i in 1..n) {
                val dep = if (i == n) "" else "val dep: Service${i + 1}Factory"
                appendLine("class Service${i}Factory @Inject constructor($dep)")
                appendLine()
            }
        })

        // Dagger wiring
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
                appendLine("    fun bindService$i(impl: Service${i}Singleton): Service$i")
            }
            appendLine("}")
            appendLine()
            appendLine("@Singleton")
            appendLine("@Component(modules = [DaggerFixtureModule::class])")
            appendLine("interface DaggerFixtureComponent {")
            appendLine("    fun inject(target: InjectionTarget)")
            appendLine("    fun inject(target: FactoryInjectionTarget)")
            appendLine("}")
        })

        // Koin wiring
        dir.resolve("KoinFixture.kt").writeText(buildString {
            appendLine("package $pkg")
            appendLine()
            appendLine("import org.koin.core.module.dsl.factoryOf")
            appendLine("import org.koin.core.module.dsl.singleOf")
            appendLine("import org.koin.dsl.bind")
            appendLine("import org.koin.dsl.module")
            appendLine()
            appendLine("val koinFixtureModule = module {")
            for (i in 1..n) {
                appendLine("    singleOf(::Service${i}Singleton) bind Service${i}::class")
            }
            for (i in 1..n) {
                appendLine("    factoryOf(::Service${i}Factory)")
            }
            appendLine("}")
        })

        // Stitch runtime registration path wiring
        dir.resolve("StitchRuntimeFixture.kt").writeText(buildString {
            appendLine("package $pkg")
            appendLine()
            appendLine("import com.harrytmthy.stitch.api.bind")
            appendLine("import com.harrytmthy.stitch.api.get")
            appendLine("import com.harrytmthy.stitch.api.module")
            appendLine()
            appendLine("val stitchRuntimeFixtureModule = module {")
            for (i in 1..n) {
                val dep = if (i == 1) "Service${i}Singleton()" else "Service${i}Singleton(dep = get())"
                appendLine("    singleton<Service${i}Singleton> { $dep }.bind<Service$i>()")
            }
            for (i in 1..n) {
                val dep = if (i == n) "Service${i}Factory()" else "Service${i}Factory(dep = get())"
                appendLine("    factory { $dep }")
            }
            appendLine("}")
        })
    }
}

val generateFixtures = tasks.register<GenerateFixturesTask>("generateFixtures") {
    count.set(fixtureCount)
    outputDir.set(layout.buildDirectory.dir("generated/fixtures"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.java?.addGeneratedSourceDirectory(
            generateFixtures,
            GenerateFixturesTask::outputDir,
        )
    }
}

tasks.matching { it.name.startsWith("ksp") }
    .configureEach { dependsOn(generateFixtures) }

dependencies {
    // Stitch
    implementation(project(":stitch"))
    implementation(project(":stitch-annotations"))
    "ksp"(project(":stitch-ksp"))

    // Dagger
    implementation(libs.dagger)
    ksp(libs.dagger.compiler)

    // Koin
    implementation(libs.koin.android)

    // javax.inject (shared annotations for Dagger + Stitch)
    compileOnly(libs.javax.inject)

    // Benchmark test dependencies
    androidTestImplementation(libs.androidx.benchmark.junit4)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}

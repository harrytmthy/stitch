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

package com.harrytmthy.stitch

import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.MicrobenchmarkConfig
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.harrytmthy.stitch.api.ScopeRef
import com.harrytmthy.stitch.api.Stitch
import com.harrytmthy.stitch.api.bind
import com.harrytmthy.stitch.api.module
import com.harrytmthy.stitch.api.named
import com.harrytmthy.stitch.api.scope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.qualifier.Qualifier
import org.koin.dsl.koinApplication
import org.koin.core.qualifier.named as koinNamed
import org.koin.dsl.bind as bindKoin
import org.koin.dsl.module as koinModule

@RunWith(AndroidJUnit4::class)
class StitchVsKoinBenchmark {

    @OptIn(ExperimentalBenchmarkConfigApi::class)
    @get:Rule
    val benchmarkRule = BenchmarkRule(
        MicrobenchmarkConfig(
            warmupCount = 20,
            measurementCount = 100,
        ),
    )

    // global sink to prevent DCE
    @Volatile private var sink: Any? = null

    @Test
    fun stitchWarm() {
        Stitch.register(createStitchModule(deep = false, leafSingleton = true))

        // JIT priming
        repeat(10) { Stitch.get<LocalRepo>() }

        benchmarkRule.measureRepeated {
            sink = Stitch.get<LocalRepo>()
        }
        Stitch.reset()
    }

    @Test
    fun stitchDeepWarm() {
        val screenRef = scope("screen")
        val module = createStitchModule(
            deep = true,
            leafSingleton = true,
            scopeRef = screenRef,
        )
        Stitch.register(module)
        val scope = screenRef.createScope().apply { open() }

        // JIT priming
        repeat(10) { sink = Stitch.get<DeepViewModel>(scope = scope) }

        benchmarkRule.measureRepeated {
            sink = Stitch.get<DeepViewModel>(scope = scope)
        }
        Stitch.reset()
    }

    @Test
    fun stitchCold_withRegister() {
        benchmarkRule.measureRepeated {
            val module = createStitchModule(deep = false, leafSingleton = false)
            Stitch.register(module)
            sink = Stitch.get<LocalRepo>()
            runWithMeasurementDisabled { Stitch.reset() }
        }
    }

    @Test
    fun stitchDeepCold_withRegister() {
        benchmarkRule.measureRepeated {
            val screenRef = scope("screen")
            val module = createStitchModule(
                deep = true,
                leafSingleton = false,
                scopeRef = screenRef,
            )
            Stitch.register(module)
            val scope = screenRef.createScope().apply { open() }

            sink = Stitch.get<DeepViewModel>(scope = scope)

            runWithMeasurementDisabled { Stitch.reset() }
        }
    }

    @Test
    fun stitchCold_engine() {
        benchmarkRule.measureRepeated {
            val module = runWithMeasurementDisabled {
                createStitchModule(deep = false, leafSingleton = false)
            }
            runWithMeasurementDisabled { Stitch.register(module) }

            sink = Stitch.get<LocalRepo>()

            runWithMeasurementDisabled { Stitch.reset() }
        }
    }

    @Test
    fun stitchDeepCold_engine() {
        benchmarkRule.measureRepeated {
            val scope = runWithMeasurementDisabled {
                val screenRef = scope("screen")
                val module = createStitchModule(
                    deep = true,
                    leafSingleton = false,
                    scopeRef = screenRef,
                )
                Stitch.register(module)
                screenRef.createScope().apply { open() }
            }

            sink = Stitch.get<DeepViewModel>(scope = scope)

            runWithMeasurementDisabled { Stitch.reset() }
        }
    }

    @Test
    fun stitchEager() {
        benchmarkRule.measureRepeated {
            val module = createStitchModule(deep = false, leafSingleton = true, eager = true)
            Stitch.register(module)
            sink = Stitch.get<LocalRepo>()
            runWithMeasurementDisabled { Stitch.reset() }
        }
    }

    @Test
    fun stitchDeepEager() {
        benchmarkRule.measureRepeated {
            val screenRef = scope("screen")
            val module = createStitchModule(
                deep = true,
                leafSingleton = true,
                eager = true,
                scopeRef = screenRef,
            )
            Stitch.register(module)
            val scope = screenRef.createScope().apply { open() }

            sink = Stitch.get<DeepViewModel>(scope = scope)

            runWithMeasurementDisabled { Stitch.reset() }
        }
    }

    @Test
    fun stitch_registerOnly() {
        benchmarkRule.measureRepeated {
            val module = runWithMeasurementDisabled {
                createStitchModule(deep = true, leafSingleton = true, eager = false)
            }
            Stitch.register(module)
            runWithMeasurementDisabled { Stitch.reset() }
        }
    }

    @Test
    fun stitchEager_registerOnly() {
        benchmarkRule.measureRepeated {
            val module = runWithMeasurementDisabled {
                createStitchModule(deep = true, leafSingleton = true, eager = true)
            }
            Stitch.register(module)
            runWithMeasurementDisabled { Stitch.reset() }
        }
    }

    @Test
    fun stitch_unregisterOnly() {
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled {
                val module = createStitchModule(deep = true, leafSingleton = true, eager = false)
                Stitch.register(module)
            }
            Stitch.reset()
        }
    }

    @Test
    fun koinWarm() {
        val koinApp = koinApplication {
            modules(createKoinModule(deep = false, leafSingleton = true))
        }

        // JIT priming
        repeat(10) { koinApp.koin.get<LocalRepo>() }

        benchmarkRule.measureRepeated {
            sink = koinApp.koin.get<LocalRepo>()
        }
        koinApp.close()
    }

    @Test
    fun koinDeepWarm() {
        val scopeQualifier = koinNamed("screen")
        val koinApp = koinApplication {
            val module = createKoinModule(
                deep = true,
                leafSingleton = true,
                scopeQualifier = scopeQualifier,
            )
            modules(module)
        }
        val scope = koinApp.koin.createScope("screen-1", scopeQualifier)

        // JIT priming
        repeat(10) { sink = scope.get<DeepViewModel>() }

        benchmarkRule.measureRepeated {
            sink = scope.get<DeepViewModel>()
        }
        koinApp.close()
    }

    @Test
    fun koinCold_withRegister() {
        benchmarkRule.measureRepeated {
            val koinApp = koinApplication {
                modules(createKoinModule(deep = false, leafSingleton = false))
            }
            sink = koinApp.koin.get<LocalRepo>()
            runWithMeasurementDisabled { koinApp.close() }
        }
    }

    @Test
    fun koinDeepCold_withRegister() {
        benchmarkRule.measureRepeated {
            val scopeQualifier = koinNamed("screen")
            val koinApp = koinApplication {
                val module = createKoinModule(
                    deep = true,
                    leafSingleton = false,
                    scopeQualifier = scopeQualifier,
                )
                modules(module)
            }
            val scope = koinApp.koin.createScope("screen-1", scopeQualifier)

            sink = scope.get<DeepViewModel>()

            runWithMeasurementDisabled { koinApp.close() }
        }
    }

    @Test
    fun koinCold_engine() {
        benchmarkRule.measureRepeated {
            val koinApp = runWithMeasurementDisabled {
                val module = createKoinModule(deep = false, leafSingleton = false)
                koinApplication { modules(module) }
            }

            sink = koinApp.koin.get<LocalRepo>()

            runWithMeasurementDisabled { koinApp.close() }
        }
    }

    @Test
    fun koinDeepCold_engine() {
        benchmarkRule.measureRepeated {
            val scopeQualifier = runWithMeasurementDisabled { koinNamed("screen") }
            val koinApp = runWithMeasurementDisabled {
                val module = createKoinModule(deep = true, leafSingleton = false, scopeQualifier = scopeQualifier)
                koinApplication { modules(module) }
            }
            val scope = runWithMeasurementDisabled {
                koinApp.koin.createScope("screen-1", scopeQualifier)
            }

            sink = scope.get<DeepViewModel>()

            runWithMeasurementDisabled { koinApp.close() }
        }
    }

    @Test
    fun koinEager() {
        benchmarkRule.measureRepeated {
            val koinApp = koinApplication {
                modules(createKoinModule(deep = false, leafSingleton = true, eager = true))
            }
            sink = koinApp.koin.get<LocalRepo>()
            runWithMeasurementDisabled { koinApp.close() }
        }
    }

    @Test
    fun koinDeepEager() {
        benchmarkRule.measureRepeated {
            val scopeQualifier = koinNamed("screen")
            val koinApp = koinApplication {
                val module = createKoinModule(
                    deep = true,
                    leafSingleton = true,
                    eager = true,
                    scopeQualifier = scopeQualifier,
                )
                modules(module)
            }
            val scope = koinApp.koin.createScope("screen-1", scopeQualifier)

            sink = scope.get<DeepViewModel>()

            runWithMeasurementDisabled { koinApp.close() }
        }
    }

    @Test
    fun koin_registerOnly() {
        benchmarkRule.measureRepeated {
            val koinModule = runWithMeasurementDisabled {
                createKoinModule(deep = true, leafSingleton = true, eager = false)
            }
            val koinApp = koinApplication { modules(koinModule) }
            runWithMeasurementDisabled { koinApp.close() }
        }
    }

    @Test
    fun koinEager_registerOnly() {
        benchmarkRule.measureRepeated {
            val koinModule = runWithMeasurementDisabled {
                createKoinModule(deep = true, leafSingleton = true, eager = true)
            }
            val koinApp = koinApplication { modules(koinModule) }
            runWithMeasurementDisabled { koinApp.close() }
        }
    }

    @Test
    fun koin_unregisterOnly() {
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled {
                val koinModule = createKoinModule(deep = true, leafSingleton = true, eager = false)
                koinApplication { modules(koinModule) }
            }.close()
        }
    }

    // Change signatures to accept an optional scope handle
    private fun createStitchModule(
        deep: Boolean,
        leafSingleton: Boolean,
        eager: Boolean = false,
        scopeRef: ScopeRef? = null,
    ) = module(forceEager = eager) {
        singleton { Logger() }
        singleton { Dao(get()) }
        singleton { Json() }
        singleton { Mapper(get()) }
        if (!deep) {
            if (leafSingleton) {
                singleton { LocalRepo(get(), get()) }
            } else {
                factory { LocalRepo(get(), get()) }
            }
            return@module
        }
        singleton { LocalRepo(get(), get()) }
        singleton { NetworkClient(get()) }
        singleton { Cache(get()) }
        singleton(named("remote")) { RemoteRepo(get(), get(), get()) }
            .bind<IRemoteRepo>()
        singleton {
            val local: LocalRepo = get()
            val remote: RemoteRepo = get(named("remote"))
            CombinedRepo(local, remote)
        }
        singleton { UseCaseA(get(), get()) }
        singleton { UseCaseB(get(), get()) }
        singleton { DeepService(get(), get()) }.bind<IService>()
        if (scopeRef != null) {
            scoped(scopeRef) { DeepViewModel(get()) }.bind<IViewModel>()
        } else {
            if (leafSingleton) {
                singleton { DeepViewModel(get()) }
            } else {
                factory { DeepViewModel(get()) }
            }.bind<IViewModel>()
        }
    }

    private fun createKoinModule(
        deep: Boolean,
        leafSingleton: Boolean,
        eager: Boolean = false,
        scopeQualifier: Qualifier? = null,
    ) = koinModule(createdAtStart = eager) {
        single { Logger() }
        single { Dao(get()) }
        single { Json() }
        single { Mapper(get()) }
        if (!deep) {
            if (leafSingleton) {
                single { LocalRepo(get(), get()) }
            } else {
                factory { LocalRepo(get(), get()) }
            }
            return@koinModule
        }
        single { LocalRepo(get(), get()) }
        single { NetworkClient(get()) }
        single { Cache(get()) }
        single(koinNamed("remote")) {
            RemoteRepo(get(), get(), get())
        }.bindKoin<IRemoteRepo>()
        single {
            val local: LocalRepo = get()
            val remote: RemoteRepo = get(koinNamed("remote"))
            CombinedRepo(local, remote)
        }
        single { UseCaseA(get(), get()) }
        single { UseCaseB(get(), get()) }
        single { DeepService(get(), get()) }.bindKoin<IService>()
        if (scopeQualifier != null) {
            scope(scopeQualifier) {
                scoped { DeepViewModel(get()) }.bindKoin<IViewModel>()
            }
        } else {
            if (leafSingleton) {
                single { DeepViewModel(get()) }
            } else {
                factory { DeepViewModel(get()) }
            }.bindKoin<IViewModel>()
        }
    }

    // ----------- tiny graph (LocalRepo) -----------
    data class Logger(val tag: String = "Deep")
    class Json
    class NetworkClient(val logger: Logger)
    class Dao(val logger: Logger)
    class Cache(val logger: Logger)
    class Mapper(val json: Json)

    class LocalRepo(val dao: Dao, val mapper: Mapper)

    // ----------- deep graph (DeepViewModel) -----------
    interface IRemoteRepo
    interface IService
    interface IViewModel
    class RemoteRepo(val net: NetworkClient, val json: Json, val mapper: Mapper) : IRemoteRepo
    class CombinedRepo(val local: LocalRepo, val remote: RemoteRepo)
    class UseCaseA(val repo: CombinedRepo, val cache: Cache)
    class UseCaseB(val repo: CombinedRepo, val logger: Logger)
    class DeepService(val a: UseCaseA, val b: UseCaseB) : IService
    class DeepViewModel(val service: DeepService) : IViewModel
}

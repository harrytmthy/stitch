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
import com.harrytmthy.stitch.api.Stitch
import com.harrytmthy.stitch.api.module
import com.harrytmthy.stitch.api.named
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.koinApplication
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
        Stitch.unregister()
    }

    @Test
    fun stitchDeepWarm() {
        val module = createStitchModule(deep = true, leafSingleton = true)
        Stitch.register(module)

        repeat(10) { Stitch.get<DeepViewModel>() }

        benchmarkRule.measureRepeated {
            sink = Stitch.get<DeepViewModel>()
        }
        Stitch.unregister()
    }

    @Test
    fun stitchCold_e2e() {
        benchmarkRule.measureRepeated {
            val module = createStitchModule(deep = false, leafSingleton = false)
            Stitch.register(module)
            sink = Stitch.get<LocalRepo>()
            runWithMeasurementDisabled { Stitch.unregister() }
        }
    }

    @Test
    fun stitchDeepCold_e2e() {
        benchmarkRule.measureRepeated {
            val module = createStitchModule(deep = true, leafSingleton = false)
            Stitch.register(module)
            sink = Stitch.get<DeepViewModel>()
            runWithMeasurementDisabled { Stitch.unregister() }
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

            runWithMeasurementDisabled { Stitch.unregister() }
        }
    }

    @Test
    fun stitchDeepCold_engine() {
        benchmarkRule.measureRepeated {
            val module = runWithMeasurementDisabled {
                createStitchModule(deep = true, leafSingleton = false)
            }
            runWithMeasurementDisabled { Stitch.register(module) }

            sink = Stitch.get<DeepViewModel>()

            runWithMeasurementDisabled { Stitch.unregister() }
        }
    }

    @Test
    fun stitchEager() {
        benchmarkRule.measureRepeated {
            val module = createStitchModule(deep = false, leafSingleton = true, eager = true)
            Stitch.register(module)
            sink = Stitch.get<LocalRepo>()
            runWithMeasurementDisabled { Stitch.unregister() }
        }
    }

    @Test
    fun stitchDeepEager() {
        benchmarkRule.measureRepeated {
            val module = createStitchModule(deep = true, leafSingleton = true, eager = true)
            Stitch.register(module)
            sink = Stitch.get<DeepViewModel>()
            runWithMeasurementDisabled { Stitch.unregister() }
        }
    }

    @Test
    fun stitch_register() {
        benchmarkRule.measureRepeated {
            val module = runWithMeasurementDisabled {
                createStitchModule(deep = true, leafSingleton = true, eager = false)
            }
            Stitch.register(module)
            runWithMeasurementDisabled { Stitch.unregister() }
        }
    }

    @Test
    fun stitchEager_register() {
        benchmarkRule.measureRepeated {
            val module = runWithMeasurementDisabled {
                createStitchModule(deep = true, leafSingleton = true, eager = true)
            }
            Stitch.register(module)
            runWithMeasurementDisabled { Stitch.unregister() }
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
        val koinApp = koinApplication {
            modules(createKoinModule(deep = true, leafSingleton = true))
        }

        repeat(10) { koinApp.koin.get<DeepViewModel>() }

        benchmarkRule.measureRepeated {
            sink = koinApp.koin.get<DeepViewModel>()
        }
        koinApp.close()
    }

    @Test
    fun koinCold_e2e() {
        benchmarkRule.measureRepeated {
            val koinApp = koinApplication {
                modules(createKoinModule(deep = false, leafSingleton = false))
            }
            sink = koinApp.koin.get<LocalRepo>()
            runWithMeasurementDisabled { koinApp.close() }
        }
    }

    @Test
    fun koinDeepCold_e2e() {
        benchmarkRule.measureRepeated {
            val koinApp = koinApplication {
                modules(createKoinModule(deep = true, leafSingleton = false))
            }
            sink = koinApp.koin.get<DeepViewModel>()
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
            val koinApp = runWithMeasurementDisabled {
                val module = createKoinModule(deep = true, leafSingleton = false)
                koinApplication { modules(module) }
            }

            sink = koinApp.koin.get<DeepViewModel>()

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
            val koinApp = koinApplication {
                modules(createKoinModule(deep = true, leafSingleton = true, eager = true))
            }
            sink = koinApp.koin.get<DeepViewModel>()
            runWithMeasurementDisabled { koinApp.close() }
        }
    }

    @Test
    fun koin_register() {
        benchmarkRule.measureRepeated {
            val koinModule = runWithMeasurementDisabled {
                createKoinModule(deep = true, leafSingleton = true, eager = false)
            }
            val koinApp = koinApplication { modules(koinModule) }
            runWithMeasurementDisabled { koinApp.close() }
        }
    }

    @Test
    fun koinEager_register() {
        benchmarkRule.measureRepeated {
            val koinModule = runWithMeasurementDisabled {
                createKoinModule(deep = true, leafSingleton = true, eager = true)
            }
            val koinApp = koinApplication { modules(koinModule) }
            runWithMeasurementDisabled { koinApp.close() }
        }
    }

    private fun createStitchModule(deep: Boolean, leafSingleton: Boolean, eager: Boolean = false) =
        module(overrideEager = eager) {
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
            if (leafSingleton) {
                singleton { DeepViewModel(get()) }
            } else {
                factory { DeepViewModel(get()) }
            }.bind<IViewModel>()
        }

    private fun createKoinModule(deep: Boolean, leafSingleton: Boolean, eager: Boolean = false) =
        koinModule(createdAtStart = eager) {
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
            single(org.koin.core.qualifier.named("remote")) {
                RemoteRepo(get(), get(), get())
            }.bindKoin<IRemoteRepo>()
            single {
                val local: LocalRepo = get()
                val remote: RemoteRepo = get(org.koin.core.qualifier.named("remote"))
                CombinedRepo(local, remote)
            }
            single { UseCaseA(get(), get()) }
            single { UseCaseB(get(), get()) }
            single { DeepService(get(), get()) }.bindKoin<IService>()
            if (leafSingleton) {
                single { DeepViewModel(get()) }
            } else {
                factory { DeepViewModel(get()) }
            }.bindKoin<IViewModel>()
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

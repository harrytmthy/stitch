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
import com.harrytmthy.stitch.api.Named
import com.harrytmthy.stitch.api.Stitch
import com.harrytmthy.stitch.api.module
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.koinApplication
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

    // ----------- tiny graph (LocalRepo) -----------
    data class Logger(val tag: String = "Deep")
    class Json
    class NetworkClient(val logger: Logger)
    class Dao(val logger: Logger)
    class Cache(val logger: Logger)
    class Mapper(val json: Json)

    class LocalRepo(val dao: Dao, val mapper: Mapper)

    // ----------- deep graph (DeepViewModel) -----------
    class RemoteRepo(val net: NetworkClient, val json: Json, val mapper: Mapper)
    class CombinedRepo(val local: LocalRepo, val remote: RemoteRepo)
    class UseCaseA(val repo: CombinedRepo, val cache: Cache)
    class UseCaseB(val repo: CombinedRepo, val logger: Logger)
    class DeepService(val a: UseCaseA, val b: UseCaseB)
    class DeepViewModel(val service: DeepService)

    // --------- module builders (mirror each other) ----------
    private fun buildStitchTiny_cold() = module {
        // cold fairness: factory for the leaf so first resolve always builds it
        singleton { Logger() }
        singleton { Dao(get()) }
        singleton { Json() }
        singleton { Mapper(get()) }
        factory { LocalRepo(get(), get()) }
    }

    private fun buildStitchTiny_warm() = module {
        // warm fairness: singleton for the leaf so resolves are cache hits
        singleton { Logger() }
        singleton { Dao(get()) }
        singleton { Json() }
        singleton { Mapper(get()) }
        singleton { LocalRepo(get(), get()) }
    }

    private fun buildKoinTiny_cold() = koinModule {
        single { Logger() }
        single { Dao(get()) }
        single { Json() }
        single { Mapper(get()) }
        factory { LocalRepo(get(), get()) }
    }

    private fun buildKoinTiny_warm() = koinModule {
        single { Logger() }
        single { Dao(get()) }
        single { Json() }
        single { Mapper(get()) }
        single { LocalRepo(get(), get()) }
    }

    private fun buildStitchDeep() = module {
        // fundamentals
        singleton { Logger() }
        singleton { Json() }
        singleton { NetworkClient(get()) }
        singleton { Dao(get()) }
        singleton { Cache(get()) }
        singleton { Mapper(get()) }

        // repos (remote is qualified)
        singleton { LocalRepo(get(), get()) }
        singleton(Named.of("remote")) { RemoteRepo(get(), get(), get()) }

        // combine local + remote (pull the qualified one explicitly)
        singleton {
            val local: LocalRepo = get()
            val remote: RemoteRepo = get(Named.of("remote"))
            CombinedRepo(local, remote)
        }

        // use cases + service + vm
        singleton { UseCaseA(get(), get()) }
        singleton { UseCaseB(get(), get()) }
        singleton { DeepService(get(), get()) }
        factory { DeepViewModel(get()) } // VM as factory is realistic
    }

    private fun buildKoinDeep() = koinModule {
        // fundamentals
        single { Logger() }
        single { Json() }
        single { NetworkClient(get()) }
        single { Dao(get()) }
        single { Cache(get()) }
        single { Mapper(get()) }

        // repos (remote is qualified)
        single { LocalRepo(get(), get()) }
        single(org.koin.core.qualifier.named("remote")) { RemoteRepo(get(), get(), get()) }

        // combine local + remote
        single {
            val local: LocalRepo = get()
            val remote: RemoteRepo = get(org.koin.core.qualifier.named("remote"))
            CombinedRepo(local, remote)
        }

        // use cases + service + vm
        single { UseCaseA(get(), get()) }
        single { UseCaseB(get(), get()) }
        single { DeepService(get(), get()) }
        factory { DeepViewModel(get()) }
    }

    @Test
    fun stitchWarm() {
        Stitch.register(buildStitchTiny_warm())

        // JIT priming
        repeat(10) { Stitch.get<LocalRepo>() }

        benchmarkRule.measureRepeated {
            sink = Stitch.get<LocalRepo>()
        }
        Stitch.unregister()
    }

    @Test
    fun stitchDeepWarm() {
        val m = buildStitchDeep()
        Stitch.register(m)

        repeat(10) { Stitch.get<DeepViewModel>() }

        benchmarkRule.measureRepeated {
            sink = Stitch.get<DeepViewModel>()
        }
        Stitch.unregister()
    }

    @Test
    fun stitchCold_e2e() = benchmarkRule.measureRepeated {
        val m = buildStitchTiny_cold()
        Stitch.register(m)
        sink = Stitch.get<LocalRepo>()
        runWithMeasurementDisabled { Stitch.unregister() }
    }

    @Test
    fun stitchDeepCold_e2e() = benchmarkRule.measureRepeated {
        val m = buildStitchDeep()
        Stitch.register(m)
        sink = Stitch.get<DeepViewModel>()
        runWithMeasurementDisabled { Stitch.unregister() }
    }

    @Test
    fun stitchCold_engine() = benchmarkRule.measureRepeated {
        val m = runWithMeasurementDisabled { buildStitchTiny_cold() }
        runWithMeasurementDisabled { Stitch.register(m) }

        sink = Stitch.get<LocalRepo>()

        runWithMeasurementDisabled { Stitch.unregister() }
    }

    @Test
    fun stitchDeepCold_engine() = benchmarkRule.measureRepeated {
        val m = runWithMeasurementDisabled { buildStitchDeep() }
        runWithMeasurementDisabled { Stitch.register(m) }

        sink = Stitch.get<DeepViewModel>()

        runWithMeasurementDisabled { Stitch.unregister() }
    }

    @Test
    fun koinWarm() {
        val koinApp = koinApplication { modules(buildKoinTiny_warm()) }

        // JIT priming
        repeat(10) { koinApp.koin.get<LocalRepo>() }

        benchmarkRule.measureRepeated {
            sink = koinApp.koin.get<LocalRepo>()
        }
        koinApp.close()
    }

    @Test
    fun koinDeepWarm() {
        val koinApp = koinApplication { modules(buildKoinDeep()) }

        repeat(10) { koinApp.koin.get<DeepViewModel>() }

        benchmarkRule.measureRepeated {
            sink = koinApp.koin.get<DeepViewModel>()
        }
        koinApp.close()
    }

    @Test
    fun koinCold_e2e() = benchmarkRule.measureRepeated {
        val koinApp = koinApplication { modules(buildKoinTiny_cold()) }
        sink = koinApp.koin.get<LocalRepo>()
        runWithMeasurementDisabled { koinApp.close() }
    }

    @Test
    fun koinDeepCold_e2e() = benchmarkRule.measureRepeated {
        val koinApp = koinApplication { modules(buildKoinDeep()) }
        sink = koinApp.koin.get<DeepViewModel>()
        runWithMeasurementDisabled { koinApp.close() }
    }

    @Test
    fun koinCold_engine() = benchmarkRule.measureRepeated {
        val koinApp = runWithMeasurementDisabled {
            val module = buildKoinTiny_cold()
            koinApplication { modules(module) }
        }

        sink = koinApp.koin.get<LocalRepo>()

        runWithMeasurementDisabled { koinApp.close() }
    }

    @Test
    fun koinDeepCold_engine() = benchmarkRule.measureRepeated {
        val koinApp = runWithMeasurementDisabled {
            val module = buildKoinDeep()
            koinApplication { modules(module) }
        }

        sink = koinApp.koin.get<DeepViewModel>()

        runWithMeasurementDisabled { koinApp.close() }
    }
}

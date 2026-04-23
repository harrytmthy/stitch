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

package com.harrytmthy.stitch.benchmark

import androidx.benchmark.ExperimentalBenchmarkConfigApi
import androidx.benchmark.MicrobenchmarkConfig
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import com.harrytmthy.stitch.api.Stitch
import com.harrytmthy.stitch.benchmark.fixture.DaggerDaggerFixtureComponent
import com.harrytmthy.stitch.benchmark.fixture.FactoryInjectionTarget
import com.harrytmthy.stitch.benchmark.fixture.InjectionTarget
import com.harrytmthy.stitch.benchmark.fixture.koinFixtureModule
import com.harrytmthy.stitch.benchmark.fixture.stitchRuntimeFixtureModule
import io.github.harrytmthy.stitch.generated.StitchSingletonGraph
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.koin.dsl.koinApplication

/**
 * Performance benchmark comparing injection time across Stitch (precompiled + runtime),
 * Dagger, and Koin.
 *
 * Run on a physical device:
 * ```
 * ./gradlew :benchmark:connectedDebugAndroidTest
 * ```
 *
 * Results are written to `/sdcard/Download/benchmark/`.
 */
@RunWith(JUnit4::class)
class StitchVsDaggerVsKoinBenchmark {

    @OptIn(ExperimentalBenchmarkConfigApi::class)
    @get:Rule
    val benchmarkRule = BenchmarkRule(
        MicrobenchmarkConfig(
            warmupCount = 20,
            measurementCount = 100,
        ),
    )

    @Test
    fun stitchPrecompiledSingletonInject() {
        val target = InjectionTarget()
        benchmarkRule.measureRepeated {
            val singletonGraph = runWithMeasurementDisabled {
                StitchSingletonGraph()
            }
            singletonGraph.inject(target)
        }
    }

    @Test
    fun stitchRuntimeSingletonInject() {
        Stitch.register(stitchRuntimeFixtureModule)
        val target = InjectionTarget()
        benchmarkRule.measureRepeated {
            target.injectWithStitch()
        }
        Stitch.reset()
    }

    @Test
    fun daggerSingletonInject() {
        val target = InjectionTarget()
        benchmarkRule.measureRepeated {
            val fixtureComponent = runWithMeasurementDisabled {
                DaggerDaggerFixtureComponent.create()
            }
            fixtureComponent.inject(target)
        }
    }

    @Test
    fun koinSingletonInject() {
        val koinApp = koinApplication { modules(koinFixtureModule) }
        val target = InjectionTarget()
        benchmarkRule.measureRepeated {
            target.injectWithKoin(koinApp.koin)
        }
        koinApp.close()
    }

    @Test
    fun stitchPrecompiledFactoryInject() {
        val target = FactoryInjectionTarget()
        benchmarkRule.measureRepeated {
            val singletonGraph = runWithMeasurementDisabled {
                StitchSingletonGraph()
            }
            singletonGraph.inject(target)
        }
    }

    @Test
    fun stitchRuntimeFactoryInject() {
        Stitch.register(stitchRuntimeFixtureModule)
        val target = FactoryInjectionTarget()
        benchmarkRule.measureRepeated {
            target.injectWithStitch()
        }
        Stitch.reset()
    }

    @Test
    fun daggerFactoryInject() {
        val target = FactoryInjectionTarget()
        benchmarkRule.measureRepeated {
            val fixtureComponent = runWithMeasurementDisabled {
                DaggerDaggerFixtureComponent.create()
            }
            fixtureComponent.inject(target)
        }
    }

    @Test
    fun koinFactoryInject() {
        val koinApp = koinApplication { modules(koinFixtureModule) }
        val target = FactoryInjectionTarget()
        benchmarkRule.measureRepeated {
            target.injectWithKoin(koinApp.koin)
        }
        koinApp.close()
    }
}

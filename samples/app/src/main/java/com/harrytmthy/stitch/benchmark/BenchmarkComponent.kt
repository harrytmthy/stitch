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

package com.harrytmthy.stitch.benchmark

import dagger.Component
import javax.inject.Singleton

/**
 * Dagger component for benchmark member injection.
 *
 * Dagger KSP compiler will generate DaggerBenchmarkComponent implementation
 * that can inject members into both warm path (singleton) and cold path (factory) targets.
 */
@Singleton
@Component(modules = [BenchmarkModule::class])
interface BenchmarkComponent {
    // Warm path (singleton dependencies)
    fun inject(target: ShallowTarget)
    fun inject(target: DeepTarget)

    // Cold path (factory dependencies)
    fun inject(target: ShallowTargetCold)
    fun inject(target: DeepTargetCold)
}

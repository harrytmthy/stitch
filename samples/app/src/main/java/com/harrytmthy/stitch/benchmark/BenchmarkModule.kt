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

/**
 * Dual-annotated module for both Dagger and Stitch.
 *
 * This module is processed by both:
 * - Dagger KSP compiler (generates DaggerBenchmarkComponent)
 * - Stitch KSP compiler (generates DI table for benchmark classes)
 *
 * No @Provides methods needed - all classes use @Inject constructors.
 */
@dagger.Module
@com.harrytmthy.stitch.annotations.Module
object BenchmarkModule

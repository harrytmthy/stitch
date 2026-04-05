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

package com.harrytmthy.stitch.benchmark

import dagger.Module

/**
 * A module that will be processed by Dagger KSP compiler (generates DaggerBenchmarkComponent)
 * No @Provides methods needed, since all classes use @Inject constructors.
 */
@Module
object BenchmarkModule

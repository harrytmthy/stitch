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

package com.harrytmthy.stitch.annotations

/**
 * Marks a function as a dependency provider.
 *
 * Can be placed on top-level functions, or on functions inside an object or class.
 * By default, providers create a new instance each time (factory behavior).
 * Add [Singleton] to make the dependency a singleton, or a scope annotation to make it scoped.
 *
 * Example:
 * ```
 * object AppModule {
 *     @Provides
 *     fun provideLogger(): Logger = LoggerImpl()
 *
 *     @Provides
 *     @Singleton
 *     fun provideDatabase(): Database = DatabaseImpl()
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Provides

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

import kotlin.reflect.KClass

/**
 * Declares type aliases for dependency bindings without duplicating singleton instances.
 *
 * This annotation enables binding an implementation to one or more supertypes (interfaces/abstract classes)
 * while maintaining a single canonical instance. This is useful for:
 * - Interface implementation bindings (Dagger parity)
 * - Multiple interface implementations
 * - Avoiding duplicate singleton fields/locks
 *
 * **Method-level usage (in interface/abstract modules):**
 * ```
 * @Module
 * interface NetworkModule {
 *     @Binds
 *     fun bindRepository(impl: UserRepositoryImpl): UserRepository
 * }
 * ```
 *
 * **Class-level usage (on @Inject classes):**
 * ```
 * @Binds(aliases = [UserRepository::class, UserReader::class])
 * @Singleton
 * class UserRepositoryImpl @Inject constructor() : UserRepository, UserReader
 * ```
 *
 * **Method-level on @Provides (chaining):**
 * ```
 * @Module
 * class AppModule {
 *     @Provides
 *     @Singleton
 *     @Binds(aliases = [Service::class])
 *     fun provideNetworkService(): NetworkService = NetworkServiceImpl()
 * }
 * ```
 *
 * @property aliases Additional supertypes to bind this implementation to.
 *                   For method-level @Binds, the return type is automatically included.
 *                   For class-level @Binds, these are the ONLY aliases created.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Binds(val aliases: Array<KClass<*>> = [])

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

package com.harrytmthy.stitch.api

import kotlin.reflect.KClass

/**
 * Enables registering type aliases for a binding, so a single instance can be resolved
 * by multiple supertypes.
 *
 * ```
 * singleton { LoggerImpl() }.bind<Logger>()
 * ```
 *
 * Calls can be chained to bind multiple types:
 * ```
 * singleton { UserRepositoryImpl() }.bind<UserRepository>().bind<UserReader>()
 * ```
 */
interface Bindable {

    /**
     * Registers [type] as an alias for this binding.
     */
    fun <T : Any> bind(type: KClass<T>): Bindable
}

/**
 * Reified convenience for [Bindable.bind].
 */
inline fun <reified T : Any> Bindable.bind(): Bindable = bind(T::class)

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

package com.harrytmthy.stitch.api

/**
 * Provides instances of [T] from a generated precompiled graph on demand.
 *
 * Request `Provider<T>` instead of `T` to defer resolution until [get] is called:
 * ```
 * class HomeViewModel @Inject constructor(
 *     private val repository: Provider<HomeRepository>,
 * )
 * ```
 *
 * Each call to [get] follows the binding's scope. A singleton or scoped binding returns its
 * cached instance, while an unscoped binding creates a new instance on every call.
 *
 * A dependency requested through a provider is excluded from compile-time cycle detection, so it
 * can break a dependency cycle. Its binding must still be visible from the requester's scope.
 * Calling [get] during construction can still lead back to a singleton or scoped binding that is
 * being initialized, which throws [IllegalStateException].
 */
fun interface Provider<out T> {

    /**
     * Returns an instance of [T] resolved from the generated graph.
     */
    fun get(): T
}

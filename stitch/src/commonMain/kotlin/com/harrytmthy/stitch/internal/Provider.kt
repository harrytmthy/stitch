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

package com.harrytmthy.stitch.internal

/**
 * Provider for DI path dependencies. Handles singleton caching independently from SL path.
 *
 * Unlike the SL path which caches instances in [Registry.singletons], the DI path maintains
 * its own cache within Provider instances to avoid unwanted side-effects (e.g. preventing
 * [com.harrytmthy.stitch.api.Stitch.unregisterAll] from clearing DI path singletons).
 *
 * Thread-safe for concurrent access using double-checked locking for singletons.
 *
 * **Note**: This is a public API used by generated code. Do not use directly.
 */
class Provider<T>(
    private val definitionType: DefinitionType,
    private val factory: () -> T,
) {

    @Volatile
    private var cachedInstance: T? = null

    /**
     * Gets the dependency instance.
     *
     * For [DefinitionType.Factory], always invokes the factory to create a new instance.
     * For [DefinitionType.Singleton], uses double-checked locking to cache the instance.
     *
     * No cycle detection is performed here - compile-time validation ensures DI-to-DI
     * cycles are caught during build. SL path handles its own cycle detection.
     */
    fun get(): T {
        return when (definitionType) {
            DefinitionType.Factory -> factory()
            DefinitionType.Singleton -> {
                // Fast path: return cached instance if available
                cachedInstance?.let { return it }

                // Slow path: create and cache instance with double-checked locking
                synchronized(this) {
                    cachedInstance?.let { return it }
                    val instance = factory()
                    cachedInstance = instance
                    instance
                }
            }
            DefinitionType.Scoped -> error("Scoped not supported in DI path v1.0")
        }
    }
}

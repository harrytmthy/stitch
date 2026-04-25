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

import com.harrytmthy.stitch.api.StitchInjector.getSingletonGraph
import com.harrytmthy.stitch.api.StitchInjector.init
import com.harrytmthy.stitch.internal.ConcurrentHashMap
import kotlinx.atomicfu.atomic

/**
 * Global registry for Stitch's precompiled path. Manages the singleton graph and caches
 * injector instances across scopes.
 *
 * Initialize with the generated singleton graph on application startup:
 * ```
 * StitchInjector.init(StitchSingletonGraph)
 * ```
 *
 * Then retrieve injectors to perform injection:
 * ```
 * val injector = StitchInjector.getSingleton()
 *     .createInjectorForChildScope("activity", cached = true)
 * injector.inject(this)
 * ```
 *
 * @see Injector
 */
object StitchInjector {

    private var singletonInjector: Injector? = null

    private val injectorPool = ConcurrentHashMap<Int, Injector>()

    private val nextId = atomic(1)

    /**
     * Initializes the precompiled graph with the generated [singletonGraph].
     *
     * This must be called before any [getSingletonGraph] or scope creation.
     */
    fun init(singletonGraph: Injector) {
        singletonInjector = singletonGraph
        injectorPool[singletonGraph.id] = singletonGraph
    }

    /**
     * Returns the singleton graph which was set via [init].
     *
     * @throws IllegalStateException if [init] has not been called.
     */
    fun getSingletonGraph(): Injector =
        singletonInjector ?: error(
            buildString {
                append("StitchInjector is not yet initialized. ")
                append("Call `StitchInjector.init(StitchSingletonGraph)`.")
            },
        )

    /**
     * Returns the cached [Injector] with the given [id], or null if not found.
     */
    fun getInjectorById(id: Int): Injector? = injectorPool[id]

    /**
     * Returns the cached [Injector] with the given [id].
     *
     * @throws IllegalStateException if no injector with [id] is cached.
     */
    fun requireInjectorById(id: Int): Injector =
        injectorPool[id] ?: error("Injector with id = $id is not found")

    /**
     * Adds an [injector] to the cache, keyed by its [Injector.id].
     */
    fun addToCache(injector: Injector) {
        injectorPool[injector.id] = injector
    }

    /**
     * Removes an [injector] from the cache.
     */
    fun removeFromCache(injector: Injector) {
        injectorPool.remove(injector.id)
    }

    /** Returns a monotonically increasing ID for new injector instances. */
    fun nextId(): Int = nextId.getAndIncrement()
}

/**
 * Removes this injector from [StitchInjector]'s cache.
 *
 * Call this when a scope's lifecycle ends to free the cached reference.
 */
fun Injector.close() {
    StitchInjector.removeFromCache(this)
}

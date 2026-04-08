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

import com.harrytmthy.stitch.internal.ConcurrentHashMap
import kotlinx.atomicfu.atomic

object StitchInjector {

    private var singletonInjector: Injector? = null

    private val injectorPool = ConcurrentHashMap<Int, Injector>()

    private val nextId = atomic(1)

    fun init(singletonGraph: Injector) {
        singletonInjector = singletonGraph
        injectorPool[singletonGraph.id] = singletonGraph
    }

    fun getSingleton(): Injector =
        singletonInjector ?: error(
            buildString {
                append("StitchInjector is not yet initialized. ")
                append("Call `StitchInjector.init(StitchSingletonGraph)`.")
            },
        )

    fun getInjectorById(id: Int): Injector? = injectorPool[id]

    fun requireInjectorById(id: Int): Injector =
        injectorPool[id] ?: error("Injector with id = $id is not found")

    fun addToCache(injector: Injector) {
        injectorPool[injector.id] = injector
    }

    fun removeFromCache(injector: Injector) {
        injectorPool.remove(injector.id)
    }

    fun nextId(): Int = nextId.getAndIncrement()
}

fun Injector.close() {
    StitchInjector.removeFromCache(this)
}

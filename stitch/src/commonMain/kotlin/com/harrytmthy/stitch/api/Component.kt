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

import com.harrytmthy.stitch.engine.Node
import com.harrytmthy.stitch.internal.computeIfAbsentCompat
import java.util.concurrent.ConcurrentHashMap

open class Component internal constructor(
    internal val nodeLookup: (Class<*>, Qualifier?) -> Node,
    internal val singletons: ConcurrentHashMap<Class<*>, ConcurrentHashMap<Any, Any>>,
) {

    private val scoped = ConcurrentHashMap<Class<*>, ConcurrentHashMap<Any, Any>>()

    @Suppress("UNCHECKED_CAST")
    open fun <T : Any> get(type: Class<T>, qualifier: Qualifier? = null): T {
        val qualifierKey = qualifier ?: DefaultQualifier

        // Fast-path: check caches with the requested key first
        scoped[type]?.get(qualifierKey)?.let { return it as T }
        singletons[type]?.get(qualifierKey)?.let { return it as T }

        // Resolve once to learn the canonical cache key
        val node = nodeLookup(type, qualifier)

        // Fast-path: If alias, recheck caches under canonical key
        if (node.type !== type) {
            scoped[node.type]?.get(qualifierKey)?.let { return it as T }
            singletons[node.type]?.get(qualifierKey)?.let { return it as T }
        }

        // Build, cache under canonical key
        return when (node.scope) {
            Scope.Factory -> node.factory(this) as T
            Scope.Scoped -> {
                val inner = scoped.computeIfAbsentCompat(node.type) { ConcurrentHashMap() }
                inner[qualifierKey]?.let { return it as T }
                synchronized(inner) {
                    inner[qualifierKey]?.let { return it as T }
                    val built = node.factory(this)
                    (inner.putIfAbsent(qualifierKey, built) ?: built) as T
                }
            }
            Scope.Singleton -> {
                val inner = singletons.computeIfAbsentCompat(node.type) { ConcurrentHashMap() }
                inner[qualifierKey]?.let { return it as T }
                synchronized(inner) {
                    inner[qualifierKey]?.let { return it as T }
                    val built = node.consumePrebuilt() ?: node.factory(this)
                    (inner.putIfAbsent(qualifierKey, built) ?: built) as T
                }
            }
        }
    }

    inline fun <reified T : Any> get(qualifier: Qualifier? = null): T =
        get(T::class.java, qualifier)

    open fun <T : Any> lazyOf(type: Class<T>, qualifier: Qualifier? = null): Lazy<T> =
        lazy(LazyThreadSafetyMode.NONE) { get(type, qualifier) }

    inline fun <reified T : Any> lazyOf(qualifier: Qualifier? = null): Lazy<T> =
        lazyOf(T::class.java, qualifier)

    private object DefaultQualifier
}

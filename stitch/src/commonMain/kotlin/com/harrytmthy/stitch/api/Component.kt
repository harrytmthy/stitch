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

import com.harrytmthy.stitch.exception.CycleException
import com.harrytmthy.stitch.internal.Node
import com.harrytmthy.stitch.internal.Signature
import com.harrytmthy.stitch.internal.computeIfAbsentCompat
import java.util.concurrent.ConcurrentHashMap

class Component internal constructor(
    internal val nodeLookup: (Class<*>, Qualifier?) -> Node,
    internal val singletons: ConcurrentHashMap<Class<*>, ConcurrentHashMap<Any, Any>>,
) {

    private val scoped = ConcurrentHashMap<Class<*>, ConcurrentHashMap<Any, Any>>()

    private val resolutionStack = threadLocal { ResolutionStack() }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: Class<T>, qualifier: Qualifier?): T {
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

        // Build with cycle guard, cache under canonical key
        val resolving = resolutionStack.get()
        resolving.enter(node.type, node.qualifier)
        try {
            val inner = when (node.scope) {
                Scope.Factory -> return node.factory(this) as T
                Scope.Scoped -> scoped
                Scope.Singleton -> singletons
            }.computeIfAbsentCompat(node.type) { ConcurrentHashMap() }
            inner[qualifierKey]?.let { return it as T }
            return synchronized(inner) {
                inner[qualifierKey]?.let { return it as T }
                val built = node.factory(this)
                (inner.putIfAbsent(qualifierKey, built) ?: built) as T
            }
        } finally {
            resolving.exit()
        }
    }

    inline fun <reified T : Any> get(qualifier: Qualifier? = null): T =
        get(T::class.java, qualifier)

    fun <T : Any> lazyOf(type: Class<T>, qualifier: Qualifier? = null): Lazy<T> =
        lazy(LazyThreadSafetyMode.NONE) { get(type, qualifier) }

    inline fun <reified T : Any> lazyOf(qualifier: Qualifier? = null): Lazy<T> =
        lazyOf(T::class.java, qualifier)

    internal fun clear() {
        scoped.clear()
        resolutionStack.get().clear()
    }

    private inline fun <T : Any> threadLocal(crossinline supplier: () -> T): ThreadLocal<T> =
        object : ThreadLocal<T>() {
            override fun initialValue(): T = supplier()
        }

    private class ResolutionStack {

        private val stack = ArrayDeque<Signature>()

        private val indexBySignature = HashMap<Signature, Int>()

        fun enter(type: Class<*>, qualifier: Qualifier?) {
            val signature = Signature(type, qualifier)
            val signatureIndex = indexBySignature[signature]
            if (signatureIndex != null) {
                val cycle = ArrayList<Signature>(stack.size - signatureIndex + 1)
                for (index in signatureIndex until stack.size) {
                    cycle += stack[index]
                }
                cycle += signature
                throw CycleException(cycle)
            }
            indexBySignature[signature] = stack.size
            stack.addLast(signature)
        }

        fun exit() {
            val removed = stack.removeLast()
            indexBySignature.remove(removed)
        }

        fun clear() {
            stack.clear()
            indexBySignature.clear()
        }
    }

    private object DefaultQualifier
}

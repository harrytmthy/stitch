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
    private val nodeLookup: (Class<*>, Qualifier?, ScopeRef?) -> Node,
    private val singletons: ConcurrentHashMap<Class<*>, ConcurrentHashMap<Any, Any>>,
    private val scoped: ConcurrentHashMap<Int, ConcurrentHashMap<Class<*>, ConcurrentHashMap<Any, Any>>>,
) {

    private val resolutionStack = threadLocal { ResolutionStack() }

    private val scopeContext = threadLocal { ScopeContext() }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(type: Class<T>, qualifier: Qualifier?, scope: Scope? = null): T {
        val qualifierKey = qualifier ?: DefaultQualifier
        val scopeContext = scopeContext.get()
        val effectiveScope = scope ?: scopeContext.scope

        // Fast-path: check caches with the requested key first
        effectiveScope?.id?.let { scopeId ->
            scoped[scopeId]?.get(type)?.get(qualifierKey)?.let {
                effectiveScope.ensureOpen()
                return it as T
            }
        }
        singletons[type]?.get(qualifierKey)?.let { return it as T }

        // Resolve once to learn the canonical cache key
        val node = nodeLookup(type, qualifier, effectiveScope?.reference)

        // Fast-path: If alias, recheck caches under canonical key
        if (node.type !== type) {
            effectiveScope?.id?.let { scopeId ->
                scoped[scopeId]?.get(node.type)?.get(qualifierKey)?.let {
                    effectiveScope.ensureOpen()
                    return it as T
                }
            }
            singletons[node.type]?.get(qualifierKey)?.let { return it as T }
        }

        // Build with cycle guard, cache under canonical key
        val resolving = resolutionStack.get()
        resolving.enter(node.type, node.qualifier)
        try {
            return when (node.definitionType) {
                DefinitionType.Factory -> node.factory(this) as T
                DefinitionType.Scoped -> {
                    val scope = effectiveScope ?: error(
                        "Failed to get ${type.name}: It exists in scope '${node.scopeRef?.name}'",
                    )
                    check(scope.reference == node.scopeRef) {
                        "Failed to get ${type.name}: Wrong scope '${scope.reference.name}'"
                    }
                    val perScope = scoped.computeIfAbsentCompat(scope.id) { ConcurrentHashMap() }
                    val inner = perScope.computeIfAbsentCompat(node.type) { ConcurrentHashMap() }
                    inner[qualifierKey]?.let {
                        scope.ensureOpen()
                        return it as T
                    }
                    synchronized(inner) {
                        inner[qualifierKey]?.let {
                            scope.ensureOpen()
                            return it as T
                        }
                        val previousScope = scopeContext.scope
                        scopeContext.scope = scope
                        try {
                            scope.ensureOpen()
                            val built = node.factory(this)
                            scope.ensureOpen()
                            (inner.putIfAbsent(qualifierKey, built) ?: built) as T
                        } finally {
                            scopeContext.scope = previousScope
                        }
                    }
                }
                DefinitionType.Singleton -> {
                    val inner = singletons.computeIfAbsentCompat(node.type) { ConcurrentHashMap() }
                    inner[qualifierKey]?.let { return it as T }
                    return synchronized(inner) {
                        inner[qualifierKey]?.let { return it as T }
                        val built = node.factory(this)
                        (inner.putIfAbsent(qualifierKey, built) ?: built) as T
                    }
                }
            }
        } finally {
            resolving.exit()
        }
    }

    inline fun <reified T : Any> get(
        qualifier: Qualifier? = null,
        scope: Scope? = null,
    ): T = get(T::class.java, qualifier, scope)

    fun <T : Any> lazyOf(
        type: Class<T>,
        qualifier: Qualifier? = null,
        scope: Scope? = null,
    ): Lazy<T> = lazy(LazyThreadSafetyMode.NONE) { get(type, qualifier, scope) }

    inline fun <reified T : Any> lazyOf(
        qualifier: Qualifier? = null,
        scope: Scope? = null,
    ): Lazy<T> = lazyOf(T::class.java, qualifier, scope)

    internal fun clear() {
        resolutionStack.remove()
        scopeContext.remove()
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

    private class ScopeContext {
        var scope: Scope? = null
    }

    private object DefaultQualifier
}

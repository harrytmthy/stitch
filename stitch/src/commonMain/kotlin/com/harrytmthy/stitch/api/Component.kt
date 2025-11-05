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
import com.harrytmthy.stitch.exception.MissingScopeException
import com.harrytmthy.stitch.exception.ScopeClosedException
import com.harrytmthy.stitch.exception.WrongScopeException
import com.harrytmthy.stitch.internal.DefinitionType
import com.harrytmthy.stitch.internal.Node
import com.harrytmthy.stitch.internal.computeIfAbsentCompat
import java.util.concurrent.ConcurrentHashMap

class Component internal constructor(
    private val nodeLookup: (Class<*>, Qualifier?, ScopeRef?) -> Node,
    private val singletons: ConcurrentHashMap<Class<*>, ConcurrentHashMap<Qualifier, Any>>,
    private val scoped: ConcurrentHashMap<Int, ConcurrentHashMap<Class<*>, ConcurrentHashMap<Qualifier, Any>>>,
) {

    private val resolutionStack = threadLocal { ResolutionStack() }

    private val scopeContext = threadLocal { ScopeContext() }

    inline fun <reified T : Any> get(qualifier: Qualifier? = null, scope: Scope? = null): T =
        getInternal(T::class.java, qualifier, scope)

    inline fun <reified T : Any> lazyOf(
        qualifier: Qualifier? = null,
        scope: Scope? = null,
    ): Lazy<T> {
        return lazy(LazyThreadSafetyMode.NONE) { get(qualifier, scope) }
    }

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> getInternal(type: Class<T>, qualifier: Qualifier?, scope: Scope?): T {
        val qualifierKey = qualifier ?: DefaultQualifier
        val scopeContext = scopeContext.get()
        val effectiveScope = scope ?: scopeContext.scope

        // Fast-path: check caches with the requested key first
        effectiveScope?.id?.let { scopeId ->
            scoped[scopeId]?.get(type)?.get(qualifierKey)?.let {
                effectiveScope.ensureOpen(type, qualifier)
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
                    effectiveScope.ensureOpen(type, qualifier)
                    return it as T
                }
            }
            singletons[node.type]?.get(qualifierKey)?.let { return it as T }
        }

        // Build with cycle guard, cache under canonical key
        val resolving = resolutionStack.get()
        resolving.enter(node)
        try {
            return when (node.definitionType) {
                DefinitionType.Factory -> node.factory(this) as T
                DefinitionType.Scoped -> {
                    val scope = effectiveScope ?: throw MissingScopeException(type, qualifier)
                    if (scope.reference != node.scopeRef) {
                        throw WrongScopeException(type, qualifier, scope.reference, node.scopeRef)
                    }
                    val perScope = scoped.computeIfAbsentCompat(scope.id) { ConcurrentHashMap() }
                    val inner = perScope.computeIfAbsentCompat(node.type) { ConcurrentHashMap() }
                    inner[qualifierKey]?.let {
                        scope.ensureOpen(type, qualifier)
                        return it as T
                    }
                    synchronized(inner) {
                        inner[qualifierKey]?.let {
                            scope.ensureOpen(type, qualifier)
                            return it as T
                        }
                        val previousScope = scopeContext.scope
                        scopeContext.scope = scope
                        try {
                            scope.ensureOpen(type, qualifier)
                            val built = node.factory(this)
                            scope.ensureOpen(type, qualifier)
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

    internal fun clear() {
        resolutionStack.remove()
        scopeContext.remove()
    }

    private inline fun <T : Any> threadLocal(crossinline supplier: () -> T): ThreadLocal<T> =
        object : ThreadLocal<T>() {
            override fun initialValue(): T = supplier()
        }

    private fun Scope.ensureOpen(type: Class<*>, qualifier: Qualifier?) {
        if (!isOpen()) {
            throw ScopeClosedException(type, qualifier, id)
        }
    }

    private class ResolutionStack {

        private val stack = ArrayDeque<Node>()

        private val indexByNode = HashMap<Node, Int>()

        fun enter(node: Node) {
            val nodeIndex = indexByNode[node]
            if (nodeIndex != null) {
                val cycle = ArrayList<Node>(stack.size - nodeIndex + 1)
                for (index in nodeIndex until stack.size) {
                    cycle += stack[index]
                }
                cycle += node
                throw CycleException(node.type, node.qualifier, cycle)
            }
            indexByNode[node] = stack.size
            stack.addLast(node)
        }

        fun exit() {
            val removed = stack.removeLast()
            indexByNode.remove(removed)
        }
    }

    private class ScopeContext {
        var scope: Scope? = null
    }
}

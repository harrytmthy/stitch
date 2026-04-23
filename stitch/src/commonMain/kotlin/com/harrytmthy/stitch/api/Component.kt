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

import com.harrytmthy.stitch.exception.MissingBindingException
import com.harrytmthy.stitch.exception.MissingScopeException
import com.harrytmthy.stitch.exception.ScopeClosedException
import com.harrytmthy.stitch.internal.ConcurrentHashMap
import com.harrytmthy.stitch.internal.DefinitionType
import com.harrytmthy.stitch.internal.Node
import com.harrytmthy.stitch.internal.Registry
import kotlinx.atomicfu.locks.synchronized
import kotlin.reflect.KClass

/**
 * Core resolution engine for the runtime path. Handles multi-level caching (singleton, scoped),
 * type alias resolution, cycle detection, and thread-safe double-checked locking.
 */
class Component internal constructor() {

    @PublishedApi
    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> getInternal(
        type: KClass<T>,
        qualifier: Qualifier?,
        scope: Scope?,
        resolutionContext: ResolutionContext?,
    ): T {
        val qualifierKey = qualifier ?: DefaultQualifier

        // Resolve once to learn the canonical cache type + owning scope
        val (node, owningScope) = lookupNode(type, qualifier, scope)
        val canonicalType = if (node.type !== type) node.type else type

        // Fast-path: Check caches under canonical type
        if (scope != null) {
            if (owningScope == scope) {
                // Current scope owns the binding. Check for cache hit.
                Registry.scoped[scope.id]?.get(canonicalType)?.get(qualifierKey)?.let {
                    scope.ensureOpen(type, qualifier)
                    return it as T
                }
            } else {
                // Current scope doesn't own the binding. Check for ancestor's cache hit.
                var currentScope = scope.parent
                while (currentScope != null) {
                    Registry.scoped[currentScope.id]?.get(canonicalType)?.get(qualifierKey)?.let {
                        scope.ensureOpen(type, qualifier)
                        return it as T
                    }
                    currentScope = currentScope.parent
                }
            }
        } else {
            Registry.singletons[canonicalType]?.get(qualifierKey)?.let { return it as T }
        }

        // Build with cycle guard, cache under canonical key
        val resolving = resolutionContext ?: ResolutionContext(this, owningScope)
        resolving.enter(node)
        try {
            return when (node.definitionType) {
                DefinitionType.Factory -> node.factory(resolving) as T

                DefinitionType.Scoped -> {
                    scope ?: throw MissingScopeException(type, qualifier)
                    val perScope = Registry.scoped.computeIfAbsent(owningScope!!.id) { ConcurrentHashMap() }
                    val inner = perScope.computeIfAbsent(node.type) { ConcurrentHashMap() }
                    inner[qualifierKey]?.let {
                        scope.ensureOpen(type, qualifier)
                        return it as T
                    }
                    synchronized(inner) {
                        inner[qualifierKey]?.let {
                            scope.ensureOpen(type, qualifier)
                            return it as T
                        }
                        scope.ensureOpen(type, qualifier)
                        val built = node.factory(resolving)
                        scope.ensureOpen(type, qualifier)
                        (inner.putIfAbsent(qualifierKey, built) ?: built) as T
                    }
                }

                DefinitionType.Singleton -> {
                    val inner = Registry.singletons.computeIfAbsent(node.type) {
                        ConcurrentHashMap()
                    }
                    inner[qualifierKey]?.let { return it as T }
                    synchronized(inner) {
                        inner[qualifierKey]?.let { return it as T }
                        val built = node.factory(resolving)
                        (inner.putIfAbsent(qualifierKey, built) ?: built) as T
                    }
                }
            }
        } finally {
            resolving.exit()
        }
    }

    /**
     * Returns [Node] paired with the scope that owns the binding.
     */
    private fun lookupNode(
        type: KClass<*>,
        qualifier: Qualifier?,
        scope: Scope?,
    ): Pair<Node, Scope?> {
        // Scope ancestors lookup
        var currentScope = scope
        while (currentScope != null) {
            Registry.scopedDefinitions[currentScope.name]?.get(type)?.get(qualifier)?.let {
                return it to currentScope
            }
            currentScope = currentScope.parent
        }

        // Fallback to the main definition lookup
        val inner = Registry.definitions[type] ?: throw MissingBindingException.missingType(type)
        return inner.getOrElse(qualifier) {
            throw MissingBindingException.missingQualifier(type, qualifier, inner.keys)
        } to null
    }

    private fun Scope.ensureOpen(type: KClass<*>, qualifier: Qualifier?) {
        if (!isOpen()) {
            throw ScopeClosedException(type, qualifier, id)
        }
    }
}

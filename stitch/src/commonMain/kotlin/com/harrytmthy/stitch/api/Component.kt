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

        // Fast-path: check caches with the requested key first
        scope?.id?.let { scopeId ->
            Registry.scoped[scopeId]?.get(type)?.get(qualifierKey)?.let {
                scope.ensureOpen(type, qualifier)
                return it as T
            }
        }
        if (scope == null) {
            Registry.singletons[type]?.get(qualifierKey)?.let { return it as T }
        }

        // Resolve once to learn the canonical cache key
        val node = lookupNode(type, qualifier, scope?.reference)

        // Fast-path: If alias, recheck caches under canonical key
        if (node.type !== type) {
            scope?.id?.let { scopeId ->
                Registry.scoped[scopeId]?.get(node.type)?.get(qualifierKey)?.let {
                    scope.ensureOpen(type, qualifier)
                    return it as T
                }
            }
            if (scope == null) {
                Registry.singletons[node.type]?.get(qualifierKey)?.let { return it as T }
            }
        }

        // Build with cycle guard, cache under canonical key
        val resolving = resolutionContext ?: ResolutionContext(this, scope)
        resolving.enter(node)
        try {
            return when (node.definitionType) {
                DefinitionType.Factory -> node.factory(resolving) as T

                DefinitionType.Scoped -> {
                    scope ?: throw MissingScopeException(type, qualifier)
                    val perScope = Registry.scoped.computeIfAbsent(scope.id) { ConcurrentHashMap() }
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
                    return synchronized(inner) {
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

    private fun lookupNode(type: KClass<*>, qualifier: Qualifier?, scopeRef: ScopeRef?): Node {
        Registry.scopedDefinitions[scopeRef]?.get(type)?.get(qualifier)?.let { return it }
        val inner = Registry.definitions[type] ?: throw MissingBindingException.missingType(type)
        return inner.getOrElse(qualifier) {
            throw MissingBindingException.missingQualifier(type, qualifier, inner.keys)
        }
    }

    private fun Scope.ensureOpen(type: KClass<*>, qualifier: Qualifier?) {
        if (!isOpen()) {
            throw ScopeClosedException(type, qualifier, id)
        }
    }
}

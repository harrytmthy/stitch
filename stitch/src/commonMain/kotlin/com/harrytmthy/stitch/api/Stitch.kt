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

import com.harrytmthy.stitch.api.Stitch.reset
import com.harrytmthy.stitch.internal.Node
import com.harrytmthy.stitch.internal.Registry
import kotlin.reflect.KClass

object Stitch {

    private val component by lazy { Component() }

    fun register(vararg modules: Module) {
        modules.forEach { module ->
            module.register()
            val eagerNodes = module.getRegisteredEagerNodes()
            if (eagerNodes.isNotEmpty()) {
                warmUp(eagerNodes)
            }
        }
    }

    private fun warmUp(nodes: List<Node>) {
        for (node in nodes) {
            component.getInternal(node.type, node.qualifier, scope = null, resolutionContext = null)
        }
    }

    fun unregister(vararg modules: Module) {
        modules.forEach { module ->
            val registeredNodes = module.getRegisteredNodes()
            val registeredEagerNodes = module.getRegisteredEagerNodes()
            Registry.remove(registeredNodes + registeredEagerNodes)
            registeredNodes.clear()
            registeredEagerNodes.clear()
        }
    }

    /**
     * Clears all registered modules and instances, but preserves Qualifiers and ScopeRefs.
     */
    fun unregisterAll() {
        Registry.clear()
    }

    /**
     * Fully resets the Stitch environment:
     * - Unregisters all modules
     * - Clears all Qualifiers and ScopeRefs
     * - Resets internal counters
     *
     * Only use in tests or in one-off tooling. After calling [reset], any previously obtained
     * [Named] or [ScopeRef] values must not be reused.
     */
    fun reset() {
        unregisterAll()
        Named.clear()
        ScopeRef.clear()
    }

    inline fun <reified T : Any> get(qualifier: Qualifier? = null, scope: Scope? = null): T =
        getInternal(T::class, qualifier, scope, resolutionContext = null)

    context(resolutionContext: ResolutionContext)
    inline fun <reified T : Any> get(qualifier: Qualifier? = null, scope: Scope? = null): T =
        getInternal(T::class, qualifier, scope, resolutionContext)

    inline fun <reified T : Any> inject(
        qualifier: Qualifier? = null,
        scope: Scope? = null,
    ): Lazy<T> {
        return lazy(LazyThreadSafetyMode.NONE) {
            getInternal(T::class, qualifier, scope, resolutionContext = null)
        }
    }

    @PublishedApi
    internal fun <T : Any> getInternal(
        type: KClass<T>,
        qualifier: Qualifier?,
        scope: Scope?,
        resolutionContext: ResolutionContext?,
    ): T {
        return component.getInternal(type, qualifier, scope, resolutionContext)
    }
}

inline fun <reified T : Any> get(qualifier: Qualifier? = null, scope: Scope? = null): T =
    Stitch.get(qualifier, scope)

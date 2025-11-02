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

import com.harrytmthy.stitch.internal.Factory
import com.harrytmthy.stitch.internal.Node
import com.harrytmthy.stitch.internal.Registry
import com.harrytmthy.stitch.internal.Signature

class Binder(private val overrideEager: Boolean) {

    private var stagedEagerDefinitions: ArrayList<Signature>? = null

    inline fun <reified T : Any> singleton(
        qualifier: Qualifier? = null,
        eager: Boolean = false,
        noinline factory: Component.() -> T,
    ): BindingChain = singleton(T::class.java, qualifier, eager, factory)

    fun <T : Any> singleton(
        type: Class<T>,
        qualifier: Qualifier? = null,
        eager: Boolean = false,
        factory: Component.() -> T,
    ): BindingChain {
        val node = createAndRegisterNode(type, qualifier, null, DefinitionType.Singleton, factory)
        if (eager || overrideEager) {
            val definitions = stagedEagerDefinitions ?: ArrayList<Signature>()
                .also { stagedEagerDefinitions = it }
            definitions += Signature(type, qualifier)
        }
        return BindingChain(this, node)
    }

    inline fun <reified T : Any> factory(
        qualifier: Qualifier? = null,
        noinline factory: Component.() -> T,
    ): BindingChain = factory(T::class.java, qualifier, factory)

    fun <T : Any> factory(
        type: Class<T>,
        qualifier: Qualifier? = null,
        factory: Component.() -> T,
    ): BindingChain {
        val node = createAndRegisterNode(type, qualifier, null, DefinitionType.Factory, factory)
        return BindingChain(this, node)
    }

    inline fun <reified T : Any> scoped(
        scopeRef: ScopeRef,
        qualifier: Qualifier? = null,
        noinline factory: Component.() -> T,
    ): BindingChain = scoped(scopeRef, T::class.java, qualifier, factory)

    fun <T : Any> scoped(
        scopeRef: ScopeRef,
        type: Class<T>,
        qualifier: Qualifier? = null,
        factory: Component.() -> T,
    ): BindingChain = createAndRegisterNode(
        type = type,
        qualifier = qualifier,
        scopeRef = scopeRef,
        definitionType = DefinitionType.Scoped,
        factory = factory,
    ).let { BindingChain(this, it) }

    private fun <T : Any> createAndRegisterNode(
        type: Class<T>,
        qualifier: Qualifier?,
        scopeRef: ScopeRef?,
        definitionType: DefinitionType,
        factory: Component.() -> T,
    ): Node {
        val node = Node(
            type = type,
            qualifier = qualifier,
            scopeRef = scopeRef,
            definitionType = definitionType,
            factory = factory as Factory,
        )
        val inner = Registry.definitions.getOrPut(type) { HashMap() }
        check(!inner.containsKey(qualifier)) {
            "Duplicate binding for ${type.name} / ${qualifier ?: "<default>"}"
        }
        inner[qualifier] = node
        Registry.version.incrementAndGet()
        return node
    }

    private fun registerAlias(aliasType: Class<*>, target: Node) {
        val primaryInner = Registry.definitions.getOrPut(target.type) { HashMap() }
        val existing = Registry.definitions[aliasType]
        check(existing == null || existing === primaryInner) {
            "Conflicting bindings for ${aliasType.name}: already has its own family."
        }
        Registry.definitions[aliasType] = primaryInner
        Registry.version.incrementAndGet()
    }

    /**
     * Chain handle that can register aliases pointing to the same Node.
     */
    class BindingChain internal constructor(
        private val binder: Binder,
        private val node: Node,
    ) {

        inline fun <reified T : Any> bind(): BindingChain =
            bind(T::class.java)

        fun <T : Any> bind(type: Class<T>): BindingChain {
            binder.registerAlias(type, node)
            return this
        }
    }

    internal fun getStagedEagerDefinitions(): List<Signature> =
        stagedEagerDefinitions.orEmpty().also { stagedEagerDefinitions = null }
}

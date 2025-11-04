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

import com.harrytmthy.stitch.internal.DefinitionType
import com.harrytmthy.stitch.internal.Node
import com.harrytmthy.stitch.internal.Registry

class Module(private val forceEager: Boolean, private val onRegister: Module.() -> Unit) {

    private val registeredNodes = ArrayList<Node>()

    private val registeredEagerNodes = ArrayList<Node>()

    internal fun register() {
        onRegister(this)
    }

    inline fun <reified T : Any> singleton(
        qualifier: Qualifier? = null,
        eager: Boolean = false,
        noinline factory: Component.() -> T,
    ): Bindable {
        return singleton(T::class.java, qualifier, eager, factory)
    }

    fun <T : Any> singleton(
        type: Class<T>,
        qualifier: Qualifier? = null,
        eager: Boolean = false,
        factory: Component.() -> T,
    ): Bindable {
        val node = createAndRegisterNode(type, qualifier, DefinitionType.Singleton, factory)
        if (eager || forceEager) {
            registeredEagerNodes.add(node)
        } else {
            registeredNodes.add(node)
        }
        return node
    }

    inline fun <reified T : Any> factory(
        qualifier: Qualifier? = null,
        noinline factory: Component.() -> T,
    ): Bindable {
        return factory(T::class.java, qualifier, factory)
    }

    fun <T : Any> factory(
        type: Class<T>,
        qualifier: Qualifier? = null,
        factory: Component.() -> T,
    ): Bindable {
        return createAndRegisterNode(type, qualifier, DefinitionType.Factory, factory)
            .also(registeredNodes::add)
    }

    inline fun <reified T : Any> scoped(
        scopeRef: ScopeRef,
        qualifier: Qualifier? = null,
        noinline factory: Component.() -> T,
    ): Bindable {
        return scoped(scopeRef, T::class.java, qualifier, factory)
    }

    fun <T : Any> scoped(
        scopeRef: ScopeRef,
        type: Class<T>,
        qualifier: Qualifier? = null,
        factory: Component.() -> T,
    ): Bindable {
        return createAndRegisterScopedNode(type, qualifier, scopeRef, factory)
            .also(registeredNodes::add)
    }

    private fun <T : Any> createAndRegisterNode(
        type: Class<T>,
        qualifier: Qualifier?,
        definitionType: DefinitionType,
        factory: Component.() -> T,
    ): Node {
        val node = Node(
            type = type,
            qualifier = qualifier,
            scopeRef = null,
            definitionType = definitionType,
            factory = factory,
            onBind = ::registerAlias,
        )
        val inner = Registry.definitions.getOrPut(type) { HashMap() }
        check(!inner.containsKey(qualifier)) {
            "Duplicate binding for ${type.name} / ${qualifier ?: "<default>"}"
        }
        inner[qualifier] = node
        return node
    }

    private fun <T : Any> createAndRegisterScopedNode(
        type: Class<T>,
        qualifier: Qualifier?,
        scopeRef: ScopeRef,
        factory: Component.() -> T,
    ): Node {
        val node = Node(
            type = type,
            qualifier = qualifier,
            scopeRef = scopeRef,
            definitionType = DefinitionType.Scoped,
            factory = factory,
            onBind = ::registerAlias,
        )
        val scopeRefByQualifier = Registry.scopedDefinitions.getOrPut(type) { HashMap() }
        val nodeByScopeRef = scopeRefByQualifier.getOrPut(qualifier) { HashMap() }
        check(!nodeByScopeRef.containsKey(scopeRef)) {
            "Duplicate binding for ${type.name} / ${qualifier ?: "<default>"} / '${scopeRef.name}'"
        }
        nodeByScopeRef[scopeRef] = node
        return node
    }

    private fun registerAlias(aliasType: Class<*>, target: Node) {
        if (target.scopeRef == null) {
            val primary = Registry.definitions.getOrPut(target.type) { HashMap() }
            val existing = Registry.definitions[aliasType]
            check(existing == null || existing === primary) {
                "Conflicting bindings for ${aliasType.name}: already has its own family."
            }
            Registry.definitions[aliasType] = primary
        } else {
            val primaryScoped = Registry.scopedDefinitions.getOrPut(target.type) { HashMap() }
            val existingScoped = Registry.scopedDefinitions[aliasType]
            check(existingScoped == null || existingScoped === primaryScoped) {
                "Conflicting scoped bindings for ${aliasType.name}: already has its own family."
            }
            Registry.scopedDefinitions[aliasType] = primaryScoped
        }
    }

    internal fun getRegisteredNodes(): ArrayList<Node> = registeredNodes

    internal fun getRegisteredEagerNodes(): ArrayList<Node> = registeredEagerNodes
}

fun module(forceEager: Boolean = false, onRegister: Module.() -> Unit): Module =
    Module(forceEager, onRegister)

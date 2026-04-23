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

import com.harrytmthy.stitch.exception.DuplicateBindingException
import com.harrytmthy.stitch.internal.DefinitionType
import com.harrytmthy.stitch.internal.DefinitionType.Factory
import com.harrytmthy.stitch.internal.DefinitionType.Scoped
import com.harrytmthy.stitch.internal.DefinitionType.Singleton
import com.harrytmthy.stitch.internal.Node
import com.harrytmthy.stitch.internal.Registry
import kotlin.reflect.KClass

/**
 * A container for binding definitions in Stitch's runtime registration path.
 *
 * Use the [module] factory function to create instances via a DSL:
 * ```
 * val networkModule = module {
 *     singleton { OkHttpClient() }
 *     factory { ApiService(client = get()) }
 *     scoped(activityScope) { HomeViewModel(api = get()) }
 * }
 * ```
 *
 * @param forceEager When true, all singletons in this module are eagerly initialized on
 *                   [Stitch.register], regardless of individual `eager` flags.
 */
class Module(private val forceEager: Boolean) {

    private val definitions = HashSet<Node>()

    private val scopedDefinitions = HashSet<Node>()

    private val nodeAliases = HashMap<Node, HashSet<KClass<*>>>()

    private val eagerNodes = ArrayList<Node>()

    /**
     * Registers this module, making its bindings available for resolution.
     *
     * Equivalent to [Stitch.register] for a single module. Eager singletons are warmed up
     * immediately after registration.
     */
    fun register() {
        for (node in definitions) {
            Registry.definitions[node.type]?.let { nodeByQualifier ->
                if (node.qualifier in nodeByQualifier) {
                    throw DuplicateBindingException(node.type, node.qualifier, scopeName = null)
                }
            }
            val nodeByQualifier = Registry.definitions.getOrPut(node.type, ::HashMap)
            nodeByQualifier[node.qualifier] = node
        }

        for (node in scopedDefinitions) {
            Registry.scopedDefinitions[node.scopeName]?.let { qualifiersByType ->
                qualifiersByType[node.type]?.let { nodeByQualifier ->
                    if (node.qualifier in nodeByQualifier) {
                        throw DuplicateBindingException(node.type, node.qualifier, node.scopeName)
                    }
                }
            }
            val qualifiersByType = Registry.scopedDefinitions.getOrPut(node.scopeName!!, ::HashMap)
            val nodeByQualifier = qualifiersByType.getOrPut(node.type, ::HashMap)
            nodeByQualifier[node.qualifier] = node
        }

        for ((node, aliases) in nodeAliases) {
            for (aliasType in aliases) {
                if (node.scopeName == null) {
                    val primary = Registry.definitions[node.type]
                        ?: error("$node is missing from the registry during alias registration")
                    val existing = Registry.definitions[aliasType]
                    check(existing == null || existing === primary) {
                        "'${aliasType.qualifiedName}' is already registered as an alias for $node"
                    }
                    Registry.definitions[aliasType] = primary
                } else {
                    val scopedByType = Registry.scopedDefinitions[node.scopeName]
                        ?: error("$node is missing from the registry during alias registration")
                    val primaryScoped = scopedByType[node.type]
                        ?: error("$node is missing from the registry during alias registration")
                    val existingScoped = scopedByType[aliasType]
                    check(existingScoped == null || existingScoped === primaryScoped) {
                        "'${aliasType.qualifiedName}' is already registered as an alias for $node"
                    }
                    scopedByType[aliasType] = primaryScoped
                }
            }
        }

        // Warmup eager nodes
        for (node in eagerNodes) {
            Stitch.get(node.type, node.qualifier)
        }
    }

    /**
     * Registers a singleton binding. The [factory] is invoked at most once; subsequent
     * resolutions return the cached instance.
     *
     * @param eager When true (or when the module's [forceEager] is true), the instance is
     *              created immediately on [Stitch.register] instead of on first access.
     * @return A [Bindable] that can be chained with [Bindable.bind] to register type aliases.
     * @throws DuplicateBindingException if a binding for the same type + qualifier already exists.
     */
    inline fun <reified T : Any> singleton(
        qualifier: Qualifier? = null,
        eager: Boolean = false,
        noinline factory: ResolutionContext.() -> T,
    ): Bindable {
        return define(T::class, qualifier, Singleton, eager, null, factory)
    }

    /**
     * Registers a factory binding. The [factory] is invoked on every resolution, producing
     * a new instance each time.
     *
     * @return A [Bindable] that can be chained with [Bindable.bind] to register type aliases.
     * @throws DuplicateBindingException if a binding for the same type + qualifier already exists.
     */
    inline fun <reified T : Any> factory(
        qualifier: Qualifier? = null,
        noinline factory: ResolutionContext.() -> T,
    ): Bindable {
        return define(T::class, qualifier, Factory, false, null, factory)
    }

    /**
     * Registers a scoped binding tied to [scopeRef]. The [factory] is invoked once per
     * [Scope] instance created from the given [scopeRef].
     *
     * @return A [Bindable] that can be chained with [Bindable.bind] to register type aliases.
     * @throws DuplicateBindingException if a binding for the same type + qualifier + scope
     *         already exists.
     */
    inline fun <reified T : Any> scoped(
        scopeRef: ScopeRef,
        qualifier: Qualifier? = null,
        noinline factory: ResolutionContext.() -> T,
    ): Bindable {
        return define(T::class, qualifier, Scoped, false, scopeRef.name, factory)
    }

    @PublishedApi
    internal fun <T : Any> define(
        type: KClass<T>,
        qualifier: Qualifier?,
        definitionType: DefinitionType,
        eager: Boolean,
        scopeName: String?,
        factory: (ResolutionContext.() -> T)?,
    ): Bindable {
        return Node(
            type = type,
            qualifier = qualifier,
            scopeName = scopeName,
            definitionType = definitionType,
            factory = factory!!,
            onBind = ::registerAlias,
        ).also { node ->
            val target = if (scopeName == null) {
                if (definitionType == Singleton && (eager || forceEager)) {
                    eagerNodes.add(node)
                }
                definitions
            } else {
                scopedDefinitions
            }
            if (node in target) {
                throw DuplicateBindingException(type, qualifier, scopeName)
            }
            target.add(node)
        }
    }

    private fun registerAlias(aliasType: KClass<*>, target: Node) {
        val aliases = nodeAliases.getOrPut(target, ::HashSet)
        if (!aliases.add(aliasType)) {
            error("'${aliasType.qualifiedName}' is already registered as an alias for $target")
        }
    }

    internal fun getRegisteredNodes(): List<Node> =
        buildList {
            addAll(definitions)
            addAll(scopedDefinitions)
        }
}

/**
 * Creates a [Module] using a DSL builder.
 *
 * @param forceEager When true, all singletons defined in this module are eagerly initialized
 *                   on [Stitch.register].
 */
inline fun module(forceEager: Boolean = false, crossinline onInit: Module.() -> Unit): Module =
    Module(forceEager).also(onInit)

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
import com.harrytmthy.stitch.exception.ScopeClosedException
import com.harrytmthy.stitch.internal.ConcurrentHashMap
import com.harrytmthy.stitch.internal.Registry
import kotlinx.atomicfu.atomic

/**
 * A runtime scope instance that controls the lifecycle and caching of scoped bindings.
 *
 * Obtain instances via [ScopeRef.createScope]:
 * ```
 * val activityScope = scope("activity")
 * val instance = activityScope.createScope()
 *
 * val viewModel: HomeViewModel = instance.get()
 *
 * instance.close() // Clears all cached instances for this scope
 * ```
 *
 * Resolving from a closed scope throws [ScopeClosedException].
 */
class Scope internal constructor(val id: Int, val name: String, val parent: Scope?) {

    private val open = atomic(true)

    /**
     * Closes this scope, clearing all cached instances and preventing further resolution.
     */
    fun close() {
        open.value = false
        Registry.scoped.remove(id)
        ScopeManager.getScopeIdsByName(name)?.remove(id)
    }

    fun isOpen(): Boolean = open.value

    /**
     * Resolves a scoped dependency of type [T].
     *
     * @throws ScopeClosedException if this scope is not open.
     * @throws MissingBindingException if no binding is found.
     */
    inline fun <reified T : Any> get(qualifier: Qualifier? = null): T =
        Stitch.get<T>(qualifier, scope = this)

    /**
     * Returns a [Lazy] that resolves a scoped dependency of type [T] on first access.
     *
     * The lazy uses [LazyThreadSafetyMode.NONE], so it is not thread-safe.
     */
    inline fun <reified T : Any> inject(qualifier: Qualifier? = null): Lazy<T> =
        lazy(LazyThreadSafetyMode.NONE) { get(qualifier) }

    /**
     * Creates a child [Scope] instance with this scope as its parent.
     *
     * The child can resolve bindings from its own scope and all ancestor scopes.
     * Prefer this over [ScopeRef.createScope] with a manual [parent] when the
     * parent-child relationship is declared via [dependsOn].
     *
     * ```
     * val homeActivityScope = activityScope.createScope()
     * val homeFragmentScope = homeActivityScope.createChildScope(fragmentScope)
     * ```
     *
     * @throws IllegalStateException if [scopeRef] is not a declared child of this scope.
     */
    fun createChildScope(scopeRef: ScopeRef): Scope = createChildScope(scopeRef.name)

    /**
     * Creates a child [Scope] instance with this scope as its parent.
     *
     * The child can resolve bindings from its own scope and all ancestor scopes.
     *
     * ```
     * val fragmentScopeInstance = activityScopeInstance.createChildScope("fragment")
     * ```
     *
     * @throws IllegalStateException if [scopeName] is not a declared child of this scope.
     */
    fun createChildScope(scopeName: String): Scope {
        val scopeChildren = ScopeManager.scopeChildren[name] ?: error("Scope '$name' has no child")
        require(scopeName in scopeChildren) { "'$scopeName' is not a child of scope '$name'" }
        return Scope(id = ScopeManager.nextId(), name = scopeName, parent = this)
    }

    override fun hashCode(): Int = id

    override fun equals(other: Any?): Boolean = other is Scope && other.id == this.id
}

/**
 * A scope template that creates [Scope] instances. Obtained via the [scope] function.
 *
 * Multiple [Scope] instances can be created from the same [ScopeRef], each with its own
 * lifecycle and cached bindings.
 */
class ScopeRef(val name: String) {

    /**
     * Creates a new [Scope] instance.
     * - Scope instances are closed by default.
     * - Use [parent] to allow this scope to resolve bindings from ancestor scopes.
     *
     * ```
     * val homeActivityScope = activityScope.createScope()
     * val homeFragmentScope = fragmentScope.createScope(parent = homeActivityScope)
     * ```
     *
     * @param parent the parent scope instance, or null for a root scope.
     */
    fun createScope(parent: Scope? = null): Scope {
        val scopeId = ScopeManager.nextId()
        val inner = ScopeManager.idsByScopeName.computeIfAbsent(name) { HashSet() }
        inner.add(scopeId)
        return Scope(scopeId, name, parent)
    }

    override fun hashCode(): Int = name.hashCode()

    override fun equals(other: Any?): Boolean = other is ScopeRef && other.name == this.name

    override fun toString(): String = name
}

internal object ScopeManager {

    val pool = ConcurrentHashMap<String, ScopeRef>()

    val scopeDependencies = ConcurrentHashMap<String, String>() // Child -> Parent

    val scopeChildren = ConcurrentHashMap<String, HashSet<String>>()

    val idsByScopeName = ConcurrentHashMap<String, HashSet<Int>>()

    val nextId = atomic(1)

    fun getOrCreate(name: String): ScopeRef {
        if (name.isEmpty()) {
            error("Scope name cannot be empty")
        }
        return pool.computeIfAbsent(name, ::ScopeRef)
    }

    fun nextId(): Int = nextId.getAndIncrement()

    fun getScopeIdsByName(name: String): HashSet<Int>? = idsByScopeName[name]

    fun clear() {
        pool.clear()
        scopeDependencies.clear()
        scopeChildren.clear()
        idsByScopeName.clear()
        nextId.value = 1
    }
}

/**
 * Returns a pooled [ScopeRef] for the given [name], creating one if it doesn't exist.
 *
 * ```
 * val activityScope = scope("activity")
 * ```
 *
 * @throws IllegalStateException if [name] is empty.
 */
fun scope(name: String): ScopeRef = ScopeManager.getOrCreate(name)

/**
 * Defines scope dependency. Each scope can only depend on one parent.
 *
 * ```
 * val activityScope = scope("activity")
 * val fragmentScope = scope("fragment").dependsOn(activityScope)
 * ```
 */
fun ScopeRef.dependsOn(parent: ScopeRef): ScopeRef = dependsOn(parent.name)

/**
 * Defines scope dependency. Each scope can only depend on one parent.
 *
 * ```
 * val fragmentScope = scope("fragment").dependsOn("activity")
 * ```
 */
fun ScopeRef.dependsOn(parentName: String): ScopeRef {
    require(parentName != this.name) { "Scope '$this' cannot depend on itself" }
    ScopeManager.scopeDependencies[this.name]?.let { parent ->
        if (parent == parentName) {
            // Multiple dependsOn towards the same parent should be ignored
            return this
        }
        throw IllegalArgumentException("Scope '$this' already has '$parent' as its parent")
    }

    // Cycle detection
    var path = this.name
    var currentParent: String? = parentName
    while (currentParent != null) {
        path += " -> $currentParent"
        check(currentParent != this.name) { "Cycle detected in scope dependencies: $path" }
        currentParent = ScopeManager.scopeDependencies[currentParent]
    }

    // No cycle, register the edge
    ScopeManager.scopeDependencies[this.name] = parentName
    val scopeChildren = ScopeManager.scopeChildren.getOrPut(parentName, ::HashSet)
    scopeChildren.add(this.name)
    return this
}

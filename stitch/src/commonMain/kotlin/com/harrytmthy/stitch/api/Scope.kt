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
 * instance.open()
 *
 * val viewModel: HomeViewModel = instance.get()
 *
 * instance.close() // Clears all cached instances for this scope
 * ```
 *
 * A scope must be opened before resolution and closed when no longer used.
 * Resolving from a closed scope throws [ScopeClosedException].
 */
class Scope internal constructor(val id: Int, val name: String) {

    private val open = atomic(false)

    /**
     * Opens this scope, allowing bindings to be resolved.
     */
    fun open() {
        val inner = ScopeManager.idsByScopeName.computeIfAbsent(name) { HashSet() }
        inner.add(id)
        open.value = true
    }

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
     * Creates a new [Scope] instance. The scope starts closed; call [Scope.open] before use.
     */
    fun createScope(): Scope = Scope(id = ScopeManager.nextId(), name)

    override fun hashCode(): Int = name.hashCode()

    override fun equals(other: Any?): Boolean = other is ScopeRef && other.name == this.name
}

internal object ScopeManager {

    val pool = ConcurrentHashMap<String, ScopeRef>()

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

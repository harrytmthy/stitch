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

import com.harrytmthy.stitch.exception.ScopeClosedException
import com.harrytmthy.stitch.internal.ConcurrentHashMap
import com.harrytmthy.stitch.internal.Registry
import kotlinx.atomicfu.atomic

class Scope internal constructor(val id: Int, val name: String) {

    private val open = atomic(false)

    fun open() {
        open.value = true
    }

    fun close() {
        open.value = false
        Registry.scoped.remove(id)
        ScopeManager.getScopeIdsByName(name)?.remove(id)
    }

    fun isOpen(): Boolean = open.value

    inline fun <reified T : Any> get(qualifier: Qualifier? = null): T {
        if (!isOpen()) {
            // Fail-fast: We already have this exception
            throw ScopeClosedException(T::class, qualifier, id)
        }
        val value = Stitch.get<T>(qualifier, scope = this)
        if (!isOpen()) {
            throw ScopeClosedException(T::class, qualifier, id)
        }
        return value
    }

    inline fun <reified T : Any> inject(qualifier: Qualifier? = null): Lazy<T> =
        lazy(LazyThreadSafetyMode.NONE) { get(qualifier) }

    override fun hashCode(): Int = id

    override fun equals(other: Any?): Boolean = other is Scope && other.id == this.id
}

sealed class ScopeRef(val name: String) {

    override fun hashCode(): Int = name.hashCode()

    override fun equals(other: Any?): Boolean = other is ScopeRef && other.name == this.name
}

class RetrievableScopeRef(name: String) : ScopeRef(name) {

    fun createScope(): Scope {
        val id = ScopeManager.nextId()
        val inner = ScopeManager.idsByScopeName.computeIfAbsent(name) { HashSet() }
        inner.add(id)
        return Scope(id, name)
    }
}

internal object ScopeManager {

    val pool = ConcurrentHashMap<String, RetrievableScopeRef>()

    val idsByScopeName = ConcurrentHashMap<String, HashSet<Int>>()

    val nextId = atomic(1)

    fun getOrCreate(name: String): RetrievableScopeRef {
        if (name.isEmpty()) {
            error("Scope name cannot be empty")
        }
        return pool.computeIfAbsent(name, ::RetrievableScopeRef)
    }

    fun nextId(): Int = nextId.getAndIncrement()

    fun getScopeIdsByName(name: String): HashSet<Int>? = idsByScopeName[name]

    fun clear() {
        pool.clear()
        idsByScopeName.clear()
        nextId.value = 1
    }
}

fun scope(name: String): RetrievableScopeRef = ScopeManager.getOrCreate(name)

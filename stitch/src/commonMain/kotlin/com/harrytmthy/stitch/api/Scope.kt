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

import com.harrytmthy.stitch.internal.Registry
import com.harrytmthy.stitch.internal.computeIfAbsentCompat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class Scope internal constructor(internal val id: Int, internal val reference: ScopeRef) {

    private val open = AtomicBoolean(false)

    fun open() {
        open.set(true)
    }

    fun close() {
        open.set(false)
        Registry.scoped.remove(id)
        ScopeRef.getScopeIds(reference)?.remove(id)
    }

    inline fun <reified T : Any> get(qualifier: Qualifier? = null): T =
        Stitch.get(qualifier, scope = this)

    inline fun <reified T : Any> inject(qualifier: Qualifier? = null): Lazy<T> =
        Stitch.inject(qualifier, scope = this)

    internal fun isOpen(): Boolean = open.get()
}

@JvmInline
value class ScopeRef private constructor(val name: String) {

    fun newInstance(): Scope {
        val id = nextId()
        val inner = idsByScopeRef.computeIfAbsentCompat(this) { HashSet() }
        inner.add(id)
        return Scope(id, reference = this)
    }

    inline fun <reified T : Any> get(qualifier: Qualifier? = null): T =
        Stitch.get(qualifier)

    companion object {

        private val pool = ConcurrentHashMap<String, ScopeRef>()

        private val idsByScopeRef = ConcurrentHashMap<ScopeRef, HashSet<Int>>()

        private val nextId = AtomicInteger(1)

        fun of(name: String): ScopeRef = pool.computeIfAbsentCompat(name) { ScopeRef(it) }

        internal fun nextId(): Int = nextId.getAndIncrement()

        internal fun getScopeIds(scopeRef: ScopeRef): HashSet<Int>? = idsByScopeRef[scopeRef]

        internal fun clear() {
            pool.clear()
            idsByScopeRef.clear()
            nextId.set(1)
        }
    }
}

fun scope(name: String): ScopeRef = ScopeRef.of(name)

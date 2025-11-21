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

import com.harrytmthy.stitch.internal.ConcurrentHashMap
import kotlinx.atomicfu.atomic

sealed interface Qualifier

class Named private constructor(val value: String) : Qualifier {

    private val id = nextId()

    override fun hashCode(): Int = id

    override fun equals(other: Any?): Boolean = other is Named && other.id == this.id

    companion object {

        private val pool = ConcurrentHashMap<String, Named>()

        private val nextId = atomic(1)

        fun of(name: String): Named = pool.computeIfAbsent(name, ::Named)

        internal fun nextId(): Int = nextId.getAndIncrement()

        internal fun clear() {
            pool.clear()
            nextId.value = 1
        }
    }
}

fun named(value: String): Named = Named.of(value)

internal object DefaultQualifier : Qualifier

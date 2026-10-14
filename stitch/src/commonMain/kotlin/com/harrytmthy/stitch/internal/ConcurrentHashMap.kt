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

package com.harrytmthy.stitch.internal

import kotlinx.atomicfu.locks.SynchronizedObject

internal expect class ConcurrentHashMap<K, V>() : SynchronizedObject {

    operator fun get(key: K): V?

    fun put(key: K, value: V): V?

    operator fun set(key: K, value: V)

    fun remove(key: K): V?

    fun isEmpty(): Boolean

    fun containsKey(key: K): Boolean

    fun putIfAbsent(key: K, value: V): V?

    inline fun computeIfAbsent(key: K, crossinline mapping: (K) -> V): V

    fun clear()
}

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

import kotlinx.atomicfu.locks.synchronized

internal class ConcurrentHashMap<K, V> : HashMap<K, V>() {

    private val lock = Any()

    override fun get(key: K): V? =
        synchronized(lock) {
            super[key]
        }

    override fun put(key: K, value: V): V? =
        synchronized(lock) {
            super.put(key, value)
        }

    override fun remove(key: K): V? =
        synchronized(lock) {
            super.remove(key)
        }

    override fun isEmpty(): Boolean =
        synchronized(lock) {
            super.isEmpty()
        }

    override fun containsKey(key: K): Boolean =
        synchronized(lock) {
            super.containsKey(key)
        }

    override fun putIfAbsent(key: K, value: V): V? =
        synchronized(lock) {
            super.putIfAbsent(key, value)
        }

    inline fun computeIfAbsent(key: K, crossinline mapping: (K) -> V): V =
        synchronized(lock) {
            super[key]?.let { return it }
            val newVal = mapping(key)
            return super.putIfAbsent(key, newVal) ?: newVal
        }

    override fun clear() {
        synchronized(lock) {
            super.clear()
        }
    }
}

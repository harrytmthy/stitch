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

internal actual class ConcurrentHashMap<K, V> actual constructor() : HashMap<K, V>() {

    private val lock = Any()

    actual override operator fun get(key: K): V? =
        synchronized(lock) {
            super[key]
        }

    actual override fun put(key: K, value: V): V? =
        synchronized(lock) {
            super.put(key, value)
        }

    actual operator fun set(key: K, value: V) {
        put(key, value)
    }

    actual override fun remove(key: K): V? =
        synchronized(lock) {
            super.remove(key)
        }

    actual override fun isEmpty(): Boolean =
        synchronized(lock) {
            super.isEmpty()
        }

    actual override fun containsKey(key: K): Boolean =
        synchronized(lock) {
            super.containsKey(key)
        }

    actual override fun putIfAbsent(key: K, value: V): V? =
        synchronized(lock) {
            val previous = super.put(key, value)
            if (previous != null) {
                super.put(key, previous)
            }
            previous
        }

    actual inline fun computeIfAbsent(key: K, crossinline mapping: (K) -> V): V =
        synchronized(lock) {
            super[key]?.let { return it }
            val newVal = mapping(key)
            val previous = super.put(key, newVal) ?: return newVal
            super.put(key, previous)
            previous
        }

    actual override fun clear() {
        synchronized(lock) {
            super.clear()
        }
    }
}

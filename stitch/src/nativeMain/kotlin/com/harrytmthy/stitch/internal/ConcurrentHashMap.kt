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
import kotlinx.atomicfu.locks.synchronized

internal actual class ConcurrentHashMap<K, V> actual constructor() : SynchronizedObject() {

    private val lock = SynchronizedObject()

    private val map = HashMap<K, V>()

    actual operator fun get(key: K): V? =
        synchronized(lock) {
            map[key]
        }

    actual fun put(key: K, value: V): V? =
        synchronized(lock) {
            map.put(key, value)
        }

    actual operator fun set(key: K, value: V) {
        put(key, value)
    }

    actual fun remove(key: K): V? =
        synchronized(lock) {
            map.remove(key)
        }

    actual fun isEmpty(): Boolean =
        synchronized(lock) {
            map.isEmpty()
        }

    actual fun containsKey(key: K): Boolean =
        synchronized(lock) {
            map.containsKey(key)
        }

    actual fun putIfAbsent(key: K, value: V): V? =
        synchronized(lock) {
            val previous = map.put(key, value)
            if (previous != null) {
                map.put(key, previous)
            }
            previous
        }

    actual inline fun computeIfAbsent(key: K, crossinline mapping: (K) -> V): V =
        synchronized(lock) {
            map[key]?.let { return it }
            val newVal = mapping(key)
            val previous = map.put(key, newVal) ?: return newVal
            map.put(key, previous)
            previous
        }

    actual fun clear() {
        synchronized(lock) {
            map.clear()
        }
    }
}

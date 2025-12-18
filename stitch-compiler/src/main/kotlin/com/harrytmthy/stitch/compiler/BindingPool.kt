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

package com.harrytmthy.stitch.compiler

/**
 * A map of bindings where both key & value point to the same instance (`key == value`).
 *
 * The purpose is to allow [Binding] and its subclasses to find the previously stored
 * instance, for example:
 *
 * ```
 * val binding = ProvidedBinding(type, qualifier, ...)
 * bindingPool.add(binding)
 *
 * // True, if requestedBinding has the same type + qualifier
 * check(binding == bindingPool[requestedBinding])
 *
 * // Also true
 * check(binding == bindingPool[Binding(type, qualifier)])
 * ```
 *
 * This allows passing [RequestedBinding] to get [ProvidedBinding] without any overhead from
 * creating a new [Binding] instance (`Binding(type, qualifier)`).
 */
class BindingPool<E : Binding>(
    initialCapacity: Int = 16,
    loadFactor: Float = 0.75f,
) : HashMap<Binding, E>(initialCapacity, loadFactor) {

    fun add(element: E) {
        this[element] = element
    }
}

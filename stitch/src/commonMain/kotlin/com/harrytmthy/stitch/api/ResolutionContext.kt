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

import com.harrytmthy.stitch.exception.CycleException
import com.harrytmthy.stitch.internal.Node

/**
 * Tracks the resolution stack during dependency resolution, enabling cycle detection.
 *
 * A [ResolutionContext] is available as a receiver inside factory lambdas, providing
 * [get] and [lazyOf] for resolving transitive dependencies:
 * ```
 * singleton { HomeViewModel(logger = get(), repo = get()) }
 * ```
 *
 * @throws com.harrytmthy.stitch.exception.CycleException if a dependency cycle is detected.
 */
class ResolutionContext internal constructor(val component: Component, val scope: Scope?) {

    private val stack = ArrayDeque<Node>()

    private val indexByNode = HashMap<Node, Int>()

    internal fun enter(node: Node) {
        val nodeIndex = indexByNode[node]
        if (nodeIndex != null) {
            val cycle = ArrayList<Node>(stack.size - nodeIndex + 1)
            for (index in nodeIndex until stack.size) {
                cycle += stack[index]
            }
            cycle += node
            throw CycleException(node.type, node.qualifier, cycle)
        }
        indexByNode[node] = stack.size
        stack.addLast(node)
    }

    internal fun exit() {
        val removed = stack.removeLast()
        indexByNode.remove(removed)
    }

    /**
     * Resolves a transitive dependency of type [T] within this resolution context.
     */
    inline fun <reified T : Any> get(qualifier: Qualifier? = null): T =
        component.getInternal(T::class, qualifier, scope, resolutionContext = this)

    /**
     * Returns a [Lazy] that resolves a dependency of type [T] on first access.
     *
     * Useful for breaking initialization order constraints without introducing a full cycle.
     * The lazy uses [LazyThreadSafetyMode.NONE].
     */
    inline fun <reified T : Any> lazyOf(qualifier: Qualifier? = null): Lazy<T> =
        lazy(LazyThreadSafetyMode.NONE) {
            component.getInternal(T::class, qualifier, scope, resolutionContext = this)
        }
}

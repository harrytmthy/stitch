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

import com.harrytmthy.stitch.engine.ComponentSignature
import com.harrytmthy.stitch.engine.Node
import com.harrytmthy.stitch.engine.Plan
import com.harrytmthy.stitch.engine.PlanCache
import com.harrytmthy.stitch.engine.Signature
import com.harrytmthy.stitch.exception.CycleException
import com.harrytmthy.stitch.exception.MissingBindingException
import com.harrytmthy.stitch.internal.Registry
import java.util.ArrayDeque

object Stitch {

    /** Implicit app-level component, planned on first get<T>(). */
    @Volatile var root: Component? = null

    fun register(vararg modules: Module) {
        modules.forEach { module ->
            module.register()
            val signatures = module.binder.getStagedEagerDefinitions()
            if (signatures.isNotEmpty()) {
                warmUp(signatures)
            }
        }
        root = null // Invalidate cached component
    }

    private fun warmUp(signatures: List<Signature>) {
        for (signature in signatures) {
            val root = root ?: component(signature).also { root = it }
            root.get(signature.type, signature.qualifier)
        }
    }

    fun unregister() {
        Registry.definitions.clear()
        Registry.singletons.clear()
        Registry.version.set(0)
        PlanCache.clear()
        root = null
    }

    inline fun <reified T : Any> get(qualifier: Qualifier? = null): T =
        get(T::class.java, qualifier)

    fun <T : Any> get(type: Class<T>, qualifier: Qualifier? = null): T {
        val root = root ?: component(Signature(type, qualifier)).also { root = it }
        return root.get(type, qualifier)
    }

    internal inline fun <reified T : Any> componentFor(qualifier: Qualifier? = null): Component =
        component(Signature(T::class.java, qualifier))

    private fun component(signature: Signature): Component {
        val componentSignature = ComponentSignature(signature, Registry.version.get())
        PlanCache.computeIfAbsent(componentSignature) {
            val graph = buildSubgraph(signature)
            if (graph.size > 1) {
                getTopologicalSortedPlan(graph)
            } else {
                val node = graph.getValue(signature)
                Plan(listOf(signature), listOf(node))
            }
        }
        return Component(
            nodeLookup = ::lookupNode,
            singletons = Registry.singletons,
        )
    }

    internal fun lookupNode(type: Class<*>, qualifier: Qualifier?): Node {
        val inner = Registry.definitions[type] ?: throw MissingBindingException.missingType(type)
        return inner.getOrElse(qualifier) {
            throw MissingBindingException.missingQualifier(type, qualifier, inner.keys)
        }
    }

    /**
     * Builds the dependency subgraph starting from the given entry signature.
     * Uses BFS to traverse dependencies and collect all reachable nodes.
     */
    private fun buildSubgraph(entry: Signature): Map<Signature, Node> {
        val graph = HashMap<Signature, Node>()
        val queue = ArrayDeque<Signature>()
        queue.add(entry)
        while (queue.isNotEmpty()) {
            val signature = queue.removeFirst()
            if (graph.containsKey(signature)) {
                continue
            }
            val node = lookupNode(signature.type, signature.qualifier)
            if (graph.put(signature, node) == null) {
                node.getDirectDependencies().forEach { dependency ->
                    if (!graph.containsKey(dependency)) {
                        queue.add(dependency)
                    }
                }
            }
        }
        return graph
    }

    /**
     * Sorts signatures in topological order (dependencies before dependents).
     * Uses Kahn's algorithm with reverse edges for efficient traversal.
     *
     * @throws CycleException if a circular dependency is detected.
     */
    private fun getTopologicalSortedPlan(graph: Map<Signature, Node>): Plan {
        val inDegree = HashMap<Signature, Int>(graph.size)
        val dependents = HashMap<Signature, MutableList<Signature>>(graph.size)
        graph.forEach { (signature, node) ->
            val dependencies = node.getDirectDependencies()
            inDegree[signature] = dependencies.size
            dependencies.forEach { dependency ->
                dependents.getOrPut(dependency) { mutableListOf() }.add(signature)
            }
        }

        // Initialize queue with nodes that have no dependencies
        val queue = ArrayDeque<Signature>()
        inDegree.forEach { (signature, degree) ->
            if (degree == 0) {
                queue.add(signature)
            }
        }

        // Process nodes in topological order
        val orderedSignatures = ArrayList<Signature>(graph.size)
        val orderedNodes = ArrayList<Node>(graph.size)
        while (queue.isNotEmpty()) {
            val signature = queue.removeFirst()
            orderedSignatures += signature
            orderedNodes += graph.getValue(signature)
            dependents[signature]?.forEach { dependent ->
                val newDegree = inDegree.getValue(dependent) - 1
                inDegree[dependent] = newDegree
                if (newDegree == 0) {
                    queue.add(dependent)
                }
            }
        }

        if (orderedSignatures.size != graph.size) {
            throw CycleException(findCyclePath(graph))
        }
        return Plan(orderedSignatures, orderedNodes)
    }

    /**
     * Finds a cycle in the dependency graph using DFS. Returns signatures forming the cycle.
     */
    private fun findCyclePath(graph: Map<Signature, Node>): List<Signature> {
        // Node colors: 0 = unvisited, 1 = visiting, 2 = visited
        val color = HashMap<Signature, Int>(graph.size)
        val parent = HashMap<Signature, Signature?>()
        graph.keys.forEach { signature ->
            color[signature] = 0
            parent[signature] = null
        }

        fun dfs(signature: Signature): Signature? {
            color[signature] = 1 // Mark as visiting
            val dependencies = graph[signature]?.getDirectDependencies().orEmpty()
            for (dependency in dependencies) {
                when (color[dependency]) {
                    // Unvisited: explore
                    0 -> {
                        parent[dependency] = signature
                        val cycleStart = dfs(dependency)
                        if (cycleStart != null) {
                            return cycleStart
                        }
                    }
                    // Back edge found: cycle detected
                    1 -> {
                        parent[dependency] = signature
                        return dependency
                    }
                    // 2 -> Already visited, skip
                }
            }
            color[signature] = 2 // Mark as visited
            return null
        }

        // Try DFS from each unvisited node
        var cycleStart: Signature? = null
        for (signature in graph.keys) {
            if (color[signature] != 0) {
                continue
            }
            cycleStart = dfs(signature)
            if (cycleStart != null) {
                break
            }
        }

        // Reconstruct cycle path
        if (cycleStart == null) {
            return emptyList()
        }

        val path = ArrayDeque<Signature>()
        var current = checkNotNull(parent[cycleStart])
        path.addFirst(cycleStart)
        while (current != cycleStart) {
            path.addFirst(current)
            current = checkNotNull(parent[current])
        }
        path.addLast(cycleStart) // Close the cycle
        return path.toList()
    }
}

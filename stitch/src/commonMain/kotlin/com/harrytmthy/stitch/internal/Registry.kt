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

import com.harrytmthy.stitch.api.DefaultQualifier
import com.harrytmthy.stitch.api.Qualifier
import com.harrytmthy.stitch.api.ScopeRef
import kotlin.reflect.KClass

internal object Registry {

    val definitions = HashMap<KClass<*>, HashMap<Qualifier?, Node>>()

    val scopedDefinitions = HashMap<ScopeRef, HashMap<KClass<*>, HashMap<Qualifier?, Node>>>()

    val singletons = ConcurrentHashMap<KClass<*>, ConcurrentHashMap<Qualifier, Any>>()

    val scoped = ConcurrentHashMap<Int, ConcurrentHashMap<KClass<*>, ConcurrentHashMap<Qualifier, Any>>>()

    fun remove(nodes: List<Node>) {
        for (node in nodes) {
            if (node.scopeRef != null) {
                scopedDefinitions[node.scopeRef]?.let { qualifiersByType ->
                    qualifiersByType[node.type]?.let { nodeByQualifier ->
                        nodeByQualifier.remove(node.qualifier)
                        if (nodeByQualifier.isEmpty()) {
                            qualifiersByType.removeFamily(node.type)
                            if (qualifiersByType.isEmpty()) {
                                scopedDefinitions.remove(node.scopeRef)
                            }
                        }
                    }
                }
                val scopeIds = ScopeRef.getScopeIds(node.scopeRef) ?: continue
                val iterator = scopeIds.iterator()
                while (iterator.hasNext()) {
                    val scopeId = iterator.next()
                    val qualifiersByType = scoped[scopeId] ?: continue
                    val instanceByQualifier = qualifiersByType[node.type] ?: continue
                    val qualifierKey = node.qualifier ?: DefaultQualifier
                    val removed = instanceByQualifier.remove(qualifierKey)
                    if (removed != null && instanceByQualifier.isEmpty()) {
                        qualifiersByType.remove(node.type)
                        if (qualifiersByType.isEmpty()) {
                            scoped.remove(scopeId)
                            iterator.remove()
                        }
                    }
                }
            } else {
                definitions[node.type]?.let { nodeByQualifier ->
                    nodeByQualifier.remove(node.qualifier)
                    if (nodeByQualifier.isEmpty()) {
                        definitions.removeFamily(node.type)
                    }
                }
                singletons[node.type]?.let { inner ->
                    val qualifierKey = node.qualifier ?: DefaultQualifier
                    inner.remove(qualifierKey)
                    if (inner.isEmpty()) {
                        singletons.remove(node.type)
                    }
                }
            }
        }
    }

    fun clear() {
        definitions.clear()
        scopedDefinitions.clear()
        singletons.clear()
        scoped.clear()
    }

    private fun <Q, V> HashMap<KClass<*>, HashMap<Q, V>>.removeFamily(type: KClass<*>) {
        val family = this[type] ?: return
        val iterator = this.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value === family) {
                iterator.remove()
            }
        }
    }
}

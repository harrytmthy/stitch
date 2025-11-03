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

import com.harrytmthy.stitch.api.Component.DefaultQualifier
import com.harrytmthy.stitch.api.Qualifier
import com.harrytmthy.stitch.api.ScopeRef
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

internal object Registry {

    val definitions = IdentityHashMap<Class<*>, MutableMap<Qualifier?, Node>>()

    val scopedDefinitions = IdentityHashMap<Class<*>, MutableMap<Qualifier?, MutableMap<ScopeRef, Node>>>()

    val singletons = ConcurrentHashMap<Class<*>, ConcurrentHashMap<Any, Any>>()

    val scoped = ConcurrentHashMap<Int, ConcurrentHashMap<Class<*>, ConcurrentHashMap<Any, Any>>>()

    fun remove(nodes: List<Node>) {
        for (node in nodes) {
            if (node.scopeRef != null) {
                scopedDefinitions[node.type]?.let { scopeRefsByQualifier ->
                    scopeRefsByQualifier[node.qualifier]?.let { nodeByScopeRef ->
                        nodeByScopeRef.remove(node.scopeRef)
                        if (nodeByScopeRef.isEmpty()) {
                            scopeRefsByQualifier.remove(node.qualifier)
                            if (scopeRefsByQualifier.isEmpty()) {
                                scopedDefinitions.removeFamily(node.type)
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

    private fun <Q, V> IdentityHashMap<Class<*>, MutableMap<Q, V>>.removeFamily(type: Class<*>) {
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

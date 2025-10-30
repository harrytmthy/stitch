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

package com.harrytmthy.stitch.engine

import com.harrytmthy.stitch.api.Qualifier
import com.harrytmthy.stitch.internal.computeIfAbsentCompat
import java.util.concurrent.ConcurrentHashMap

internal data class Plan(val signatures: List<Signature>, val nodes: List<Node>)

internal object PlanCache {

    private val cache = ConcurrentHashMap<ComponentSignature, Plan>()

    fun computeIfAbsent(componentSignature: ComponentSignature, loader: () -> Plan): Plan =
        cache.computeIfAbsentCompat(componentSignature) { loader() }

    fun clear() = cache.clear()
}

internal data class Signature(val type: Class<*>, val qualifier: Qualifier?) {

    override fun toString(): String = "${type.simpleName}[${qualifier ?: "<default>"}]"
}

internal data class ComponentSignature(val signature: Signature, val moduleHash: Int)

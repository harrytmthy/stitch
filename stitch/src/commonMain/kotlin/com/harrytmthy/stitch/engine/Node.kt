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
import com.harrytmthy.stitch.api.Scope
import com.harrytmthy.stitch.internal.Factory
import com.harrytmthy.stitch.internal.TraceResult

internal class Node(
    val type: Class<*>,
    val qualifier: Qualifier?,
    val scope: Scope,
    val factory: Factory,
    private val tracer: (Factory) -> TraceResult,
) {

    @Volatile private var dependencies: List<Signature>? = null

    @Volatile
    var prebuilt: Any? = null
        private set

    fun getDirectDependencies(): List<Signature> {
        dependencies?.let { return it }
        if (scope == Scope.Factory) {
            dependencies = emptyList()
            return emptyList()
        }
        synchronized(this) {
            dependencies?.let { return it }
            val result = tracer(factory)
            dependencies = result.dependencies
            if (scope == Scope.Singleton && result.prebuilt != null) {
                prebuilt = result.prebuilt
            }
            return result.dependencies
        }
    }

    fun consumePrebuilt(): Any? {
        val instance = prebuilt
        if (instance != null) {
            prebuilt = null
        }
        return instance
    }
}

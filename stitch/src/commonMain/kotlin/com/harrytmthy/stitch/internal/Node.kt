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

import com.harrytmthy.stitch.api.Bindable
import com.harrytmthy.stitch.api.Qualifier
import com.harrytmthy.stitch.api.ResolutionContext
import kotlin.reflect.KClass

internal class Node(
    val type: KClass<*>,
    val qualifier: Qualifier?,
    val scopeName: String?,
    val definitionType: DefinitionType,
    val factory: (ResolutionContext) -> Any,
    val onBind: (KClass<*>, Node) -> Unit,
) : Bindable {

    override fun <T : Any> bind(type: KClass<T>): Bindable = apply { onBind(type, this) }

    /**
     * Node is unique per [type], [qualifier], and [scopeName].
     */
    override fun hashCode(): Int {
        val qualifierHashCode = qualifier?.let { 31 * it.hashCode() } ?: 0
        val scopeNameHashCode = scopeName?.let { 31 * it.hashCode() } ?: 0
        return type.hashCode() + qualifierHashCode + scopeNameHashCode
    }

    override fun equals(other: Any?): Boolean =
        other is Node && other.type == this.type && other.qualifier == this.qualifier && other.scopeName == this.scopeName

    override fun toString(): String =
        buildString {
            append(type)
            qualifier?.let { append(" (qualifier: $it)") }
            scopeName?.let { append(" in scope \"${it}\"") }
        }
}

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
 * Represents a binding.
 */
open class Binding(val type: String, val qualifier: Qualifier?) {

    override fun hashCode(): Int =
        if (qualifier != null) {
            type.hashCode() + (31 * qualifier.hashCode())
        } else {
            type.hashCode()
        }

    override fun equals(other: Any?): Boolean =
        other is Binding && other.type == this.type && other.qualifier == this.qualifier
}

/**
 * Represents a provided binding that has been finalized by the aggregator module.
 */
class ProvidedBinding(
    type: String,
    qualifier: Qualifier?,
    val scope: Scope?,
    val location: String, // File path + line number
    val alias: Boolean = false,
) : Binding(type, qualifier) {

    var dependencies: HashSet<Binding>? = null
}

/**
 * Represents a requested binding via `@Inject`-annotated field. If there are requested bindings
 * that are never provided, Stitch will apply an action based on the current module type:
 * - Contributor: Put them as params of `@Contribute` which will be collected by the aggregator.
 * - Aggregator: After collecting all contributions, they are considered as missing bindings.
 */
class RequestedBinding(
    type: String,
    qualifier: Qualifier?,
    val fieldName: String,
) : Binding(type, qualifier)

sealed class Qualifier {

    abstract fun encode(): String

    data class Named(val value: String) : Qualifier() {
        override fun encode(): String = "Named:$value"
    }
}

sealed class Scope {

    data object Singleton : Scope()

    class Custom(
        val canonicalName: String,
        val qualifiedName: String = "",
        val location: String = "",
    ) : Scope() {

        override fun toString(): String = canonicalName

        override fun hashCode(): Int = canonicalName.hashCode()

        override fun equals(other: Any?): Boolean =
            other is Custom && other.canonicalName == this.canonicalName
    }
}

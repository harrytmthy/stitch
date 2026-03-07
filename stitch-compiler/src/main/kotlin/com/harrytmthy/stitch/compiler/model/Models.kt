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

package com.harrytmthy.stitch.compiler.model

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
 * Represents a binding declaration, which could be a provided or requested binding.
 * Declaration means the binding has [location] of where it is declared.
 */
open class BindingDeclaration(
    type: String,
    qualifier: Qualifier?,
    val location: String,
) : Binding(type, qualifier)

/**
 * Represents a provided binding that has been finalized by the aggregator module.
 *
 * @see com.harrytmthy.stitch.annotations.ContributedBinding
 */
class ProvidedBinding(
    type: String,
    qualifier: Qualifier?,
    val scope: Scope?,
    location: String, // File path + line number
    val kind: Int,
    val providerPackageName: String = "",
    val providerFunctionName: String = "",
    val providerClassName: String = "",
    val moduleKey: String = "", // Only used by the aggregator
) : BindingDeclaration(type, qualifier, location) {

    var dependencies: ArrayList<BindingDeclaration>? = null
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
    location: String,
    val fieldName: String,
) : BindingDeclaration(type, qualifier, location)

sealed class Qualifier {

    abstract fun encode(): String

    data class Named(val value: String) : Qualifier() {
        override fun encode(): String = "Named:$value"
    }

    companion object {

        fun of(value: String): Qualifier? {
            if (value.isEmpty()) {
                return null
            }
            val parts = value.split(":")
            if (parts.size < 2) {
                error("Should not happen")
            }
            return when {
                parts[0] == "Named" -> Named(parts[1])
                else -> error("Should not happen")
            }
        }
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

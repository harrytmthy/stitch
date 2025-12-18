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
    data class Named(val value: String) : Qualifier()
}

sealed class Scope {

    data object Singleton : Scope()

    /**
     * Considering a case where user provides a custom annotation:
     *
     * ```
     * @Scope
     * annotation class Activity
     * ```
     *
     * There are 2 distinct names:
     * - [originalName] = "Activity". Used in logs.
     * - [canonicalName] = "activity". Used as the true key.
     */
    class Custom(val originalName: String) : Scope() {

        val canonicalName: String = originalName.trim().lowercase()

        override fun toString(): String = originalName

        override fun hashCode(): Int = canonicalName.hashCode()

        override fun equals(other: Any?): Boolean =
            other is Custom && other.canonicalName == this.canonicalName
    }
}

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

package com.harrytmthy.stitch.exception

import com.harrytmthy.stitch.api.Qualifier

/**
 * Thrown when a binding is missing for the requested type or qualifier.
 *
 * Cases:
 * - The type has no binding registered.
 * - The requested qualifier does not exist for that type.
 */
class MissingBindingException internal constructor(
    type: Class<*>,
    qualifier: Qualifier?,
    explanation: String,
) : GetFailedException(type, qualifier, explanation) {

    companion object {

        /**
         * Creates a MissingBindingException for a type that has no binding registered.
         */
        fun missingType(type: Class<*>) =
            MissingBindingException(type, null, "No binding for the requested type.")

        /**
         * Creates a MissingBindingException for a qualifier that doesn't exist for a type.
         */
        fun missingQualifier(
            type: Class<*>,
            qualifier: Qualifier?,
            available: Collection<Qualifier?>,
        ): MissingBindingException {
            val message = buildString {
                append("No binding for the requested qualifier. ")
                append("Available: ${available.joinToString { it?.toString() ?: "<default>" }}")
            }
            return MissingBindingException(type, qualifier, message)
        }
    }
}

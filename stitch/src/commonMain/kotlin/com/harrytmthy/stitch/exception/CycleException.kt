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
import com.harrytmthy.stitch.internal.Signature

/**
 * Thrown when Stitch detects a dependency cycle during resolution.
 *
 * The message includes the requested type and qualifier, followed by a human readable
 * cycle path, for example:
 *
 * > "Failed to get com.example.HomeRepository (Qualifier: n/a): Dependency cycle detected:
 *   HomeRepository[<default>] -> HomeUseCase[<default>] -> HomeRepository[<default>]"
 *
 * Typical causes:
 * - Two or more singletons or scoped bindings reference each other directly or indirectly.
 * - A factory constructs a type that eventually asks for the original type again.
 *
 * Fix by breaking the cycle with an interface boundary, a provider/lazy, or by
 * moving one side to a factory that defers construction until first use.
 */
class CycleException internal constructor(
    type: Class<*>,
    qualifier: Qualifier?,
    path: List<Signature>,
) : GetFailedException(
    type = type,
    qualifier = qualifier,
    explanation = buildString {
        append("Dependency cycle detected: ")
        append(path.joinToString(" -> ") { it.toString() })
    },
)

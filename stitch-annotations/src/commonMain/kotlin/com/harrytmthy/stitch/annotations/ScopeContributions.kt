/*
 * Copyright 2026 Harry Timothy Tumalewa
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

package com.harrytmthy.stitch.annotations

/**
 * Generated annotation placed on contributor stub classes to carry scope metadata
 * across module boundaries. The aggregator at the [StitchRoot] module reads these
 * to build the scope hierarchy.
 *
 * This is not intended for manual use.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class ScopeContributions(val scopes: Array<RegisteredScope>)

/**
 * Represents a custom scope that is registered in a contributor module.
 * Each scope depends on Singleton by default (id = 0).
 *
 * The aggregator will register custom scopes and their dependencies using [canonicalName]
 * as the identifier, while enforcing these rules:
 * - A registered scope with a non-empty [qualifiedName] will be prioritized.
 * - If there are more than one scope with same [canonicalName] but different [qualifiedName],
 *   the aggregator will report them as duplicates + mention each [location] to ease debugging.
 */
annotation class RegisteredScope(
    val id: Int,
    val originalName: String,
    val canonicalName: String,
    val qualifiedName: String,
    val location: String,
    val dependsOn: Int,
)

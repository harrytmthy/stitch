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
 * Stores useful data to build dependency graph and generate code.
 *
 * This shouldn't be an `object` since KSP runs per module inside the Gradle daemon, and
 * processor classes can live long enough that static state bleeds across compilations.
 */
class Registry {

    /**
     * Represents bindings that are provided via `@Provides`, `@Inject` constructor, and `@Binds`.
     * Binding requests should lookup here. Bindings that don't exist here are never provided.
     *
     * @see BindingPool
     */
    val providedBindings = BindingPool<ProvidedBinding>()

    /**
     * Represents bindings that are requested via field injections. This map collects the
     * required data to generate DCL instances + `inject(target: T)`.
     */
    val requestedBindingsByClass = HashMap<String, ArrayList<RequestedBinding>>()

    /**
     * Represents bindings that are missing.
     *
     * Each contributor will register bindings that are requested but never provided in its module.
     * The aggregator will append all provided + missing bindings into their respective fields,
     * then ensure all missing bindings are actually present in [providedBindings].
     */
    val missingBindings = HashSet<Binding>()

    /**
     * Represents custom scopes grouped by their FQN.
     */
    val customScopeByQualifiedName = HashMap<String, Scope.Custom>()

    /**
     * Represents scope dependencies that are registered via `@DependsOn`.
     * - Key: The registered scope
     * - Value: Its dependency
     */
    val scopeDependencies = HashMap<Scope, Scope>()

    /**
     * Represents whether the current module is the aggregator module.
     */
    var isAggregator = false
}

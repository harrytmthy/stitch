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

package com.harrytmthy.stitch.compiler.model.plan

import com.harrytmthy.stitch.compiler.model.RequestedBinding
import com.harrytmthy.stitch.compiler.model.Scope

/**
 * Represents the generated 'per scope' ScopeRef used for the code generation.
 *
 * Example:
 *
 * ```
 * package com.harrytmthy.stitch.generated
 *
 * import com.harrytmthy.stitch.generated.StitchActivityGraph
 * import com.harrytmthy.stitch.generated.StitchFragmentGraph
 * import com.harrytmthy.stitch.generated.StitchSingletonGraph
 *
 * // Step 1: Transform each registered scope into `<scopeName>Graph: Stitch<ScopeName>Graph`
 * class StitchInjector private constructor(
 *   val currentScope: String,
 *   private val singletonGraph: StitchSingletonGraph,
 *   private val activityGraph: StitchActivityGraph? = null,
 *   private val fragmentGraph: StitchFragmentGraph? = null,
 * ) : Injector {
 *
 *   private val currentScopeDirectChildren = ScopeDependencies.directChildren[currentScope]
 *
 *   private val dependencyProvider = object : DependencyProvider {
 *
 *     // Step 2: Wire all external dependencies from all scopes into DependencyProvider
 *     override fun logger(): Logger = singletonGraph.logger()
 *   }
 *
 *   // Step 3: Wire each requested field per requester
 *   fun inject(target: HomeActivity) {
 *     target.logger = singletonInjectorScope.logger()
 *     target.viewModel = activityInjectorScope?.homeViewModel()
 *   }
 *
 *   override fun createInjectorForChildScope(scopeName: String): Injector {
 *     if (currentScopeDirectChildren?.contains(scopeName) != true) {
 *       childNotFoundError(scopeName)
 *     }
 *
 *     // Step 4: Transform each scope to this implementation
 *     return when (scopeName) {
 *       "activity" -> {
 *         val activityScopeRef = StitchActivityScopeRef(dependencyProvider) // ✅ Pass it inside
 *         StitchInjector(currentScope = "activity", singletonInjectorScope, activityInjectorScope, fragmentInjectorScope, ...)
 *       }
 *       "fragment" -> {
 *         val fragmentScopeRef = StitchFragmentScopeRef(dependencyProvider) // ✅ Pass it inside
 *         StitchInjector(currentScope = "fragment", singletonInjectorScope, activityInjectorScope, fragmentInjectorScope, ...)
 *       }
 *       // ... (other scopes)
 *     }
 *   }
 *
 *   private fun childNotFoundError(scopeName: String): Nothing {
 *     val message = buildString {
 *       append("This injector was created for $currentScope")
 *       append(" which doesn't have $scopeName in its direct children")
 *       if (!currentScopeDirectChildren.isNullOrEmpty()) {
 *         append(" (options: $currentScopeDirectChildren.joinToString())")
 *       }
 *     }
 *     error(message)
 *   }
 * }
 * ```
 */
class InjectorPlan(
    val scopePlans: Map<Scope, ScopePlan>,
    val requestedBindings: Map<String, List<RequestedBinding>>,
)

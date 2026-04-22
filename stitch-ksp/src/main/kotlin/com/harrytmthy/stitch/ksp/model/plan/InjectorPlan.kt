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

package com.harrytmthy.stitch.ksp.model.plan

import com.harrytmthy.stitch.ksp.model.Scope
import com.harrytmthy.stitch.ksp.model.ValidatedBinding

/**
 * Represents the generated 'scoped graph' used for the code generation.
 *
 * Example:
 *
 * ```
 * package io.github.harrytmthy.stitch.generated
 *
 * import com.harrytmthy.stitch.api.DclHolder
 * import com.harrytmthy.stitch.api.Injector
 * import com.harrytmthy.stitch.api.Injector.Companion
 * // ... (other imports)
 *
 * // Step 1: Transform each registered scope into `Stitch<ScopeName>Graph(...) : Injector`
 * @Suppress("UNCHECKED_CAST")
 * class StitchActivityGraph(
 *   override val id: Int,
 *   override val currentScope: String,
 *   override val upstream: StitchSingletonGraph, // Use direct type instead of Injector
 * ) : Injector {
 *
 *   // Step 2: Transform `ownedBindings` into `val providerClassName = ProviderClassName()`
 *   private val homeModule = HomeModule()
 *
 *   // Step 3: Transform `ownedBindings` into `val type_qualifier = DclHolder<Type>()`
 *   private val homeViewModel = DclHolder<HomeViewModel>()
 *
 *   // Step 4: Traverse `ownedBindings` again to render each public getter
 *   fun homeViewModel(): HomeViewModel {
 *     val cached = homeViewModel.reference
 *     if (cached !== UNINITIALIZED) {
 *       return cached as HomeViewModel
 *     }
 *     synchronized(homeViewModel.lock) {
 *       val locked = homeViewModel.reference
 *       if (locked !== UNINITIALIZED) {
 *         return locked as HomeViewModel
 *       }
 *       val created = homeModule.provideHomeViewModel(upstream.logger)
 *       homeViewModel.reference = created
 *       return created
 *     }
 *   }
 *
 *   // Step 5: Wire each requested field per requester
 *   fun inject(target: HomeActivity) {
 *     target.viewModel = homeViewModel()
 *     target.logger = upstream.logger()
 *   }
 *
 *   // ... (other requesters with AT LEAST one binding that resolves to "activity" scope)
 *
 *   override fun createInjectorForChildScope(scopeName: String): Injector =
 *     when (scopeName) {
 *       // Step 6: Transform each child scope to `"<scopeName>" -> Stitch<ScopeName>Graph(...)`
 *       "fragment" -> StitchFragmentGraph(nextId(), scopeName, this)
 *       else -> childNotFoundError(currentScope, scopeName)
 *     }
 *
 *   override fun <T : Any> get(type: KClass<T>, qualifier: Qualifier?): T =
 *     when (type) {
 *       // Step 7: Transform ancestorsBindings + ownedBindings to this
 *       Logger::class -> logger()
 *       String::class -> {
 *         // Generated when there is AT LEAST 1 qualifier
 *         if (qualifier is Named) {
 *           // Generated when there is AT LEAST 1 named qualifier
 *           when (qualifier.value.trim().lowercase()) {
 *             "baseurl" -> string_named_baseUrl()
 *             else -> error("Binding with type '${type.simpleName}' has no qualifier with name '${qualifier.value}'")
 *           }
 *         }
 *         error("Binding with type '${type.simpleName}' has no qualifier with type '${qualifier}'")
 *       }
 *       else -> error("Binding with type '${type.simpleName}' is not found in $currentScope scope and its ancestors.")
 *     } as T
 * }
 * ```
 *
 * For StitchFragmentGraph, accessing StitchSingletonGraph will require `upstream.upstream.xxx()`,
 * where the number of `upstream` chain can be inferred by subtracting `scope.depth` with
 * `dependency.owningScope.depth` or `requestedBinding.owningScope.depth`.
 */
class InjectorPlan(
    val scope: Scope, // Step 1
    val parentScope: Scope?, // Also Step 1
    val ownedBindings: List<ValidatedBinding>, // Step 2, 3, and 4
    val requestedBindings: Map<String, List<RequestedFieldPlan>>, // Step 5
    val directChildScopes: List<Scope.Custom>, // Step 6
    val ancestorBindings: List<ValidatedBinding>, // Step 7
)

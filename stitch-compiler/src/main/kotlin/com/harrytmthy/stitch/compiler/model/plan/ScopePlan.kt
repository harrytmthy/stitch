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

import com.harrytmthy.stitch.compiler.model.ProvidedBinding
import com.harrytmthy.stitch.compiler.model.Scope

/**
 * Represents the generated 'per scope' ScopeRef used for the code generation.
 *
 * Example:
 *
 * ```
 * package com.harrytmthy.stitch.generated
 *
 * import com.harrytmthy.stitch.generated.DependencyProvider
 *
 * // Step 1: Transform `scope` FQN into `Stitch<ScopeName>Graph`
 * class StitchActivityGraph(val dependencyProvider: DependencyProvider) {
 *
 *   // Step 2: Transform `providerClassNames` into `providerClassName: ProviderClassName? = null`
 *   private var homeModule: HomeModule? = null
 *
 *   // Step 3: Transform `ownedBindings` into `type_qualifier: DclHolder<Type>? = null`
 *   private var homeViewModel_namedDev: DclHolder<HomeViewModel>? = null
 *   private var homeViewModel_namedProd: DclHolder<HomeViewModel>? = null
 *
 *   // Step 4: Traverse `ownedBindings` again to render each public getter
 *   fun homeViewModel_namedDev(): HomeViewModel {
 *     val instance = homeViewModel_namedDev ?: run {
 *       DclWrapper<HomeViewModel>().also { homeViewModel_namedDev = it }
 *     }
 *     if (instance.initialized.value) instance.reference.value!!
 *     synchronized(instance.lock) {
 *       if (instance.initialized.value) return instance.reference.value!!
 *       val container = homeModule ?: HomeModule().also { homeModule = it }
 *       val v = container.provideHomeViewModel(homeService())
 *       instance.reference.value = v
 *       instance.initialized.value = true
 *       return v
 *     }
 *   }
 * }
 * ```
 *
 * During Step 4, if `ownedBinding.kind == BindingKind.PROVIDED_IN_CONSTRUCTOR`, codegen should
 * use direct constructor combined with `ownedBinding.dependsOn` to infer each param:
 *
 * ```
 * val v = HomeViewModel(...)
 * ```
 *
 * If `ownedBinding.kind == BindingKind.PROVIDED_IN_TOP_LEVEL`, then codegen should use
 * direct function call, also with `ownedBinding.dependsOn` to infer each param:
 *
 * ```
 * val v = provideHomeViewModel(...)
 * ```
 *
 * If `ownedBinding.kind == BindingKind.PROVIDED_IN_OBJECT`, then codegen should access
 * the corresponding object and provider function:
 *
 * ```
 * val v = HomeModule.provideHomeViewModel(...)
 * ```
 *
 * If `ownedBinding.kind == BindingKind.PROVIDED_IN_CLASS`, then codegen should instantiate
 * the class that owns the provider function first before accessing it:
 *
 * ```
 * val container = homeModule ?: HomeModule().also { homeModule = it }
 * val v = container.provideHomeViewModel(...)
 * ```
 *
 * Dependencies that are owned by the scope should be retrieved via local getters, while
 * external dependencies should be retrieved via `dependencyProvider`.
 */
class ScopePlan(
    val scope: Scope,
    val ownedBindings: List<ProvidedBinding>,
    val externalDependencies: Set<ProvidedBinding>,
    val providerClassNames: Set<String>,
)

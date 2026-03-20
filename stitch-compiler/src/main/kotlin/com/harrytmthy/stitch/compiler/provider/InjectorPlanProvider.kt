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

package com.harrytmthy.stitch.compiler.provider

import com.harrytmthy.stitch.compiler.consts.BindingKind
import com.harrytmthy.stitch.compiler.model.ContributionScanResult
import com.harrytmthy.stitch.compiler.model.ProvidedBinding
import com.harrytmthy.stitch.compiler.model.ResolvedBinding
import com.harrytmthy.stitch.compiler.model.Scope
import com.harrytmthy.stitch.compiler.model.plan.InjectorPlan
import com.harrytmthy.stitch.compiler.model.plan.ScopePlan

object InjectorPlanProvider {

    fun get(
        scanResult: ContributionScanResult,
        scopeDirectChildren: Map<Scope, List<Scope.Custom>>,
        resolvedBindings: Map<ProvidedBinding, ResolvedBinding>,
    ): InjectorPlan {
        val scopeCount = scopeDirectChildren.keys.size
        val scopePlans = HashMap<Scope, ScopePlan>(scopeCount, 1f)
        val ownedBindingsByScope = HashMap<Scope, ArrayList<ProvidedBinding>>(scopeCount, 1f)
        val externalDependenciesByScope = HashMap<Scope, HashSet<ProvidedBinding>>(scopeCount, 1f)
        val providerClassNamesByScope = HashMap<Scope, HashSet<String>>(scopeCount, 1f)
        for (providedBinding in scanResult.providedBindings.values) {
            val scope = providedBinding.scope
                ?: resolvedBindings.getValue(providedBinding).owningScope
            val ownedBindings = ownedBindingsByScope.getOrPut(scope, ::ArrayList)
            ownedBindings.add(providedBinding)
            if (providedBinding.kind == BindingKind.PROVIDED_IN_CLASS) {
                val providerClassNames = providerClassNamesByScope.getOrPut(scope, ::HashSet)
                providerClassNames.add(providedBinding.providerClassName)
            }
            providedBinding.dependencies?.forEach {
                val dependency = scanResult.providedBindings.getValue(it)
                val dependencyScope = dependency.scope
                    ?: resolvedBindings.getValue(dependency).owningScope
                if (dependencyScope != scope) {
                    val externalDependencies = externalDependenciesByScope.getOrPut(scope, ::HashSet)
                    externalDependencies.add(dependency)
                }
            }
        }
        for (scope in scopeDirectChildren.keys) {
            val ownedBindings = ownedBindingsByScope.getOrDefault(scope, emptyList())
            val externalDependencies = externalDependenciesByScope.getOrDefault(scope, emptySet())
            val providerClassNames = providerClassNamesByScope.getOrDefault(scope, emptySet())
            scopePlans[scope] = ScopePlan(scope, ownedBindings, externalDependencies, providerClassNames)
        }
        return InjectorPlan(scopePlans, scanResult.requestedBindings)
    }
}

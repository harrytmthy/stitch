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
import com.harrytmthy.stitch.compiler.model.BindingValidationResult
import com.harrytmthy.stitch.compiler.model.RequestedBinding
import com.harrytmthy.stitch.compiler.model.Scope
import com.harrytmthy.stitch.compiler.model.plan.InjectorPlan

object InjectorPlanProvider {

    fun get(
        validationResult: BindingValidationResult,
        requestedBindings: Map<String, List<RequestedBinding>>,
        scopeDirectChildren: Map<Scope, List<Scope.Custom>>,
    ): Map<Scope, InjectorPlan> {
        val scopeCount = scopeDirectChildren.keys.size
        val injectorPlans = HashMap<Scope, InjectorPlan>(scopeCount, 1f)
        val providerClassNamesByScope = HashMap<Scope, HashSet<String>>(scopeCount, 1f)
        val requestersByScope = HashMap<Scope, HashMap<String, ArrayList<RequestedBinding>>>(scopeCount, 1f)
        for (binding in validationResult.bindingPool.values) {
            if (binding.kind == BindingKind.PROVIDED_IN_CLASS) {
                val providerClassNames = providerClassNamesByScope
                    .getOrPut(binding.owningScope, ::HashSet)
                providerClassNames.add(binding.providerClassName)
            }
        }
        for ((requester, requestedBindings) in requestedBindings) {
            for (requestedBinding in requestedBindings) {
                val binding = validationResult.bindingPool.getValue(requestedBinding)
                val requesters = requestersByScope.getOrPut(binding.owningScope, ::HashMap)
                val requests = requesters.getOrPut(requester, ::ArrayList)
                requests.add(requestedBinding)
            }
        }
        for (scope in scopeDirectChildren.keys) {
            injectorPlans[scope] = InjectorPlan(
                scope = scope,
                providerClassNames = providerClassNamesByScope.getOrDefault(scope, emptySet()),
                ownedBindings = validationResult.bindingsByScope.getValue(scope),
                requestedBindings = requestersByScope.getOrDefault(scope, emptyMap()),
                directChildScopes = scopeDirectChildren.getOrDefault(scope, emptyList()),
            )
        }
        return injectorPlans
    }
}

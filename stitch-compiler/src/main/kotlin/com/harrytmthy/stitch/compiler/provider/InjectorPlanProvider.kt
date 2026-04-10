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

import com.harrytmthy.stitch.compiler.model.BindingValidationResult
import com.harrytmthy.stitch.compiler.model.RequestedBinding
import com.harrytmthy.stitch.compiler.model.Scope
import com.harrytmthy.stitch.compiler.model.ScopeMetadata
import com.harrytmthy.stitch.compiler.model.plan.InjectorPlan
import com.harrytmthy.stitch.compiler.model.plan.RequestedFieldPlan

object InjectorPlanProvider {

    fun get(
        validationResult: BindingValidationResult,
        requestedBindings: Map<String, List<RequestedBinding>>,
        scopeMetadata: ScopeMetadata,
        scopeDependencies: Map<Scope, Scope>,
    ): List<InjectorPlan> {
        val scopeCount = scopeMetadata.directChildren.keys.size
        val injectorPlans = ArrayList<InjectorPlan>(scopeCount)
        val requestedFieldPlans = HashMap<Scope, HashMap<String, ArrayList<RequestedFieldPlan>>>(scopeCount, 1f)
        for ((scope, scopeAncestors) in scopeMetadata.ancestors) {
            for ((requester, requestedBindings) in requestedBindings) {
                for (requestedBinding in requestedBindings) {
                    val binding = validationResult.bindingPool.getValue(requestedBinding)
                    if (binding.owningScope in scopeAncestors) {
                        val requesters = requestedFieldPlans.getOrPut(scope, ::HashMap)
                        val requests = requesters.getOrPut(requester, ::ArrayList)
                        val plan = RequestedFieldPlan(requestedBinding.fieldName, binding)
                        requests.add(plan)
                    }
                }
            }
        }
        for ((scope, ancestors) in scopeMetadata.ancestors) {
            val ancestorBindings = ancestors.flatMap {
                validationResult.bindingsByScope.getOrDefault(it, emptyList())
            }
            val injectorPlan = InjectorPlan(
                scope = scope,
                parentScope = scopeDependencies[scope],
                ownedBindings = validationResult.bindingsByScope.getOrDefault(scope, emptyList()),
                requestedBindings = requestedFieldPlans.getOrDefault(scope, emptyMap()),
                directChildScopes = scopeMetadata.directChildren.getOrDefault(scope, emptyList()),
                ancestorBindings = ancestorBindings,
            )
            injectorPlans.add(injectorPlan)
        }
        return injectorPlans
    }
}

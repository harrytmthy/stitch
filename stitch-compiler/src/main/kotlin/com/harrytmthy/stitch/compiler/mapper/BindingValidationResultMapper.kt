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

package com.harrytmthy.stitch.compiler.mapper

import com.harrytmthy.stitch.compiler.model.BindingPool
import com.harrytmthy.stitch.compiler.model.BindingResolution
import com.harrytmthy.stitch.compiler.model.BindingValidationResult
import com.harrytmthy.stitch.compiler.model.ProvidedBinding
import com.harrytmthy.stitch.compiler.model.Scope
import com.harrytmthy.stitch.compiler.model.ValidatedBinding

/**
 * Transforms all [ProvidedBinding] into [ValidatedBinding] grouped by owning scope,
 * then return them as [BindingValidationResult].
 */
object BindingValidationResultMapper {

    fun map(
        bindingResolutions: Map<ProvidedBinding, BindingResolution>,
        scopeCount: Int,
    ): BindingValidationResult {
        val bindingPool = BindingPool<ValidatedBinding>(bindingResolutions.size, 1f)
        val bindingsByScope = HashMap<Scope, ArrayList<ValidatedBinding>>(scopeCount, 1f)
        for ((providedBinding, bindingResolution) in bindingResolutions) {
            val bindings = bindingsByScope.getOrPut(bindingResolution.owningScope, ::ArrayList)
            val validatedBinding = ValidatedBinding(
                type = providedBinding.type,
                qualifier = providedBinding.qualifier,
                owningScope = bindingResolution.owningScope,
                kind = providedBinding.kind,
                providerPackageName = providedBinding.providerPackageName,
                providerFunctionName = providedBinding.providerFunctionName,
                providerClassName = providedBinding.providerClassName,
            )
            bindings.add(validatedBinding)
            bindingPool.add(validatedBinding)
        }
        for ((providedBinding, _) in bindingResolutions) {
            val binding = bindingPool.getValue(providedBinding)
            binding.dependencies = providedBinding.dependencies?.map(bindingPool::getValue)
        }
        return BindingValidationResult(bindingPool, bindingsByScope)
    }
}

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

package com.harrytmthy.stitch.compiler

import com.harrytmthy.stitch.compiler.model.BindingDeclaration
import com.harrytmthy.stitch.compiler.model.ContributionScanResult
import com.harrytmthy.stitch.compiler.model.ProvidedBinding
import com.harrytmthy.stitch.compiler.model.ResolvedBinding
import com.harrytmthy.stitch.compiler.model.Scope
import com.harrytmthy.stitch.compiler.model.ScopedOwner

class BindingGraphValidator(
    private val scanResult: ContributionScanResult,
    private val scopeAncestors: Map<Scope, Set<Scope>>,
) {

    fun validate(): Map<ProvidedBinding, ResolvedBinding> {
        val visiting = LinkedHashSet<ProvidedBinding>()
        val resolvedByBinding = HashMap<ProvidedBinding, ResolvedBinding>()
        for (binding in scanResult.providedBindings.values) {
            resolve(binding, visiting, resolvedByBinding)
        }
        return resolvedByBinding
    }

    private fun resolve(
        binding: ProvidedBinding,
        visiting: LinkedHashSet<ProvidedBinding>,
        resolvedByBinding: HashMap<ProvidedBinding, ResolvedBinding>,
    ): ResolvedBinding {
        resolvedByBinding[binding]?.let { return it }
        if (!visiting.add(binding)) {
            cycleError(binding, visiting)
        }
        try {
            val dependencyResults = ArrayList<Pair<ProvidedBinding, ResolvedBinding>>(
                binding.dependencies?.size ?: 0,
            )
            binding.dependencies?.forEach { dependencyDeclaration ->
                val dependency = scanResult.providedBindings[dependencyDeclaration]
                    ?: missingBindingError(dependencyDeclaration)
                val resolvedDependency = resolve(dependency, visiting, resolvedByBinding)
                dependencyResults += dependency to resolvedDependency
            }
            val result = when (val declaredScope = binding.scope) {
                null -> resolveUnscoped(binding, dependencyResults)
                else -> resolveScoped(binding, declaredScope, dependencyResults)
            }
            resolvedByBinding[binding] = result
            return result
        } finally {
            visiting.remove(binding)
        }
    }

    private fun resolveScoped(
        binding: ProvidedBinding,
        declaredScope: Scope,
        dependencyResults: List<Pair<ProvidedBinding, ResolvedBinding>>,
    ): ResolvedBinding {
        val ancestors = scopeAncestors.getValue(declaredScope)
        val scopedOwnersInClosure = LinkedHashSet<ScopedOwner>()
        for ((dependency, resolvedDependency) in dependencyResults) {
            if (resolvedDependency.owningScope !in ancestors) {
                wrongScopeError(
                    binding = binding,
                    dependency = dependency,
                    dependencyOwningScope = resolvedDependency.owningScope,
                    allowedScopes = ancestors,
                )
            }
            scopedOwnersInClosure += resolvedDependency.scopedOwnersInClosure
        }
        if (declaredScope is Scope.Custom) {
            scopedOwnersInClosure += ScopedOwner(binding, declaredScope)
        }
        return ResolvedBinding(
            owningScope = declaredScope,
            scopedOwnersInClosure = scopedOwnersInClosure,
        )
    }

    private fun resolveUnscoped(
        binding: ProvidedBinding,
        dependencyResults: List<Pair<ProvidedBinding, ResolvedBinding>>,
    ): ResolvedBinding {
        val scopedOwnersInClosure = LinkedHashSet<ScopedOwner>()
        var deepestOwner: ScopedOwner? = null
        for ((dependency, resolvedDependency) in dependencyResults) {
            scopedOwnersInClosure += resolvedDependency.scopedOwnersInClosure
            val owningScope = resolvedDependency.owningScope
            if (owningScope is Scope.Custom) {
                val candidate = ScopedOwner(dependency, owningScope)
                if (deepestOwner == null || owningScope.depth > deepestOwner.scope.depth) {
                    deepestOwner = candidate
                }
            }
        }
        val deepestScope = deepestOwner?.scope
        if (deepestScope != null) {
            val ancestors = scopeAncestors.getValue(deepestScope)
            for (scopedOwner in scopedOwnersInClosure) {
                if (scopedOwner.scope !in ancestors) {
                    incompatibleUnscopedClosureError(
                        binding = binding,
                        deepestOwner = deepestOwner,
                        conflictingOwner = scopedOwner,
                        allowedScopes = ancestors,
                    )
                }
            }
        }
        return ResolvedBinding(
            owningScope = deepestScope ?: Scope.Singleton,
            scopedOwnersInClosure = scopedOwnersInClosure,
        )
    }

    private fun cycleError(
        binding: ProvidedBinding,
        visiting: LinkedHashSet<ProvidedBinding>,
    ): Nothing {
        val cycle = buildList {
            var include = false
            for (visited in visiting) {
                if (visited == binding) {
                    include = true
                }
                if (include) {
                    add(visited)
                }
            }
            add(binding)
        }
        val path = cycle.joinToString(" → ")
        fatalError(
            message = "A cycle is detected in the dependency graph:\n$path",
            symbol = null,
        )
    }

    private fun wrongScopeError(
        binding: ProvidedBinding,
        dependency: ProvidedBinding,
        dependencyOwningScope: Scope,
        allowedScopes: Set<Scope>,
    ): Nothing =
        fatalError(
            message = buildString {
                append("Binding $binding is declared in ${binding.scope} (${binding.location}), ")
                append("but depends on $dependency which resolves to $dependencyOwningScope ")
                append("(${dependency.location}). Allowed scopes: ${allowedScopes.joinToString()}")
            },
            symbol = null,
        )

    private fun missingBindingError(binding: BindingDeclaration): Nothing =
        fatalError("Binding $binding is requested but never provided", symbol = null)

    private fun incompatibleUnscopedClosureError(
        binding: ProvidedBinding,
        deepestOwner: ScopedOwner,
        conflictingOwner: ScopedOwner,
        allowedScopes: Set<Scope>,
    ): Nothing =
        fatalError(
            message = buildString {
                append("Unscoped binding $binding resolves into ${deepestOwner.scope} because of")
                append(" ${deepestOwner.binding}, but also depends on ${conflictingOwner.binding}")
                append(" which resolves into ${conflictingOwner.scope}")
                append(" (${conflictingOwner.binding.location}). ")
                append("Allowed scopes from ${deepestOwner.scope}: ${allowedScopes.joinToString()}")
            },
            symbol = null,
        )
}

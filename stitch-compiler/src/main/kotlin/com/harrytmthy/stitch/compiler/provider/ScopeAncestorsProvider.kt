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

import com.harrytmthy.stitch.compiler.fatalError
import com.harrytmthy.stitch.compiler.model.Scope
import com.harrytmthy.stitch.compiler.scanner.ContributionScanResult

class ScopeAncestorsProvider(private val scanResult: ContributionScanResult) {

    private val scopeAncestors = HashMap<Scope, LinkedHashSet<Scope>>()

    fun get(): Map<Scope, Set<Scope>> {
        for (scope in scanResult.customScopeByCanonicalName.values) {
            collectAncestors(scope, scanResult.scopeDependencies)
        }
        return scopeAncestors
    }

    private fun collectAncestors(scope: Scope, scopeDependencies: Map<Scope, Scope>) {
        if (scope in scopeAncestors) {
            // Already visited
            return
        }
        val ancestors = linkedSetOf(scope)
        scopeAncestors[scope] = ancestors
        val directAncestor = scopeDependencies[scope]
        if (directAncestor == null || directAncestor == Scope.Singleton) {
            ancestors.add(Scope.Singleton)
            return
        }
        collectAncestors(directAncestor, scopeDependencies)
        val directAncestorAncestors = scopeAncestors.getValue(directAncestor)
        if (scope in directAncestorAncestors) {
            val cyclePath = directAncestorAncestors.joinToString(" → ")
            fatalError(
                message = "A cycle is detected in scope graph: $scope → $cyclePath",
                symbol = null,
            )
        }
        ancestors.addAll(directAncestorAncestors)
    }
}

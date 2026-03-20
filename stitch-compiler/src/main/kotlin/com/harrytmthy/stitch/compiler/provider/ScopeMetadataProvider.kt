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
import com.harrytmthy.stitch.compiler.model.ContributionScanResult
import com.harrytmthy.stitch.compiler.model.Scope
import com.harrytmthy.stitch.compiler.model.ScopeMetadata

object ScopeMetadataProvider {

    fun get(scanResult: ContributionScanResult): ScopeMetadata {
        val scopeAncestors = HashMap<Scope, LinkedHashSet<Scope>>()
        val directChildren = HashMap<Scope, ArrayList<Scope.Custom>>()
        for (scope in scanResult.customScopeByCanonicalName.values) {
            collectAncestors(scope, scanResult.scopeDependencies, scopeAncestors, directChildren)
        }
        scopeAncestors[Scope.Singleton] = linkedSetOf(Scope.Singleton)
        return ScopeMetadata(scopeAncestors, directChildren)
    }

    private fun collectAncestors(
        scope: Scope,
        scopeDependencies: Map<Scope, Scope>,
        scopeAncestors: HashMap<Scope, LinkedHashSet<Scope>>,
        directChildren: HashMap<Scope, ArrayList<Scope.Custom>>,
    ) {
        if (scope in scopeAncestors) {
            // Already visited
            return
        }
        val ancestors = linkedSetOf(scope)
        scopeAncestors[scope] = ancestors
        val directAncestor = scopeDependencies[scope]
        if (directAncestor == null || directAncestor == Scope.Singleton) {
            // Direct child of Singleton
            ancestors.add(Scope.Singleton)
            val singletonChildren = directChildren.getOrPut(Scope.Singleton) { ArrayList() }
            singletonChildren.add(scope as Scope.Custom)
            scope.depth = 2
            return
        }
        collectAncestors(directAncestor, scopeDependencies, scopeAncestors, directChildren)
        val directAncestorAncestors = scopeAncestors.getValue(directAncestor)
        if (scope in directAncestorAncestors) {
            val cyclePath = directAncestorAncestors.joinToString(" → ")
            fatalError(
                message = "A cycle is detected in scope graph: $scope → $cyclePath",
                symbol = null,
            )
        }
        ancestors.addAll(directAncestorAncestors)
        val directAncestorChildren = directChildren.getOrPut(directAncestor) { ArrayList() }
        directAncestorChildren.add(scope as Scope.Custom)
        scope.depth = directAncestor.depth + 1
    }
}

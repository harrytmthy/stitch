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

class ScopeMetadataProvider(private val scanResult: ContributionScanResult) {

    fun get(): Map<Scope, ScopeMetadata> {
        val scopeMetadata = HashMap<Scope, ScopeMetadata>()
        for (scope in scanResult.customScopeByCanonicalName.values) {
            collectMetadata(scope, scanResult.scopeDependencies, scopeMetadata)
        }
        return scopeMetadata
    }

    private fun collectMetadata(
        scope: Scope,
        scopeDependencies: Map<Scope, Scope>,
        scopeMetadata: HashMap<Scope, ScopeMetadata>,
    ) {
        if (scope in scopeMetadata) {
            // Already visited
            return
        }
        val ancestors = linkedSetOf(scope)
        scopeMetadata[scope] = ScopeMetadata(ancestors)
        val directAncestor = scopeDependencies[scope]
        if (directAncestor == null || directAncestor == Scope.Singleton) {
            // Direct child of Singleton
            ancestors.add(Scope.Singleton)
            scopeMetadata[scope] = ScopeMetadata(ancestors, depth = 2)
            return
        }
        collectMetadata(directAncestor, scopeDependencies, scopeMetadata)
        val directAncestorAncestors = scopeMetadata.getValue(directAncestor).ancestors
        if (scope in directAncestorAncestors) {
            val cyclePath = directAncestorAncestors.joinToString(" → ")
            fatalError(
                message = "A cycle is detected in scope graph: $scope → $cyclePath",
                symbol = null,
            )
        }
        ancestors.addAll(directAncestorAncestors)
        val depth = scopeMetadata.getValue(directAncestor).depth + 1
        scopeMetadata.getValue(scope).depth = depth
    }
}

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

import com.harrytmthy.stitch.compiler.consts.BindingKind
import com.harrytmthy.stitch.compiler.model.Binding
import com.harrytmthy.stitch.compiler.model.ContributionScanResult
import com.harrytmthy.stitch.compiler.model.ProvidedBinding
import com.harrytmthy.stitch.compiler.model.Qualifier
import com.harrytmthy.stitch.compiler.model.Scope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BindingGraphValidatorTest {

    @Test
    fun validate_shouldInferSingleton_whenUnscopedClosureHasNoCustomScope() {
        val logger = provided("Logger", scope = Scope.Singleton)
        val config = provided("Config", dependencies = listOf(logger))
        val presenter = provided("Presenter", dependencies = listOf(config))

        val validator = BindingGraphValidator(
            scanResult = scanResult(logger, config, presenter),
            scopeAncestors = scopeAncestors(),
        )

        val resolved = validator.validate()

        assertEquals(Scope.Singleton, resolved.getValue(config).owningScope)
        assertEquals(Scope.Singleton, resolved.getValue(presenter).owningScope)
    }

    @Test
    fun validate_shouldInferDeepestCustomScope_forUnscopedBinding() {
        val activity = customScope("activity", depth = 2)
        val repository = provided("Repository", scope = activity)
        val config = provided("Config", dependencies = listOf(repository))
        val presenter = provided("Presenter", dependencies = listOf(config))

        val validator = BindingGraphValidator(
            scanResult = scanResult(repository, config, presenter),
            scopeAncestors = scopeAncestors(activity),
        )

        val resolved = validator.validate()

        assertEquals(activity, resolved.getValue(config).owningScope)
        assertEquals(activity, resolved.getValue(presenter).owningScope)
    }

    @Test
    fun validate_shouldFail_whenUnscopedBindingDependsOnMultipleBranches() {
        val activity = customScope("activity", depth = 2)
        val fragment = customScope("fragment", depth = 3)
        val service = customScope("service", depth = 2)

        val c = provided("C", scope = activity)
        val d = provided("D", scope = fragment)
        val e = provided("E", scope = service)

        val a = provided("A", dependencies = listOf(c))
        val b = provided("B", dependencies = listOf(a, d, e))

        val validator = BindingGraphValidator(
            scanResult = scanResult(c, d, e, a, b),
            scopeAncestors = scopeAncestors(activity, fragment, service),
        )

        assertFailsWith<StitchProcessingException> {
            validator.validate()
        }
    }

    @Test
    fun validate_shouldFail_whenScopedBindingDependsOnDescendantScope() {
        val activity = customScope("activity", depth = 2)
        val fragment = customScope("fragment", depth = 3)

        val child = provided("Child", scope = fragment)
        val parent = provided("Parent", scope = activity, dependencies = listOf(child))

        val validator = BindingGraphValidator(
            scanResult = scanResult(child, parent),
            scopeAncestors = scopeAncestors(activity, fragment),
        )

        assertFailsWith<StitchProcessingException> {
            validator.validate()
        }
    }

    @Test
    fun validate_shouldFail_whenCycleExists() {
        val a = provided("A")
        val b = provided("B")

        a.dependencies = arrayListOf(b)
        b.dependencies = arrayListOf(a)

        val validator = BindingGraphValidator(
            scanResult = scanResult(a, b),
            scopeAncestors = scopeAncestors(),
        )

        assertFailsWith<StitchProcessingException> {
            validator.validate()
        }
    }

    @Test
    fun validate_shouldNotTreatResolvedNodeAsCycle() {
        val loggerImpl = provided("LoggerImpl", scope = Scope.Singleton)
        val logger = provided("Logger", dependencies = listOf(loggerImpl))
        val baseUrl = provided("String", qualifier = Qualifier.Named("baseUrl"), scope = Scope.Singleton)
        val apiService = provided("ApiService", dependencies = listOf(logger, baseUrl))
        val userRepositoryImpl = provided("UserRepositoryImpl", dependencies = listOf(logger, apiService))
        val userReader = provided("UserReader", dependencies = listOf(userRepositoryImpl))

        val validator = BindingGraphValidator(
            scanResult = scanResult(loggerImpl, logger, baseUrl, apiService, userRepositoryImpl, userReader),
            scopeAncestors = scopeAncestors(),
        )

        validator.validate() // should not throw
    }

    private fun scanResult(vararg bindings: ProvidedBinding): ContributionScanResult {
        val providedBindings = LinkedHashMap<Binding, ProvidedBinding>()
        bindings.forEach { providedBindings[it] = it }
        return ContributionScanResult(
            providedBindings = providedBindings,
            requestedBindingsByModuleKey = emptyMap(),
            customScopeByCanonicalName = bindings.mapNotNull { it.scope as? Scope.Custom }
                .associateBy { it.canonicalName },
            scopeDependencies = emptyMap(),
        )
    }

    private fun provided(
        type: String,
        qualifier: Qualifier? = null,
        scope: Scope? = null,
        dependencies: List<ProvidedBinding> = emptyList(),
    ): ProvidedBinding =
        ProvidedBinding(
            type = type,
            qualifier = qualifier,
            scope = scope,
            location = "$type.kt:1",
            kind = BindingKind.PROVIDED_IN_CONSTRUCTOR,
            moduleKey = "TEST",
        ).apply {
            this.dependencies = ArrayList(dependencies)
        }

    private fun customScope(name: String, depth: Int): Scope.Custom =
        Scope.Custom(
            canonicalName = name,
            qualifiedName = "test.$name",
            location = "$name.kt:1",
        ).also { it.depth = depth }

    private fun scopeAncestors(
        vararg scopes: Scope.Custom,
    ): Map<Scope, Set<Scope>> {
        val map = LinkedHashMap<Scope, Set<Scope>>()
        map[Scope.Singleton] = setOf(Scope.Singleton)

        scopes.forEach { scope ->
            val ancestors = linkedSetOf(scope, Scope.Singleton)
            when (scope.canonicalName) {
                "fragment" -> {
                    val activity = scopes.first { it.canonicalName == "activity" }
                    ancestors.add(activity)
                }
            }
            map[scope] = ancestors
        }
        return map
    }
}

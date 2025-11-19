/*
 * Copyright 2025 Harry Timothy Tumalewa
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

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueArgument
import kotlin.reflect.KClass

/**
 * Builds the scope dependency graph from @Scope-annotated annotations.
 *
 * Responsibilities:
 * 1. Discover all scope annotations (annotations annotated with @Scope)
 * 2. Extract `dependsOn` parameter from each @Scope annotation
 * 3. Build dependency graph and validate acyclicity
 * 4. Calculate depth for each scope (distance from root)
 * 5. Return structured ScopeGraph
 */
class ScopeGraphBuilder(private val logger: KSPLogger) {

    fun buildScopeGraph(resolver: Resolver): ScopeGraph {
        // Step 1: Discover all scope annotations
        val scopeAnnotations = discoverScopeAnnotations(resolver)

        if (scopeAnnotations.isEmpty()) {
            logger.info("Stitch: No custom scopes found")
            return ScopeGraph(emptyMap(), emptySet())
        }

        logger.info("Stitch: Discovered ${scopeAnnotations.size} custom scope annotation(s)")

        // Step 2: Extract dependsOn for each scope
        val scopeDependencies = mutableMapOf<KSType, KSType?>()
        scopeAnnotations.forEach { scopeAnnotation ->
            val dependsOn = extractDependsOn(scopeAnnotation, resolver)
            scopeDependencies[scopeAnnotation] = dependsOn

            val scopeName = scopeAnnotation.declaration.simpleName.asString()
            val dependsOnName = dependsOn?.declaration?.simpleName?.asString() ?: "Singleton"
            logger.info("Stitch: @$scopeName → @$dependsOnName")
        }

        // Step 3: Validate acyclic graph
        validateAcyclic(scopeDependencies)

        // Step 4: Calculate depth for each scope
        val depths = calculateDepths(scopeDependencies)

        // Step 5: Build ScopeInfo map
        val scopeInfos = scopeDependencies.map { (scope, dependsOn) ->
            scope to ScopeInfo(
                annotation = scope,
                dependsOn = dependsOn,
                depth = depths[scope]!!,
            )
        }.toMap()

        // Step 6: Identify root scopes (those that depend on Singleton)
        val rootScopes = scopeInfos.filter { it.value.dependsOn == null }.keys

        return ScopeGraph(scopeInfos, rootScopes)
    }

    /**
     * Discovers all annotations that are themselves annotated with @Scope.
     */
    private fun discoverScopeAnnotations(resolver: Resolver): List<KSType> {
        val scopeAnnotations = mutableListOf<KSType>()

        // Find all symbols annotated with @Scope meta-annotation
        listOf(STITCH_SCOPE, JAVAX_SCOPE).forEach { scopeMetaAnnotation ->
            val annotated = resolver.getSymbolsWithAnnotation(scopeMetaAnnotation)

            annotated.forEach { symbol ->
                // The symbol itself is a scope annotation (e.g. @ActivityScope)
                if (symbol is KSClassDeclaration) {
                    val annotationType = symbol.asStarProjectedType()
                    scopeAnnotations.add(annotationType)
                }
            }
        }

        return scopeAnnotations
    }

    /**
     * Extracts the `dependsOn` parameter from a @Scope annotation.
     * Returns null if depends on Singleton (default or explicit).
     */
    private fun extractDependsOn(scopeAnnotationType: KSType, resolver: Resolver): KSType? {
        val scopeDeclaration = scopeAnnotationType.declaration

        // Find the @Scope annotation on this annotation class
        val scopeAnnotation = scopeDeclaration.annotations.firstOrNull { annotation ->
            val annotationName = annotation.annotationType.resolve().declaration.qualifiedName?.asString()
            annotationName == STITCH_SCOPE || annotationName == JAVAX_SCOPE
        } ?: return null

        // Extract the `dependsOn` parameter value
        val dependsOnArg = scopeAnnotation.arguments.firstOrNull { it.name?.asString() == "dependsOn" }

        if (dependsOnArg == null) {
            // No explicit dependsOn parameter, defaults to Singleton
            return null
        }

        // The value is a KClass reference
        val dependsOnKClass = dependsOnArg.value as? KClass<*>

        // Resolve the KClass to a KSType
        val dependsOnType = resolveKClassToKSType(dependsOnArg, resolver)

        // Check if it's Singleton (either Stitch or javax)
        val isSingleton = isSingletonType(dependsOnType)

        return if (isSingleton) null else dependsOnType
    }

    /**
     * Resolves a KClass<*> argument to a KSType.
     */
    private fun resolveKClassToKSType(argument: KSValueArgument, resolver: Resolver): KSType {
        // The value is stored as a KSType in KSP
        return argument.value as KSType
    }

    /**
     * Checks if a type is Singleton (Stitch or javax).
     */
    private fun isSingletonType(type: KSType?): Boolean {
        if (type == null) return true
        val qualifiedName = type.declaration.qualifiedName?.asString() ?: return false
        return qualifiedName == STITCH_SINGLETON || qualifiedName == JAVAX_SINGLETON
    }

    /**
     * Validates that the scope dependency graph is acyclic.
     * Uses DFS-based cycle detection.
     */
    private fun validateAcyclic(scopeDependencies: Map<KSType, KSType?>) {
        val visiting = mutableSetOf<KSType>()
        val visited = mutableSetOf<KSType>()

        fun dfs(scope: KSType, path: List<String>) {
            if (scope in visiting) {
                // Cycle detected
                val cyclePath = path.joinToString(" → ")
                logger.error(
                    "Cycle detected in scope dependencies: $cyclePath → ${scope.declaration.simpleName.asString()}. " +
                        "Scope dependency chains must be acyclic.",
                    scope.declaration,
                )
                throw IllegalStateException("Cycle in scope graph")
            }

            if (scope in visited) return

            visiting.add(scope)

            val upstream = scopeDependencies[scope]
            if (upstream != null) {
                val newPath = path + scope.declaration.simpleName.asString()
                dfs(upstream, newPath)
            }

            visiting.remove(scope)
            visited.add(scope)
        }

        scopeDependencies.keys.forEach { scope ->
            dfs(scope, emptyList())
        }
    }

    /**
     * Calculates the depth of each scope (distance from Singleton).
     * Root scopes (depend on Singleton) have depth 0.
     */
    private fun calculateDepths(scopeDependencies: Map<KSType, KSType?>): Map<KSType, Int> {
        val depths = mutableMapOf<KSType, Int>()

        fun calculateDepth(scope: KSType): Int {
            if (scope in depths) return depths[scope]!!

            val upstream = scopeDependencies[scope]
            val depth = if (upstream == null) {
                // Root scope (depends on Singleton)
                0
            } else {
                // Recursive: depth = parent depth + 1
                calculateDepth(upstream) + 1
            }

            depths[scope] = depth
            return depth
        }

        scopeDependencies.keys.forEach { calculateDepth(it) }
        return depths
    }

    companion object {
        private const val STITCH_SCOPE = "com.harrytmthy.stitch.annotations.Scope"
        private const val JAVAX_SCOPE = "javax.inject.Scope"
        private const val STITCH_SINGLETON = "com.harrytmthy.stitch.annotations.Singleton"
        private const val JAVAX_SINGLETON = "javax.inject.Singleton"
    }
}

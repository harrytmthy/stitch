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
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

/**
 * Builds the scope dependency graph from @Scope-annotated annotations.
 */
class ScopeGraphBuilder(private val logger: KSPLogger) {

    fun buildScopeGraph(resolver: Resolver): ScopeGraph {
        val singletons = getSingletonAnnotatedSymbols(resolver)
        return getScopeGraph(resolver, singletons)
    }

    private fun getSingletonAnnotatedSymbols(resolver: Resolver): Set<KSAnnotated> {
        val singletonAnnotatedSymbols = HashSet<KSAnnotated>()
        listOf(STITCH_SINGLETON, JAVAX_SINGLETON).forEach { singletonAnnotation ->
            resolver.getSymbolsWithAnnotation(singletonAnnotation).forEach { symbol ->
                singletonAnnotatedSymbols.add(symbol)
            }
        }
        return singletonAnnotatedSymbols
    }

    /**
     * Scans all annotations that are annotated with @Scope, and return the dependency map.
     */
    private fun getScopeGraph(resolver: Resolver, singletons: Set<KSAnnotated>): ScopeGraph {
        val scopeDependencies = HashMap<KSType, KSType?>()
        resolver.getSymbolsWithAnnotation(STITCH_SCOPE).forEach { symbol ->
            (symbol as? KSClassDeclaration)?.asStarProjectedType()
                ?.let { scopeDependencies[it] = extractDependsOn(it) }
        }
        if (scopeDependencies.isEmpty()) {
            return ScopeGraph(emptyMap(), emptyMap(), singletons)
        }
        validateAcyclic(scopeDependencies)
        val scopeBySymbol = HashMap<KSAnnotated, KSType>()
        for (scopeAnnotation in scopeDependencies.keys) {
            val annotationName = scopeAnnotation.declaration.qualifiedName?.asString() ?: continue
            resolver.getSymbolsWithAnnotation(annotationName).forEach { symbol ->
                if (singletons.contains(symbol)) {
                    val name = scopeAnnotation.declaration.simpleName.asString()
                    logger.error(
                        "$symbol is annotated with @Singleton and @$name at the same time",
                        symbol,
                    )
                }
                if (scopeBySymbol.containsKey(symbol)) {
                    val existing = scopeBySymbol.getValue(symbol)
                    val name = scopeAnnotation.declaration.simpleName.asString()
                    logger.error(
                        "$symbol is annotated with @$existing and @$name at the same time",
                        symbol,
                    )
                }
                scopeBySymbol[symbol] = scopeAnnotation
            }
        }
        return ScopeGraph(scopeDependencies, scopeBySymbol, singletons)
    }

    /**
     * Extracts the `dependsOn` parameter from a @Scope annotation.
     * Returns null if depends on Singleton (default or explicit).
     */
    private fun extractDependsOn(annotationType: KSType): KSType? =
        annotationType.declaration.annotations
            .first { it.annotationType.resolve().declaration.qualifiedName?.asString() == STITCH_SCOPE }
            .arguments
            .first { it.name?.asString() == "dependsOn" }
            .let { it.value as KSType }
            .takeUnless(::isSingletonType)

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

    private companion object {
        const val STITCH_SCOPE = "com.harrytmthy.stitch.annotations.Scope"
        const val STITCH_SINGLETON = "com.harrytmthy.stitch.annotations.Singleton"
        const val JAVAX_SINGLETON = "javax.inject.Singleton"
    }
}

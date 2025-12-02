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

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType

/**
 * Builds the scope dependency graph from @Scope-annotated annotations.
 */
class ScopeGraphBuilder {

    fun buildScopeGraph(resolver: Resolver): ScopeGraph {
        val singletons = getSingletonAnnotatedSymbols(resolver)
        return getScopeGraph(resolver, singletons)
    }

    private fun getSingletonAnnotatedSymbols(resolver: Resolver): Set<KSAnnotated> {
        val singletonAnnotatedSymbols = HashSet<KSAnnotated>()
        for (singletonAnnotation in listOf(STITCH_SINGLETON, JAVAX_SINGLETON)) {
            for (symbol in resolver.getSymbolsWithAnnotation(singletonAnnotation)) {
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
        for (symbol in resolver.getSymbolsWithAnnotation(STITCH_SCOPE)) {
            (symbol as? KSClassDeclaration)?.asStarProjectedType()
                ?.let { scopeDependencies[it] = null }
        }
        for (scope in scopeDependencies.keys) {
            scopeDependencies[scope] = extractDependsOn(scope, scopeDependencies.keys)
        }
        if (scopeDependencies.isEmpty()) {
            return ScopeGraph(emptyMap(), emptyMap(), singletons)
        }
        val scopeBySymbol = getScopeBySymbol(resolver, singletons, scopeDependencies)
        return ScopeGraph(scopeDependencies, scopeBySymbol, singletons)
    }

    /**
     * Extracts the `dependsOn` parameter from a @Scope annotation.
     * Returns null if depends on Singleton (default or explicit).
     */
    private fun extractDependsOn(scope: KSType, scopes: Set<KSType>): KSType? =
        scope.declaration.annotations
            .first { it.annotationType.resolve().declaration.qualifiedName?.asString() == STITCH_SCOPE }
            .arguments
            .first { it.name?.asString() == "dependsOn" }
            .let { argument ->
                val dependsOn = argument.value as KSType
                if (dependsOn == scope) {
                    val scopeName = scope.declaration.simpleName.asString()
                    throw StitchProcessingException(
                        message = "@$scopeName depends on itself",
                        symbol = argument,
                    )
                }
                if (isSingletonType(dependsOn)) {
                    return@let null
                }
                if (dependsOn !in scopes) {
                    val scopeName = scope.declaration.simpleName.asString()
                    val other = dependsOn.declaration.simpleName.asString()
                    throw StitchProcessingException(
                        message = "@$scopeName depends on $other that is not a scope",
                        symbol = argument,
                    )
                }
                dependsOn
            }

    /**
     * Checks if a type is Singleton (Stitch or javax).
     */
    private fun isSingletonType(type: KSType): Boolean {
        val qualifiedName = type.declaration.qualifiedName?.asString() ?: return false
        return qualifiedName == STITCH_SINGLETON || qualifiedName == JAVAX_SINGLETON
    }

    private fun getScopeBySymbol(
        resolver: Resolver,
        singletons: Set<KSAnnotated>,
        scopeDependencies: Map<KSType, KSType?>,
    ): Map<KSAnnotated, KSType> {
        val visiting = mutableSetOf<KSType>()
        val visited = mutableSetOf<KSType>()

        /**
         * DFS-based cycle detection.
         */
        fun ensureNoCycles(scope: KSType, path: List<String>) {
            if (scope in visiting) {
                // Cycle detected
                val cyclePath = path.joinToString(" → ")
                throw StitchProcessingException(
                    "Cycle detected in scope dependencies: $cyclePath → ${scope.declaration.simpleName.asString()}. " +
                        "Scope dependency chains must be acyclic.",
                    scope.declaration,
                )
            }
            if (scope in visited) {
                return
            }
            visiting.add(scope)
            val upstream = scopeDependencies[scope]
            if (upstream != null) {
                val newPath = path + scope.declaration.simpleName.asString()
                ensureNoCycles(upstream, newPath)
            }
            visiting.remove(scope)
            visited.add(scope)
        }

        val scopeBySymbol = HashMap<KSAnnotated, KSType>()
        for (scope in scopeDependencies.keys) {
            ensureNoCycles(scope, emptyList())
            scopeBySymbol.addAnnotatedSymbols(resolver, singletons, scope)
        }
        return scopeBySymbol
    }

    private fun HashMap<KSAnnotated, KSType>.addAnnotatedSymbols(
        resolver: Resolver,
        singletons: Set<KSAnnotated>,
        scope: KSType,
    ) {
        val annotationName = scope.declaration.qualifiedName?.asString() ?: return
        for (symbol in resolver.getSymbolsWithAnnotation(annotationName)) {
            if (singletons.contains(symbol)) {
                val name = scope.declaration.simpleName.asString()
                throw StitchProcessingException(
                    message = "$symbol is annotated with @Singleton and @$name at the same time",
                    symbol = symbol,
                )
            }
            if (this.containsKey(symbol)) {
                val existing = this.getValue(symbol)
                val name = scope.declaration.simpleName.asString()
                throw StitchProcessingException(
                    message = "$symbol is annotated with @$existing and @$name at the same time",
                    symbol = symbol,
                )
            }
            this[symbol] = scope
        }
    }

    private companion object {
        const val STITCH_SCOPE = "com.harrytmthy.stitch.annotations.Scope"
        const val STITCH_SINGLETON = "com.harrytmthy.stitch.annotations.Singleton"
        const val JAVAX_SINGLETON = "javax.inject.Singleton"
    }
}

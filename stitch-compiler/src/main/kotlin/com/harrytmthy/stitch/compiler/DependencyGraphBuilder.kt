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

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSType

/**
 * Builds and validates the dependency graph.
 *
 * Performs cycle detection and ensures all dependencies are satisfied.
 */
class DependencyGraphBuilder(private val logger: KSPLogger) {

    /**
     * Helper to find dependency using scope-aware resolution.
     * Returns the node if found in current scope, ancestor scopes, or singleton/unscoped.
     */
    private fun findDependency(
        type: KSType,
        qualifier: QualifierInfo?,
        currentScope: KSType?,
        registry: Map<BindingKey, DependencyNode>,
        scopeGraph: ScopeGraph,
    ): DependencyNode? {
        // 1. Try exact match in current scope
        val exactKey = BindingKey(type, qualifier, currentScope)
        registry[exactKey]?.let { return it }

        // 2. Walk up the scope chain
        if (currentScope != null) {
            var ancestorScope: KSType? = scopeGraph.scopeDependencies[currentScope]
            while (ancestorScope != null) {
                val ancestorKey = BindingKey(type, qualifier, ancestorScope)
                registry[ancestorKey]?.let { return it }
                ancestorScope = scopeGraph.scopeDependencies[ancestorScope]
            }
        }

        // 3. Try singleton/unscoped (scope = null)
        val singletonKey = BindingKey(type, qualifier, null)
        return registry[singletonKey]
    }

    fun buildGraph(scanResult: ScanResult, scopeGraph: ScopeGraph): DependencyGraph {
        val nodes = mutableListOf<DependencyNode>()
        val registry = mutableMapOf<BindingKey, DependencyNode>()
        val scopeByTypeAndQualifier = mutableMapOf<BindingKey, KSType?>()

        // Helper function to register a node under multiple keys (canonical + aliases)
        fun registerNode(node: DependencyNode, aliasTypes: List<KSType> = emptyList()) {
            val allKeys = listOf(BindingKey(node.type, node.qualifier, node.scopeAnnotation)) +
                aliasTypes.map { BindingKey(it, node.qualifier, node.scopeAnnotation) }

            for (key in allKeys) {
                // Check for duplicate within same scope (existing behavior)
                if (registry.containsKey(key)) {
                    val existing = registry[key]!!
                    val existingLocation = if (existing.providerFunction.isConstructor()) {
                        "${existing.providerModule.qualifiedName?.asString()} @Inject constructor"
                    } else {
                        "${existing.providerModule.qualifiedName?.asString()}.${existing.providerFunction.simpleName.asString()}()"
                    }
                    val currentLocation = if (node.providerFunction.isConstructor()) {
                        "${node.providerModule.qualifiedName?.asString()} @Inject constructor"
                    } else {
                        "${node.providerModule.qualifiedName?.asString()}.${node.providerFunction.simpleName.asString()}()"
                    }
                    val scopeName = key.scope?.declaration?.simpleName?.asString() ?: "Singleton"
                    logger.error(
                        "Duplicate binding for ${key.type.declaration.qualifiedName?.asString()} " +
                            "with qualifier ${key.qualifier} in scope @$scopeName.\n" +
                            "Already provided by: $existingLocation\n" +
                            "Duplicate found in: $currentLocation",
                        node.providerFunction,
                    )
                } else {
                    // Check for duplicate across all scopes (Iteration 5)
                    val typeName = key.type.declaration.qualifiedName?.asString()
                    if (typeName != null) {
                        val typeQualifierKey = BindingKey(key.type, key.qualifier)
                        if (scopeByTypeAndQualifier.containsKey(typeQualifierKey)) {
                            val existingScope = scopeByTypeAndQualifier[typeQualifierKey]
                            val existingScopeName = existingScope?.declaration?.simpleName?.asString() ?: "Singleton"
                            val currentScopeName = key.scope?.declaration?.simpleName?.asString() ?: "Singleton"
                            val currentLocation = if (node.providerFunction.isConstructor()) {
                                "${node.providerModule.qualifiedName?.asString()} @Inject constructor"
                            } else {
                                "${node.providerModule.qualifiedName?.asString()}.${node.providerFunction.simpleName.asString()}()"
                            }
                            val qualifierStr = if (key.qualifier != null) " with qualifier ${key.qualifier}" else ""
                            logger.error(
                                "Binding for ${key.type.declaration.qualifiedName?.asString()}$qualifierStr " +
                                    "already exists in @$existingScopeName scope.\n" +
                                    "Cannot define another binding in @$currentScopeName scope.\n" +
                                    "Each (type, qualifier) pair must have exactly one binding across all scopes.\n" +
                                    "Duplicate found in: $currentLocation",
                                node.providerFunction,
                            )
                        } else {
                            scopeByTypeAndQualifier[typeQualifierKey] = key.scope
                        }
                    }
                    registry[key] = node
                }
            }

            nodes.add(node)
        }

        // First pass: Create nodes from @Provides and @Inject methods
        scanResult.modules.forEach { module ->
            module.provides.forEach { provider ->
                val node = DependencyNode(
                    providerModule = module.declaration,
                    providerFunction = provider.declaration,
                    type = provider.returnType,
                    qualifier = provider.qualifier,
                    isSingleton = provider.isSingleton,
                    dependencies = provider.parameters.map { param ->
                        FieldInfo(type = param.type, qualifier = param.qualifier)
                    },
                    aliases = provider.aliases.toMutableList(),
                    scopeAnnotation = provider.scopeAnnotation,
                )

                registerNode(node, provider.aliases)
            }
        }

        scanResult.injectables.forEach { injectable ->
            val returnType = injectable.classDeclaration.asStarProjectedType()

            // Dependencies include both constructor params AND injectable fields
            val allDependencies = injectable.constructorParameters.map { param ->
                FieldInfo(type = param.type, qualifier = param.qualifier)
            } + injectable.injectableFields.map { field ->
                FieldInfo(type = field.type, qualifier = field.qualifier)
            }

            val node = DependencyNode(
                providerModule = injectable.classDeclaration,
                providerFunction = injectable.constructor,
                type = returnType,
                qualifier = injectable.qualifier,
                isSingleton = injectable.isSingleton,
                dependencies = allDependencies,
                injectableFields = injectable.injectableFields,
                aliases = injectable.aliases.toMutableList(),
                scopeAnnotation = injectable.scopeAnnotation,
            )

            registerNode(node, injectable.aliases)
        }

        // Second pass: Process @Binds methods
        // @Binds methods reference an existing node and register it under an alias type
        // Note: @Binds doesn't have its own scope - it inherits from the implementation node
        scanResult.modules.forEach { module ->
            module.binds.forEach { binds ->
                val scopeKey = BindingKey(binds.implementationType, binds.qualifier)
                val scope = scopeByTypeAndQualifier[scopeKey]
                val key = BindingKey(binds.implementationType, binds.qualifier, scope)
                val existingNode = registry[key] ?: run {
                    logger.error(
                        "@Binds method ${binds.declaration.simpleName.asString()}: " +
                            "implementation type ${binds.implementationType.declaration.qualifiedName?.asString()} " +
                            "with qualifier ${binds.qualifier} not found in dependency graph",
                        binds.declaration,
                    )
                    return@forEach
                }
                val aliasKey = BindingKey(binds.aliasType, binds.qualifier, existingNode.scopeAnnotation)
                if (registry.containsKey(aliasKey)) {
                    val conflicting = registry[aliasKey]!!
                    val conflictingLocation = if (conflicting.providerFunction.isConstructor()) {
                        "${conflicting.providerModule.qualifiedName?.asString()} @Inject constructor"
                    } else {
                        "${conflicting.providerModule.qualifiedName?.asString()}.${conflicting.providerFunction.simpleName.asString()}()"
                    }
                    val currentLocation = "${module.declaration.qualifiedName?.asString()}.${binds.declaration.simpleName.asString()}()"
                    val scopeName = aliasKey.scope?.declaration?.simpleName?.asString() ?: "Singleton"
                    logger.error(
                        "Duplicate binding for ${aliasKey.type.declaration.qualifiedName?.asString()} " +
                            "with qualifier ${aliasKey.qualifier} in scope @$scopeName.\n" +
                            "Already provided by: $conflictingLocation\n" +
                            "Duplicate found in: $currentLocation",
                        binds.declaration,
                    )
                } else {
                    // Register the same node instance under the alias key
                    registry[aliasKey] = existingNode
                    // Add alias to node's aliases list so code generator creates provider method
                    if (!existingNode.aliases.contains(binds.aliasType)) {
                        existingNode.aliases += binds.aliasType
                    }
                }
            }
        }

        // Third pass: Validate all dependencies exist (scope-aware resolution)
        val adjacency = HashMap<DependencyNode, MutableList<DependencyNode>>()
        nodes.forEach { node ->
            val dependenciesForNode = ArrayList<DependencyNode>()
            node.dependencies.forEach { dep ->
                val dependencyNode = findDependency(dep.type, dep.qualifier, node.scopeAnnotation, registry, scopeGraph)
                if (dependencyNode == null) {
                    val requiredBy = if (node.providerFunction.isConstructor()) {
                        "${node.providerModule.qualifiedName?.asString()} @Inject constructor"
                    } else {
                        "${node.providerModule.qualifiedName?.asString()}.${node.providerFunction.simpleName.asString()}()"
                    }
                    logger.error(
                        "Missing binding for ${dep.type.declaration.qualifiedName?.asString()} " +
                            "with qualifier ${dep.qualifier}.\n" +
                            "Required by: $requiredBy",
                        node.providerFunction,
                    )
                } else {
                    dependenciesForNode += dependencyNode
                }
            }
            adjacency[node] = dependenciesForNode
        }

        // Fourth pass: Validate field injection dependencies (scope-agnostic)
        // For relaxed multi-component inject, we only check that binding exists ANYWHERE
        scanResult.fieldInjectors.forEach { injector ->
            injector.injectableFields.forEach { field ->
                val hasBindingAnywhere = registry.keys.any { key ->
                    key.type.declaration == field.type.declaration &&
                        key.qualifier == field.qualifier
                }

                if (!hasBindingAnywhere) {
                    val className = injector.classDeclaration.qualifiedName?.asString()
                    logger.error(
                        "Missing binding for ${field.type.declaration.qualifiedName?.asString()} " +
                            "with qualifier ${field.qualifier}.\n" +
                            "Required by: $className @Inject field '${field.name}'",
                        injector.classDeclaration,
                    )
                }
            }
        }

        // Fifth pass: Detect cycles
        detectCycles(nodes, adjacency)

        // Sixth pass: Validate scoped dependencies
        validateScopedDependencies(nodes, registry, scopeGraph)

        return DependencyGraph(nodes, registry)
    }

    private fun detectCycles(
        nodes: List<DependencyNode>,
        adjacency: Map<DependencyNode, List<DependencyNode>>,
    ) {
        val visiting = mutableSetOf<DependencyNode>()
        val visited = mutableSetOf<DependencyNode>()

        fun dfs(node: DependencyNode, path: List<DependencyNode>) {
            if (node in visiting) {
                // cycle detected
                val startIndex = path.indexOfFirst {
                    it.type == node.type &&
                        it.qualifier == node.qualifier &&
                        it.scopeAnnotation == node.scopeAnnotation
                }.takeIf { it >= 0 } ?: 0

                val cyclePath = path.drop(startIndex) + node
                val cycleDescription = cyclePath.joinToString(" -> ") {
                    val qual = if (it.qualifier != null) " (${it.qualifier})" else ""
                    val scope = if (it.scopeAnnotation != null) " @${it.scopeAnnotation.declaration.simpleName.asString()}" else ""
                    "${it.type.declaration.qualifiedName?.asString()}$qual$scope"
                }
                logger.error(
                    "Dependency cycle detected: $cycleDescription",
                    node.providerFunction,
                )
                return
            }

            if (visited.contains(node)) {
                return
            }

            visiting.add(node)

            adjacency[node]?.forEach { depNode ->
                dfs(depNode, path + node)
            }

            visiting -= node
            visited += node
        }

        nodes.forEach { node ->
            if (!visited.contains(node)) {
                dfs(node, emptyList())
            }
        }
    }

    /**
     * Validates that scoped dependencies follow the correct dependency flow.
     * Rule: A binding in scope S can only depend on same scope S or ancestor scopes.
     */
    private fun validateScopedDependencies(
        nodes: List<DependencyNode>,
        registry: Map<BindingKey, DependencyNode>,
        scopeGraph: ScopeGraph,
    ) {
        nodes.forEach { node ->
            val nodeScope = node.scopeAnnotation ?: return@forEach // Unscoped/singleton, skip
            node.dependencies.forEach { dep ->
                val depNode = findDependency(dep.type, dep.qualifier, nodeScope, registry, scopeGraph) ?: return@forEach // Missing dep, already reported
                val depScope = depNode.scopeAnnotation

                if (depScope != null) {
                    // Validate: dependency must be same scope or ancestor
                    if (depScope != nodeScope && !isAncestor(depScope, nodeScope, scopeGraph)) {
                        logger.error(
                            "Scoped binding ${node.type.declaration.simpleName.asString()} " +
                                "(${nodeScope.declaration.simpleName.asString()}) cannot depend on " +
                                "${depNode.type.declaration.simpleName.asString()} " +
                                "(${depScope.declaration.simpleName.asString()}). " +
                                "Dependencies must flow upstream in the scope chain.",
                            node.providerFunction,
                        )
                    }
                }
                // Singleton/unscoped dependencies are always OK (they're upstream of everything)
            }
        }
    }

    /**
     * Checks if ancestor is an ancestor of descendant in the scope graph.
     */
    private fun isAncestor(ancestor: KSType, descendant: KSType, scopeGraph: ScopeGraph): Boolean {
        var current = descendant
        while (true) {
            val upstream = scopeGraph.scopeDependencies[current] ?: return false
            if (upstream == ancestor) return true
            current = upstream
        }
    }
}

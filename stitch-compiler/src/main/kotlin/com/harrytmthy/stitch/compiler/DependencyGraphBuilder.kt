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

    fun buildGraph(scanResult: ScanResult): List<DependencyNode> {
        val nodes = mutableListOf<DependencyNode>()
        val registry = mutableMapOf<DependencyKey, DependencyNode>()

        // Helper function to register a node under multiple keys (canonical + aliases)
        fun registerNode(node: DependencyNode, aliasTypes: List<KSType> = emptyList()) {
            val allKeys = listOf(DependencyKey(node.type, node.qualifier)) +
                aliasTypes.map { DependencyKey(it, node.qualifier) }

            for (key in allKeys) {
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
                    logger.error(
                        "Duplicate binding for ${key.type.declaration.qualifiedName?.asString()} " +
                            "with qualifier ${key.qualifier}.\n" +
                            "Already provided by: $existingLocation\n" +
                            "Duplicate found in: $currentLocation",
                        node.providerFunction,
                    )
                } else {
                    registry[key] = node
                }
            }

            nodes.add(node)
        }

        // First pass: Create nodes from @Provides methods
        scanResult.modules.forEach { module ->
            module.provides.forEach { provider ->
                val node = DependencyNode(
                    providerModule = module.declaration,
                    providerFunction = provider.declaration,
                    type = provider.returnType,
                    qualifier = provider.qualifier,
                    isSingleton = provider.isSingleton,
                    dependencies = provider.parameters.map { param ->
                        DependencyRef(param.type, param.qualifier)
                    },
                    aliases = provider.aliases.toMutableList(),
                )

                registerNode(node, provider.aliases)
            }
        }

        // Second pass: Create nodes from @Inject classes
        scanResult.injectables.forEach { injectable ->
            val returnType = injectable.classDeclaration.asStarProjectedType()

            // Dependencies include both constructor params AND injectable fields
            val allDependencies = injectable.constructorParameters.map { param ->
                DependencyRef(param.type, param.qualifier)
            } + injectable.injectableFields.map { field ->
                DependencyRef(field.type, field.qualifier)
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
            )

            registerNode(node, injectable.aliases)
        }

        // Third pass: Process @Binds methods
        // @Binds methods reference an existing node and register it under an alias type
        scanResult.modules.forEach { module ->
            module.binds.forEach { binds ->
                val implKey = DependencyKey(binds.implementationType, binds.qualifier)
                val existingNode = registry[implKey]
                if (existingNode != null) {
                    // Register the existing node under the alias type
                    val aliasKey = DependencyKey(binds.aliasType, binds.qualifier)
                    if (registry.containsKey(aliasKey)) {
                        val conflicting = registry[aliasKey]!!
                        val conflictingLocation = if (conflicting.providerFunction.isConstructor()) {
                            "${conflicting.providerModule.qualifiedName?.asString()} @Inject constructor"
                        } else {
                            "${conflicting.providerModule.qualifiedName?.asString()}.${conflicting.providerFunction.simpleName.asString()}()"
                        }
                        val currentLocation = "${module.declaration.qualifiedName?.asString()}.${binds.declaration.simpleName.asString()}()"
                        logger.error(
                            "Duplicate binding for ${aliasKey.type.declaration.qualifiedName?.asString()} " +
                                "with qualifier ${aliasKey.qualifier}.\n" +
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
                } else {
                    // Implementation type not found - error
                    logger.error(
                        "@Binds method ${binds.declaration.simpleName.asString()}: " +
                            "implementation type ${binds.implementationType.declaration.qualifiedName?.asString()} " +
                            "with qualifier ${binds.qualifier} not found in dependency graph",
                        binds.declaration,
                    )
                }
            }
        }

        // Fourth pass: Validate all dependencies exist
        nodes.forEach { node ->
            node.dependencies.forEach { dep ->
                val depKey = DependencyKey(dep.type, dep.qualifier)
                if (!registry.containsKey(depKey)) {
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
                }
            }
        }

        // Fifth pass: Validate field injection dependencies
        scanResult.fieldInjectors.forEach { injector ->
            injector.injectableFields.forEach { field ->
                val depKey = DependencyKey(field.type, field.qualifier)
                if (!registry.containsKey(depKey)) {
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

        // Sixth pass: Detect cycles
        detectCycles(nodes, registry)

        return nodes
    }

    private fun detectCycles(nodes: List<DependencyNode>, registry: Map<DependencyKey, DependencyNode>) {
        val visiting = mutableSetOf<DependencyKey>()
        val visited = mutableSetOf<DependencyKey>()

        fun dfs(node: DependencyNode, path: List<DependencyNode>) {
            val key = DependencyKey(node.type, node.qualifier)

            if (visiting.contains(key)) {
                // Cycle detected
                val cyclePath = path.dropWhile { it.type != node.type || it.qualifier != node.qualifier } + node
                val cycleDescription = cyclePath.joinToString(" -> ") {
                    val qual = if (it.qualifier != null) " (${it.qualifier})" else ""
                    "${it.type.declaration.qualifiedName?.asString()}$qual"
                }
                logger.error(
                    "Dependency cycle detected: $cycleDescription",
                    node.providerFunction,
                )
                return
            }

            if (visited.contains(key)) {
                return
            }

            visiting.add(key)

            node.dependencies.forEach { dep ->
                val depKey = DependencyKey(dep.type, dep.qualifier)
                registry[depKey]?.let { depNode ->
                    dfs(depNode, path + node)
                }
            }

            visiting.remove(key)
            visited.add(key)
        }

        nodes.forEach { node ->
            val key = DependencyKey(node.type, node.qualifier)
            if (!visited.contains(key)) {
                dfs(node, emptyList())
            }
        }
    }

    private data class DependencyKey(
        val type: KSType,
        val qualifier: QualifierInfo?,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DependencyKey) return false
            return type.declaration == other.type.declaration && qualifier == other.qualifier
        }

        override fun hashCode(): Int {
            var result = type.declaration.hashCode()
            result = 31 * result + (qualifier?.hashCode() ?: 0)
            return result
        }
    }
}

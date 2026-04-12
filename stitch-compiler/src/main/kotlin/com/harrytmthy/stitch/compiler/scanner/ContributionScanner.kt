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

package com.harrytmthy.stitch.compiler.scanner

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.harrytmthy.stitch.annotations.BindingContributions
import com.harrytmthy.stitch.annotations.ScopeContributions
import com.harrytmthy.stitch.compiler.StitchSymbolProcessor.Companion.GENERATED_PACKAGE_NAME
import com.harrytmthy.stitch.compiler.consts.BindingKind
import com.harrytmthy.stitch.compiler.model.Binding
import com.harrytmthy.stitch.compiler.model.BindingDeclaration
import com.harrytmthy.stitch.compiler.model.ContributionScanResult
import com.harrytmthy.stitch.compiler.model.LocalScanResult
import com.harrytmthy.stitch.compiler.model.ProvidedBinding
import com.harrytmthy.stitch.compiler.model.Qualifier
import com.harrytmthy.stitch.compiler.model.RequestedBinding
import com.harrytmthy.stitch.compiler.model.Scope
import com.harrytmthy.stitch.compiler.utils.StitchErrorLogger

object ContributionScanner {

    @OptIn(KspExperimental::class)
    @Suppress("UNCHECKED_CAST")
    fun scan(
        resolver: Resolver,
        logger: StitchErrorLogger,
        scanResult: LocalScanResult,
    ): ContributionScanResult? {
        // Step 1: Collect all bindings and scopes from the aggregator
        val providedBindings = HashMap(scanResult.providedBindings)
        val requestedBindings = HashMap(scanResult.requestedBindings)
        val customScopeByCanonicalName = HashMap(scanResult.customScopeByCanonicalName)
        val scopeDependencies = HashMap(scanResult.scopeDependencies)

        // Step 2: Collect all scope + binding contributions' annotations
        val scopeContributions = ArrayList<KSAnnotation>()
        val bindingContributions = ArrayList<KSAnnotation>()
        for (declaration in resolver.getDeclarationsFromPackage(GENERATED_PACKAGE_NAME)) {
            for (annotation in declaration.annotations) {
                when (annotation.shortName.asString()) {
                    ScopeContributions::class.simpleName -> scopeContributions.add(annotation)
                    BindingContributions::class.simpleName -> bindingContributions.add(annotation)
                    else -> continue
                }
            }
        }

        // Step 3: Scan scope contributions
        scanScopeContributions(
            scopeContributions = scopeContributions,
            customScopeByCanonicalName = customScopeByCanonicalName,
            scopeDependencies = scopeDependencies,
            logger = logger,
        )
        if (logger.hasError) {
            return null
        }

        // Step 4: Canonicalize aggregator module's provided binding scopes
        for (binding in providedBindings.values) {
            providedBindings[binding] = ProvidedBinding(
                type = binding.type,
                qualifier = binding.qualifier,
                scope = when (binding.scope) {
                    is Scope.Singleton -> Scope.Singleton
                    is Scope.Custom -> customScopeByCanonicalName[binding.scope.canonicalName]
                    else -> null
                },
                nullable = binding.nullable,
                location = binding.location,
                kind = binding.kind,
                providerPackageName = binding.providerPackageName,
                providerFunctionName = binding.providerFunctionName,
                providerClassName = binding.providerClassName,
            ).apply { dependencies = binding.dependencies }
        }

        // Step 5: Scan binding contributions
        val contributedBindings = ArrayList<BindingDeclaration>()
        val contributedDependencies = ArrayList<List<Int>>() // Flattened indices, NOT bindingId
        scanBindingContributions(
            bindingContributions = bindingContributions,
            providedBindings = providedBindings,
            requestedBindings = requestedBindings,
            contributedBindings = contributedBindings,
            contributedDependencies = contributedDependencies,
            customScopeByCanonicalName = customScopeByCanonicalName,
            logger = logger,
        )
        if (logger.hasError) {
            return null
        }

        // Step 6: Ensure all requested bindings are actually provided
        for (requested in requestedBindings.values) {
            for (requestedBinding in requested) {
                if (requestedBinding !in providedBindings) {
                    logger.missingBindingError(requestedBinding)
                }
            }
        }
        if (logger.hasError) {
            return null
        }

        // Step 7: Build binding edges
        for (index in contributedBindings.indices) {
            val binding = contributedBindings[index]
            val dependencies = contributedDependencies[index]
            val providedBinding = providedBindings.getValue(binding)
            val providedBindingDependencies = providedBinding.dependencies
                ?: ArrayList<BindingDeclaration>(dependencies.size).also { providedBinding.dependencies = it }
            for (index in dependencies) {
                val bindingDependency = contributedBindings[index]
                providedBindingDependencies.add(bindingDependency)
            }
        }

        return ContributionScanResult(
            providedBindings,
            requestedBindings,
            customScopeByCanonicalName,
            scopeDependencies,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun scanScopeContributions(
        scopeContributions: List<KSAnnotation>,
        customScopeByCanonicalName: HashMap<String, Scope.Custom>,
        scopeDependencies: HashMap<Scope, Scope>,
        logger: StitchErrorLogger,
    ) {
        for (annotation in scopeContributions) {
            // Step 3.1: Collect all scopes
            val scopeAnnotations = annotation.arguments[0].value as List<KSAnnotation>
            val localScopes = ArrayList<Scope.Custom>(scopeAnnotations.size)
            val localScopeDependencyIndices = ArrayList<Int>(scopeAnnotations.size)
            for (scopeAnnotation in scopeAnnotations) {
                val id = scopeAnnotation.arguments[0].value as Int
                val originalName = scopeAnnotation.arguments[1].value as String
                val canonicalName = scopeAnnotation.arguments[2].value as String
                val qualifiedName = scopeAnnotation.arguments[3].value as String
                val location = scopeAnnotation.arguments[4].value as String
                val dependsOn = scopeAnnotation.arguments[5].value as Int
                customScopeByCanonicalName[canonicalName]?.let { scope ->
                    // Existing scope path
                    if (scope.qualifiedName.isNotEmpty() && qualifiedName.isNotEmpty()) {
                        // There is more than 1 annotation class (for scope) with a same name
                        logger.error("Duplicate scope: $scope. Already provided at ${scope.location}")
                    }
                    if (qualifiedName.isEmpty()) {
                        // Avoid scope re-registration, unless it's an annotation class declaration
                        continue
                    }
                }
                val customScope = Scope.Custom(originalName, canonicalName, qualifiedName, location)
                customScopeByCanonicalName[canonicalName] = customScope
                localScopes.add(customScope)
                localScopeDependencyIndices.add(dependsOn - 1) // -1 since ID starts from 1
            }

            // Step 3.2: Collect scope dependencies
            for (index in localScopes.indices) {
                val scope = localScopes[index]
                val dependencyIndex = localScopeDependencyIndices[index]
                if (dependencyIndex == -1) {
                    // Singleton path (scopeId = 0 - 1 = -1)
                    scopeDependencies[scope] = Scope.Singleton
                } else {
                    // Custom scope path
                    scopeDependencies[scope] = localScopes[dependencyIndex]
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun scanBindingContributions(
        bindingContributions: List<KSAnnotation>,
        providedBindings: HashMap<Binding, ProvidedBinding>,
        requestedBindings: HashMap<String, ArrayList<RequestedBinding>>,
        contributedBindings: ArrayList<BindingDeclaration>,
        contributedDependencies: ArrayList<List<Int>>,
        customScopeByCanonicalName: Map<String, Scope.Custom>,
        logger: StitchErrorLogger,
    ) {
        for (annotation in bindingContributions) {
            val bindingAnnotations = annotation.arguments[0].value as List<KSAnnotation>
            val requesterAnnotations = annotation.arguments[1].value as List<KSAnnotation>

            // Step 4.1: Collect all provided + requested bindings from the contributors
            val lastBindingIndex = contributedBindings.lastIndex
            for (bindingAnnotation in bindingAnnotations) {
                val id = bindingAnnotation.arguments[0].value as Int
                val type = bindingAnnotation.arguments[1].value as String
                val qualifier = Qualifier.of(bindingAnnotation.arguments[2].value as String)
                val scopeCanonicalName = bindingAnnotation.arguments[3].value as String
                val nullable = bindingAnnotation.arguments[4].value as Boolean
                val location = bindingAnnotation.arguments[5].value as String
                val kind = bindingAnnotation.arguments[6].value as Int
                val providerPackageName = bindingAnnotation.arguments[7].value as String
                val providerFunctionName = bindingAnnotation.arguments[8].value as String
                val providerClassName = bindingAnnotation.arguments[9].value as String
                val dependsOn = bindingAnnotation.arguments[10].value as List<Int>
                val binding = BindingDeclaration(type, qualifier, location)
                contributedBindings.add(binding)
                contributedDependencies += dependsOn.map {
                    lastBindingIndex + it // Converts bindingId to contributedBindings's index
                }
                if (kind != BindingKind.REQUESTED) {
                    providedBindings[binding]?.let {
                        logger.duplicateBindingError(it)
                        continue
                    }
                    val providedBinding = ProvidedBinding(
                        type = type,
                        qualifier = qualifier,
                        scope = when (scopeCanonicalName) {
                            "singleton" -> Scope.Singleton
                            "" -> null
                            else -> customScopeByCanonicalName.getValue(scopeCanonicalName)
                        },
                        nullable = nullable,
                        location = location,
                        kind = kind,
                        providerPackageName = providerPackageName,
                        providerFunctionName = providerFunctionName,
                        providerClassName = providerClassName,
                    )
                    providedBindings[binding] = providedBinding
                }
            }

            // Step 4.2: Collect all requested bindings, grouped by requester's FQN
            for (requesterAnnotation in requesterAnnotations) {
                val requesterQualifiedName = requesterAnnotation.arguments[0].value as String
                val fields = requesterAnnotation.arguments[1].value as List<KSAnnotation>
                val requested = requestedBindings.getOrPut(requesterQualifiedName) {
                    ArrayList(fields.size)
                }
                for (field in fields) {
                    val bindingId = field.arguments[0].value as Int
                    val fieldName = field.arguments[1].value as String
                    val binding = contributedBindings[lastBindingIndex + bindingId]
                    val requestedBinding = RequestedBinding(
                        type = binding.type,
                        qualifier = binding.qualifier,
                        location = binding.location,
                        fieldName = fieldName,
                    )
                    requested.add(requestedBinding)
                }
            }
        }
    }

    private fun StitchErrorLogger.duplicateBindingError(existing: ProvidedBinding) {
        error(
            message = buildString {
                append("Duplicate binding for ${existing.type}")
                existing.qualifier?.let { append(" (qualifier: $it)") }
                if (existing.scope != null) {
                    append(" in scope \"${existing.scope}\"")
                }
                append(". Already provided at ${existing.location}")
            },
        )
    }

    private fun StitchErrorLogger.missingBindingError(binding: BindingDeclaration) {
        error(
            message = buildString {
                append("Binding with type '${binding.type}'")
                binding.qualifier?.let { append(" (qualifier: $it)") }
                append(" is never provided, but requested in ${binding.location}")
            },
        )
    }
}

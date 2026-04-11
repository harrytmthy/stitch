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
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.harrytmthy.stitch.annotations.BindingRequester
import com.harrytmthy.stitch.annotations.ContributedBinding
import com.harrytmthy.stitch.annotations.RegisteredScope
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
        val generatedClasses = ArrayList<KSClassDeclaration>()

        // Step 2: Collect all scope + binding contributions' annotations
        val scopeContributionsByModuleKey = HashMap<String, ArrayList<KSAnnotation>>()
        val bindingContributionsByModuleKey = HashMap<String, ArrayList<KSAnnotation>>()
        val bindingRequestersByModuleKey = HashMap<String, ArrayList<KSAnnotation>>()
        for (declaration in resolver.getDeclarationsFromPackage(GENERATED_PACKAGE_NAME)) {
            val moduleKey = declaration.simpleName.asString().substringAfterLast("_")
            val scopeContributions = scopeContributionsByModuleKey.getOrPut(moduleKey, ::ArrayList)
            val bindingContributions = bindingContributionsByModuleKey.getOrPut(moduleKey, ::ArrayList)
            val bindingRequesters = bindingRequestersByModuleKey.getOrPut(moduleKey, ::ArrayList)
            for (annotation in declaration.annotations) {
                when (annotation.shortName.asString()) {
                    RegisteredScope::class.simpleName -> {
                        scopeContributions.add(annotation)
                        generatedClasses.add(declaration as KSClassDeclaration)
                    }

                    ContributedBinding::class.simpleName -> {
                        bindingContributions.add(annotation)
                        generatedClasses.add(declaration as KSClassDeclaration)
                    }

                    BindingRequester::class.simpleName -> {
                        bindingRequesters.add(annotation)
                        generatedClasses.add(declaration as KSClassDeclaration)
                    }

                    else -> continue
                }
            }
        }

        // Step 3: Scan scope contributions
        scanScopeContributions(
            scopeContributionsByModuleKey = scopeContributionsByModuleKey,
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
                location = binding.location,
                kind = binding.kind,
                providerPackageName = binding.providerPackageName,
                providerFunctionName = binding.providerFunctionName,
                providerClassName = binding.providerClassName,
            ).apply { dependencies = binding.dependencies }
        }

        // Step 5: Scan binding contributions
        val localBindingsByModuleKey = HashMap<String, HashMap<Int, BindingDeclaration>>()
        val localDependenciesByModuleKey = HashMap<String, HashMap<Int, List<Int>>>()
        scanBindingContributions(
            bindingContributionsByModuleKey = bindingContributionsByModuleKey,
            bindingRequestersByModuleKey = bindingRequestersByModuleKey,
            providedBindings = providedBindings,
            requestedBindings = requestedBindings,
            localBindingsByModuleKey = localBindingsByModuleKey,
            localDependenciesByModuleKey = localDependenciesByModuleKey,
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
        for ((moduleKey, localBindingById) in localBindingsByModuleKey) {
            val localDependencies = localDependenciesByModuleKey.getValue(moduleKey)
            for ((bindingId, binding) in localBindingById) {
                val dependencyIds = localDependencies.getValue(bindingId)
                val providedBinding = providedBindings.getValue(binding)
                val providedBindingDependencies = providedBinding.dependencies
                    ?: ArrayList<BindingDeclaration>(dependencyIds.size).also { providedBinding.dependencies = it }
                for (dependencyId in dependencyIds) {
                    val bindingDependency = localBindingById.getValue(dependencyId)
                    providedBindingDependencies.add(bindingDependency)
                }
            }
        }

        return ContributionScanResult(
            providedBindings,
            requestedBindings,
            customScopeByCanonicalName,
            scopeDependencies,
            generatedClasses,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun scanScopeContributions(
        scopeContributionsByModuleKey: Map<String, List<KSAnnotation>>,
        customScopeByCanonicalName: HashMap<String, Scope.Custom>,
        scopeDependencies: HashMap<Scope, Scope>,
        logger: StitchErrorLogger,
    ) {
        val customScopesByModuleKey = HashMap<String, HashMap<Int, Scope.Custom>>()
        val scopeDependencyByModuleKey = HashMap<String, HashMap<Int, Int>>()
        for ((moduleKey, scopeContributions) in scopeContributionsByModuleKey) {
            for (annotation in scopeContributions) {
                // Step 3.1: Collect all scopes
                val id = annotation.arguments[0].value as Int
                val originalName = annotation.arguments[1].value as String
                val canonicalName = annotation.arguments[2].value as String
                val qualifiedName = annotation.arguments[3].value as String
                val location = annotation.arguments[4].value as String
                val dependsOn = annotation.arguments[5].value as Int
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
                val customScopesById = customScopesByModuleKey.getOrPut(moduleKey, ::HashMap)
                customScopesById[id] = customScope
                val scopeDependency = scopeDependencyByModuleKey.getOrPut(moduleKey, ::HashMap)
                scopeDependency[id] = dependsOn
            }
        }

        // Step 3.2: Collect scope dependencies
        for ((moduleKey, scopeDependency) in scopeDependencyByModuleKey) {
            val customScopesById = customScopesByModuleKey.getValue(moduleKey)
            for ((scopeId, dependsOn) in scopeDependency) {
                val customScope = customScopesById.getValue(scopeId)
                val dependency = customScopesById[dependsOn] ?: Scope.Singleton
                scopeDependencies[customScope] = dependency
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun scanBindingContributions(
        bindingContributionsByModuleKey: Map<String, List<KSAnnotation>>,
        bindingRequestersByModuleKey: Map<String, List<KSAnnotation>>,
        providedBindings: HashMap<Binding, ProvidedBinding>,
        requestedBindings: HashMap<String, ArrayList<RequestedBinding>>,
        localBindingsByModuleKey: HashMap<String, HashMap<Int, BindingDeclaration>>,
        localDependenciesByModuleKey: HashMap<String, HashMap<Int, List<Int>>>,
        customScopeByCanonicalName: Map<String, Scope.Custom>,
        logger: StitchErrorLogger,
    ) {
        // Step 5.1: Collect all provided + requested bindings from the contributors
        for ((moduleKey, bindingAnnotations) in bindingContributionsByModuleKey) {
            val localBindingById = localBindingsByModuleKey.getOrPut(moduleKey, ::HashMap)
            val localDependencies = localDependenciesByModuleKey.getOrPut(moduleKey, ::HashMap)
            for (bindingAnnotation in bindingAnnotations) {
                val id = bindingAnnotation.arguments[0].value as Int
                val type = bindingAnnotation.arguments[1].value as String
                val qualifier = Qualifier.of(bindingAnnotation.arguments[2].value as String)
                val scopeCanonicalName = bindingAnnotation.arguments[3].value as String
                val location = bindingAnnotation.arguments[4].value as String
                val kind = bindingAnnotation.arguments[5].value as Int
                val providerPackageName = bindingAnnotation.arguments[6].value as String
                val providerFunctionName = bindingAnnotation.arguments[7].value as String
                val providerClassName = bindingAnnotation.arguments[8].value as String
                val dependsOn = bindingAnnotation.arguments[9].value as List<Int>
                val binding = BindingDeclaration(type, qualifier, location)
                localBindingById[id] = binding
                localDependencies[id] = dependsOn
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
                        location = location,
                        kind = kind,
                        providerPackageName = providerPackageName,
                        providerFunctionName = providerFunctionName,
                        providerClassName = providerClassName,
                    )
                    providedBindings[binding] = providedBinding
                }
            }
        }

        // Step 5.2: Collect all requested bindings, grouped by requester's FQN
        for ((moduleKey, requesterAnnotations) in bindingRequestersByModuleKey) {
            val localBindingById = localBindingsByModuleKey.getValue(moduleKey)
            for (requesterAnnotation in requesterAnnotations) {
                val requesterQualifiedName = requesterAnnotation.arguments[0].value as String
                val fields = requesterAnnotation.arguments[1].value as List<KSAnnotation>
                val requested = requestedBindings.getOrPut(requesterQualifiedName) {
                    ArrayList(fields.size)
                }
                for (field in fields) {
                    val bindingId = field.arguments[0].value as Int
                    val fieldName = field.arguments[1].value as String
                    val binding = localBindingById.getValue(bindingId)
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

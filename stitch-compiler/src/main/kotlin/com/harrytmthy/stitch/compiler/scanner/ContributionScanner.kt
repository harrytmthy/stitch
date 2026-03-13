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
import com.harrytmthy.stitch.annotations.Contribute
import com.harrytmthy.stitch.compiler.StitchSymbolProcessor.Companion.GENERATED_PACKAGE_NAME
import com.harrytmthy.stitch.compiler.consts.BindingKind
import com.harrytmthy.stitch.compiler.model.BindingDeclaration
import com.harrytmthy.stitch.compiler.model.ContributionScanResult
import com.harrytmthy.stitch.compiler.model.LocalScanResult
import com.harrytmthy.stitch.compiler.model.ProvidedBinding
import com.harrytmthy.stitch.compiler.model.Qualifier
import com.harrytmthy.stitch.compiler.model.RequestedBinding
import com.harrytmthy.stitch.compiler.model.Scope
import com.harrytmthy.stitch.compiler.utils.StitchErrorLogger

class ContributionScanner(
    private val resolver: Resolver,
    private val logger: StitchErrorLogger,
    private val moduleKey: String,
    private val scanResult: LocalScanResult,
) {

    @OptIn(KspExperimental::class)
    @Suppress("UNCHECKED_CAST")
    fun scan(): ContributionScanResult? {
        // Step 1: Collect all bindings and scopes from the aggregator
        val providedBindings = HashMap(scanResult.providedBindings)
        val requestedBindingsByModuleKey = HashMap<String, Map<String, List<RequestedBinding>>>()
        requestedBindingsByModuleKey[moduleKey] = HashMap(scanResult.requestedBindingsByClass)
        val customScopeByCanonicalName = HashMap<String, Scope.Custom>(scanResult.customScopeByCanonicalName)
        val scopeDependencies = HashMap<Scope, Scope>(scanResult.scopeDependencies)

        // Step 2: Collect all bindings and scopes from the contributors
        val contributedBindings = ArrayList<BindingDeclaration>()
        val contributedDependencies = ArrayList<List<Int>>() // Flattened indices, NOT bindingId
        for (declaration in resolver.getDeclarationsFromPackage(GENERATED_PACKAGE_NAME)) {
            val annotation = declaration.annotations
                .find { it.shortName.asString() == Contribute::class.simpleName }
                ?: continue
            val moduleKey = annotation.arguments[0].value as String
            val bindingAnnotations = annotation.arguments[1].value as List<KSAnnotation>
            val requesterAnnotations = annotation.arguments[2].value as List<KSAnnotation>
            val scopeAnnotations = annotation.arguments[3].value as List<KSAnnotation>

            // Step 2.1: Collect all provided + requested bindings from the contributors
            val lastBindingIndex = contributedBindings.lastIndex
            for (bindingAnnotation in bindingAnnotations) {
                val id = bindingAnnotation.arguments[0].value as Int
                val type = bindingAnnotation.arguments[1].value as String
                val qualifier = Qualifier.of(bindingAnnotation.arguments[2].value as String)
                val scope = Scope.of(bindingAnnotation.arguments[3].value as String)
                val location = bindingAnnotation.arguments[4].value as String
                val kind = bindingAnnotation.arguments[5].value as Int
                val providerPackageName = bindingAnnotation.arguments[6].value as String
                val providerFunctionName = bindingAnnotation.arguments[7].value as String
                val providerClassName = bindingAnnotation.arguments[8].value as String
                val dependsOn = bindingAnnotation.arguments[9].value as List<Int>
                val binding = BindingDeclaration(type, qualifier, location)
                contributedBindings.add(binding)
                contributedDependencies += dependsOn.map {
                    lastBindingIndex + it // Converts bindingId to contributedBindings's index
                }
                if (kind != BindingKind.REQUESTED) {
                    providedBindings[binding]?.let {
                        duplicateBindingError(it)
                        continue
                    }
                    val providedBinding = ProvidedBinding(
                        type = type,
                        qualifier = qualifier,
                        scope = scope,
                        location = location,
                        kind = kind,
                        providerPackageName = providerPackageName,
                        providerFunctionName = providerFunctionName,
                        providerClassName = providerClassName,
                        moduleKey = moduleKey,
                    )
                    providedBindings[binding] = providedBinding
                }
            }

            // Step 2.2: Collect all requesters, grouped by moduleKey
            val requestedBindingsByRequester = HashMap<String, List<RequestedBinding>>()
            for (requesterAnnotation in requesterAnnotations) {
                val requesterQualifiedName = requesterAnnotation.arguments[0].value as String
                val fields = requesterAnnotation.arguments[1].value as List<KSAnnotation>
                val requestedBindings = ArrayList<RequestedBinding>(fields.size)
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
                    requestedBindings.add(requestedBinding)
                    requestedBindingsByRequester[requesterQualifiedName] = requestedBindings
                }
            }
            requestedBindingsByModuleKey[moduleKey] = requestedBindingsByRequester

            // Step 2.3: Collect all scopes
            val localScopes = ArrayList<Scope.Custom>(scopeAnnotations.size)
            val localScopeDependencyIndices = ArrayList<Int>(scopeAnnotations.size)
            for (scopeAnnotation in scopeAnnotations) {
                val id = scopeAnnotation.arguments[0].value as Int
                val canonicalName = scopeAnnotation.arguments[1].value as String
                val qualifiedName = scopeAnnotation.arguments[2].value as String
                val location = scopeAnnotation.arguments[3].value as String
                val dependsOn = scopeAnnotation.arguments[4].value as Int
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
                val registeredScope = Scope.Custom(canonicalName, qualifiedName, location)
                customScopeByCanonicalName[canonicalName] = registeredScope
                localScopes.add(registeredScope)
                localScopeDependencyIndices.add(dependsOn - 1) // -1 since ID starts from 1
            }

            // Step 2.4: Collect scope dependencies
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
        if (logger.hasError) {
            return null
        }

        // Step 3: Ensure all requested bindings are actually provided
        for (requestedBindingsByRequester in requestedBindingsByModuleKey.values) {
            for (requestedBindings in requestedBindingsByRequester.values) {
                for (requestedBinding in requestedBindings) {
                    if (requestedBinding !in providedBindings) {
                        missingBindingError(requestedBinding)
                    }
                }
            }
        }
        if (logger.hasError) {
            return null
        }

        // Step 4: Build binding edges
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
            requestedBindingsByModuleKey,
            customScopeByCanonicalName,
            scopeDependencies,
        )
    }

    private fun duplicateBindingError(existing: ProvidedBinding) {
        logger.error(
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

    private fun missingBindingError(binding: BindingDeclaration) {
        logger.error(
            message = buildString {
                append("Binding with type '${binding.type}'")
                binding.qualifier?.let { append(" (qualifier: $it)") }
                append(" is never provided, but requested in ${binding.location}")
            },
        )
    }
}

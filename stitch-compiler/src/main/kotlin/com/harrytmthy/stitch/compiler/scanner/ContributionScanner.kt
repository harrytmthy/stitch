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
import com.harrytmthy.stitch.compiler.model.ProvidedBinding
import com.harrytmthy.stitch.compiler.model.Qualifier
import com.harrytmthy.stitch.compiler.model.RequestedBinding
import com.harrytmthy.stitch.compiler.model.Scope

class ContributionScanner(
    private val resolver: Resolver,
    private val moduleKey: String,
    private val scanResult: LocalScanResult,
) {

    @OptIn(KspExperimental::class)
    @Suppress("UNCHECKED_CAST")
    fun scan() {
        val providedBindings = HashMap(scanResult.providedBindings)
        val requestedBindingsByModuleKey = HashMap<String, Map<String, List<RequestedBinding>>>()
        requestedBindingsByModuleKey[moduleKey] = HashMap(scanResult.requestedBindingsByClass)
        for (declaration in resolver.getDeclarationsFromPackage(GENERATED_PACKAGE_NAME)) {
            val annotation = declaration.annotations
                .find { it.shortName.asString() == Contribute::class.simpleName }
                ?: continue
            val moduleKey = annotation.arguments[0].value as String
            val bindingAnnotations = annotation.arguments[1].value as List<KSAnnotation>
            val requesterAnnotations = annotation.arguments[2].value as List<KSAnnotation>
            val scopeAnnotations = annotation.arguments[3].value as List<KSAnnotation>

            // Step 1: Collect all provided + requested bindings from the aggregator & contributors
            val localBindings = ArrayList<BindingDeclaration>()
            for (bindingAnnotation in bindingAnnotations) {
                val id = bindingAnnotation.arguments[0].value as Int
                val type = bindingAnnotation.arguments[1].value as String
                val qualifier = Qualifier.of(bindingAnnotation.arguments[2].value as String)
                val scope = bindingAnnotation.arguments[3].value as String
                val location = bindingAnnotation.arguments[4].value as String
                val kind = bindingAnnotation.arguments[5].value as Int
                val providerPackageName = bindingAnnotation.arguments[6].value as String
                val providerFunctionName = bindingAnnotation.arguments[7].value as String
                val providerClassName = bindingAnnotation.arguments[8].value as String
                val dependsOn = bindingAnnotation.arguments[9].value as List<Int>
                val bindingKey = BindingDeclaration(type, qualifier, location)
                localBindings.add(bindingKey)
                if (kind != BindingKind.REQUESTED) {
                    // ProvidedBinding path
                    if (bindingKey in providedBindings) {
                        // TODO(#120): Throw duplicate binding exception
                    }
                    val scope = when (scope) {
                        "Singleton" -> Scope.Singleton
                        "" -> null
                        else -> Scope.Custom(scope)
                    }
                    val binding = ProvidedBinding(
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
                    providedBindings[bindingKey] = binding
                }
            }

            // Step 2: Collect all requesters, grouped by moduleKey
            val requestedBindingsByRequester = HashMap<String, List<RequestedBinding>>()
            for (requesterAnnotation in requesterAnnotations) {
                val requesterQualifiedName = requesterAnnotation.arguments[0].value as String
                val fields = requesterAnnotation.arguments[1].value as List<KSAnnotation>
                val requestedBindings = ArrayList<RequestedBinding>(fields.size)
                for (field in fields) {
                    val bindingId = field.arguments[0].value as Int
                    val fieldName = field.arguments[1].value as String
                    val binding = localBindings[bindingId - 1] // -1 since ID starts from 1
                    if (binding !in providedBindings) {
                        // TODO(#120): Throw missing binding exception
                    }
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

            // Step 3: Collect all scopes
            for (scopeAnnotation in scopeAnnotations) {
                val id = scopeAnnotation.arguments[0].value as Int
                val canonicalName = scopeAnnotation.arguments[1].value as String
                val qualifiedName = scopeAnnotation.arguments[2].value as String
                val location = scopeAnnotation.arguments[3].value as String
                val dependsOn = scopeAnnotation.arguments[4].value as Int
            }

            // Step 4: Build binding edges
            // TODO(#120): Implement this step

            // Step 5: Build scope edges
            // TODO(#126): Implement this step
        }
    }
}

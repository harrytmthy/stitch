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
import com.harrytmthy.stitch.compiler.Registry
import com.harrytmthy.stitch.compiler.StitchSymbolProcessor.Companion.GENERATED_PACKAGE_NAME

class ContributionScanner(
    private val resolver: Resolver,
    private val registry: Registry,
) {

    @OptIn(KspExperimental::class)
    @Suppress("UNCHECKED_CAST")
    fun scan() {
        for (declaration in resolver.getDeclarationsFromPackage(GENERATED_PACKAGE_NAME)) {
            val annotation = declaration.annotations
                .find { it.shortName.asString() == Contribute::class.simpleName }
                ?: continue
            val moduleKey = annotation.arguments[0].value as String
            val bindingAnnotations = annotation.arguments[1].value as List<KSAnnotation>
            for (bindingAnnotation in bindingAnnotations) {
                val id = bindingAnnotation.arguments[0].value as Int
                val type = bindingAnnotation.arguments[1].value as String
                val qualifier = bindingAnnotation.arguments[2].value as String
                val scope = bindingAnnotation.arguments[3].value as String
                val location = bindingAnnotation.arguments[4].value as String
                val alias = bindingAnnotation.arguments[5].value as Boolean
                val dependsOn = bindingAnnotation.arguments[6].value as List<Int>

                // TODO: For provided bindings, check for duplicates then collect it under a new ID.

                // TODO: For requested bindings, put it into a separate collection.
            }

            // TODO: Traverse the requested + 'local missing' bindings to ensure they are provided.

            // TODO: Traverse the provided bindings to build graph edges using dependsOn.

            val requesterAnnotations = annotation.arguments[2].value as List<KSAnnotation>
            for (requesterAnnotation in requesterAnnotations) {
                val requesterQualifiedName = requesterAnnotation.arguments[0].value as String
                val fields = requesterAnnotation.arguments[1].value as List<KSAnnotation>
                for (field in fields) {
                    val bindingId = field.arguments[0].value as Int
                    val fieldName = field.arguments[1].value as String

                    // TODO: Collect all fields, grouped by requesterQualifiedName.
                    //       This collection will be used to generate `inject(target: T)`.
                }

                // TODO: Collect all requesters, grouped by moduleKey
                //       This collection will be used to generate `Part<ModuleKey> class`
            }

            val scopeAnnotations = annotation.arguments[3].value as List<KSAnnotation>
            for (scopeAnnotation in scopeAnnotations) {
                val id = scopeAnnotation.arguments[0].value as Int
                val canonicalName = scopeAnnotation.arguments[1].value as String
                val qualifiedName = scopeAnnotation.arguments[2].value as String
                val location = scopeAnnotation.arguments[3].value as String
                val dependsOn = scopeAnnotation.arguments[4].value as Int

                // TODO: Build scope edges using dependsOn
            }
        }
    }
}

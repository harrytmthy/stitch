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
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.harrytmthy.stitch.compiler.scanner.ContributionScanner
import com.harrytmthy.stitch.compiler.scanner.LocalAnnotationScanner
import java.security.MessageDigest

/**
 * KSP symbol processor for Stitch dependency injection code generation.
 *
 * This processor scans for @Module classes, @Provides methods, and @Inject constructors/fields,
 * then generates DI component and injector objects for compile-time dependency resolution.
 */
class StitchSymbolProcessor(private val environment: SymbolProcessorEnvironment) : SymbolProcessor {

    private var processed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Only process once per compilation
        if (processed) {
            return emptyList()
        }
        val logger = environment.logger
        logger.info("Stitch: Starting dependency injection code generation")
        try {
            val moduleName = getOption("stitch.moduleName")
            val moduleKey = moduleName.toModuleKey()
            val registry = Registry()
            LocalAnnotationScanner(resolver, moduleKey, registry).scan()
            if (!registry.isAggregator) {
                ContributionCodeGenerator(environment.codeGenerator)
                    .generate(moduleName, moduleKey, registry)
            } else {
                ContributionScanner(resolver, registry).scan()
                // TODO: Add scope graph builder
                // TODO: Add binding graph builder
                // TODO: Add codegen for InjectorScope's implementation
            }
            processed = true
        } catch (e: StitchProcessingException) {
            e.message?.let { logger.error(it, e.symbol) }
            throw e
        }
        return emptyList()
    }

    private fun getOption(name: String): String =
        environment.options[name] ?: throw StitchProcessingException(
            "Missing KSP option '$name'. Configure via ksp { arg(...) } or apply 'io.github.harrytmthy.stitch' plugin.",
        )

    /**
     * Produces a stable 6-byte hex key used to disambiguate generated contribution names
     * for modules that normalize to the same PascalCase name.
     */
    private fun String.toModuleKey(): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
        return digest.take(6).joinToString("") { "%02X".format(it.toInt() and 0xFF) }
    }

    companion object {
        const val GENERATED_PACKAGE_NAME = "com.harrytmthy.stitch.generated"
    }
}

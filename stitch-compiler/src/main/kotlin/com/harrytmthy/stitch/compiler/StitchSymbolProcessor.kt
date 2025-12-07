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

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated

/**
 * KSP symbol processor for Stitch dependency injection code generation.
 *
 * This processor scans for @Module classes, @Provides methods, and @Inject constructors/fields,
 * then generates DI component and injector objects for compile-time dependency resolution.
 */
class StitchSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: StitchLogger,
) : SymbolProcessor {

    private var processed = false

    override fun process(resolver: Resolver): List<KSAnnotated> {
        // Only process once per compilation
        if (processed) {
            return emptyList()
        }
        processed = true

        logger.info("Stitch: Starting dependency injection code generation")
        try {
            AnnotationScanner(resolver).scan()

            // Build scope graph first
            val scopeGraph = ScopeGraphBuilder().buildScopeGraph(resolver)
            ensureNoError()

            // Scan for @Module classes and @Inject constructors
            val scanResult = ModuleScanner(logger, scopeGraph).scanAll(resolver)
            ensureNoError()
            if (scanResult.modules.isEmpty() && scanResult.injectables.isEmpty()) {
                logger.info("Stitch: No @Module or @Inject found, skipping code generation")
                return emptyList()
            }

            // Build dependency graph and validate
            val dependencyGraph = DependencyGraphBuilder(logger).buildGraph(scanResult, scopeGraph)
            ensureNoError()

            // Generate DI component and injector objects
            StitchCodeGenerator(codeGenerator, logger).generateComponentAndInjector(
                dependencyGraph.nodes,
                dependencyGraph.registry,
                scanResult.fieldInjectors,
                scopeGraph,
            )
            ensureNoError()
            logger.info("Stitch: Code generation completed successfully")
        } catch (e: StitchProcessingException) {
            e.message?.let { logger.error(it, e.symbol) }
        }
        return emptyList()
    }

    private fun ensureNoError() {
        if (logger.hasError) {
            throw StitchProcessingException()
        }
    }
}

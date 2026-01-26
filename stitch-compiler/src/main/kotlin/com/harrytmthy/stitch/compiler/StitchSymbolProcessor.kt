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
        processed = true

        val logger = environment.logger
        logger.info("Stitch: Starting dependency injection code generation")
        try {
            val registry = Registry()
            AnnotationScanner(resolver, logger, registry).scan()

            if (!registry.isAggregator) {
                val moduleName = getOption("stitch.moduleName")
                val moduleKey = getOption("stitch.moduleKey")
                ContributionCodeGenerator(environment.codeGenerator).generate(
                    packageSuffix = moduleKey,
                    fileName = "Generated${moduleName}Contribution",
                    registry = registry,
                )
            }
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
}

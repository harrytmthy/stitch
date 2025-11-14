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
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Modifier

/**
 * Scans for @Module annotated classes and extracts dependency information.
 */
class ModuleScanner(private val logger: KSPLogger) {

    private val namedQualifierCache = HashMap<KSAnnotated, QualifierInfo.Named>()

    /**
     * Scans for @Module classes, @Inject constructors, and classes with field injection.
     */
    fun scanAll(resolver: Resolver): ScanResult {
        cacheQualifiers(resolver)

        val modules = scanModules(resolver)
        val injectScan = scanInjectables(resolver)

        return ScanResult(
            modules = modules,
            injectables = injectScan.injectables,
            fieldInjectors = injectScan.fieldInjectors,
        )
    }

    private fun cacheQualifiers(resolver: Resolver) {
        fun collectNamed(annotationName: String) {
            resolver.getSymbolsWithAnnotation(annotationName).forEach { symbol ->
                for (annotation in symbol.annotations) {
                    annotation.annotationType.resolve().declaration.qualifiedName?.asString()
                        ?.takeIf { it == annotationName }
                        ?.let {
                            val value = annotation.arguments.first().value as String
                            namedQualifierCache[symbol] = QualifierInfo.Named(value)
                            break
                        }
                }
            }
        }

        collectNamed(STITCH_NAMED)
        collectNamed(JAVAX_NAMED)
    }

    /**
     * Scans for @Module classes and their @Provides methods.
     */
    private fun scanModules(resolver: Resolver): List<ModuleInfo> =
        resolver.getSymbolsWithAnnotation(STITCH_MODULE)
            .mapNotNull {
                if (it !is KSClassDeclaration) {
                    return@mapNotNull null
                }
                scanModule(it)
            }.toList()

    /**
     * Single combined scan for all @Inject usage (Stitch + javax.inject).
     */
    private fun scanInjectables(resolver: Resolver): InjectScanResult {
        val accByClass = LinkedHashMap<KSClassDeclaration, ConstructorInjectionAccumulator>()
        val seenSymbols = HashSet<KSAnnotated>()

        fun recordInjectAnnotations(qualifiedName: String) {
            resolver.getSymbolsWithAnnotation(qualifiedName).forEach { symbol ->
                // Avoid processing the same symbol twice if it somehow carries both annotations.
                if (!seenSymbols.add(symbol)) {
                    return@forEach
                }

                when (symbol) {
                    is KSFunctionDeclaration -> {
                        if (!symbol.isConstructor()) return@forEach
                        val classDeclaration = symbol.parentDeclaration as? KSClassDeclaration ?: return@forEach
                        val acc = accByClass.getOrPut(classDeclaration) {
                            ConstructorInjectionAccumulator(classDeclaration)
                        }
                        val existing = acc.injectConstructor
                        if (existing != null && existing != symbol) {
                            val className = classDeclaration.qualifiedName?.asString()
                                ?: classDeclaration.simpleName.asString()
                            logger.error(
                                "Multiple @Inject constructors found in $className. Only one @Inject constructor is allowed.",
                                classDeclaration,
                            )
                            acc.hasConstructorError = true
                        } else {
                            acc.injectConstructor = symbol
                        }
                    }
                    is KSPropertyDeclaration -> {
                        val classDeclaration = symbol.parentDeclaration as? KSClassDeclaration ?: return@forEach
                        accByClass.getOrPut(classDeclaration) {
                            ConstructorInjectionAccumulator(classDeclaration)
                        }.fieldProps.add(symbol)
                    }
                }
            }
        }

        // Collect both Stitch and javax @Inject usage.
        recordInjectAnnotations(STITCH_INJECT)
        recordInjectAnnotations(JAVAX_INJECT)

        val injectables = ArrayList<InjectableClassInfo>()
        val fieldInjectors = ArrayList<FieldInjectorInfo>()

        accByClass.values.forEach { accumulator ->
            val classDeclaration = accumulator.classDeclaration
            val className = classDeclaration.qualifiedName?.asString() ?: classDeclaration.simpleName.asString()
            val ctor = accumulator.injectConstructor
            val fields = accumulator.fieldProps

            // If this class has no ctor and no fields, nothing to do.
            if (ctor == null && fields.isEmpty()) {
                return@forEach
            }

            // Interfaces and annotation classes cannot participate in injection.
            if (classDeclaration.classKind == ClassKind.INTERFACE) {
                logger.error("@Inject cannot be used on interfaces: $className", classDeclaration)
                return@forEach
            }
            if (classDeclaration.classKind == ClassKind.ANNOTATION_CLASS) {
                logger.error("@Inject cannot be used on annotation classes: $className", classDeclaration)
                return@forEach
            }

            // Abstract classes cannot have @Inject constructors (no instantiation).
            if (ctor != null && classDeclaration.modifiers.contains(Modifier.ABSTRACT)) {
                logger.error("@Inject cannot be used on abstract classes: $className", classDeclaration)
                return@forEach
            }

            // If constructor already marked invalid, skip this class entirely.
            if (accumulator.hasConstructorError) {
                return@forEach
            }

            // Single validation pass for all @Inject fields in this class.
            val injectableFields = ArrayList<InjectableFieldInfo>()
            fields.forEach { property ->
                val fieldName = property.simpleName.asString()

                // Field must be mutable
                if (!property.isMutable) {
                    logger.error(
                        "@Inject field '$fieldName' must be mutable (var or lateinit var).",
                        property,
                    )
                    return@forEach
                }

                // Field must be accessible (not private)
                if (property.modifiers.contains(Modifier.PRIVATE)) {
                    logger.error(
                        "@Inject field '$fieldName' cannot be private. " +
                            "Generated code needs to access this field.",
                        property,
                    )
                    return@forEach
                }

                val fieldType = property.type.resolve()
                val qualifier = namedQualifierCache[property]
                injectableFields += InjectableFieldInfo(fieldName, fieldType, qualifier)
            }

            // 1) Build InjectableClassInfo for constructor-injected classes (if any).
            if (ctor != null) {
                val constructorParameters = ctor.parameters.map { param ->
                    ParameterInfo(
                        name = param.name?.asString() ?: "",
                        type = param.type.resolve(),
                        qualifier = namedQualifierCache[param],
                    )
                }

                val isSingleton = classDeclaration.hasAnnotation(STITCH_SINGLETON) ||
                    classDeclaration.hasAnnotation(JAVAX_SINGLETON)

                val qualifier = namedQualifierCache[classDeclaration]
                injectables += InjectableClassInfo(
                    classDeclaration = classDeclaration,
                    constructor = ctor,
                    constructorParameters = constructorParameters,
                    injectableFields = injectableFields,
                    isSingleton = isSingleton,
                    qualifier = qualifier,
                )
            }

            // 2) Build FieldInjectorInfo for field injection, independent from ctor presence.
            if (injectableFields.isNotEmpty()) {
                fieldInjectors += FieldInjectorInfo(classDeclaration, injectableFields)
            }
        }

        return InjectScanResult(injectables, fieldInjectors)
    }

    private fun scanModule(moduleClass: KSClassDeclaration): ModuleInfo? {
        val provides = moduleClass.getAllFunctions()
            .mapNotNull {
                if (!it.hasAnnotation(STITCH_PROVIDES)) {
                    return@mapNotNull null
                }
                scanProvider(it)
            }.toList()

        if (provides.isEmpty()) {
            logger.warn("Stitch: Module ${moduleClass.simpleName.asString()} has no @Provides methods")
            return null
        }

        return ModuleInfo(moduleClass, provides)
    }

    private fun scanProvider(function: KSFunctionDeclaration): ProvidesInfo? {
        val returnType = function.returnType?.resolve()
        if (returnType == null) {
            logger.error("@Provides method ${function.simpleName.asString()} has no return type", function)
            return null
        }

        val isSingleton = function.hasAnnotation(STITCH_SINGLETON) ||
            function.hasAnnotation(JAVAX_SINGLETON)

        val qualifier = namedQualifierCache[function]

        val parameters = function.parameters.map { param ->
            ParameterInfo(
                name = param.name?.asString() ?: "",
                type = param.type.resolve(),
                qualifier = namedQualifierCache[param],
            )
        }

        return ProvidesInfo(
            declaration = function,
            returnType = returnType,
            parameters = parameters,
            isSingleton = isSingleton,
            qualifier = qualifier,
        )
    }

    private fun KSAnnotated.hasAnnotation(qualifiedName: String): Boolean =
        annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName
        }

    /**
     * Aggregated scan result for all @Inject usage.
     */
    private data class InjectScanResult(
        val injectables: List<InjectableClassInfo>,
        val fieldInjectors: List<FieldInjectorInfo>,
    )

    /**
     * Internal accumulator for all injection-related info per class.
     */
    private data class ConstructorInjectionAccumulator(
        val classDeclaration: KSClassDeclaration,
        var injectConstructor: KSFunctionDeclaration? = null,
        val fieldProps: ArrayList<KSPropertyDeclaration> = ArrayList(),
        var hasConstructorError: Boolean = false,
    )

    private companion object {
        const val STITCH_MODULE = "com.harrytmthy.stitch.annotations.Module"
        const val STITCH_PROVIDES = "com.harrytmthy.stitch.annotations.Provides"
        const val STITCH_INJECT = "com.harrytmthy.stitch.annotations.Inject"
        const val STITCH_SINGLETON = "com.harrytmthy.stitch.annotations.Singleton"
        const val STITCH_NAMED = "com.harrytmthy.stitch.annotations.Named"
        const val JAVAX_INJECT = "javax.inject.Inject"
        const val JAVAX_SINGLETON = "javax.inject.Singleton"
        const val JAVAX_NAMED = "javax.inject.Named"
    }
}

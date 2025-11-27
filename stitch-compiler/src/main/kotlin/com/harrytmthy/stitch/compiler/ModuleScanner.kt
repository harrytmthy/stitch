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
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

/**
 * Scans for @Module annotated classes and extracts dependency information.
 */
class ModuleScanner(private val logger: KSPLogger) {

    private val namedQualifierCache = HashMap<KSAnnotated, QualifierInfo.Named>()

    /**
     * Scans for @Module classes, @Inject constructors, and classes with field injection.
     */
    fun scanAll(resolver: Resolver, scopeGraph: ScopeGraph): ScanResult {
        cacheQualifiers(resolver)

        val modules = scanModules(resolver)
        val injectScan = scanInjectables(resolver, scopeGraph)

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
    private fun scanInjectables(resolver: Resolver, scopeGraph: ScopeGraph): InjectScanResult {
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
            val injectableFields = ArrayList<FieldInfo>()
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
                val scopeAnnotation = property.getScopeAnnotation()
                injectableFields += FieldInfo(
                    type = fieldType,
                    qualifier = qualifier,
                    name = fieldName,
                    scopeAnnotation = scopeAnnotation,
                )
            }

            // 1) Build InjectableClassInfo for constructor-injected classes (if any).
            if (ctor != null) {
                val constructorParameters = ctor.parameters.map { param ->
                    FieldInfo(
                        type = param.type.resolve(),
                        qualifier = namedQualifierCache[param],
                        name = param.name?.asString() ?: "",
                    )
                }

                val isSingleton = classDeclaration.hasAnnotation(STITCH_SINGLETON) ||
                    classDeclaration.hasAnnotation(JAVAX_SINGLETON)

                val scopeAnnotation = classDeclaration.getScopeAnnotation()

                // Validation: cannot be both @Singleton and scoped
                if (isSingleton && scopeAnnotation != null) {
                    logger.error(
                        "@Inject class ${classDeclaration.simpleName.asString()} cannot be both @Singleton and scoped " +
                            "(${scopeAnnotation.declaration.simpleName.asString()})",
                        classDeclaration,
                    )
                    return@forEach
                }

                val qualifier = namedQualifierCache[classDeclaration]

                // Extract @Binds(aliases = [...]) from class
                val aliases = extractAliases(classDeclaration)

                injectables += InjectableClassInfo(
                    classDeclaration = classDeclaration,
                    constructor = ctor,
                    constructorParameters = constructorParameters,
                    injectableFields = injectableFields,
                    isSingleton = isSingleton,
                    qualifier = qualifier,
                    aliases = aliases,
                    scopeAnnotation = scopeAnnotation,
                )
            }

            // 2) Build FieldInjectorInfo for field injection, independent from ctor presence.
            if (injectableFields.isNotEmpty()) {
                val scopeUsage = analyzeClassScopeUsage(injectableFields, scopeGraph, classDeclaration)
                fieldInjectors += FieldInjectorInfo(classDeclaration, injectableFields, scopeUsage)
            }
        }

        return InjectScanResult(injectables, fieldInjectors)
    }

    private fun scanModule(moduleClass: KSClassDeclaration): ModuleInfo? {
        // Get functions from both declarations (for interfaces) and getAllFunctions (for classes)
        val declaredFunctions = moduleClass.declarations.filterIsInstance<KSFunctionDeclaration>()
        val allFunctions = (declaredFunctions + moduleClass.getAllFunctions()).distinct()

        val provides = allFunctions
            .mapNotNull {
                if (!it.hasAnnotation(STITCH_PROVIDES)) {
                    return@mapNotNull null
                }
                scanProvider(it)
            }.toList()

        val binds = allFunctions
            .mapNotNull {
                if (!it.hasAnnotation(STITCH_BINDS)) {
                    return@mapNotNull null
                }
                scanBinds(it)
            }.toList()

        if (provides.isEmpty() && binds.isEmpty()) {
            logger.warn("Stitch: Module ${moduleClass.simpleName.asString()} has no @Provides or @Binds methods")
            return null
        }

        return ModuleInfo(moduleClass, provides, binds)
    }

    private fun scanProvider(function: KSFunctionDeclaration): ProvidesInfo? {
        val returnType = function.returnType?.resolve()
        if (returnType == null) {
            logger.error("@Provides method ${function.simpleName.asString()} has no return type", function)
            return null
        }

        val isSingleton = function.hasAnnotation(STITCH_SINGLETON) ||
            function.hasAnnotation(JAVAX_SINGLETON)

        val scopeAnnotation = function.getScopeAnnotation()

        // Validation: cannot be both @Singleton and scoped
        if (isSingleton && scopeAnnotation != null) {
            logger.error(
                "@Provides method ${function.simpleName.asString()} cannot be both @Singleton and scoped " +
                    "(${scopeAnnotation.declaration.simpleName.asString()})",
                function,
            )
            return null
        }

        val qualifier = namedQualifierCache[function]

        val parameters = function.parameters.map { param ->
            FieldInfo(
                type = param.type.resolve(),
                qualifier = namedQualifierCache[param],
                name = param.name?.asString() ?: "",
            )
        }

        // Extract @Binds(aliases = [...]) from @Provides method
        val aliases = extractAliases(function)

        return ProvidesInfo(
            declaration = function,
            returnType = returnType,
            parameters = parameters,
            isSingleton = isSingleton,
            qualifier = qualifier,
            aliases = aliases,
            scopeAnnotation = scopeAnnotation,
        )
    }

    private fun scanBinds(function: KSFunctionDeclaration): BindsInfo? {
        val functionName = function.simpleName.asString()

        // Validate: @Binds methods must be abstract
        if (!function.isAbstract) {
            logger.error("@Binds method $functionName must be abstract", function)
            return null
        }

        // Validate: Must have exactly one parameter (implementation type)
        if (function.parameters.size != 1) {
            logger.error(
                "@Binds method $functionName must have exactly one parameter (the implementation type). " +
                    "Found ${function.parameters.size} parameters.",
                function,
            )
            return null
        }

        // Get return type (alias type)
        val aliasType = function.returnType?.resolve()
        if (aliasType == null) {
            logger.error("@Binds method $functionName has no return type", function)
            return null
        }

        // Get implementation type (parameter type)
        val implementationType = function.parameters.first().type.resolve()

        // TODO: Validate that alias type is a supertype of implementation type
        // KSP doesn't have a reliable isAssignableTo API, so we skip validation for now

        val isSingleton = function.hasAnnotation(STITCH_SINGLETON) ||
            function.hasAnnotation(JAVAX_SINGLETON)

        val qualifier = namedQualifierCache[function]

        return BindsInfo(
            declaration = function,
            implementationType = implementationType,
            aliasType = aliasType,
            isSingleton = isSingleton,
            qualifier = qualifier,
        )
    }

    /**
     * Extracts aliases from @Binds(aliases = [...]) annotation.
     */
    private fun extractAliases(annotated: KSAnnotated): List<KSType> {
        // Find @Binds annotation
        val bindsAnnotation = annotated.annotations.find { annotation ->
            val qualifiedName = annotation.annotationType.resolve().declaration.qualifiedName?.asString()
            val shortName = annotation.shortName.asString()
            qualifiedName == STITCH_BINDS || (qualifiedName == null && shortName == "Binds")
        } ?: return emptyList()

        // Find the "aliases" argument
        val aliasesArg = bindsAnnotation.arguments.find { it.name?.asString() == "aliases" }
            ?: return emptyList()

        // The value is an ArrayList<KSType> (KSP has already resolved the class references)
        @Suppress("UNCHECKED_CAST")
        val aliasesList = aliasesArg.value as? ArrayList<*> ?: return emptyList()

        // Each element is a KSType
        return aliasesList.mapNotNull { it as? KSType }
    }

    private fun KSAnnotated.hasAnnotation(qualifiedName: String): Boolean {
        val shortName = qualifiedName.substringAfterLast('.')
        return annotations.any {
            val resolved = it.annotationType.resolve()
            val qual = resolved.declaration.qualifiedName?.asString()
            // Try qualified name first, fallback to short name if qual name is null
            qual == qualifiedName || (qual == null && it.shortName.asString() == shortName)
        }
    }

    /**
     * Detects if this element has a scope annotation (annotated with @Scope meta-annotation).
     * Returns the scope annotation type if found, null otherwise.
     */
    private fun KSAnnotated.getScopeAnnotation(): KSType? {
        return annotations.firstOrNull { annotation ->
            val annotationDeclaration = annotation.annotationType.resolve().declaration
            // Check if this annotation is itself annotated with @Scope
            annotationDeclaration.annotations.any { metaAnnotation ->
                val qualifiedName = metaAnnotation.annotationType.resolve().declaration.qualifiedName?.asString()
                qualifiedName == STITCH_SCOPE
            }
        }?.annotationType?.resolve()
    }

    /**
     * Analyzes scope usage for a class with field injection.
     * For relaxed multi-component inject, field injectors are scope-neutral.
     * Each component will inject only fields it can resolve from its vantage point.
     */
    private fun analyzeClassScopeUsage(
        injectableFields: List<FieldInfo>,
        scopeGraph: ScopeGraph,
        classDeclaration: KSClassDeclaration,
    ): ClassScopeUsage {
        // For relaxed multi-component inject, field injectors are scope-neutral.
        // We only care that bindings exist somewhere, not where the class "belongs".
        // Each component will inject only fields it can resolve from its vantage point.
        return ClassScopeUsage(
            deepestScope = null,
            usedScopes = emptySet(),
            ancestorPath = emptyList(),
        )
    }

    /**
     * Builds the ancestor path from a scope to root (Singleton).
     * Returns list of scopes from deepest to root, excluding Singleton.
     */
    private fun buildAncestorPath(scope: KSType, scopeGraph: ScopeGraph): List<KSType> {
        val path = mutableListOf<KSType>()
        var current: KSType? = scope

        while (current != null) {
            path.add(current)
            current = scopeGraph.scopes[current]?.dependsOn
        }

        return path // [FragmentScope, ActivityScope] (Singleton not included)
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
        const val STITCH_BINDS = "com.harrytmthy.stitch.annotations.Binds"
        const val STITCH_INJECT = "com.harrytmthy.stitch.annotations.Inject"
        const val STITCH_SINGLETON = "com.harrytmthy.stitch.annotations.Singleton"
        const val STITCH_NAMED = "com.harrytmthy.stitch.annotations.Named"
        const val STITCH_SCOPE = "com.harrytmthy.stitch.annotations.Scope"
        const val JAVAX_INJECT = "javax.inject.Inject"
        const val JAVAX_SINGLETON = "javax.inject.Singleton"
        const val JAVAX_NAMED = "javax.inject.Named"
    }
}

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

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Modifier

/**
 * Extension to check if a KSFunctionDeclaration is a constructor.
 */
private fun KSFunctionDeclaration.isConstructor(): Boolean {
    return this.simpleName.asString() == "<init>"
}

/**
 * Scans for @Module annotated classes and extracts dependency information.
 */
class ModuleScanner(private val logger: KSPLogger) {

    private companion object {
        // Stitch annotations
        const val STITCH_MODULE = "com.harrytmthy.stitch.annotations.Module"
        const val STITCH_PROVIDES = "com.harrytmthy.stitch.annotations.Provides"
        const val STITCH_INJECT = "com.harrytmthy.stitch.annotations.Inject"
        const val STITCH_SINGLETON = "com.harrytmthy.stitch.annotations.Singleton"
        const val STITCH_NAMED = "com.harrytmthy.stitch.annotations.Named"
        const val STITCH_QUALIFIER = "com.harrytmthy.stitch.annotations.Qualifier"
        const val STITCH_ENTRY_POINT = "com.harrytmthy.stitch.annotations.EntryPoint"

        // Dagger/javax.inject annotations
        const val DAGGER_MODULE = "dagger.Module"
        const val DAGGER_PROVIDES = "dagger.Provides"
        const val JAVAX_INJECT = "javax.inject.Inject"
        const val JAVAX_SINGLETON = "javax.inject.Singleton"
        const val JAVAX_NAMED = "javax.inject.Named"
        const val JAVAX_QUALIFIER = "javax.inject.Qualifier"
    }

    /**
     * Scans for @Module classes, @Inject constructors, and @EntryPoint classes.
     * Returns a ScanResult containing all three types of bindings.
     */
    fun scanAll(resolver: Resolver): ScanResult {
        val modules = scanModules(resolver)
        val injectables = scanInjectConstructors(resolver)
        val entryPoints = scanEntryPoints(resolver)
        return ScanResult(modules, injectables, entryPoints)
    }

    fun scanModules(resolver: Resolver): List<ModuleInfo> {
        val modules = mutableListOf<ModuleInfo>()

        // Scan for Stitch @Module
        val stitchModules = resolver.getSymbolsWithAnnotation(STITCH_MODULE)
            .filterIsInstance<KSClassDeclaration>()

        // Scan for Dagger @Module
        val daggerModules = resolver.getSymbolsWithAnnotation(DAGGER_MODULE)
            .filterIsInstance<KSClassDeclaration>()

        // Process both types
        (stitchModules + daggerModules).forEach { moduleClass ->
            val moduleInfo = scanModule(moduleClass)
            if (moduleInfo != null) {
                modules.add(moduleInfo)
            }
        }

        return modules
    }

    /**
     * Scans for @Inject annotated constructors across all classes.
     */
    private fun scanInjectConstructors(resolver: Resolver): List<InjectableClassInfo> {
        val injectables = mutableListOf<InjectableClassInfo>()

        // Scan for Stitch @Inject
        val stitchInject = resolver.getSymbolsWithAnnotation(STITCH_INJECT)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { it.isConstructor() }

        // Scan for javax.inject.Inject
        val javaxInject = resolver.getSymbolsWithAnnotation(JAVAX_INJECT)
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { it.isConstructor() }

        // Process both types
        (stitchInject + javaxInject).forEach { constructor ->
            val injectableInfo = scanInjectableClass(constructor)
            if (injectableInfo != null) {
                injectables.add(injectableInfo)
            }
        }

        return injectables
    }

    /**
     * Scans for @EntryPoint annotated classes.
     *
     * Entry points are classes that cannot use constructor injection (e.g. Activities,
     * Fragments) but need field injection via Stitch.inject(this).
     */
    private fun scanEntryPoints(resolver: Resolver): List<EntryPointInfo> {
        val entryPoints = mutableListOf<EntryPointInfo>()

        val entryPointClasses = resolver.getSymbolsWithAnnotation(STITCH_ENTRY_POINT)
            .filterIsInstance<KSClassDeclaration>()

        entryPointClasses.forEach { classDecl ->
            val entryPointInfo = scanEntryPointClass(classDecl)
            if (entryPointInfo != null) {
                entryPoints.add(entryPointInfo)
            }
        }

        return entryPoints
    }

    /**
     * Scans a single @EntryPoint class and extracts injectable fields.
     */
    private fun scanEntryPointClass(classDecl: KSClassDeclaration): EntryPointInfo? {
        val className = classDecl.qualifiedName?.asString() ?: classDecl.simpleName.asString()
        logger.info("Stitch: Scanning @EntryPoint class $className")

        // Validation: Check class kind
        if (classDecl.classKind == ClassKind.INTERFACE) {
            logger.error("@EntryPoint cannot be used on interfaces: $className", classDecl)
            return null
        }
        if (classDecl.classKind == ClassKind.ANNOTATION_CLASS) {
            logger.error("@EntryPoint cannot be used on annotation classes: $className", classDecl)
            return null
        }

        // Validation: Ensure class doesn't have @Inject constructor
        val hasInjectConstructor = classDecl.getAllFunctions()
            .filter { it.isConstructor() }
            .any { it.hasAnnotation(STITCH_INJECT) || it.hasAnnotation(JAVAX_INJECT) }

        if (hasInjectConstructor) {
            logger.error(
                "@EntryPoint class $className has @Inject constructor. " +
                    "Use either @Inject constructor OR @EntryPoint, not both.",
                classDecl,
            )
            return null
        }

        // Scan for @Inject fields
        val injectableFields = mutableListOf<InjectableFieldInfo>()

        classDecl.getAllProperties().forEach { property ->
            val hasInject = property.hasAnnotation(STITCH_INJECT) || property.hasAnnotation(JAVAX_INJECT)
            if (!hasInject) return@forEach

            val fieldName = property.simpleName.asString()

            // Validation: Field must be mutable
            if (property.isMutable == false) {
                logger.error(
                    "@Inject field '$fieldName' in @EntryPoint class must be mutable (var or lateinit var).",
                    property,
                )
                return@forEach
            }

            // Validation: Field must be accessible (not private)
            if (property.modifiers.contains(Modifier.PRIVATE)) {
                logger.error(
                    "@Inject field '$fieldName' in @EntryPoint class cannot be private. " +
                        "Generated code needs to access this field.",
                    property,
                )
                return@forEach
            }

            val fieldType = property.type.resolve()
            val qualifier = extractQualifier(property)

            injectableFields.add(
                InjectableFieldInfo(
                    name = fieldName,
                    type = fieldType,
                    qualifier = qualifier,
                ),
            )
        }

        if (injectableFields.isEmpty()) {
            logger.warn("@EntryPoint class $className has no @Inject fields. @EntryPoint annotation is unnecessary.")
        }

        return EntryPointInfo(
            classDeclaration = classDecl,
            injectableFields = injectableFields,
        )
    }

    /**
     * Scans a single class with @Inject constructor.
     * Performs validation and extracts constructor params and injectable fields.
     */
    private fun scanInjectableClass(constructor: KSFunctionDeclaration): InjectableClassInfo? {
        val classDecl = constructor.parentDeclaration as? KSClassDeclaration
        if (classDecl == null) {
            logger.error("@Inject found on non-class constructor", constructor)
            return null
        }

        val className = classDecl.qualifiedName?.asString() ?: classDecl.simpleName.asString()
        logger.info("Stitch: Scanning @Inject class $className")

        // Validation: Check class kind
        if (classDecl.classKind == ClassKind.INTERFACE) {
            logger.error("@Inject cannot be used on interfaces: $className", classDecl)
            return null
        }
        if (classDecl.classKind == ClassKind.ANNOTATION_CLASS) {
            logger.error("@Inject cannot be used on annotation classes: $className", classDecl)
            return null
        }
        if (classDecl.modifiers.contains(Modifier.ABSTRACT)) {
            logger.error("@Inject cannot be used on abstract classes: $className", classDecl)
            return null
        }

        // Validation: Check for multiple @Inject constructors
        val injectConstructors = classDecl.getAllFunctions()
            .filter { it.isConstructor() }
            .filter { it.hasAnnotation(STITCH_INJECT) || it.hasAnnotation(JAVAX_INJECT) }
            .toList()

        if (injectConstructors.size > 1) {
            logger.error(
                "Multiple @Inject constructors found in $className. Only one @Inject constructor is allowed.",
                classDecl,
            )
            return null
        }

        // Extract constructor parameters
        val constructorParameters = constructor.parameters.map { param ->
            ParameterInfo(
                name = param.name?.asString() ?: "",
                type = param.type.resolve(),
                qualifier = extractQualifier(param),
            )
        }

        // Scan for @Inject fields
        val injectableFields = scanInjectableFields(classDecl, constructor)

        // Check for @Singleton on class
        val isSingleton = classDecl.hasAnnotation(STITCH_SINGLETON) ||
            classDecl.hasAnnotation(JAVAX_SINGLETON)

        // Check for @Named on class
        val qualifier = extractQualifier(classDecl)

        val returnType = classDecl.asStarProjectedType()

        return InjectableClassInfo(
            classDeclaration = classDecl,
            constructor = constructor,
            returnType = returnType,
            constructorParameters = constructorParameters,
            injectableFields = injectableFields,
            isSingleton = isSingleton,
            qualifier = qualifier,
        )
    }

    /**
     * Scans for @Inject annotated fields within a class.
     * Validates field accessibility and mutability.
     */
    private fun scanInjectableFields(classDecl: KSClassDeclaration, constructor: KSFunctionDeclaration): List<InjectableFieldInfo> {
        val fields = mutableListOf<InjectableFieldInfo>()

        classDecl.getAllProperties().forEach { property ->
            val hasInject = property.hasAnnotation(STITCH_INJECT) || property.hasAnnotation(JAVAX_INJECT)
            if (!hasInject) return@forEach

            val fieldName = property.simpleName.asString()

            // Validation: Field must be mutable
            if (property.isMutable == false) {
                logger.error(
                    "@Inject field '$fieldName' must be mutable (var or lateinit var). " +
                        "Use constructor injection for immutable fields.",
                    property,
                )
                return@forEach
            }

            // Validation: Field must be accessible (not private)
            if (property.modifiers.contains(Modifier.PRIVATE)) {
                logger.error(
                    "@Inject field '$fieldName' cannot be private. " +
                        "Generated code needs to access this field.",
                    property,
                )
                return@forEach
            }

            // Warning: Prefer constructor injection
            if (constructor.parameters.isNotEmpty()) {
                logger.warn(
                    "Class ${classDecl.simpleName.asString()} uses both constructor and field injection. " +
                        "Consider using constructor injection only for better immutability.",
                    property,
                )
            }

            val fieldType = property.type.resolve()
            val qualifier = extractQualifier(property)

            fields.add(
                InjectableFieldInfo(
                    name = fieldName,
                    type = fieldType,
                    qualifier = qualifier,
                ),
            )
        }

        return fields
    }

    private fun scanModule(moduleClass: KSClassDeclaration): ModuleInfo? {
        logger.info("Stitch: Scanning module ${moduleClass.qualifiedName?.asString()}")

        val provides = moduleClass.getAllFunctions()
            .filter { it.hasAnnotation(STITCH_PROVIDES) || it.hasAnnotation(DAGGER_PROVIDES) }
            .mapNotNull { scanProvider(it) }
            .toList()

        if (provides.isEmpty()) {
            logger.warn("Stitch: Module ${moduleClass.simpleName.asString()} has no @Provides methods")
            return null
        }

        return ModuleInfo(
            declaration = moduleClass,
            provides = provides,
        )
    }

    private fun scanProvider(function: KSFunctionDeclaration): ProvidesInfo? {
        val returnType = function.returnType?.resolve()
        if (returnType == null) {
            logger.error("@Provides method ${function.simpleName.asString()} has no return type", function)
            return null
        }

        val isSingleton = function.hasAnnotation(STITCH_SINGLETON) ||
            function.hasAnnotation(JAVAX_SINGLETON)

        val qualifier = extractQualifier(function)

        val parameters = function.parameters.map { param ->
            ParameterInfo(
                name = param.name?.asString() ?: "",
                type = param.type.resolve(),
                qualifier = extractQualifier(param),
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

    private fun extractQualifier(annotated: KSAnnotated): QualifierInfo? {
        annotated.findAnnotation(STITCH_NAMED)?.let { return extractNamedValue(it) }
        annotated.findAnnotation(JAVAX_NAMED)?.let { return extractNamedValue(it) }
        annotated.annotations.forEach { annotation ->
            val qualifierDeclaration = annotation.annotationType.resolve().declaration
            if (qualifierDeclaration.hasAnnotation(STITCH_QUALIFIER) || qualifierDeclaration.hasAnnotation(JAVAX_QUALIFIER)) {
                logger.error(
                    "Custom qualifiers are not supported in v1.0. Use @Named(\"...\") instead.",
                    annotated,
                )
            }
        }
        return null
    }

    private fun extractNamedValue(annotation: KSAnnotation): QualifierInfo.Named? {
        val value = annotation.arguments.firstOrNull()?.value as? String
        return if (value != null) {
            QualifierInfo.Named(value)
        } else {
            null
        }
    }

    private fun KSAnnotated.hasAnnotation(qualifiedName: String): Boolean =
        annotations.any { it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName }

    private fun KSAnnotated.findAnnotation(qualifiedName: String): KSAnnotation? =
        annotations.firstOrNull { it.annotationType.resolve().declaration.qualifiedName?.asString() == qualifiedName }
}

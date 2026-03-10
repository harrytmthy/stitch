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

package com.harrytmthy.stitch.compiler.scanner

import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier
import com.harrytmthy.stitch.compiler.consts.BindingKind
import com.harrytmthy.stitch.compiler.fatalError
import com.harrytmthy.stitch.compiler.model.BindingDeclaration
import com.harrytmthy.stitch.compiler.model.BindingPool
import com.harrytmthy.stitch.compiler.model.ProvidedBinding
import com.harrytmthy.stitch.compiler.model.Qualifier
import com.harrytmthy.stitch.compiler.model.RequestedBinding
import com.harrytmthy.stitch.compiler.model.Scope
import com.harrytmthy.stitch.compiler.utils.filePathAndLineNumber
import com.harrytmthy.stitch.compiler.utils.find
import com.harrytmthy.stitch.compiler.utils.findArgument
import com.harrytmthy.stitch.compiler.utils.qualifiedName

class LocalAnnotationScanner(
    private val resolver: Resolver,
    private val moduleKey: String,
    private val scanResult: LocalScanResult,
) {

    private val scopeBySymbol = HashMap<KSAnnotated, Scope>()

    private val customScopeByQualifiedName = HashMap<String, Scope.Custom>()

    private val qualifierBySymbol = HashMap<KSAnnotated, Qualifier>()

    private val providedBindingBySymbol = HashMap<KSAnnotated, ProvidedBinding>()

    private val parametersByBinding = HashMap<ProvidedBinding, ArrayList<KSValueParameter>>()

    private val providedAliases = BindingPool<ProvidedBinding>()

    fun scan() {
        scanRoot()
        scanScopes()
        scanDependsOn()
        scanQualifiers()
        scanProvides()
        scanInjects()
        scanBinds()
        collectDependencies()
    }

    private fun scanRoot() {
        scanResult.isAggregator = resolver.getSymbolsWithAnnotation(ROOT).any()
    }

    /**
     * `@Scope` has 3 different cases:
     *
     * Case #1 - KSClassDeclaration (annotation class):
     *
     * ```
     * @Scope
     * @Retention(AnnotationRetention.RUNTIME)
     * annotation class Activity
     * ```
     *
     * Case #2 - KSClassDeclaration (non-annotation class):
     *
     * ```
     * @Scope("Activity")
     * class UserViewModel @Inject constructor(...) : ViewModel()
     * ```
     *
     * OR:
     *
     * ```
     * @Activity
     * class UserViewModel @Inject constructor(...) : ViewModel()
     * ```
     *
     * Case #3 - KSFunctionDeclaration:
     *
     * ```
     * @Scope("Activity")
     * @Provides
     * fun provideUserViewModel(...): UserViewModel = UserViewModel(...)
     * ```
     *
     * OR:
     *
     * ```
     * @Activity
     * @Provides
     * fun provideUserViewModel(...): UserViewModel = UserViewModel(...)
     * ```
     */
    private fun scanScopes() {
        for (singletonAnnotation in listOf(STITCH_SINGLETON, JAVAX_SINGLETON)) {
            for (symbol in resolver.getSymbolsWithAnnotation(singletonAnnotation)) {
                scopeBySymbol[symbol] = Scope.Singleton
            }
        }
        for (symbol in resolver.getSymbolsWithAnnotation(SCOPE)) {
            // At this point, scopeBySymbol only contains @Singleton (if any)
            if (symbol in scopeBySymbol) {
                fatalError("@Scope cannot be used with @Singleton at the same time", symbol)
            }
            val scopeName = symbol.annotations.find(SCOPE).arguments.first().value as String
            when (symbol) {
                is KSClassDeclaration -> {
                    if (symbol.classKind == ClassKind.ANNOTATION_CLASS) {
                        // Case #1 path: Register both FQN + canonical name
                        val canonicalName = scopeName.ifBlank { symbol.simpleName.asString() }
                            .lowercase()
                        if (canonicalName == "singleton") {
                            fatalError("@Singleton already exists!", symbol)
                        }
                        scanResult.customScopeByCanonicalName[canonicalName]?.let {
                            fatalError(
                                message = "Duplicate scope: $it. Already provided at ${it.location}",
                                symbol = symbol,
                            )
                        }
                        val qualifiedName = symbol.qualifiedName(symbol)
                        val location = symbol.filePathAndLineNumber.orEmpty()
                        val scope = Scope.Custom(canonicalName, qualifiedName, location)
                        scanResult.customScopeByCanonicalName[canonicalName] = scope
                        customScopeByQualifiedName[qualifiedName] = scope
                        scopeBySymbol[symbol] = scope
                        continue
                    }
                    // Case #2 path: FQN of @Scope("activity") is unknown. Leave it empty.
                    if (scopeName.isEmpty()) {
                        fatalError("Scope name cannot be empty", symbol)
                    }
                    val canonicalName = scopeName.lowercase()
                    if (canonicalName == "singleton") {
                        scopeBySymbol[symbol] = Scope.Singleton
                        continue
                    }
                    val scope = Scope.Custom(canonicalName)
                    if (scope.canonicalName !in scanResult.customScopeByCanonicalName) {
                        scanResult.customScopeByCanonicalName[scope.canonicalName] = scope
                    }
                    scopeBySymbol[symbol] = scope
                }

                is KSFunctionDeclaration -> {
                    // Case #3 path: FQN of @Scope("activity") is unknown. Leave it empty.
                    if (symbol.isConstructor()) {
                        fatalError("@Scope cannot be used on constructors", symbol)
                    }
                    if (scopeName.isEmpty()) {
                        fatalError("Scope name cannot be empty", symbol)
                    }
                    val canonicalName = scopeName.lowercase()
                    if (canonicalName == "singleton") {
                        scopeBySymbol[symbol] = Scope.Singleton
                        continue
                    }
                    val scope = Scope.Custom(canonicalName)
                    if (scope.canonicalName !in scanResult.customScopeByCanonicalName) {
                        scanResult.customScopeByCanonicalName[scope.canonicalName] = scope
                    }
                    scopeBySymbol[symbol] = scope
                }
            }
        }
    }

    private fun scanDependsOn() {
        for (symbol in resolver.getSymbolsWithAnnotation(DEPENDS_ON)) {
            val scope = scopeBySymbol[symbol]
                ?: fatalError("@DependsOn cannot be used without @Scope", symbol)
            val annotation = symbol.annotations.find(DEPENDS_ON)
            val dependency = annotation.arguments[0].value as KSType
            val qualifiedName = dependency.declaration.qualifiedName(symbol)
            customScopeByQualifiedName[qualifiedName]?.let { dependency ->
                scanResult.scopeDependencies[scope] = dependency
                continue
            }
            if (qualifiedName == STITCH_SINGLETON || qualifiedName == JAVAX_SINGLETON) {
                scanResult.scopeDependencies[scope] = Scope.Singleton
                continue
            }
            val scopeAnnotation = dependency.declaration.annotations.find {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == SCOPE
            } ?: fatalError("Scope '$scope' depends on a type that isn't a scope", symbol)
            val canonicalName = (scopeAnnotation.arguments[0].value as String)
                .ifBlank { dependency.declaration.simpleName.asString() }
                .lowercase()
            val scopeDependency = Scope.Custom(canonicalName)
            if (canonicalName !in scanResult.customScopeByCanonicalName) {
                scanResult.customScopeByCanonicalName[canonicalName] = scopeDependency
            }
            customScopeByQualifiedName[qualifiedName] = scopeDependency
            scanResult.scopeDependencies[scope] = scopeDependency
        }
    }

    private fun scanQualifiers() {
        scanNamedQualifiers()
        // TODO: Add more qualifier types
    }

    private fun scanNamedQualifiers() {
        for (annotationName in listOf(STITCH_NAMED, JAVAX_NAMED)) {
            for (symbol in resolver.getSymbolsWithAnnotation(annotationName)) {
                if (symbol in qualifierBySymbol) {
                    fatalError("@Named cannot be used with other qualifiers", symbol)
                }
                val name = symbol.annotations.find(annotationName).arguments.first().value as String
                qualifierBySymbol[symbol] = Qualifier.Named(name)
            }
        }
    }

    /**
     * There are 3 supported kinds of binding:
     * - [BindingKind.PROVIDED_IN_TOP_LEVEL]
     * - [BindingKind.PROVIDED_IN_OBJECT]
     * - [BindingKind.PROVIDED_IN_CLASS]
     *
     * Example of [BindingKind.PROVIDED_IN_TOP_LEVEL]:
     *
     * ```
     * // Top-level declaration
     * @Singleton
     * @Provides
     * fun providesLogger(): Logger = Logger()
     * ```
     *
     * Example of [BindingKind.PROVIDED_IN_OBJECT]:
     *
     * ```
     * object CoreModule {
     *
     *     @Singleton
     *     @Provides
     *     fun providesLogger(): Logger = Logger()
     * }
     * ```
     *
     * Example of [BindingKind.PROVIDED_IN_CLASS]:
     *
     * ```
     * class CoreModule {
     *
     *     @Singleton
     *     @Binds(ILogger::class) // Ignored. It will be registered separately via scanBinds()
     *     @Provides
     *     fun providesLogger(): Logger = Logger()
     * }
     * ```
     */
    private fun scanProvides() {
        for (symbol in resolver.getSymbolsWithAnnotation(PROVIDES)) {
            if (symbol !is KSFunctionDeclaration) {
                fatalError("@Provides can only be used on functions", symbol)
            }
            val type = symbol.returnType?.resolve()?.declaration?.qualifiedName(symbol)
                ?: fatalError("@Provides has no return type", symbol)
            val qualifier = qualifierBySymbol[symbol]
            val scope = getScopeFromSymbol(symbol)
            val location = symbol.filePathAndLineNumber!!
            val parentDeclaration = symbol.parentDeclaration as? KSClassDeclaration
            val kind = when {
                parentDeclaration == null -> BindingKind.PROVIDED_IN_TOP_LEVEL

                parentDeclaration.classKind == ClassKind.OBJECT -> BindingKind.PROVIDED_IN_OBJECT

                parentDeclaration.classKind == ClassKind.CLASS -> {
                    if (!parentDeclaration.primaryConstructor?.parameters.isNullOrEmpty()) {
                        fatalError(
                            message = "Class that contains @Provides must have an empty constructor",
                            symbol = parentDeclaration,
                        )
                    }
                    BindingKind.PROVIDED_IN_CLASS
                }

                else -> fatalError(
                    message = "Unsupported class type for @Provides. Please use class or object.",
                    symbol = parentDeclaration,
                )
            }
            val binding = ProvidedBinding(
                type = type,
                qualifier = qualifier,
                scope = scope,
                location = location,
                kind = kind,
                providerPackageName = symbol.packageName.asString(),
                providerFunctionName = symbol.simpleName.asString(),
                providerClassName = parentDeclaration?.simpleName?.asString().orEmpty(),
                moduleKey = moduleKey,
            )

            // ProvidedBinding is keyed only by type + qualifier, allowing `providedBindings`
            // to detect if there is another symbol providing the same type + qualifier.
            if (binding in scanResult.providedBindings) {
                duplicateBindingError(scanResult.providedBindings.getValue(binding), symbol)
            }
            providedBindingBySymbol[symbol] = binding
            scanResult.providedBindings.add(binding)

            // Parameters (or "dependencies") that will be collected after scanning @Inject.
            if (symbol.parameters.isNotEmpty()) {
                val parameters = parametersByBinding.getOrPut(binding) { ArrayList() }
                parameters += symbol.parameters
            }
        }
    }

    /**
     * Constructor injections require a special treatment where the symbol registered
     * in [providedBindingBySymbol] is the class declaration instead of the constructor,
     * since other annotations are targeting the class, not the constructor.
     */
    private fun scanInjects() {
        for (annotationName in listOf(STITCH_INJECT, JAVAX_INJECT)) {
            for (symbol in resolver.getSymbolsWithAnnotation(annotationName)) {
                when (symbol) {
                    is KSFunctionDeclaration -> handleConstructorInjection(symbol)
                    is KSPropertyDeclaration -> handleFieldInjection(symbol)
                }
            }
        }
    }

    private fun handleConstructorInjection(symbol: KSFunctionDeclaration) {
        if (!symbol.isConstructor()) {
            fatalError("@Inject can only be used on constructors/fields", symbol)
        }
        val canonicalSymbol = symbol.parentDeclaration as KSClassDeclaration
        if (canonicalSymbol.modifiers.contains(Modifier.ABSTRACT)) {
            fatalError("@Inject cannot be used on abstract classes", canonicalSymbol)
        }
        if (canonicalSymbol in providedBindingBySymbol) {
            fatalError(
                "Multiple @Inject-annotated constructors found. Only one is allowed",
                canonicalSymbol,
            )
        }
        val type = canonicalSymbol.asStarProjectedType().declaration.qualifiedName(canonicalSymbol)
        val qualifier = qualifierBySymbol[canonicalSymbol]
        val scope = getScopeFromSymbol(canonicalSymbol)
        val location = canonicalSymbol.filePathAndLineNumber!!
        val binding = ProvidedBinding(
            type = type,
            qualifier = qualifier,
            scope = scope,
            location = location,
            kind = BindingKind.PROVIDED_IN_CONSTRUCTOR,
            moduleKey = moduleKey,
        )

        // ProvidedBinding is keyed only by type + qualifier, allowing `providedBindings`
        // to detect if there is another symbol providing the same type + qualifier.
        if (binding in scanResult.providedBindings) {
            duplicateBindingError(scanResult.providedBindings.getValue(binding), canonicalSymbol)
        }
        providedBindingBySymbol[canonicalSymbol] = binding
        scanResult.providedBindings.add(binding)

        // Parameters (or "dependencies") that will be collected after scanning @Inject.
        if (symbol.parameters.isNotEmpty()) {
            val parameters = parametersByBinding.getOrPut(binding) { ArrayList() }
            parameters += symbol.parameters
        }
    }

    private fun handleFieldInjection(symbol: KSPropertyDeclaration) {
        if (symbol.parentDeclaration == null) {
            fatalError("@Inject cannot be used on top level fields", symbol)
        }
        if (!symbol.isMutable) {
            fatalError("@Inject field '${symbol.simpleName}' must be mutable", symbol)
        }
        if (symbol.modifiers.contains(Modifier.PRIVATE)) {
            fatalError("@Inject field '${symbol.simpleName}' cannot be private", symbol)
        }
        val type = symbol.type.resolve().declaration.qualifiedName(symbol)
        val qualifier = qualifierBySymbol[symbol]
        val location = symbol.filePathAndLineNumber!!
        val fieldName = symbol.simpleName.asString()
        val binding = RequestedBinding(type, qualifier, location, fieldName)
        val parentQualifiedName = symbol.parentDeclaration!!.qualifiedName!!.asString()
        val bindings = scanResult.requestedBindingsByClass.getOrPut(parentQualifiedName) { ArrayList() }
        bindings.add(binding)
    }

    private fun collectDependencies() {
        for ((providedBinding, parameters) in parametersByBinding) {
            for (parameter in parameters) {
                val type = parameter.type.resolve().declaration.qualifiedName(parameter)
                val qualifier = qualifierBySymbol[parameter]
                val location = parameter.filePathAndLineNumber!!
                val binding = BindingDeclaration(type, qualifier, location)
                val dependencies = providedBinding.dependencies
                    ?: ArrayList<BindingDeclaration>(parameters.size).also { providedBinding.dependencies = it }
                dependencies.add(binding)
            }
        }
    }

    /**
     * `@Binds` has 3 different cases:
     *
     * Case #1 - KSClassDeclaration:
     *
     * ```
     * @Singleton
     * @Binds(aliases = [UserRepository::class])
     * class UserRepositoryImpl @Inject constructor(...) : UserRepository
     * ```
     *
     * Case #2 - KSFunctionDeclaration (abstract function):
     *
     * ```
     * @Binds
     * fun bindUserRepository(repo: UserRepositoryImpl): UserRepository
     * ```
     *
     * OR:
     *
     * ```
     * @Binds(aliases = [...])
     * fun bindUserRepository(repo: UserRepositoryImpl): UserRepository
     * ```
     *
     * Unlike Case #1 where `@Binds` targets the existing symbol, Case #2 targets a symbol which
     * isn't annotated with `@Provides` or `@Inject`, so cannot use [providedBindingBySymbol].
     *
     * Case #3 - KSFunctionDeclaration:
     *
     * ```
     * @Binds(aliases = [UserRepository::class])
     * @Singleton
     * @Provides
     * fun provideUserRepository(...): UserRepositoryImpl = UserRepositoryImpl(...)
     * ```
     */
    private fun scanBinds() {
        for (symbol in resolver.getSymbolsWithAnnotation(BINDS)) {
            when (symbol) {
                is KSClassDeclaration -> {
                    val aliasArg = symbol.annotations.find(BINDS).findArgument("aliases")
                    val aliases = aliasArg.value as List<*>
                    if (aliases.isEmpty()) {
                        fatalError(
                            "@Binds(aliases = ...) is required when annotating classes",
                            symbol,
                        )
                    }
                    // TODO: Display a proper KSP warning to inform about the skipped @Binds
                    val dependency = providedBindingBySymbol[symbol] ?: continue
                    val qualifier = qualifierBySymbol[symbol]
                    val location = symbol.filePathAndLineNumber.orEmpty()
                    for (alias in aliases) {
                        val type = (alias as KSType).declaration.qualifiedName(symbol)
                        registerAlias(type, qualifier, location, dependency, symbol)
                    }
                }

                is KSFunctionDeclaration -> {
                    if (symbol.isConstructor()) {
                        fatalError("@Binds cannot be used on constructors", symbol)
                    }
                    val returnType = symbol.returnType?.resolve()?.declaration
                        ?.qualifiedName(symbol)
                        ?: fatalError(
                            "@Binds requires a return type when annotating functions",
                            symbol,
                        )
                    val qualifier = qualifierBySymbol[symbol]
                    val location = symbol.filePathAndLineNumber.orEmpty()
                    if (symbol.isAbstract) {
                        val parameter = symbol.parameters.singleOrNull()
                            ?: fatalError(
                                "@Binds requires one parameter when annotating abstract functions",
                                symbol,
                            )
                        val aliasArg = symbol.annotations.find(BINDS).findArgument("aliases")
                        val type = parameter.type.resolve().declaration.qualifiedName(parameter)
                        val parameterLocation = parameter.filePathAndLineNumber!!
                        val dependency = BindingDeclaration(type, qualifier, parameterLocation)
                        registerAlias(returnType, qualifier, location, dependency, symbol)
                        for (alias in (aliasArg.value as List<*>)) {
                            val type = (alias as KSType).declaration.qualifiedName(parameter)
                            registerAlias(type, qualifier, location, dependency, symbol)
                        }
                    } else {
                        val aliasArg = symbol.annotations.find(BINDS).findArgument("aliases")
                        val aliases = aliasArg.value as List<*>
                        if (aliases.isEmpty()) {
                            fatalError(
                                "@Binds(aliases = ...) is required when annotating functions",
                                symbol,
                            )
                        }
                        // TODO: Display a proper KSP warning to inform about the skipped @Binds
                        val dependency = providedBindingBySymbol[symbol] ?: continue
                        for (alias in (aliasArg.value as List<*>)) {
                            val type = (alias as KSType).declaration.qualifiedName(symbol)
                            registerAlias(type, qualifier, location, dependency, symbol)
                        }
                    }
                }
            }
        }
    }

    /**
     * Registers an alias as a provided binding, ensuring no duplicate. Each alias
     * has exactly one [dependency], which can be another alias or the real binding.
     */
    private fun registerAlias(
        type: String,
        qualifier: Qualifier?,
        location: String,
        dependency: BindingDeclaration,
        symbol: KSAnnotated,
    ) {
        val alias = ProvidedBinding(
            type = type,
            qualifier = qualifier,
            scope = null,
            location = location,
            kind = BindingKind.PROVIDED_ALIAS,
            moduleKey = moduleKey,
        )
        providedAliases[alias]?.let { existingBinding ->
            duplicateBindingError(existingBinding, symbol)
        }
        alias.dependencies = arrayListOf(dependency)
        providedAliases[alias] = alias
        scanResult.providedBindings[alias] = alias
    }

    /**
     * Should be used only when scanning `@Provides` + constructor injections.
     */
    private fun getScopeFromSymbol(symbol: KSAnnotated): Scope? {
        // Fast-path: The scope is either `@Singleton` or provided via `@Scope(name = ...)`
        scopeBySymbol[symbol]?.let { return it }

        // Slow-path: Check if there is any annotation that is annotated with `@Scope`
        for (annotation in symbol.annotations) {
            val declaration = annotation.annotationType.resolve().declaration
            val qualifiedName = declaration.qualifiedName(symbol)
            customScopeByQualifiedName[qualifiedName]?.let { return it }
            for (metaAnnotation in declaration.annotations) {
                val fqn = metaAnnotation.annotationType.resolve().declaration.qualifiedName(symbol)
                if (fqn == SCOPE) {
                    val canonicalName = (metaAnnotation.arguments[0].value as String)
                        .ifBlank { annotation.shortName.asString() }
                        .lowercase()
                    val scope = Scope.Custom(canonicalName)
                    if (canonicalName !in scanResult.customScopeByCanonicalName) {
                        scanResult.customScopeByCanonicalName[canonicalName] = scope
                    }
                    customScopeByQualifiedName[qualifiedName] = scope
                    scopeBySymbol[symbol] = scope
                    return scope
                }
            }
        }
        return null // Unscoped
    }

    private fun duplicateBindingError(existing: ProvidedBinding, symbol: KSAnnotated): Nothing =
        fatalError(
            message = buildString {
                append("Duplicate binding for ${existing.type}")
                existing.qualifier?.let { append(" (qualifier: $it)") }
                if (existing.kind != BindingKind.PROVIDED_ALIAS && existing.scope != null) {
                    append(" in scope \"${existing.scope}\"")
                }
                append(". Already provided at ${existing.location}")
            },
            symbol = symbol,
        )

    private companion object {
        const val ROOT = "com.harrytmthy.stitch.annotations.StitchRoot"
        const val PROVIDES = "com.harrytmthy.stitch.annotations.Provides"
        const val STITCH_INJECT = "com.harrytmthy.stitch.annotations.Inject"
        const val JAVAX_INJECT = "javax.inject.Inject"
        const val STITCH_NAMED = "com.harrytmthy.stitch.annotations.Named"
        const val JAVAX_NAMED = "javax.inject.Named"
        const val SCOPE = "com.harrytmthy.stitch.annotations.Scope"
        const val STITCH_SINGLETON = "com.harrytmthy.stitch.annotations.Singleton"
        const val JAVAX_SINGLETON = "javax.inject.Singleton"
        const val DEPENDS_ON = "com.harrytmthy.stitch.annotations.DependsOn"
        const val BINDS = "com.harrytmthy.stitch.annotations.Binds"
    }
}

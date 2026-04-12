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

import com.google.devtools.ksp.containingFile
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
import com.google.devtools.ksp.symbol.Nullability
import com.harrytmthy.stitch.annotations.Binds
import com.harrytmthy.stitch.annotations.DependsOn
import com.harrytmthy.stitch.annotations.Inject
import com.harrytmthy.stitch.annotations.Named
import com.harrytmthy.stitch.annotations.Nullable
import com.harrytmthy.stitch.annotations.Provides
import com.harrytmthy.stitch.annotations.Singleton
import com.harrytmthy.stitch.annotations.StitchRoot
import com.harrytmthy.stitch.compiler.consts.BindingKind
import com.harrytmthy.stitch.compiler.fatalError
import com.harrytmthy.stitch.compiler.model.BindingDeclaration
import com.harrytmthy.stitch.compiler.model.BindingPool
import com.harrytmthy.stitch.compiler.model.LocalScanResult
import com.harrytmthy.stitch.compiler.model.ProvidedBinding
import com.harrytmthy.stitch.compiler.model.Qualifier
import com.harrytmthy.stitch.compiler.model.RequestedBinding
import com.harrytmthy.stitch.compiler.model.Scope
import com.harrytmthy.stitch.compiler.utils.filePathAndLineNumber
import com.harrytmthy.stitch.compiler.utils.find
import com.harrytmthy.stitch.compiler.utils.findArgument
import com.harrytmthy.stitch.compiler.utils.qualifiedName
import com.harrytmthy.stitch.annotations.Scope as ScopeAnnotation

class LocalAnnotationScanner(
    private val resolver: Resolver,
    private val scanResult: LocalScanResult,
) {

    private val scopeBySymbol = HashMap<KSAnnotated, Scope>()

    private val customScopeByQualifiedName = HashMap<String, Scope.Custom>()

    private val qualifierBySymbol = HashMap<KSAnnotated, Qualifier>()

    private val nullableBySymbol = HashSet<KSAnnotated>()

    private val providedBindingBySymbol = HashMap<KSAnnotated, ProvidedBinding>()

    private val parametersByBinding = HashMap<ProvidedBinding, ArrayList<KSValueParameter>>()

    private val providedAliases = BindingPool<ProvidedBinding>()

    fun scan() {
        scanRoot()
        scanScopes()
        scanDependsOn()
        scanQualifiers()
        scanNullables()
        scanProvides()
        scanInjects()
        scanBinds()
        collectDependencies()
    }

    private fun scanRoot() {
        val rootAnnotation = StitchRoot::class.qualifiedName!!
        scanResult.isAggregator = resolver.getSymbolsWithAnnotation(rootAnnotation).any()
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
        for (singletonAnnotation in singletonAnnotations) {
            for (symbol in resolver.getSymbolsWithAnnotation(singletonAnnotation)) {
                scopeBySymbol[symbol] = Scope.Singleton
            }
        }
        val scopeAnnotation = ScopeAnnotation::class.qualifiedName!!
        for (symbol in resolver.getSymbolsWithAnnotation(scopeAnnotation)) {
            // At this point, scopeBySymbol only contains @Singleton (if any)
            if (symbol in scopeBySymbol) {
                fatalError("@Scope cannot be used with @Singleton at the same time", symbol)
            }
            val scopeName = symbol.annotations.find(scopeAnnotation).arguments.first().value as String
            when (symbol) {
                is KSClassDeclaration -> {
                    if (symbol.classKind == ClassKind.ANNOTATION_CLASS) {
                        // Case #1 path: Register both FQN + canonical name
                        val originalName = scopeName.ifBlank { symbol.simpleName.asString() }
                        val canonicalName = originalName.lowercase()
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
                        val scope = Scope.Custom(originalName, canonicalName, qualifiedName, location)
                        scanResult.customScopeByCanonicalName[canonicalName] = scope
                        scanResult.scopeSources[scope] = symbol.containingFile!!
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
                    val scope = Scope.Custom(originalName = scopeName, canonicalName)
                    if (scope.canonicalName !in scanResult.customScopeByCanonicalName) {
                        scanResult.customScopeByCanonicalName[scope.canonicalName] = scope
                        scanResult.scopeSources[scope] = symbol.containingFile!!
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
                    val scope = Scope.Custom(originalName = scopeName, canonicalName)
                    if (scope.canonicalName !in scanResult.customScopeByCanonicalName) {
                        scanResult.customScopeByCanonicalName[scope.canonicalName] = scope
                        scanResult.scopeSources[scope] = symbol.containingFile!!
                    }
                    scopeBySymbol[symbol] = scope
                }
            }
        }
    }

    private fun scanDependsOn() {
        val dependsOnAnnotation = DependsOn::class.qualifiedName!!
        for (symbol in resolver.getSymbolsWithAnnotation(dependsOnAnnotation)) {
            val scope = scopeBySymbol[symbol]
                ?: fatalError("@DependsOn cannot be used without @Scope", symbol)
            val annotation = symbol.annotations.find(dependsOnAnnotation)
            val dependency = annotation.arguments[0].value as KSType
            val qualifiedName = dependency.declaration.qualifiedName(symbol)
            if (scope is Scope.Custom && scope.qualifiedName == qualifiedName) {
                fatalError("Scope '$scope' depends on itself", symbol)
            }
            customScopeByQualifiedName[qualifiedName]?.let { dependency ->
                scanResult.scopeDependencies[scope] = dependency
                continue
            }
            if (qualifiedName in singletonAnnotations) {
                scanResult.scopeDependencies[scope] = Scope.Singleton
                continue
            }
            val scopeAnnotation = dependency.declaration.annotations.find {
                val qualifiedName = it.annotationType.resolve().declaration.qualifiedName?.asString()
                qualifiedName == ScopeAnnotation::class.qualifiedName!!
            } ?: fatalError("Scope '$scope' depends on a type that isn't a scope", symbol)
            val originalName = (scopeAnnotation.arguments[0].value as String)
                .ifBlank { dependency.declaration.simpleName.asString() }
            val canonicalName = originalName.lowercase()
            val scopeDependency = Scope.Custom(originalName, canonicalName)
            if (canonicalName !in scanResult.customScopeByCanonicalName) {
                scanResult.customScopeByCanonicalName[canonicalName] = scopeDependency
                scanResult.scopeSources[scopeDependency] = symbol.containingFile!!
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
        for (annotationName in namedAnnotations) {
            for (symbol in resolver.getSymbolsWithAnnotation(annotationName)) {
                if (symbol in qualifierBySymbol) {
                    fatalError("@Named cannot be used with other qualifiers", symbol)
                }
                val name = symbol.annotations.find(annotationName).arguments.first().value as String
                qualifierBySymbol[symbol] = Qualifier.Named(name)
            }
        }
    }

    private fun scanNullables() {
        for (nullableAnnotation in nullableAnnotations) {
            for (symbol in resolver.getSymbolsWithAnnotation(nullableAnnotation)) {
                nullableBySymbol.add(symbol)
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
        for (symbol in resolver.getSymbolsWithAnnotation(Provides::class.qualifiedName!!)) {
            if (symbol !is KSFunctionDeclaration) {
                fatalError("@Provides can only be used on functions", symbol)
            }
            val resolvedType = symbol.returnType?.resolve()
            val type = resolvedType?.declaration?.qualifiedName(symbol)
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
            val nullable = symbol in nullableBySymbol
            val binding = ProvidedBinding(
                type = type,
                qualifier = qualifier,
                scope = scope,
                nullable = resolvedType.nullability == Nullability.NULLABLE || nullable,
                location = location,
                kind = kind,
                providerPackageName = symbol.packageName.asString(),
                providerFunctionName = symbol.simpleName.asString(),
                providerClassName = parentDeclaration?.simpleName?.asString().orEmpty(),
            )

            // ProvidedBinding is keyed only by type + qualifier, allowing `providedBindings`
            // to detect if there is another symbol providing the same type + qualifier.
            if (binding in scanResult.providedBindings) {
                duplicateBindingError(scanResult.providedBindings.getValue(binding), symbol)
            }
            providedBindingBySymbol[symbol] = binding
            scanResult.providedBindings.add(binding)
            scanResult.providedBindingSources[binding] = symbol.containingFile!!

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
        for (annotationName in injectAnnotations) {
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
        val resolvedType = canonicalSymbol.asStarProjectedType()
        val type = resolvedType.declaration.qualifiedName(canonicalSymbol)
        val qualifier = qualifierBySymbol[canonicalSymbol]
        val scope = getScopeFromSymbol(canonicalSymbol)
        val location = canonicalSymbol.filePathAndLineNumber!!
        val annotatedWithNullable = canonicalSymbol in nullableBySymbol
        val binding = ProvidedBinding(
            type = type,
            qualifier = qualifier,
            scope = scope,
            nullable = resolvedType.nullability == Nullability.NULLABLE || annotatedWithNullable,
            location = location,
            kind = BindingKind.PROVIDED_IN_CONSTRUCTOR,
        )

        // ProvidedBinding is keyed only by type + qualifier, allowing `providedBindings`
        // to detect if there is another symbol providing the same type + qualifier.
        if (binding in scanResult.providedBindings) {
            duplicateBindingError(scanResult.providedBindings.getValue(binding), canonicalSymbol)
        }
        providedBindingBySymbol[canonicalSymbol] = binding
        scanResult.providedBindings.add(binding)
        scanResult.providedBindingSources[binding] = symbol.containingFile!!

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
        val bindings = scanResult.requestedBindings.getOrPut(parentQualifiedName) { ArrayList() }
        bindings.add(binding)
        scanResult.requesterSources[parentQualifiedName] = symbol.containingFile!!
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
                scanResult.providedBindingSources[binding] = parameter.containingFile!!
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
        val bindsAnnotation = Binds::class.qualifiedName!!
        for (symbol in resolver.getSymbolsWithAnnotation(bindsAnnotation)) {
            when (symbol) {
                is KSClassDeclaration -> {
                    val aliasArg = symbol.annotations.find(bindsAnnotation).findArgument("aliases")
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
                        val nullable = alias.nullability == Nullability.NULLABLE
                        registerAlias(type, qualifier, nullable, location, dependency, symbol)
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
                        val aliasArg = symbol.annotations.find(bindsAnnotation).findArgument("aliases")
                        val resolvedType = parameter.type.resolve()
                        val type = resolvedType.declaration.qualifiedName(parameter)
                        val parameterLocation = parameter.filePathAndLineNumber!!
                        val dependency = BindingDeclaration(type, qualifier, parameterLocation)
                        val nullable = resolvedType.nullability == Nullability.NULLABLE
                        registerAlias(returnType, qualifier, nullable, location, dependency, symbol)
                        for (alias in (aliasArg.value as List<*>)) {
                            val type = (alias as KSType).declaration.qualifiedName(parameter)
                            val nullable = alias.nullability == Nullability.NULLABLE
                            registerAlias(type, qualifier, nullable, location, dependency, symbol)
                        }
                    } else {
                        val aliasArg = symbol.annotations.find(bindsAnnotation).findArgument("aliases")
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
                            val nullable = alias.nullability == Nullability.NULLABLE
                            registerAlias(type, qualifier, nullable, location, dependency, symbol)
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
        nullable: Boolean,
        location: String,
        dependency: BindingDeclaration,
        symbol: KSAnnotated,
    ) {
        val alias = ProvidedBinding(
            type = type,
            qualifier = qualifier,
            scope = null,
            nullable = nullable || symbol in nullableBySymbol,
            location = location,
            kind = BindingKind.PROVIDED_ALIAS,
        )
        providedAliases[alias]?.let { existingBinding ->
            duplicateBindingError(existingBinding, symbol)
        }
        alias.dependencies = arrayListOf(dependency)
        providedAliases[alias] = alias
        scanResult.providedBindings[alias] = alias
        scanResult.providedBindingSources[alias] = symbol.containingFile!!
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
                if (fqn == ScopeAnnotation::class.qualifiedName) {
                    val originalName = (metaAnnotation.arguments[0].value as String)
                        .ifBlank { annotation.shortName.asString() }
                    val canonicalName = originalName.lowercase()
                    val scope = Scope.Custom(originalName, canonicalName)
                    if (canonicalName !in scanResult.customScopeByCanonicalName) {
                        scanResult.customScopeByCanonicalName[canonicalName] = scope
                        scanResult.scopeSources[scope] = symbol.containingFile!!
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

        val injectAnnotations = listOf(
            Inject::class.qualifiedName!!,
            "javax.inject.Inject",
        )

        val namedAnnotations = listOf(
            Named::class.qualifiedName!!,
            "javax.inject.Named",
        )

        val singletonAnnotations = listOf(
            Singleton::class.qualifiedName!!,
            "javax.inject.Singleton",
        )

        val nullableAnnotations = listOf(
            Nullable::class.qualifiedName!!,
            "androidx.annotation.Nullable",
            "org.jetbrains.annotations.Nullable",
            "javax.annotation.Nullable",
            "org.jspecify.annotations.Nullable",
        )
    }
}

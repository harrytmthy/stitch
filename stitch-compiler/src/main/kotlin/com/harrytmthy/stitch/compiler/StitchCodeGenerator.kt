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
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.ksp.toTypeName
import java.io.OutputStreamWriter
import com.google.devtools.ksp.processing.CodeGenerator as KspCodeGenerator

/**
 * Generates the DI implementation using direct method calls (Dagger-style).
 */
class StitchCodeGenerator(
    private val codeGenerator: KspCodeGenerator,
    private val logger: KSPLogger,
) {

    /**
     * Finds a dependency and determines its location relative to the current scope.
     */
    private fun resolveDependency(
        depType: KSType,
        depQualifier: QualifierInfo?,
        currentScope: KSType?,
        allNodes: List<DependencyNode>,
        scopeGraph: ScopeGraph,
    ): Pair<DependencyNode, DependencyLocation>? {
        // Build a lookup function
        fun findNode(scope: KSType?): DependencyNode? {
            return allNodes.firstOrNull { node ->
                // Check if type matches either the primary type or any alias
                val typeMatches = node.type.declaration == depType.declaration ||
                    node.aliases.any { it.declaration == depType.declaration }
                typeMatches &&
                    node.qualifier == depQualifier &&
                    node.scopeAnnotation?.declaration == scope?.declaration
            }
        }

        // 1. Try current scope first
        if (currentScope != null) {
            findNode(currentScope)?.let {
                return it to DependencyLocation.SAME_SCOPE
            }
        }

        // 2. Walk up the scope chain to find in ancestor scopes
        if (currentScope != null) {
            var ancestorScope: KSType? = scopeGraph.scopes[currentScope]?.dependsOn
            while (ancestorScope != null) {
                findNode(ancestorScope)?.let {
                    return it to DependencyLocation.UPSTREAM
                }
                ancestorScope = scopeGraph.scopes[ancestorScope]?.dependsOn
            }
        }

        // 3. Try singleton/unscoped
        findNode(null)?.let {
            return it to DependencyLocation.SINGLETON
        }

        // Not found
        return null
    }

    fun generateComponentAndInjector(nodes: List<DependencyNode>, fieldInjectors: List<FieldInjectorInfo>, scopeGraph: ScopeGraph) {
        logger.info("Stitch: Generating DI component with ${nodes.size} bindings and ${fieldInjectors.size} injectors")

        val dependencies = Dependencies(
            aggregating = true,
            sources = nodes.flatMap { listOf(it.providerModule.containingFile, it.providerFunction.containingFile) }
                .plus(fieldInjectors.map { it.classDeclaration.containingFile })
                .filterNotNull()
                .distinct()
                .toTypedArray(),
        )

        // Separate nodes by scope
        val unscopedNodes = nodes.filter { it.scopeAnnotation == null }
        val scopedNodesByScope = nodes.filter { it.scopeAnnotation != null }
            .groupBy { it.scopeAnnotation!! }

        // All discovered custom scopes, regardless of whether they have bindings
        val allScopes = scopeGraph.scopes.keys

        // Generate DI component file (singletons + factories only)
        generateDiComponentFile(unscopedNodes, fieldInjectors, scopeGraph, dependencies, nodes)

        // Generate scope component files for every scope in the graph
        allScopes.forEach { scopeAnnotation ->
            val scopeNodes = scopedNodesByScope[scopeAnnotation] ?: emptyList()
            generateScopeComponentFile(scopeAnnotation, scopeNodes, fieldInjectors, scopeGraph, dependencies, nodes)
        }

        logger.info("Stitch: Generated StitchDiComponent, ${allScopes.size} scope component(s)")
    }

    /**
     * Generates a scope component class for a specific scope.
     *
     * Root scopes (depend on Singleton): class with no upstream property
     * Downstream scopes: class with val upstream: StitchXxxScopeComponent
     */
    private fun generateScopeComponentFile(
        scopeAnnotation: KSType,
        nodes: List<DependencyNode>,
        fieldInjectors: List<FieldInjectorInfo>,
        scopeGraph: ScopeGraph,
        dependencies: Dependencies,
        allNodes: List<DependencyNode>, // All nodes for dependency resolution
    ) {
        val scopeInfo = scopeGraph.scopes[scopeAnnotation]!!
        val scopeName = scopeAnnotation.declaration.simpleName.asString()
        val componentClassName = "Stitch${scopeName}Component"

        val component = TypeSpec.classBuilder(componentClassName)
            .superclass(ClassName("com.harrytmthy.stitch.internal", "StitchComponent"))

        // Check if this is a root scope (depends on Singleton)
        val upstreamScope = scopeInfo.dependsOn
        val isRootScope = upstreamScope == null

        // Add upstream property for non-root scopes
        if (!isRootScope) {
            val upstreamScopeName = upstreamScope.declaration.simpleName.asString()
            val upstreamClassName = ClassName(GENERATED_PACKAGE, "Stitch${upstreamScopeName}Component")
            component.primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("upstream", upstreamClassName)
                    .build(),
            )
            component.addProperty(
                PropertySpec.builder("upstream", upstreamClassName)
                    .initializer("upstream")
                    .build(),
            )
        }

        // Generate DCL fields and provider methods for scoped dependencies
        nodes.forEach { node ->
            // Add DCL field, initialized flag, and lock
            component.addProperty(generateSingletonField(node))
            component.addProperty(generateInitializedFlag(node))
            component.addProperty(generateLockField(node))

            // Generate canonical provider method (with current scope context)
            component.addFunction(generateProviderMethod(node, scopeAnnotation, allNodes, scopeGraph))

            // Generate alias provider methods
            node.aliases.forEach { aliasType ->
                component.addFunction(generateAliasProviderMethod(node, aliasType))
            }
        }

        // Generate inject(target: T) for ALL field injectors
        // Each scope component can inject fields resolvable from its vantage point
        fieldInjectors.forEach { fieldInjector ->
            if (fieldInjector.injectableFields.isEmpty()) return@forEach

            component.addFunction(
                generateScopeFieldInjector(fieldInjector, scopeAnnotation, scopeGraph, allNodes),
            )
        }

        // Generate factory methods for child scopes (scopes that depend on this scope)
        val childScopes = scopeGraph.scopes.filter {
            it.value.dependsOn?.declaration == scopeAnnotation.declaration
        }.keys
        childScopes.forEach { childScope ->
            val childScopeName = childScope.declaration.simpleName.asString()
            val childComponentClassName = ClassName(GENERATED_PACKAGE, "Stitch${childScopeName}Component")

            component.addFunction(
                FunSpec.builder("create${childScopeName}Component")
                    .returns(childComponentClassName)
                    .addStatement("return %T(upstream = this)", childComponentClassName)
                    .build(),
            )
        }

        val file = FileSpec.builder(GENERATED_PACKAGE, componentClassName)
            .addFileComment("Generated by Stitch KSP Compiler - DO NOT EDIT")
            .addType(component.build())
            .build()

        val outputStream = codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = GENERATED_PACKAGE,
            fileName = componentClassName,
        )

        OutputStreamWriter(outputStream).use { writer ->
            file.writeTo(writer)
        }

        logger.info("Stitch: Generated $componentClassName")
    }

    /**
     * Generates a field injector method for a scope component.
     *
     * Injects ALL fields directly with qualified calls to the appropriate component.
     */
    private fun generateScopeFieldInjector(
        fieldInjector: FieldInjectorInfo,
        scopeAnnotation: KSType,
        scopeGraph: ScopeGraph,
        allNodes: List<DependencyNode>,
    ): FunSpec {
        val className = fieldInjector.classDeclaration.toClassName()
        val methodName = "inject"

        val method = FunSpec.builder(methodName)
            .addParameter("target", className)

        method.addCode(
            buildCodeBlock {
                // Inject ALL fields directly with qualified calls
                fieldInjector.injectableFields.forEach { field ->
                    val (resolvedNode, _) = resolveDependency(
                        field.type,
                        field.qualifier,
                        scopeAnnotation,
                        allNodes,
                        scopeGraph,
                    ) ?: return@forEach // Skip if not found (error already reported)

                    val depMethodName = generateMethodName(field.type, field.qualifier)
                    val targetScope = resolvedNode.scopeAnnotation

                    val call = when {
                        // Singleton / unscoped
                        targetScope == null -> "StitchDiComponent.$depMethodName()"

                        // Same scope
                        targetScope.declaration == scopeAnnotation.declaration -> "$depMethodName()"

                        // Ancestor scope: build chain of .upstream
                        else -> {
                            val chain = buildUpstreamChain(
                                currentScope = scopeAnnotation,
                                targetScope = targetScope,
                                scopeGraph = scopeGraph,
                            )
                            "$chain.$depMethodName()"
                        }
                    }
                    addStatement("target.${field.name} = $call")
                }
            },
        )

        return method.build()
    }

    private fun generateDiComponentFile(
        nodes: List<DependencyNode>,
        fieldInjectors: List<FieldInjectorInfo>,
        scopeGraph: ScopeGraph,
        dependencies: Dependencies,
        allNodes: List<DependencyNode>, // All nodes for dependency resolution
    ) {
        val file = FileSpec.builder(GENERATED_PACKAGE, DI_COMPONENT_NAME)
            .addFileComment("Generated by Stitch KSP Compiler - DO NOT EDIT")
            .addType(generateDiComponent(nodes, fieldInjectors, scopeGraph, allNodes))
            .build()

        val outputStream = codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = GENERATED_PACKAGE,
            fileName = DI_COMPONENT_NAME,
        )

        OutputStreamWriter(outputStream).use { writer ->
            file.writeTo(writer)
        }
    }

    /**
     * Generates the StitchDiComponent object with direct provider methods.
     */
    private fun generateDiComponent(
        nodes: List<DependencyNode>,
        fieldInjectors: List<FieldInjectorInfo>,
        scopeGraph: ScopeGraph,
        allNodes: List<DependencyNode>,
    ): TypeSpec {
        val component = TypeSpec.objectBuilder(DI_COMPONENT_NAME)

        // Collect module holders for non-object modules
        val moduleClasses = nodes
            .filter { !it.providerFunction.isConstructor() } // Only @Provides methods
            .map { it.providerModule }
            .filter { it.classKind != ClassKind.OBJECT }
            .distinctBy { it.qualifiedName?.asString() }

        if (moduleClasses.isNotEmpty()) {
            component.addType(generateModuleHolders(moduleClasses))
        }

        // Generate fields and methods for each dependency
        nodes.forEach { node ->
            // Add field and lock for singletons (only for canonical type, not aliases)
            if (node.isSingleton) {
                component.addProperty(generateSingletonField(node))
                component.addProperty(generateInitializedFlag(node))
                component.addProperty(generateLockField(node))
            }

            // Generate canonical provider method (currentScope = null for DI component)
            component.addFunction(generateProviderMethod(node, null, allNodes, scopeGraph))

            // Generate alias provider methods that delegate to canonical method
            node.aliases.forEach { aliasType ->
                component.addFunction(generateAliasProviderMethod(node, aliasType))
            }
        }

        // Generate inject(target: T) for ALL field injectors
        // DI component can inject all singleton/unscoped fields
        fieldInjectors.forEach { fieldInjector ->
            if (fieldInjector.injectableFields.isNotEmpty()) {
                component.addFunction(
                    generateFieldInjector(
                        classDeclaration = fieldInjector.classDeclaration,
                        fields = fieldInjector.injectableFields,
                        allNodes = allNodes,
                        scopeGraph = scopeGraph,
                    ),
                )
            }
        }

        // Generate factory methods for root custom scopes (scopes that depend on Singleton)
        val rootScopes = scopeGraph.scopes.filter {
            it.value.dependsOn == null
        }.keys
        rootScopes.forEach { scopeAnnotation ->
            val scopeName = scopeAnnotation.declaration.simpleName.asString()
            val componentClassName = ClassName(GENERATED_PACKAGE, "Stitch${scopeName}Component")

            component.addFunction(
                FunSpec.builder("create${scopeName}Component")
                    .returns(componentClassName)
                    .addStatement("return %T()", componentClassName)
                    .build(),
            )
        }

        return component.build()
    }

    /**
     * Generates a @Volatile field for a singleton dependency.
     */
    private fun generateSingletonField(node: DependencyNode): PropertySpec {
        val typeCls = node.type.toTypeName()
        val fieldName = "_${generateMethodName(node)}"

        return PropertySpec.builder(fieldName, typeCls.copy(nullable = true), KModifier.PRIVATE)
            .addAnnotation(Volatile::class)
            .mutable(true)
            .initializer("null")
            .build()
    }

    /**
     * Generates an initialized flag for singleton dependencies.
     */
    private fun generateInitializedFlag(node: DependencyNode): PropertySpec {
        val flagName = "_${generateMethodName(node)}_initialized"

        return PropertySpec.builder(flagName, Boolean::class, KModifier.PRIVATE)
            .addAnnotation(Volatile::class)
            .mutable(true)
            .initializer("false")
            .build()
    }

    /**
     * Generates a lock field for a singleton dependency.
     */
    private fun generateLockField(node: DependencyNode): PropertySpec {
        val lockFieldName = "_lock_${generateMethodName(node)}"

        return PropertySpec.builder(lockFieldName, Any::class, KModifier.PRIVATE)
            .initializer("Any()")
            .build()
    }

    /**
     * Generates an alias provider method that delegates to the canonical provider.
     */
    private fun generateAliasProviderMethod(node: DependencyNode, aliasType: KSType): FunSpec {
        val aliasTypeCls = aliasType.toTypeName()
        val canonicalMethodName = generateMethodName(node)
        val aliasMethodName = generateMethodName(aliasType, node.qualifier)

        return FunSpec.builder(aliasMethodName)
            .returns(aliasTypeCls)
            .addStatement("return $canonicalMethodName()")
            .build()
    }

    /**
     * Generates a provider method for a dependency.
     */
    private fun generateProviderMethod(
        node: DependencyNode,
        currentScope: KSType?,
        allNodes: List<DependencyNode>,
        scopeGraph: ScopeGraph,
    ): FunSpec {
        val typeCls = node.type.toTypeName()
        val methodName = generateMethodName(node)
        val method = FunSpec.builder(methodName)
            .returns(typeCls)

        // Use DCL for singletons and scoped bindings
        val useDCL = node.isSingleton || node.scopeAnnotation != null

        if (useDCL) {
            // Singleton or scoped: double-checked locking pattern with initialized flag
            val fieldName = "_$methodName"
            val initFlagName = "${fieldName}_initialized"
            val lockName = "_lock_$methodName"
            // Field is stored as nullable, need !! for non-nullable return types
            val fieldReturn = if (node.type.isMarkedNullable) fieldName else "$fieldName!!"
            method.addCode(
                buildCodeBlock {
                    addStatement("if ($initFlagName) return $fieldReturn")
                    addStatement("synchronized($lockName) {")
                    indent()
                    addStatement("if ($initFlagName) return $fieldReturn")
                    add("val v = ")
                    addProviderCall(node, currentScope, allNodes, scopeGraph)
                    addStatement("$fieldName = v")
                    addStatement("$initFlagName = true")
                    addStatement("return v")
                    unindent()
                    addStatement("}")
                },
            )
        } else {
            // Factory: direct call, no caching
            method.addCode(
                buildCodeBlock {
                    add("return ")
                    addProviderCall(node, currentScope, allNodes, scopeGraph)
                },
            )
        }

        return method.build()
    }

    /**
     * Adds the code to create an instance (constructor call or module method call).
     * Uses scope-aware resolution to generate qualified dependency calls.
     */
    private fun CodeBlock.Builder.addProviderCall(
        node: DependencyNode,
        currentScope: KSType?,
        allNodes: List<DependencyNode>,
        scopeGraph: ScopeGraph,
    ) {
        /**
         * Generates a qualified dependency call based on where the dependency is resolved.
         */
        fun generateDependencyCall(dep: DependencyRef): String {
            val (resolvedNode, _) = resolveDependency(dep.type, dep.qualifier, currentScope, allNodes, scopeGraph)
                ?: return "/* UNRESOLVED: ${dep.type.declaration.simpleName.asString()} */"

            // Use the requested dependency type to generate method name (not the node's primary type)
            // This ensures we call userRepository() when UserRepository is requested, even if the
            // underlying implementation is UserRepositoryImpl
            val methodName = generateMethodName(dep.type, dep.qualifier)
            val targetScope = resolvedNode.scopeAnnotation

            return when {
                // 1) Singleton / unscoped → StitchDiComponent
                targetScope == null -> "StitchDiComponent.$methodName()"

                // 2) We are in DI component (currentScope == null) but resolved to scoped node
                //    This should not happen if validateScopedDependencies did its job
                currentScope == null -> "/* invalid scoped dep from DI component: $methodName */"

                // 3) Same scope
                targetScope.declaration == currentScope.declaration -> "$methodName()"

                // 4) Ancestor scope: build chain of .upstream
                else -> {
                    val chain = buildUpstreamChain(
                        currentScope = currentScope,
                        targetScope = targetScope,
                        scopeGraph = scopeGraph,
                    )
                    "$chain.$methodName()"
                }
            }
        }

        val isInjectConstructor = node.providerFunction.isConstructor()

        if (isInjectConstructor) {
            // @Inject constructor
            val typeCls = node.type.toClassName()
            val constructorParamCount = node.dependencies.size - node.injectableFields.size
            val constructorParams = node.dependencies.take(constructorParamCount)

            if (constructorParams.isEmpty() && node.injectableFields.isEmpty()) {
                add("%T()\n", typeCls)
            } else {
                // Create instance with field injection
                if (constructorParams.isEmpty()) {
                    add("%T()", typeCls)
                } else {
                    add("%T(\n", typeCls)
                    indent()
                    constructorParams.forEachIndexed { i, dep ->
                        val depCall = generateDependencyCall(dep)
                        val comma = if (i < constructorParams.size - 1) "," else ""
                        addStatement("$depCall$comma")
                    }
                    unindent()
                    add(")")
                }

                // Field injection using .also
                if (node.injectableFields.isNotEmpty()) {
                    add(".also { instance ->\n")
                    indent()
                    node.injectableFields.forEach { field ->
                        val fieldDep = DependencyRef(field.type, field.qualifier)
                        val depCall = generateDependencyCall(fieldDep)
                        addStatement("instance.${field.name} = $depCall")
                    }
                    unindent()
                    add("}\n")
                } else {
                    // No field injection, just end the statement
                    add("\n")
                }
            }
        } else {
            // @Provides method
            val module = node.providerModule
            val moduleClass = module.toClassName()
            val isObjectModule = module.classKind == ClassKind.OBJECT
            val moduleAccess = if (isObjectModule) {
                "%T"
            } else {
                "_Modules._${moduleClass.simpleName.replaceFirstChar { it.lowercase() }}"
            }
            val fn = node.providerFunction.simpleName.asString()

            if (node.dependencies.isEmpty()) {
                if (isObjectModule) {
                    add("$moduleAccess.$fn()\n", moduleClass)
                } else {
                    add("$moduleAccess.$fn()\n")
                }
            } else {
                if (isObjectModule) {
                    add("$moduleAccess.$fn(\n", moduleClass)
                } else {
                    add("$moduleAccess.$fn(\n")
                }
                indent()
                node.dependencies.forEachIndexed { i, dep ->
                    val depCall = generateDependencyCall(dep)
                    val comma = if (i < node.dependencies.size - 1) "," else ""
                    addStatement("$depCall$comma")
                }
                unindent()
                add(")\n")
            }
        }
    }

    /**
     * Generates a field injector method for DI component (singletons/unscoped only).
     * Only injects fields that are resolvable from the singleton scope.
     */
    private fun generateFieldInjector(
        classDeclaration: KSClassDeclaration,
        fields: List<InjectableFieldInfo>,
        allNodes: List<DependencyNode>,
        scopeGraph: ScopeGraph,
    ): FunSpec {
        val className = classDeclaration.toClassName()

        return FunSpec.builder("inject")
            .addParameter("target", className)
            .addCode(
                buildCodeBlock {
                    fields.forEach { field ->
                        // Only inject fields resolvable from singleton scope (null)
                        val resolved = resolveDependency(
                            depType = field.type,
                            depQualifier = field.qualifier,
                            currentScope = null,
                            allNodes = allNodes,
                            scopeGraph = scopeGraph,
                        ) ?: return@forEach // Skip unresolvable fields

                        val depMethodName = generateMethodName(field.type, field.qualifier)
                        addStatement("target.${field.name} = $depMethodName()")
                    }
                },
            )
            .build()
    }

    private fun generateModuleHolders(moduleClasses: List<KSClassDeclaration>): TypeSpec {
        val type = TypeSpec.objectBuilder("_Modules").addModifiers(KModifier.PRIVATE)
        moduleClasses.forEach { m ->
            val className = m.toClassName()
            val propName = "_${className.simpleName.replaceFirstChar { it.lowercase() }}"
            type.addProperty(
                PropertySpec.builder(propName, className)
                    .initializer("%T()", className)
                    .addModifiers(KModifier.INTERNAL)
                    .build(),
            )
        }
        return type.build()
    }

    /**
     * Generates a method name for a dependency.
     * Format: {typeName} for unqualified, {typeName}_named_{value} for Named qualifiers
     */
    private fun generateMethodName(node: DependencyNode): String {
        return generateMethodName(node.type, node.qualifier)
    }

    private fun generateMethodName(type: KSType, qualifier: QualifierInfo?): String {
        val typeName = type.declaration.simpleName.asString()
        val baseName = typeName.replaceFirstChar { it.lowercase() }

        return when (qualifier) {
            null -> baseName
            is QualifierInfo.Named -> "${baseName}_named_${sanitizeName(qualifier.value)}"
            is QualifierInfo.Custom -> error("Custom qualifiers not supported in v1.0")
        }
    }

    private fun sanitizeName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9]"), "_")
    }

    private fun KSType.toClassName(): ClassName {
        val declaration = this.declaration
        val packageName = declaration.packageName.asString()
        val simpleName = declaration.simpleName.asString()
        return ClassName(packageName, simpleName)
    }

    private fun KSClassDeclaration.toClassName(): ClassName {
        val packageName = this.packageName.asString()
        val simpleName = this.simpleName.asString()
        return ClassName(packageName, simpleName)
    }

    private fun QualifierInfo.toCode(): String =
        when (this) {
            is QualifierInfo.Named -> "com.harrytmthy.stitch.api.named(${this.value.quote()})"
            is QualifierInfo.Custom -> error("Custom qualifiers are not supported in v1.0")
        }

    private fun String.quote(): String =
        buildString {
            append('"')
            for (ch in this@quote) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }

    /**
     * Builds the upstream chain from currentScope to targetScope.
     * Returns "upstream", "upstream.upstream", etc.
     */
    private fun buildUpstreamChain(
        currentScope: KSType,
        targetScope: KSType,
        scopeGraph: ScopeGraph,
    ): String {
        var chain = "upstream"
        var cursor: KSType? = currentScope

        while (true) {
            val parent = scopeGraph.scopes[cursor]?.dependsOn
                ?: error("Scope ${cursor?.declaration?.simpleName?.asString()} has no upstream, but dependency resolved in ${targetScope.declaration.simpleName.asString()}")

            if (parent.declaration == targetScope.declaration) {
                return chain // e.g. "upstream" or "upstream.upstream"
            }

            chain += ".upstream"
            cursor = parent
        }
    }

    /**
     * Represents where a dependency is located for resolution.
     */
    private enum class DependencyLocation {
        SAME_SCOPE, // In the current scope component
        UPSTREAM, // In an ancestor custom scope (access via upstream)
        SINGLETON, // In singleton/unscoped (access via StitchDiComponent)
    }

    private companion object {
        const val GENERATED_PACKAGE = "com.harrytmthy.stitch.generated"
        const val DI_COMPONENT_NAME = "StitchDiComponent"
    }
}

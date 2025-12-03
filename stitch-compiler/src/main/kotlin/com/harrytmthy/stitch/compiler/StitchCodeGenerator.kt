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
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
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

    private lateinit var dependencyRegistry: Map<BindingKey, DependencyNode>

    private lateinit var scopeGraph: ScopeGraph

    /**
     * Finds a dependency and determines its location relative to the current scope.
     */
    private fun resolveDependency(
        depType: KSType,
        depQualifier: QualifierInfo?,
        currentScope: KSType?,
    ): DependencyNode? {
        // 1. Try current scope first
        if (currentScope != null) {
            val currentKey = BindingKey(depType, depQualifier, currentScope)
            dependencyRegistry[currentKey]?.let {
                return it
            }
        }

        // 2. Walk up the scope chain to find in ancestor scopes
        if (currentScope != null) {
            var ancestorScope: KSType? = scopeGraph.scopeDependencies[currentScope]
            while (ancestorScope != null) {
                val ancestorKey = BindingKey(depType, depQualifier, ancestorScope)
                dependencyRegistry[ancestorKey]?.let {
                    return it
                }
                ancestorScope = scopeGraph.scopeDependencies[ancestorScope]
            }
        }

        // 3. Try singleton/unscoped (scope = null)
        val singletonKey = BindingKey(depType, depQualifier, null)
        dependencyRegistry[singletonKey]?.let {
            return it
        }

        // Not found
        return null
    }

    fun generateComponentAndInjector(
        nodes: List<DependencyNode>,
        registry: Map<BindingKey, DependencyNode>,
        fieldInjectors: List<FieldInjectorInfo>,
        scopeGraph: ScopeGraph,
    ) {
        // Store registry and scopeGraph as fields for use in resolveDependency
        this.dependencyRegistry = registry
        this.scopeGraph = scopeGraph

        logger.info("Stitch: Generating DI component with ${nodes.size} bindings and ${fieldInjectors.size} injectors")

        val dependencies = Dependencies(
            aggregating = true,
            sources = nodes.flatMap { listOf(it.providerModule.containingFile, it.providerFunction.containingFile) }
                .plus(fieldInjectors.map { it.classDeclaration.containingFile })
                .filterNotNull()
                .distinct()
                .toTypedArray(),
        )

        // Collect field injection requests by scope (including transitive dependencies)
        val requestedDepsByScope = mutableMapOf<KSType?, MutableSet<BindingKey>>()

        // Build node lookup map (index both canonical types and aliases)
        val nodeMap = mutableMapOf<BindingKey, DependencyNode>()
        for (node in nodes) {
            // Index canonical type
            nodeMap[BindingKey(node.type, node.qualifier)] = node
            // Index all aliases
            node.aliases.forEach { aliasType ->
                nodeMap[BindingKey(aliasType, node.qualifier)] = node
            }
        }

        // Helper to transitively collect dependencies
        fun collectTransitiveDeps(node: DependencyNode, visited: MutableSet<DependencyNode> = mutableSetOf()) {
            if (node in visited) return
            visited.add(node)

            // Add the node itself to its binding's scope
            val key = BindingKey(node.type, node.qualifier)
            val bindingScope = node.scopeAnnotation
            requestedDepsByScope.getOrPut(bindingScope) { mutableSetOf() }.add(key)

            // Recursively collect transitive dependencies
            node.dependencies.forEach { dep ->
                val depKey = BindingKey(dep.type, dep.qualifier)
                nodeMap[depKey]?.let { depNode ->
                    collectTransitiveDeps(depNode, visited)
                }
            }
        }

        // For each field injection, collect the field type and its transitive deps
        fieldInjectors.forEach { injector ->
            injector.injectableFields.forEach { field ->
                val fieldKey = BindingKey(field.type, field.qualifier)
                nodeMap[fieldKey]?.let { node ->
                    // Collect this node and all its transitive dependencies
                    collectTransitiveDeps(node)
                }
            }
        }

        // Separate nodes by scope
        val unscopedNodes = nodes.filter { it.scopeAnnotation == null }
        val scopedNodesByScope = nodes.filter { it.scopeAnnotation != null }
            .groupBy { it.scopeAnnotation!! }

        // Generate ModuleProvider file (before DiComponent and BindingProvider)
        val moduleProviderFile = generateModuleProvider(nodes)
        if (moduleProviderFile != null) {
            val moduleProviderStream = codeGenerator.createNewFile(
                dependencies = dependencies,
                packageName = GENERATED_PACKAGE,
                fileName = "ModuleProvider",
            )
            OutputStreamWriter(moduleProviderStream).use { writer ->
                moduleProviderFile.writeTo(writer)
            }
        }

        // Generate DI component file (singletons + factories only)
        generateDiComponentFile(unscopedNodes, fieldInjectors, dependencies, requestedDepsByScope)

        // Generate scope component files for every scope in the graph
        val allScopes = scopeGraph.scopeDependencies.keys
        allScopes.forEach { scopeAnnotation ->
            val scopeNodes = scopedNodesByScope[scopeAnnotation] ?: emptyList()
            generateScopeComponentFile(scopeAnnotation, scopeNodes, fieldInjectors, dependencies, requestedDepsByScope)
        }

        // Generate BindingProvider file (contains factory methods for non-singleton bindings)
        val bindingProviderFile = generateBindingProvider(nodes)
        val outputStream = codeGenerator.createNewFile(
            dependencies = dependencies,
            packageName = GENERATED_PACKAGE,
            fileName = "BindingProvider",
        )
        OutputStreamWriter(outputStream).use { writer ->
            bindingProviderFile.writeTo(writer)
        }

        val moduleProviderMsg = if (moduleProviderFile != null) "ModuleProvider, " else ""
        logger.info("Stitch: Generated ${moduleProviderMsg}StitchDiComponent, ${allScopes.size} scope component(s), BindingProvider")
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
        dependencies: Dependencies,
        requestedDepsByScope: Map<KSType?, Set<BindingKey>>,
    ) {
        val upstreamScope = scopeGraph.scopeDependencies[scopeAnnotation]
        val scopeName = scopeAnnotation.declaration.simpleName.asString()
        val componentClassName = "Stitch${scopeName}Component"

        val component = TypeSpec.classBuilder(componentClassName)
            .superclass(ClassName("com.harrytmthy.stitch.internal", "StitchComponent"))

        // Get requested deps for this scope
        val requestedScopedDeps = requestedDepsByScope[scopeAnnotation] ?: emptySet()

        // Add upstream property for ALL scopes
        // Root scopes: upstream is StitchDiComponent
        // Non-root scopes: upstream is the parent scope component
        val upstreamClassName = if (upstreamScope == null) {
            ClassName(GENERATED_PACKAGE, DI_COMPONENT_NAME)
        } else {
            val upstreamScopeName = upstreamScope.declaration.simpleName.asString()
            ClassName(GENERATED_PACKAGE, "Stitch${upstreamScopeName}Component")
        }

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

        // Generate DCL fields and provider methods for scoped dependencies
        nodes.forEach { node ->
            // Check if this node is requested via field injection
            val nodeKey = BindingKey(node.type, node.qualifier)
            val isRequested = nodeKey in requestedScopedDeps

            // Add DCL field, initialized flag, and lock only if requested
            if (isRequested) {
                component.addProperty(generateSingletonField(node))
                component.addProperty(generateInitializedFlag(node))
                component.addProperty(generateLockField(node))
            }

            // Generate canonical provider method (with current scope context)
            component.addFunction(generateProviderMethod(node, isRequested))

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
                generateScopeFieldInjector(fieldInjector, scopeAnnotation),
            )
        }

        // Generate factory methods for child scopes (scopes that depend on this scope)
        for ((scope, dependsOn) in scopeGraph.scopeDependencies) {
            if (dependsOn != scopeAnnotation) {
                continue
            }
            val childScopeName = scope.declaration.simpleName.asString()
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
            .addImport("kotlinx.atomicfu", "atomic")
            .addImport("kotlinx.atomicfu.locks", "synchronized")
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
    ): FunSpec {
        val className = fieldInjector.classDeclaration.toClassName()
        val methodName = "inject"

        val method = FunSpec.builder(methodName)
            .addParameter("target", className)

        method.addCode(
            buildCodeBlock {
                // Inject ALL fields directly with qualified calls
                fieldInjector.injectableFields.forEach { field ->
                    val resolvedNode = resolveDependency(
                        field.type,
                        field.qualifier,
                        scopeAnnotation,
                    ) ?: return@forEach // Skip if not found (error already reported)

                    val depMethodName = generateDependencyName(field.type, field.qualifier)
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
        dependencies: Dependencies,
        requestedDepsByScope: Map<KSType?, Set<BindingKey>>,
    ) {
        val file = FileSpec.builder(GENERATED_PACKAGE, DI_COMPONENT_NAME)
            .addFileComment("Generated by Stitch KSP Compiler - DO NOT EDIT")
            .addImport("kotlinx.atomicfu", "atomic")
            .addImport("kotlinx.atomicfu.locks", "synchronized")
            .addType(generateDiComponent(nodes, fieldInjectors, requestedDepsByScope))
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
        requestedDepsByScope: Map<KSType?, Set<BindingKey>>,
    ): TypeSpec {
        val component = TypeSpec.objectBuilder(DI_COMPONENT_NAME)
            .superclass(ClassName("com.harrytmthy.stitch.internal", "StitchComponent"))

        // Get requested deps for singleton scope (null key)
        val requestedSingletonDeps = requestedDepsByScope[null] ?: emptySet()

        // Generate fields and methods for each dependency
        nodes.forEach { node ->
            // Check if this node is requested via field injection
            val nodeKey = BindingKey(node.type, node.qualifier)
            val isRequested = nodeKey in requestedSingletonDeps

            // Add field and lock for singletons only if requested via field injection
            if (node.isSingleton && isRequested) {
                component.addProperty(generateSingletonField(node))
                component.addProperty(generateInitializedFlag(node))
                component.addProperty(generateLockField(node))
            }

            // Generate canonical provider method (currentScope = null for DI component)
            component.addFunction(generateProviderMethod(node, isRequested))

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
                    ),
                )
            }
        }

        // Generate factory methods for root custom scopes (scopes that depend on Singleton)
        for ((scope, dependsOn) in scopeGraph.scopeDependencies) {
            if (dependsOn != null) {
                continue
            }
            val scopeName = scope.declaration.simpleName.asString()
            val componentClassName = ClassName(GENERATED_PACKAGE, "Stitch${scopeName}Component")

            component.addFunction(
                FunSpec.builder("create${scopeName}Component")
                    .returns(componentClassName)
                    .addStatement("return %T(upstream = this)", componentClassName)
                    .build(),
            )
        }
        return component.build()
    }

    /**
     * Generates an atomic reference field for a singleton dependency.
     */
    private fun generateSingletonField(node: DependencyNode): PropertySpec {
        val typeCls = node.type.toTypeName()
        val fieldName = "_${generateDependencyName(node)}_ref"

        return PropertySpec.builder(
            fieldName,
            ClassName("kotlinx.atomicfu", "AtomicRef").parameterizedBy(typeCls.copy(nullable = true)),
            KModifier.PRIVATE,
        )
            .initializer("atomic(null)")
            .build()
    }

    /**
     * Generates an atomic boolean flag for singleton dependencies.
     */
    private fun generateInitializedFlag(node: DependencyNode): PropertySpec {
        val flagName = "_${generateDependencyName(node)}_initialized"

        return PropertySpec.builder(
            flagName,
            ClassName("kotlinx.atomicfu", "AtomicBoolean"),
            KModifier.PRIVATE,
        )
            .initializer("atomic(false)")
            .build()
    }

    /**
     * Generates a lock field for a singleton dependency.
     */
    private fun generateLockField(node: DependencyNode): PropertySpec {
        val lockFieldName = "_lock_${generateDependencyName(node)}"

        return PropertySpec.builder(lockFieldName, Any::class, KModifier.PRIVATE)
            .initializer("Any()")
            .build()
    }

    /**
     * Generates an alias provider method that delegates to the canonical provider.
     */
    private fun generateAliasProviderMethod(node: DependencyNode, aliasType: KSType): FunSpec {
        val aliasTypeCls = aliasType.toTypeName()
        val canonicalMethodName = generateDependencyName(node)
        val aliasMethodName = generateDependencyName(aliasType, node.qualifier)

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
        isRequested: Boolean, // Whether this type is requested via field injection
    ): FunSpec {
        val typeCls = node.type.toTypeName()
        val methodName = generateDependencyName(node)
        val method = FunSpec.builder(methodName)
            .returns(typeCls)

        if (isRequested && (node.isSingleton || node.scopeAnnotation != null)) {
            // DCL for requested node
            val fieldRefName = "_${methodName}_ref"
            val initFlagName = "_${methodName}_initialized"
            val lockName = "_lock_$methodName"
            val fieldReturn = if (node.type.isMarkedNullable) "$fieldRefName.value" else "$fieldRefName.value!!"
            method.addCode(
                buildCodeBlock {
                    addStatement("if ($initFlagName.value) return $fieldReturn")
                    addStatement("synchronized($lockName) {")
                    indent()
                    addStatement("if ($initFlagName.value) return $fieldReturn")
                    add("val v = ")
                    if (node.isSingleton) {
                        addProviderCall(node)
                    } else {
                        addBindingProviderCall(node)
                    }
                    addStatement("$fieldRefName.value = v")
                    addStatement("$initFlagName.value = true")
                    addStatement("return v")
                    unindent()
                    addStatement("}")
                },
            )
        } else {
            // Factory method for unrequested singletons
            method.addCode(
                buildCodeBlock {
                    add("return ")
                    if (node.isSingleton) {
                        addProviderCall(node)
                    } else {
                        addBindingProviderCall(node)
                    }
                },
            )
        }

        return method.build()
    }

    /**
     * Adds a call to BindingProvider with resolved dependencies to the CodeBlock.
     * Generates code like: BindingProvider.methodName(param1 = dep1(), param2 = dep2())
     */
    private fun CodeBlock.Builder.addBindingProviderCall(node: DependencyNode) {
        val methodName = generateDependencyName(node)
        val bindingProviderClass = ClassName(GENERATED_PACKAGE, "BindingProvider")

        if (node.dependencies.isEmpty()) {
            add("%T.$methodName()\n", bindingProviderClass)
        } else {
            add("%T.$methodName(\n", bindingProviderClass)
            indent()
            for (dep in node.dependencies) {
                val paramName = generateDependencyName(dep.type, dep.qualifier)

                // Resolve where this dependency is located
                val resolved = resolveDependency(dep.type, dep.qualifier, node.scopeAnnotation)
                if (resolved == null) {
                    reportUnresolvedDependency(dep.type, dep.qualifier, node.scopeAnnotation)
                    add("$paramName = error(\"Unresolved dependency\")")
                    add(",\n")
                    continue
                }

                val depMethodName = generateDependencyName(dep.type, dep.qualifier)
                val targetScope = resolved.scopeAnnotation

                add("$paramName = ")
                when {
                    // Singleton / unscoped
                    targetScope == null -> {
                        if (node.scopeAnnotation == null) {
                            // We're in DiComponent
                            add("$depMethodName()")
                        } else {
                            // Navigate upstream to DiComponent
                            val chain = buildUpstreamChain(node.scopeAnnotation, null, scopeGraph)
                            add("$chain.$depMethodName()")
                        }
                    }

                    // We're in DiComponent but resolved to scoped node (shouldn't happen)
                    node.scopeAnnotation == null -> add("/* invalid scoped dep from DI component: $depMethodName */")

                    // Same scope
                    targetScope.declaration == node.scopeAnnotation.declaration -> add("$depMethodName()")

                    // Ancestor scope: build chain of .upstream
                    else -> {
                        val chain = buildUpstreamChain(node.scopeAnnotation, targetScope, scopeGraph)
                        add("$chain.$depMethodName()")
                    }
                }
                add(",\n")
            }
            unindent()
            add(")\n")
        }
    }

    /**
     * Adds the code to create an instance (constructor call or module method call).
     * Uses scope-aware resolution to generate qualified dependency calls.
     */
    private fun CodeBlock.Builder.addProviderCall(node: DependencyNode) {
        /**
         * Generates a qualified dependency call based on where the dependency is resolved.
         */
        fun generateDependencyCall(type: KSType, qualifier: QualifierInfo?): String {
            val resolved = resolveDependency(type, qualifier, node.scopeAnnotation)
            if (resolved == null) {
                reportUnresolvedDependency(type, qualifier, node.scopeAnnotation)
                return "error(\"Unresolved dependency: ${type.declaration.simpleName.asString()}\")"
            }

            // Use the requested dependency type to generate method name (not the node's primary type)
            // This ensures we call userRepository() when UserRepository is requested, even if the
            // underlying implementation is UserRepositoryImpl
            val methodName = generateDependencyName(type, qualifier)
            val targetScope = resolved.scopeAnnotation

            return when {
                // 1) Singleton / unscoped → StitchDiComponent
                targetScope == null -> "StitchDiComponent.$methodName()"

                // 2) We are in DI component (currentScope == null) but resolved to scoped node
                //    This should not happen if validateScopedDependencies did its job
                node.scopeAnnotation == null -> "/* invalid scoped dep from DI component: $methodName */"

                // 3) Same scope
                targetScope.declaration == node.scopeAnnotation.declaration -> "$methodName()"

                // 4) Ancestor scope: build chain of .upstream
                else -> {
                    val chain = buildUpstreamChain(
                        currentScope = node.scopeAnnotation,
                        targetScope = targetScope,
                        scopeGraph = scopeGraph,
                    )
                    "$chain.$methodName()"
                }
            }
        }

        if (node.providerFunction.isConstructor()) {
            // @Inject constructor
            val typeCls = node.type.toClassName()
            val constructorParams = node.constructorParameters

            if (constructorParams.isEmpty() && node.injectableFields.isEmpty()) {
                add("%T()\n", typeCls)
            } else {
                // Create instance with field injection
                if (constructorParams.isEmpty()) {
                    add("%T()", typeCls)
                } else {
                    add("%T(\n", typeCls)
                    indent()
                    for (field in constructorParams) {
                        val depCall = generateDependencyCall(field.type, field.qualifier)
                        addStatement("$depCall,")
                    }
                    unindent()
                    add(")")
                }

                // Field injection using .apply
                if (node.injectableFields.isNotEmpty()) {
                    add(".apply {\n")
                    indent()
                    for (field in node.injectableFields) {
                        val depCall = generateDependencyCall(field.type, field.qualifier)
                        addStatement("this.${field.name} = $depCall")
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
            val moduleClass = node.providerModule.toClassName()
            val fn = node.providerFunction.simpleName.asString()
            if (node.providerModule.classKind == ClassKind.OBJECT) {
                // Object module: direct access
                if (node.dependencies.isEmpty()) {
                    add("%T.$fn()\n", moduleClass)
                } else {
                    add("%T.$fn(\n", moduleClass)
                    indent()
                    for (dependency in node.dependencies) {
                        val depCall = generateDependencyCall(dependency.type, dependency.qualifier)
                        addStatement("$depCall,")
                    }
                    unindent()
                    add(")\n")
                }
            } else {
                // Class module: access via ModuleProvider
                val moduleProviderClass = ClassName(GENERATED_PACKAGE, "ModuleProvider")
                val modulePropName = moduleClass.simpleName.replaceFirstChar { it.lowercase() }

                if (node.dependencies.isEmpty()) {
                    add("%T.$modulePropName.$fn()\n", moduleProviderClass)
                } else {
                    add("%T.$modulePropName.$fn(\n", moduleProviderClass)
                    indent()
                    for (dependency in node.dependencies) {
                        val depCall = generateDependencyCall(dependency.type, dependency.qualifier)
                        addStatement("$depCall,")
                    }
                    unindent()
                    add(")\n")
                }
            }
        }
    }

    /**
     * Generates a field injector method for DI component (singletons/unscoped only).
     * Only injects fields that are resolvable from the singleton scope.
     */
    private fun generateFieldInjector(
        classDeclaration: KSClassDeclaration,
        fields: List<FieldInfo>,
    ): FunSpec {
        val className = classDeclaration.toClassName()

        return FunSpec.builder("inject")
            .addParameter("target", className)
            .addCode(
                buildCodeBlock {
                    fields.forEach { field ->
                        // Only inject fields resolvable from singleton scope (null)
                        resolveDependency(
                            depType = field.type,
                            depQualifier = field.qualifier,
                            currentScope = null,
                        ) ?: return@forEach // Skip unresolvable fields

                        val depMethodName = generateDependencyName(field.type, field.qualifier)
                        addStatement("target.${field.name} = $depMethodName()")
                    }
                },
            )
            .build()
    }

    /**
     * Generates the ModuleProvider object file.
     * Holds singleton instances of class-based @Module classes.
     * Skips generation if there are no class-based modules.
     */
    private fun generateModuleProvider(nodes: List<DependencyNode>): FileSpec? {
        // Collect non-object modules from @Provides bindings
        val moduleClasses = nodes
            .filter { !it.providerFunction.isConstructor() } // Only @Provides methods
            .map { it.providerModule }
            .filter { it.classKind != ClassKind.OBJECT }
            .distinctBy { it.qualifiedName?.asString() }

        // Skip generation if no class-based modules
        if (moduleClasses.isEmpty()) {
            return null
        }

        val moduleProvider = TypeSpec.objectBuilder("ModuleProvider")

        moduleClasses.forEach { module ->
            val className = module.toClassName()
            val propName = className.simpleName.replaceFirstChar { it.lowercase() }

            moduleProvider.addProperty(
                PropertySpec.builder(propName, className)
                    .initializer("%T()", className)
                    .build(),
            )
        }

        return FileSpec.builder(GENERATED_PACKAGE, "ModuleProvider")
            .addFileComment("Generated by Stitch KSP Compiler - DO NOT EDIT")
            .addType(moduleProvider.build())
            .build()
    }

    /**
     * Generates a dependency name.
     * Format: {typeName} for unqualified, {typeName}_named_{value} for Named qualifiers
     */
    private fun generateDependencyName(node: DependencyNode): String {
        return generateDependencyName(node.type, node.qualifier)
    }

    private fun generateDependencyName(type: KSType, qualifier: QualifierInfo?): String {
        val typeName = type.declaration.simpleName.asString()
        val baseName = typeName.replaceFirstChar { it.lowercase() }

        return when (qualifier) {
            null -> baseName
            is QualifierInfo.Named -> "${baseName}_named_${sanitizeName(qualifier.value)}"
            is QualifierInfo.Custom -> error("Custom qualifiers not supported in v1.0")
        }
    }

    /**
     * Generates the BindingProvider object file.
     * Contains factory functions for all non-singleton bindings (scoped + unscoped).
     */
    private fun generateBindingProvider(nodes: List<DependencyNode>): FileSpec {
        val bindingProvider = TypeSpec.objectBuilder("BindingProvider")

        // Only generate functions for non-singleton bindings
        for (node in nodes) {
            if (node.isSingleton) {
                continue
            }
            bindingProvider.addFunction(generateBindingProviderFunction(node))
        }

        return FileSpec.builder(GENERATED_PACKAGE, "BindingProvider")
            .addFileComment("Generated by Stitch KSP Compiler - DO NOT EDIT")
            .addType(bindingProvider.build())
            .build()
    }

    /**
     * Generates a factory function for BindingProvider.
     * Only called for non-singleton bindings (scoped + unscoped).
     */
    private fun generateBindingProviderFunction(node: DependencyNode): FunSpec {
        val methodName = generateDependencyName(node)
        val returnType = node.type.toTypeName()

        val function = FunSpec.builder(methodName)
            .returns(returnType)

        // Add parameter for each dependency (including field injections)
        // BindingProvider accepts ALL dependencies as parameters
        node.dependencies.forEach { dep ->
            val paramName = generateDependencyName(dep.type, dep.qualifier)
            val paramType = dep.type.toTypeName()
            function.addParameter(paramName, paramType)
        }

        val isInjectConstructor = node.providerFunction.isConstructor()
        val constructorDeps = node.constructorParameters

        // Generate construction call
        if (isInjectConstructor) {
            // @Inject constructor: new ClassName(param1, param2, ...)
            val typeCls = node.type.toClassName()
            function.addCode(
                buildCodeBlock {
                    if (constructorDeps.isEmpty() && node.injectableFields.isEmpty()) {
                        addStatement("return %T()", typeCls)
                    } else {
                        if (constructorDeps.isEmpty()) {
                            add("return %T()", typeCls)
                        } else {
                            add("return %T(\n", typeCls)
                            indent()
                            for (field in constructorDeps) {
                                val paramName = generateDependencyName(field.type, field.qualifier)
                                addStatement("$paramName,")
                            }
                            unindent()
                            add(")")
                        }

                        // Field injection using .apply
                        if (node.injectableFields.isNotEmpty()) {
                            add(".apply {\n")
                            indent()
                            for (field in node.injectableFields) {
                                val paramName = generateDependencyName(field.type, field.qualifier)
                                addStatement("this.${field.name} = $paramName")
                            }
                            unindent()
                            add("}\n")
                        } else {
                            add("\n")
                        }
                    }
                },
            )
        } else {
            // @Provides function: Use ModuleProvider for class modules
            val moduleClass = node.providerModule.toClassName()
            val providerMethod = node.providerFunction.simpleName.asString()
            val isObjectModule = node.providerModule.classKind == ClassKind.OBJECT
            val moduleProviderClass = ClassName(GENERATED_PACKAGE, "ModuleProvider")

            function.addCode(
                buildCodeBlock {
                    if (isObjectModule) {
                        // Object module: direct access
                        if (constructorDeps.isEmpty()) {
                            addStatement("return %T.$providerMethod()", moduleClass)
                        } else {
                            add("return %T.$providerMethod(\n", moduleClass)
                            indent()
                            for (field in constructorDeps) {
                                val paramName = generateDependencyName(field.type, field.qualifier)
                                addStatement("$paramName,")
                            }
                            unindent()
                            addStatement(")")
                        }
                    } else {
                        // Class module: access via ModuleProvider
                        val modulePropName = moduleClass.simpleName.replaceFirstChar { it.lowercase() }
                        if (constructorDeps.isEmpty()) {
                            addStatement("return %T.$modulePropName.$providerMethod()", moduleProviderClass)
                        } else {
                            add("return %T.$modulePropName.$providerMethod(\n", moduleProviderClass)
                            indent()
                            for (field in constructorDeps) {
                                val paramName = generateDependencyName(field.type, field.qualifier)
                                addStatement("$paramName,")
                            }
                            unindent()
                            addStatement(")")
                        }
                    }
                },
            )
        }

        return function.build()
    }

    private fun reportUnresolvedDependency(
        depType: KSType,
        depQualifier: QualifierInfo?,
        currentScope: KSType?,
    ) {
        val typeName = depType.declaration.qualifiedName?.asString() ?: depType.toString()
        val qualifier = when (depQualifier) {
            null -> "<default>"
            is QualifierInfo.Named -> "@Named(\"${depQualifier.value}\")"
            is QualifierInfo.Custom -> error("Custom qualifiers are not supported in v1.0")
        }
        val scopeName = currentScope?.declaration?.qualifiedName?.asString() ?: "singleton"
        logger.error("Failed to resolve dependency $typeName ($qualifier) from scope $scopeName")
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

    /**
     * Builds the upstream chain from currentScope to targetScope.
     * Returns "upstream", "upstream.upstream", etc.
     */
    private fun buildUpstreamChain(
        currentScope: KSType,
        targetScope: KSType?,
        scopeGraph: ScopeGraph,
    ): String {
        var chain = "upstream"
        var current: KSType? = currentScope

        while (true) {
            val parent = scopeGraph.scopeDependencies[current] ?: return chain

            if (parent.declaration == targetScope?.declaration) {
                return chain // e.g. "upstream" or "upstream.upstream"
            }

            chain += ".upstream"
            current = parent
        }
    }

    private companion object {
        const val GENERATED_PACKAGE = "com.harrytmthy.stitch.generated"
        const val DI_COMPONENT_NAME = "StitchDiComponent"
    }
}

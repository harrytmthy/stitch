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
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

class AnnotationScanner(private val resolver: Resolver) {

    private val annotationsBySymbol = HashMap<KSAnnotated, SymbolAnnotations>()

    /**
     * Groups field injection requests by the class that owns the field.
     */
    private val fieldInjections = HashMap<KSClassDeclaration, ArrayList<KSPropertyDeclaration>>()

    /**
     * Tracks the origin of an existing node.
     */
    private val symbolByNode = HashMap<BindingNode, KSAnnotated>()

    /**
     * Represents the dependency graph.
     */
    private val bindingEdges = HashMap<BindingNode, HashSet<BindingNode>>()

    fun scan() {
        scanScopes()
        scanQualifiers()
        scanProvides()
        scanInjects()
        scanBinds()
    }

    private fun scanScopes() {
        for (singletonAnnotation in listOf(STITCH_SINGLETON, JAVAX_SINGLETON)) {
            for (symbol in resolver.getSymbolsWithAnnotation(singletonAnnotation)) {
                getOrCreateSymbolAnnotations(symbol).apply { singleton = true }
            }
        }
        for (symbol in resolver.getSymbolsWithAnnotation(SCOPE)) {
            annotationsBySymbol[symbol]?.let { annotations ->
                if (annotations.singleton) {
                    fatalError("@Scope cannot be used with @Singleton at the same time", symbol)
                }
            }
            val scopeName = symbol.annotations.find(SCOPE).arguments.first().value as String
            when (symbol) {
                is KSClassDeclaration -> getOrCreateSymbolAnnotations(symbol).apply { scope = scopeName }

                is KSFunctionDeclaration -> {
                    if (symbol.isConstructor()) {
                        fatalError("@Scope cannot be used on constructors", symbol)
                    }
                    getOrCreateSymbolAnnotations(symbol).apply { scope = scopeName }
                }
            }
        }
    }

    private fun scanQualifiers() {
        scanNamedQualifiers()
        // TODO: Add more qualifier types
    }

    private fun scanNamedQualifiers() {
        for (annotationName in listOf(STITCH_NAMED, JAVAX_NAMED)) {
            for (symbol in resolver.getSymbolsWithAnnotation(annotationName)) {
                val name = symbol.annotations.find(annotationName).arguments.first().value as String
                getOrCreateSymbolAnnotations(symbol).qualifier = Qualifier.Named(name)
            }
        }
    }

    private fun scanProvides() {
        for (symbol in resolver.getSymbolsWithAnnotation(PROVIDES)) {
            if (symbol !is KSFunctionDeclaration) {
                fatalError("@Provides can only be used on functions", symbol)
            }
            val type = symbol.returnType?.resolve()
                ?: fatalError("@Provides has no return type", symbol)
            val annotations = getOrCreateSymbolAnnotations(symbol).apply { provides = true }
            val node = BindingNode(type, annotations.qualifier)
            symbolByNode[node]?.let { duplicateBindingError(node, symbol, it) }
            symbolByNode[node] = symbol
            val dependencies = bindingEdges.getOrPut(node) { HashSet() }
            for (functionParameter in symbol.parameters) {
                val type = functionParameter.type.resolve()
                val qualifier = annotationsBySymbol[functionParameter]?.qualifier
                val node = BindingNode(type, qualifier)
                dependencies.add(node)
            }
        }
    }

    /**
     * Constructor injections require a special treatment where the one registered
     * in [annotationsBySymbol] is the class declaration instead of the constructor,
     * since other annotations are targeting the class, not the constructor.
     */
    private fun scanInjects() {
        for (annotationName in listOf(STITCH_INJECT, JAVAX_INJECT)) {
            for (symbol in resolver.getSymbolsWithAnnotation(annotationName)) {
                if (annotationsBySymbol[symbol]?.provides == true) {
                    fatalError(
                        message = "$symbol is annotated with @Provides & @Inject at the same time",
                        symbol = symbol,
                    )
                }
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
        val annotations = getOrCreateSymbolAnnotations(canonicalSymbol)
        if (annotations.inject != null) {
            fatalError("Multiple @Inject-annotated constructors found. Only one is allowed", canonicalSymbol)
        }
        annotations.inject = Inject.Constructor
        val type = canonicalSymbol.asStarProjectedType()
        val node = BindingNode(type, annotations.qualifier)
        symbolByNode[node]?.let { duplicateBindingError(node, symbol, it) }
        symbolByNode[node] = canonicalSymbol
        val dependencies = bindingEdges.getOrPut(node) { HashSet() }
        for (functionParameter in symbol.parameters) {
            val type = functionParameter.type.resolve()
            val qualifier = annotationsBySymbol[functionParameter]?.qualifier
            val node = BindingNode(type, qualifier)
            dependencies.add(node)
        }
    }

    private fun handleFieldInjection(symbol: KSPropertyDeclaration) {
        if (!symbol.isMutable) {
            fatalError("@Inject field '${symbol.simpleName}' must be mutable", symbol)
        }
        if (symbol.modifiers.contains(Modifier.PRIVATE)) {
            fatalError("@Inject field '${symbol.simpleName}' cannot be private", symbol)
        }
        val classDeclaration = symbol.parentDeclaration as KSClassDeclaration
        val fieldInjections = fieldInjections
            .getOrPut(classDeclaration) { ArrayList() }
        fieldInjections.add(symbol)
        getOrCreateSymbolAnnotations(symbol).inject = Inject.Field
    }

    private fun scanBinds() {
        for (symbol in resolver.getSymbolsWithAnnotation(BINDS)) {
            when (symbol) {
                is KSClassDeclaration -> {
                    val aliasArg = symbol.annotations.find(BINDS).findArgument("aliases")
                    val aliases = aliasArg.value as List<*>
                    if (aliases.isEmpty()) {
                        fatalError("@Binds(aliases = ...) is required when annotating classes", symbol)
                    }
                    val annotations = getOrCreateSymbolAnnotations(symbol)
                    val type = symbol.asStarProjectedType()
                    val node = BindingNode(type, annotations.qualifier)
                    for (alias in aliases) {
                        val aliasNode = BindingNode(alias as KSType, annotations.qualifier)
                        val dependencies = bindingEdges.getOrPut(aliasNode) { HashSet() }
                        dependencies.add(node) // Creates an edge: AliasNode → Node
                    }
                }

                is KSFunctionDeclaration -> {
                    if (symbol.isConstructor()) {
                        fatalError("@Binds cannot be used on constructors", symbol)
                    }
                    val returnType = symbol.returnType?.resolve()
                        ?: fatalError("@Binds requires a return type when annotating functions", symbol)
                    if (symbol.isAbstract) {
                        val parameter = symbol.parameters.singleOrNull()
                            ?: fatalError("@Binds requires one parameter when annotating abstract functions", symbol)
                        val aliasArg = symbol.annotations.find(BINDS).findArgument("aliases")
                        val annotations = getOrCreateSymbolAnnotations(symbol)
                        val node = BindingNode(parameter.type.resolve(), annotations.qualifier)
                        val aliasNode = BindingNode(returnType, annotations.qualifier)
                        val dependencies = bindingEdges.getOrPut(aliasNode) { HashSet() }
                        dependencies.add(node)
                        for (alias in (aliasArg.value as List<*>)) {
                            val aliasNode = BindingNode(alias as KSType, annotations.qualifier)
                            val dependencies = bindingEdges.getOrPut(aliasNode) { HashSet() }
                            dependencies.add(node)
                        }
                    } else {
                        val aliasArg = symbol.annotations.find(BINDS).findArgument("aliases")
                        val aliases = aliasArg.value as List<*>
                        if (aliases.isEmpty()) {
                            fatalError("@Binds(aliases = ...) is required when annotating functions", symbol)
                        }
                        val annotations = getOrCreateSymbolAnnotations(symbol)
                        val node = BindingNode(returnType, annotations.qualifier)
                        for (alias in (aliasArg.value as List<*>)) {
                            val aliasNode = BindingNode(alias as KSType, annotations.qualifier)
                            val dependencies = bindingEdges.getOrPut(aliasNode) { HashSet() }
                            dependencies.add(node)
                        }
                    }
                }
            }
        }
    }

    private fun duplicateBindingError(
        node: BindingNode,
        currentSymbol: KSAnnotated,
        existingSymbol: KSAnnotated,
    ) {
        val typeName = node.type.declaration.qualifiedName?.asString()
        val qualifierName = node.qualifier?.name
        val scopeName = annotationsBySymbol[existingSymbol]?.scope
        val existingLocation = existingSymbol.filePathAndLineNumber
        fatalError(
            message = buildString {
                append("Duplicate binding for $typeName")
                qualifierName?.let { append(" (qualifier: $it)") }
                scopeName?.let { append(" in scope \"$it\"") }
                append(".")
                existingLocation?.let { append(" Already provided by $it") }
            },
            symbol = currentSymbol,
        )
    }

    private fun getOrCreateSymbolAnnotations(symbol: KSAnnotated): SymbolAnnotations =
        annotationsBySymbol.getOrPut(symbol) { SymbolAnnotations(symbol) }

    private companion object {
        const val PROVIDES = "com.harrytmthy.stitch.annotations.Provides"
        const val STITCH_INJECT = "com.harrytmthy.stitch.annotations.Inject"
        const val JAVAX_INJECT = "javax.inject.Inject"
        const val STITCH_NAMED = "com.harrytmthy.stitch.annotations.Named"
        const val JAVAX_NAMED = "javax.inject.Named"
        const val SCOPE = "com.harrytmthy.stitch.annotations.ScopeV2"
        const val STITCH_SINGLETON = "com.harrytmthy.stitch.annotations.Singleton"
        const val JAVAX_SINGLETON = "javax.inject.Singleton"
        const val BINDS = "com.harrytmthy.stitch.annotations.Binds"
    }
}

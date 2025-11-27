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

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType

/**
 * Represents a scanned module class annotated with @Module.
 */
data class ModuleInfo(
    val declaration: KSClassDeclaration,
    val provides: List<ProvidesInfo>,
    val binds: List<BindsInfo>,
)

/**
 * Represents a provider method annotated with @Provides.
 */
data class ProvidesInfo(
    val declaration: KSFunctionDeclaration,
    val returnType: KSType,
    val parameters: List<FieldInfo>,
    val isSingleton: Boolean,
    val qualifier: QualifierInfo?,
    val aliases: List<KSType> = emptyList(),
    val scopeAnnotation: KSType? = null,
)

/**
 * Represents a @Binds method in an interface/abstract module.
 *
 * @Binds methods declare type bindings without providing implementation.
 * They must be abstract, take exactly one parameter (the implementation),
 * and return a supertype (the alias).
 */
data class BindsInfo(
    val declaration: KSFunctionDeclaration,
    val implementationType: KSType,
    val aliasType: KSType,
    val isSingleton: Boolean,
    val qualifier: QualifierInfo?,
)

/**
 * Represents qualifier information.
 */
sealed class QualifierInfo {
    data class Named(val value: String) : QualifierInfo()
    data class Custom(val qualifiedName: String) : QualifierInfo()
}

/**
 * Unified field/parameter information.
 * Replaces DependencyRef, ParameterInfo, and InjectableFieldInfo.
 *
 * @param type The type of the field/parameter
 * @param qualifier Optional qualifier annotation
 * @param name Optional field/parameter name (null for dependency references)
 * @param scopeAnnotation Optional scope annotation (null for unscoped/singleton)
 */
data class FieldInfo(
    val type: KSType,
    val qualifier: QualifierInfo?,
    val name: String? = null,
    val scopeAnnotation: KSType? = null,
)

/**
 * Key for looking up dependencies in maps.
 * Includes scope for differentiation between same type/qualifier in different scopes.
 *
 * @param type The dependency type
 * @param qualifier Optional qualifier
 * @param scope Optional scope annotation (null for singleton/unscoped)
 */
data class BindingKey(
    val type: KSType,
    val qualifier: QualifierInfo? = null,
    val scope: KSType? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BindingKey) return false
        return type.declaration == other.type.declaration &&
            qualifier == other.qualifier &&
            scope?.declaration == other.scope?.declaration
    }

    override fun hashCode(): Int {
        var result = type.declaration.hashCode()
        result = 31 * result + (qualifier?.hashCode() ?: 0)
        result = 31 * result + (scope?.declaration?.hashCode() ?: 0)
        return result
    }
}

/**
 * Represents a validated dependency in the graph.
 *
 * For @Provides methods: all dependencies are in [dependencies]
 * For @Inject constructors: [dependencies] = constructor params + injectable fields,
 *                            [injectableFields] contains field-specific info for code generation
 * For @Binds: [type] is the canonical implementation type, [aliases] contains supertypes
 */
data class DependencyNode(
    val providerModule: KSClassDeclaration,
    val providerFunction: KSFunctionDeclaration,
    val type: KSType,
    val qualifier: QualifierInfo?,
    val isSingleton: Boolean,
    val dependencies: List<FieldInfo>,
    val injectableFields: List<FieldInfo> = emptyList(),
    val aliases: MutableList<KSType> = mutableListOf(),
    val scopeAnnotation: KSType? = null,
)

/**
 * Represents a class with @Inject constructor (and optional @Inject fields).
 */
data class InjectableClassInfo(
    val classDeclaration: KSClassDeclaration,
    val constructor: KSFunctionDeclaration,
    val constructorParameters: List<FieldInfo>,
    val injectableFields: List<FieldInfo>,
    val isSingleton: Boolean,
    val qualifier: QualifierInfo?,
    val aliases: List<KSType> = emptyList(),
    val scopeAnnotation: KSType? = null,
)

/**
 * Represents an injector for a class that contains @Inject-annotated fields.
 * Generates a `Stitch<ClassName>Injector.kt`.
 */
data class FieldInjectorInfo(
    val classDeclaration: KSClassDeclaration,
    val injectableFields: List<FieldInfo>,
    val scopeUsage: ClassScopeUsage = ClassScopeUsage(null, emptySet(), emptyList()),
)

/**
 * Represents information about a scope annotation.
 */
data class ScopeInfo(
    val annotation: KSType, // The scope annotation type (e.g. ActivityScope)
    val dependsOn: KSType?, // Upstream scope (null if depends on Singleton)
    val depth: Int, // Distance from Singleton (0 = root custom scope, 1+ = downstream)
)

/**
 * Represents the complete scope dependency graph.
 */
data class ScopeGraph(
    val scopes: Map<KSType, ScopeInfo>, // All discovered custom scopes
    val rootScopes: Set<KSType>, // Scopes that depend directly on Singleton
)

/**
 * Represents scope usage analysis for a class with field injection.
 */
data class ClassScopeUsage(
    val deepestScope: KSType?, // Deepest scope used in fields (null if only Singleton/unscoped)
    val usedScopes: Set<KSType>, // All custom scopes used in fields (excludes Singleton)
    val ancestorPath: List<KSType>, // Ordered path from deepest to Singleton (excludes Singleton)
)

/**
 * Result of scanning modules, injectable classes, and field injection targets.
 */
data class ScanResult(
    val modules: List<ModuleInfo>,
    val injectables: List<InjectableClassInfo>,
    val fieldInjectors: List<FieldInjectorInfo>,
)

/**
 * Result of building and validating the dependency graph.
 * Contains both the node list and the dependency registry for O(1) lookup.
 */
data class DependencyGraph(
    val nodes: List<DependencyNode>,
    val registry: Map<BindingKey, DependencyNode>,
)

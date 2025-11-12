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
)

/**
 * Represents a provider method annotated with @Provides.
 */
data class ProvidesInfo(
    val declaration: KSFunctionDeclaration,
    val returnType: KSType,
    val parameters: List<ParameterInfo>,
    val isSingleton: Boolean,
    val qualifier: QualifierInfo?,
)

/**
 * Represents a parameter of a @Provides method.
 */
data class ParameterInfo(
    val name: String,
    val type: KSType,
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
 * Represents a validated dependency in the graph.
 *
 * For @Provides methods: all dependencies are in [dependencies]
 * For @Inject constructors: [dependencies] = constructor params + injectable fields,
 *                            [injectableFields] contains field-specific info for code generation
 */
data class DependencyNode(
    val providerModule: KSClassDeclaration,
    val providerFunction: KSFunctionDeclaration,
    val type: KSType,
    val qualifier: QualifierInfo?,
    val isSingleton: Boolean,
    val dependencies: List<DependencyRef>,
    val injectableFields: List<InjectableFieldInfo> = emptyList(),
)

/**
 * Represents a reference to a dependency.
 */
data class DependencyRef(
    val type: KSType,
    val qualifier: QualifierInfo?,
)

/**
 * Represents a class with @Inject constructor (and optional @Inject fields).
 */
data class InjectableClassInfo(
    val classDeclaration: KSClassDeclaration,
    val constructor: KSFunctionDeclaration,
    val returnType: KSType,
    val constructorParameters: List<ParameterInfo>,
    val injectableFields: List<InjectableFieldInfo>,
    val isSingleton: Boolean,
    val qualifier: QualifierInfo?,
)

/**
 * Represents a field with @Inject annotation.
 */
data class InjectableFieldInfo(
    val name: String,
    val type: KSType,
    val qualifier: QualifierInfo?,
)

/**
 * Represents a class annotated with @EntryPoint that needs field injection.
 *
 * Entry points are classes (typically Android components) that cannot use
 * constructor injection and instead rely on field injection via Stitch.inject(this).
 */
data class EntryPointInfo(
    val classDeclaration: KSClassDeclaration,
    val injectableFields: List<InjectableFieldInfo>,
)

/**
 * Result of scanning modules, injectable classes, and entry points.
 */
data class ScanResult(
    val modules: List<ModuleInfo>,
    val injectables: List<InjectableClassInfo>,
    val entryPoints: List<EntryPointInfo>,
)

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

package com.harrytmthy.stitch.annotations

/**
 * Meta-annotation that identifies a scope annotation for the DI path.
 *
 * Scope annotations are used to control the lifecycle and sharing of dependencies
 * within a specific scope (e.g. Activity scope, Fragment scope). Each scope
 * can depend on an upstream scope, forming a unidirectional dependency chain.
 *
 * Example usage:
 * ```kotlin
 * @Scope(dependsOn = Singleton::class)
 * @Retention(AnnotationRetention.RUNTIME)
 * annotation class ActivityScope
 *
 * @Scope(dependsOn = ActivityScope::class)
 * @Retention(AnnotationRetention.RUNTIME)
 * annotation class FragmentScope
 * ```
 *
 * This creates a dependency chain: `@FragmentScope → @ActivityScope → @Singleton`
 *
 * **Dependency Flow Rules:**
 * - Bindings in a scope can depend on bindings in the same scope or any ancestor scope
 * - Bindings cannot depend on descendant scopes or sibling scopes
 * - Example: `@FragmentScope` bindings can depend on `@ActivityScope` and `@Singleton` bindings
 *
 * **Generated Components:**
 * - Each scope generates a `StitchXxxScopeComponent` class with double-checked locking
 * - Root scopes (depend on Singleton) have no upstream property
 * - Downstream scopes have an `upstream` property pointing to their dependency
 * - Each component has a factory: `StitchXxxScopeComponentFactory.create()`
 *
 * **Field Injection:**
 * - Classes can have fields from multiple scopes (must be on the same ancestor path)
 * - Injector takes the deepest scope component as a parameter
 * - Injection chains through upstream scopes automatically
 *
 * @param dependsOn The upstream scope that this scope depends on. Defaults to [Singleton]
 *                  which marks this as a root custom scope. Use another scope annotation
 *                  class to create downstream scopes.
 *
 * @see Singleton
 * @see Named
 * @see Qualifier
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FIELD)
@Retention(AnnotationRetention.RUNTIME)
annotation class Scope(val name: String = "")

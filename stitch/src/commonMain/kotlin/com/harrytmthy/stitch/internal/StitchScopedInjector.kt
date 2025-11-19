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

package com.harrytmthy.stitch.internal

/**
 * Type-safe scoped injector interface for field injection with custom scopes.
 *
 * This interface is implemented by generated injector objects for each class
 * that has scoped field-level @Inject annotations, ensuring zero-overhead injection
 * with compile-time type safety for scope components.
 *
 * Example:
 * ```
 * val activityComponent = StitchActivityScopeComponentFactory.create()
 * StitchMainActivityInjector.inject(this, activityComponent)
 * ```
 *
 * **Note**: This is a public API implemented by generated code. Do not implement manually.
 *
 * @param T The type to inject fields into
 * @param C The scope component type required for injection
 */
interface StitchScopedInjector<in T, in C> {

    /**
     * Injects fields into the target instance using the provided scope component.
     *
     * @param target The instance to inject fields into
     * @param scopeComponent The scope component providing scoped dependencies
     */
    fun inject(target: T, scopeComponent: C)
}

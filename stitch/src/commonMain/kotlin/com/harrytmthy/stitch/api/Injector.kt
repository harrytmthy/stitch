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

package com.harrytmthy.stitch.api

import kotlin.reflect.KClass

/**
 * Core interface for Stitch's DI path. Generated scope graphs implement this interface.
 *
 * Each [Injector] represents a single scope in the dependency graph and can create child
 * injectors for downstream scopes:
 * ```
 * val singleton: Injector = StitchInjector.getSingleton()
 * val activityInjector = singleton.createInjectorForChildScope("activity")
 * activityInjector.inject(this)
 * ```
 *
 * @see StitchInjector
 */
interface Injector {

    /** Unique identifier for this injector instance. */
    val id: Int

    /** The scope name this injector manages (e.g., "singleton", "activity"). */
    val currentScope: String

    /** The parent injector, or null if this is the root (singleton) scope. */
    val upstream: Injector?

    /**
     * Performs field injection on [target], setting all `@Inject`-annotated fields.
     */
    fun inject(target: Any)

    /**
     * Creates an [Injector] for a child scope.
     *
     * Scope names are canonicalized (lowercased), so `"MyFragment"` and `"myfragment"` resolve
     * to the same child.
     *
     * @param cached When true, the child injector is stored in [StitchInjector]'s cache and
     *               can be retrieved later via [StitchInjector.getInjectorById].
     * @throws IllegalStateException if [scopeName] is not a known child of this scope.
     */
    fun createInjectorForChildScope(scopeName: String, cached: Boolean = false): Injector

    /**
     * Resolves a dependency by [type] and optional [qualifier].
     *
     * @throws IllegalStateException if no binding is found in this scope or its ancestors.
     */
    fun <T : Any> get(type: KClass<*>, qualifier: Qualifier? = null): T
}

/**
 * Reified convenience for [Injector.get].
 */
inline fun <reified T : Any> Injector.get(qualifier: Qualifier? = null): T =
    get(T::class, qualifier)

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

import com.harrytmthy.stitch.api.Stitch.get
import com.harrytmthy.stitch.api.Stitch.inject
import com.harrytmthy.stitch.api.Stitch.register
import com.harrytmthy.stitch.api.Stitch.reset
import com.harrytmthy.stitch.api.Stitch.unregister
import com.harrytmthy.stitch.internal.Registry
import kotlin.reflect.KClass

/**
 * Main entry point for Stitch's service locator (SL) path.
 *
 * Use [register] and [unregister] to manage modules at runtime, and [get] or [inject] to
 * resolve dependencies.
 *
 * Example:
 * ```
 * val appModule = module {
 *     singleton { LoggerImpl() }.bind<Logger>()
 *     factory { HomeRepository(logger = get()) }
 * }
 * Stitch.register(appModule)
 *
 * val logger: Logger = Stitch.get()
 * ```
 *
 * For the DI path, see [StitchInjector].
 */
object Stitch {

    private val component by lazy { Component() }

    /**
     * Registers one or more [modules], making their bindings available for resolution.
     *
     * Eager singletons are warmed up immediately after registration.
     */
    fun register(vararg modules: Module) {
        modules.forEach { module ->
            module.register()
        }
    }

    /**
     * Unregisters one or more [modules], removing all their bindings from the registry.
     */
    fun unregister(vararg modules: Module) {
        modules.forEach { module ->
            val registeredNodes = module.getRegisteredNodes()
            Registry.remove(registeredNodes)
        }
    }

    /**
     * Clears all registered modules and instances, but preserves Qualifiers and ScopeRefs.
     */
    fun unregisterAll() {
        Registry.clear()
    }

    /**
     * Fully resets the Stitch environment:
     * - Unregisters all modules
     * - Clears all Qualifiers and ScopeRefs
     * - Resets internal counters
     *
     * Only use in tests or in one-off tooling. After calling [reset], any previously obtained
     * [Named] or [ScopeRef] values must not be reused.
     */
    fun reset() {
        unregisterAll()
        Named.clear()
        ScopeManager.clear()
    }

    fun <T : Any> get(type: KClass<T>, qualifier: Qualifier? = null, scope: Scope? = null): T =
        getInternal(type, qualifier, scope, resolutionContext = null)

    /**
     * Resolves a dependency of type [T] with the given [qualifier] and [scope].
     */
    inline fun <reified T : Any> get(qualifier: Qualifier? = null, scope: Scope? = null): T =
        getInternal(T::class, qualifier, scope, resolutionContext = null)

    /**
     * Returns a [Lazy] that resolves a dependency of type [T] on first access.
     *
     * The lazy uses [LazyThreadSafetyMode.NONE], so it is not thread-safe.
     */
    inline fun <reified T : Any> inject(
        qualifier: Qualifier? = null,
        scope: Scope? = null,
    ): Lazy<T> {
        return lazy(LazyThreadSafetyMode.NONE) {
            getInternal(T::class, qualifier, scope, resolutionContext = null)
        }
    }

    @PublishedApi
    internal fun <T : Any> getInternal(
        type: KClass<T>,
        qualifier: Qualifier?,
        scope: Scope?,
        resolutionContext: ResolutionContext?,
    ): T {
        return component.getInternal(type, qualifier, scope, resolutionContext)
    }
}

/**
 * Top-level convenience for [Stitch.get].
 */
inline fun <reified T : Any> get(qualifier: Qualifier? = null, scope: Scope? = null): T =
    Stitch.get(qualifier, scope)

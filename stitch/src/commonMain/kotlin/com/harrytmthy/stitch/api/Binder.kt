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

import com.harrytmthy.stitch.engine.Node
import com.harrytmthy.stitch.engine.Signature
import com.harrytmthy.stitch.internal.Factory
import com.harrytmthy.stitch.internal.Registry
import com.harrytmthy.stitch.internal.TraceResult

class Binder(private val overrideEager: Boolean) {

    private var stagedEagerDefinitions: ArrayList<Signature>? = null

    inline fun <reified T : Any> singleton(
        qualifier: Qualifier? = null,
        eager: Boolean = false,
        noinline factory: Component.() -> T,
    ) = singleton(T::class.java, qualifier, eager, factory)

    fun <T : Any> singleton(
        type: Class<T>,
        qualifier: Qualifier?,
        eager: Boolean,
        factory: Component.() -> T,
    ) {
        bind(type, qualifier, Scope.Singleton, factory)
        if (eager || overrideEager) {
            val definitions = stagedEagerDefinitions ?: ArrayList<Signature>()
                .also { stagedEagerDefinitions = it }
            definitions += Signature(type, qualifier)
        }
    }

    inline fun <reified T : Any> factory(
        qualifier: Qualifier? = null,
        noinline factory: Component.() -> T,
    ) = factory(T::class.java, qualifier, factory)

    fun <T : Any> factory(
        type: Class<T>,
        qualifier: Qualifier?,
        factory: Component.() -> T,
    ) = bind(type, qualifier, Scope.Factory, factory)

    private fun <T : Any> bind(
        type: Class<T>,
        qualifier: Qualifier?,
        scope: Scope,
        factory: Component.() -> T,
    ) {
        val node = Node(
            type = type,
            qualifier = qualifier,
            scope = scope,
            factory = factory as Factory,
            tracer = this::traceDependencies,
        )
        val inner = Registry.definitions.getOrPut(type) { HashMap() }
        check(!inner.containsKey(qualifier)) {
            "Duplicate binding for ${type.name} / ${qualifier ?: "<default>"}"
        }
        inner[qualifier] = node
        Registry.version.incrementAndGet()
    }

    internal fun getStagedEagerDefinitions(): List<Signature> =
        stagedEagerDefinitions.orEmpty().also { stagedEagerDefinitions = null }

    private inline fun <T : Any> traceDependencies(
        crossinline factory: Component.() -> T,
    ): TraceResult {
        val dependencies = LinkedHashSet<Signature>(16)
        var touched = false
        var prebuilt: Any? = null
        val proxyComponent = object : Component(
            nodeLookup = { _, _ -> error("noop") },
            singletons = Registry.singletons,
        ) {
            override fun <R : Any> get(type: Class<R>, qualifier: Qualifier?): R {
                dependencies.add(Signature(type, qualifier))
                touched = true
                throw TraceAbort
            }

            override fun <R : Any> lazyOf(type: Class<R>, qualifier: Qualifier?): Lazy<R> {
                dependencies.add(Signature(type, qualifier))
                touched = true
                throw TraceAbort
            }
        }
        prebuilt = try {
            factory(proxyComponent)
        } catch (_: TraceAbort) {
            null
        } catch (_: Throwable) {
            null
        }
        return TraceResult(
            dependencies = ArrayList(dependencies),
            prebuilt = prebuilt.takeUnless { touched },
        )
    }

    private object TraceAbort : RuntimeException() {
        private fun readResolve(): Any = this
        override fun fillInStackTrace() = this
    }
}

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

import com.harrytmthy.stitch.internal.ConcurrentHashMap
import kotlinx.atomicfu.atomic
import kotlin.reflect.KClass

/**
 * Differentiates between multiple bindings of the same type.
 */
sealed interface Qualifier

/**
 * A string-based [Qualifier] for the runtime path. Instances are pooled, where calling
 * [named] with the same value always returns the same instance.
 *
 * ```
 * val prodModule = module {
 *     singleton(qualifier = named("prod")) { ProdConfig() }.bind<Config>()
 *     singleton(qualifier = named("staging")) { StagingConfig() }.bind<Config>()
 * }
 *
 * val config: Config = Stitch.get(qualifier = named("prod"))
 * ```
 */
class Named internal constructor(val value: String) : Qualifier {

    private val id = QualifierManager.nextId()

    override fun hashCode(): Int = id

    override fun equals(other: Any?): Boolean = other is Named && other.id == this.id
}

/**
 * A type-based [Qualifier] for the runtime path. Instances are pooled, where calling
 * [typed] with the same type always returns the same instance.
 *
 * Useful when qualifier annotations are already defined and reusing them avoids string duplication:
 *
 * ```
 * val appModule = module {
 *     singleton(qualifier = typed<Production>()) { ProdConfig() }.bind<Config>()
 *     singleton(qualifier = typed<Staging>()) { StagingConfig() }.bind<Config>()
 * }
 *
 * val config: Config = Stitch.get(qualifier = typed<Production>())
 * ```
 */
class Typed internal constructor(val value: KClass<*>) : Qualifier {

    private val id = QualifierManager.nextId()

    override fun hashCode(): Int = id

    override fun equals(other: Any?): Boolean = other is Typed && other.id == this.id
}

internal object QualifierManager {

    private val namedPool = ConcurrentHashMap<String, Named>()

    private val typedPool = ConcurrentHashMap<KClass<*>, Typed>()

    private val nextId = atomic(1)

    fun getOrCreate(name: String): Named = namedPool.computeIfAbsent(name, ::Named)

    fun getOrCreate(type: KClass<*>): Typed = typedPool.computeIfAbsent(type, ::Typed)

    fun nextId(): Int = nextId.getAndIncrement()

    fun clear() {
        namedPool.clear()
        typedPool.clear()
        nextId.value = 1
    }
}

/**
 * Returns a pooled [Named] qualifier for the given [value], creating one if it doesn't exist.
 */
fun named(value: String): Named = QualifierManager.getOrCreate(value)

/**
 * Returns a pooled [Typed] qualifier for the given type, creating one if it doesn't exist.
 */
inline fun <reified T> typed(): Typed = typed(T::class)

/**
 * Returns a pooled [Typed] qualifier for the given type, creating one if it doesn't exist.
 * Use this to ease migration from Koin.
 */
inline fun <reified T> typeQualifier(): Typed = typed(T::class)

/**
 * Returns a pooled [Typed] qualifier for the given [type], creating one if it doesn't exist.
 */
fun typed(type: KClass<*>): Typed = QualifierManager.getOrCreate(type)

internal object DefaultQualifier : Qualifier

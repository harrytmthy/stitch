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

package com.harrytmthy.stitch

import com.harrytmthy.stitch.api.Stitch
import com.harrytmthy.stitch.api.module
import com.harrytmthy.stitch.api.named
import com.harrytmthy.stitch.exception.CycleException
import com.harrytmthy.stitch.exception.MissingBindingException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class StitchTest {

    @BeforeTest
    fun setUp() {
        Stitch.unregister()
    }

    @Test
    fun `singleton should return same instance`() {
        val module = module {
            singleton { Logger() }
        }
        Stitch.register(module)

        val firstInstance = Stitch.get<Logger>()
        val secondInstance = Stitch.get<Logger>()
        assertSame(firstInstance, secondInstance)
    }

    @Test
    fun `default singleton and qualified singleton should not clash`() {
        val module = module {
            singleton { Logger() }
            singleton(qualifier = named("prod")) { Logger() }
        }
        Stitch.register(module)

        val defaultFirst = Stitch.get<Logger>()
        val defaultSecond = Stitch.get<Logger>()
        val prodFirst = Stitch.get<Logger>(named("prod"))
        val prodSecond = Stitch.get<Logger>(named("prod"))

        assertSame(defaultFirst, defaultSecond)
        assertSame(prodFirst, prodSecond)
        assertNotSame(defaultFirst, prodFirst)
    }

    @Test
    fun `singleton with different qualifiers should not throw`() {
        val module = module {
            singleton(qualifier = named("staging")) { Logger() }
            singleton(qualifier = named("prod")) { Logger() }
        }
        Stitch.register(module)

        val staging = Stitch.get<Logger>(named("staging"))
        val prod = Stitch.get<Logger>(named("prod"))

        assertNotSame(staging, prod)
    }

    @Test
    fun `factory should return new instance each time`() {
        val module = module {
            factory { Logger() }
        }
        Stitch.register(module)

        val first = Stitch.get<Logger>()
        val second = Stitch.get<Logger>()
        assertNotSame(first, second)
    }

    @Test
    fun `factory should behave per-qualifier`() {
        val module = module {
            factory { Logger() }
            factory(qualifier = named("prod")) { Logger() }
        }
        Stitch.register(module)

        val defaultFirst = Stitch.get<Logger>()
        val defaultSecond = Stitch.get<Logger>()
        val prodFirst = Stitch.get<Logger>(named("prod"))
        val prodSecond = Stitch.get<Logger>(named("prod"))

        assertNotSame(defaultFirst, defaultSecond)
        assertNotSame(prodFirst, prodSecond)
        assertNotSame(defaultFirst, prodFirst)
    }

    @Test
    fun `dependencies are resolved across modules (additive register)`() {
        val base = module {
            singleton { Logger() }
        }
        val feature = module {
            singleton { Dao(get()) } // depends on Logger from base
        }

        Stitch.register(base)
        val sharedLogger = Stitch.get<Logger>()
        assertNotNull(sharedLogger)

        Stitch.register(feature)
        val dao = Stitch.get<Dao>()
        assertSame(sharedLogger, dao.logger)
    }

    @Test
    fun `unregister should clear singletons`() {
        var buildCount = 0
        val module = module {
            singleton {
                buildCount++
                Logger()
            }
        }

        Stitch.register(module)
        val first = Stitch.get<Logger>()
        assertEquals(1, buildCount)

        Stitch.unregister()
        Stitch.register(module)

        val second = Stitch.get<Logger>()
        assertEquals(2, buildCount)
        assertNotSame(first, second)
    }

    @Test
    fun `get with missing binding should throw MissingBindingException`() {
        val module = module {
            // NeedsMissing depends on Repo (not bound)
            singleton { NeedsMissing(get()) }
        }
        Stitch.register(module)

        assertFailsWith<MissingBindingException> { Stitch.get<NeedsMissing>() }
    }

    @Test
    fun `get when only qualified exists should throw MissingBindingException`() {
        val module = module {
            singleton(qualifier = named("prod")) { Logger() }
        }
        Stitch.register(module)

        assertFailsWith<MissingBindingException> { Stitch.get<Logger>() }
    }

    @Test
    fun `get qualified when only non-qualified exists should throw MissingBindingException`() {
        val module = module {
            singleton { Logger() }
        }
        Stitch.register(module)

        assertFailsWith<MissingBindingException> { Stitch.get<Logger>(named("prod")) }
    }

    @Test
    fun `get with detected cycle should throw CycleException`() {
        val module = module {
            singleton { A(get()) } // A -> B
            singleton { B(get()) } // B -> A
        }
        Stitch.register(module)

        assertFailsWith<CycleException> { Stitch.get<A>() }
    }

    @Test
    fun `register with duplicate binding should throw IllegalStateException`() {
        val module = module {
            singleton { Logger() }
            singleton { Logger() }
        }

        assertFailsWith<IllegalStateException> { Stitch.register(module) }
    }

    @Test
    fun `eagerWarmup should build singletons but defers factories via Lazy`() {
        var singletonBuilds = 0
        var factoryBuilds = 0

        val module = module(overrideEager = true) {
            factory {
                factoryBuilds++
                Bar()
            }
            singleton {
                val barLazy: Lazy<Bar> = lazyOf(Bar::class.java, null)
                singletonBuilds++
                UsesLazyFactory(barLazy)
            }
        }

        Stitch.register(module)

        // Build a component (this warms singletons once)
        val component = Stitch.componentFor<UsesLazyFactory>()
        assertEquals(1, singletonBuilds)
        assertEquals(0, factoryBuilds)

        // Resolve via the same component (avoid a second eager warm)
        val holder = component.get<UsesLazyFactory>()
        assertEquals(0, factoryBuilds)

        // Touch the lazy -> build factory once
        holder.barLazy.value
        assertEquals(1, factoryBuilds)
    }

    @Test
    fun `componentFor should return working component sharing the same singleton pool`() {
        val module = module {
            singleton { Logger() }
            singleton { Dao(get()) }
        }
        Stitch.register(module)

        val component = Stitch.componentFor<Dao>()
        val daoFromComponent = component.get<Dao>()
        val loggerFromRoot = Stitch.get<Logger>()

        assertSame(loggerFromRoot, daoFromComponent.logger)
    }

    @Test
    fun `factory should produce new instance even via component`() {
        val module = module {
            factory { RepoImpl() as Repo }
        }
        Stitch.register(module)

        val component = Stitch.componentFor<Repo>()
        val first = component.get<Repo>()
        val second = component.get<Repo>()
        assertNotSame(first, second)
    }

    @Test
    fun `qualified dependency should resolve across modules`() {
        val base = module { singleton(qualifier = named("prod")) { Logger() } }
        val feature = module { singleton { Dao(get(qualifier = named("prod"))) } }

        Stitch.register(base)
        Stitch.register(feature)

        val dao = Stitch.get<Dao>()
        val prodLogger = Stitch.get<Logger>(named("prod"))
        assertSame(prodLogger, dao.logger)
    }

    @Test
    fun `factory and singleton of same type should respect qualifiers`() {
        val module = module {
            singleton { Logger() }
            factory(qualifier = named("prod")) { Logger() }
        }
        Stitch.register(module)

        val firstInstance = Stitch.get<Logger>()
        val secondInstance = Stitch.get<Logger>()
        val firstProdInstance = Stitch.get<Logger>(named("prod"))
        val secondProdInstance = Stitch.get<Logger>(named("prod"))

        assertSame(firstInstance, secondInstance)
        assertNotSame(firstProdInstance, secondProdInstance)
        assertNotSame(firstInstance, firstProdInstance)
    }

    @Test
    fun `singletons with the same type when one is eager should throw IllegalStateException`() {
        val module = module {
            singleton(eager = true) { Logger() }
            singleton { Logger() }
        }
        assertFailsWith<IllegalStateException> { Stitch.register(module) }
    }

    @Test
    fun `overrideEager should warm all singletons in that module`() {
        var firstSingletonCount = 0
        var secondSingletonCount = 0
        var factoryCount = 0
        val module = module(overrideEager = true) {
            singleton {
                firstSingletonCount++
                Logger()
            }
            singleton(qualifier = named("prod")) {
                secondSingletonCount++
                Logger()
            }
            factory(qualifier = named("dev")) {
                factoryCount++
                Logger()
            }
        }

        Stitch.register(module)

        assertEquals(1, firstSingletonCount)
        assertEquals(1, secondSingletonCount)
        assertEquals(0, factoryCount)
    }

    @Test
    fun `qualified eager should warm that qualifier only`() {
        var firstSingletonCount = 0
        var secondSingletonCount = 0
        val module = module {
            singleton(qualifier = named("prod"), eager = true) {
                firstSingletonCount++
                Logger()
            }
            singleton {
                secondSingletonCount++
                Logger()
            }
        }
        Stitch.register(module)
        assertEquals(1, firstSingletonCount)
        assertEquals(0, secondSingletonCount)
    }

    @Test
    fun `eager should not be contagious to dependencies`() {
        var daoBuiltCount = 0
        var loggerBuiltCount = 0
        val module = module {
            singleton(eager = true) {
                loggerBuiltCount++
                Logger()
            }
            singleton {
                val logger = get<Logger>()
                daoBuiltCount++
                Dao(logger)
            }
        }
        Stitch.register(module)
        assertEquals(0, daoBuiltCount)
        assertEquals(1, loggerBuiltCount)
        Stitch.get<Dao>()
        assertEquals(1, daoBuiltCount)
        assertEquals(1, loggerBuiltCount)
    }
}

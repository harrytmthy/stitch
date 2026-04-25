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
import com.harrytmthy.stitch.api.bind
import com.harrytmthy.stitch.api.dependsOn
import com.harrytmthy.stitch.api.module
import com.harrytmthy.stitch.api.named
import com.harrytmthy.stitch.api.scope
import com.harrytmthy.stitch.api.typed
import com.harrytmthy.stitch.exception.CycleException
import com.harrytmthy.stitch.exception.MissingBindingException
import com.harrytmthy.stitch.exception.ScopeClosedException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class StitchTest {

    @BeforeTest
    fun setUp() {
        Stitch.reset()
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

        Stitch.unregisterAll()
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
        module {
            singleton { Logger() }
        }.register()

        module {
            singleton { Logger() }
        }.apply {
            assertFailsWith<IllegalStateException> { Stitch.register(this) }
        }
    }

    @Test
    fun `eagerWarmup should build singletons but defers factories via Lazy`() {
        var singletonBuilds = 0
        var factoryBuilds = 0

        val module = module(forceEager = true) {
            factory {
                factoryBuilds++
                Bar()
            }
            singleton {
                val barLazy: Lazy<Bar> = lazyOf()
                singletonBuilds++
                UsesLazyFactory(barLazy)
            }
        }

        Stitch.register(module)

        // Warmup already ran during register
        assertEquals(1, singletonBuilds)
        assertEquals(0, factoryBuilds)

        // Resolve via public API, still no factory build
        val holder = Stitch.get<UsesLazyFactory>()
        assertEquals(0, factoryBuilds)

        // Touch the lazy -> factory builds once
        holder.barLazy.value
        assertEquals(1, factoryBuilds)
    }

    @Test
    fun `singletons resolved later share the same global pool`() {
        val module = module {
            singleton { Logger() }
            singleton { Dao(get()) }
        }
        Stitch.register(module)

        val dao = Stitch.get<Dao>()
        val logger = Stitch.get<Logger>()

        assertSame(logger, dao.logger) // they come from the same singleton pool
    }

    @Test
    fun `factory should produce new instance via public API`() {
        val module = module {
            factory { RepoImpl() as Repo }
        }
        Stitch.register(module)

        val first = Stitch.get<Repo>()
        val second = Stitch.get<Repo>()
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
        module {
            singleton(eager = true) { Logger() }
            assertFailsWith<IllegalStateException> { singleton { Logger() } }
        }
    }

    @Test
    fun `overrideEager should warm all singletons in that module`() {
        var firstSingletonCount = 0
        var secondSingletonCount = 0
        var factoryCount = 0
        val module = module(forceEager = true) {
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

    @Test
    fun `bind should expose the same singleton across aliases`() {
        val module = module {
            singleton { DualRepo() } // primary key: DualRepo / <default>
                .bind<Repo>() // alias: Repo / <default>
                .bind<Auditable>() // alias: Auditable / <default>
        }
        Stitch.register(module)

        val asImpl = Stitch.get<DualRepo>()
        val asRepo = Stitch.get<Repo>()
        val asAuditable = Stitch.get<Auditable>()

        assertSame(asImpl, asRepo)
        assertTrue(asRepo === asAuditable)
    }

    @Test
    fun `bind keeps primary qualifier`() {
        val module = module {
            singleton(qualifier = named("prod")) { DualRepo() }
                .bind<Repo>()
        }
        Stitch.register(module)

        // default qualifier does not exist
        assertFailsWith<MissingBindingException> { Stitch.get<Repo>() }
        assertFailsWith<MissingBindingException> { Stitch.get<DualRepo>() }

        // "prod" qualifier works and points to the same instance
        val implProd = Stitch.get<DualRepo>(named("prod"))
        val repoProd = Stitch.get<Repo>(named("prod"))
        assertSame(implProd, repoProd)
    }

    @Test
    fun `bind when existing binding collides should throw`() {
        val module = module {
            singleton<Repo> { RepoImpl() } // Repo / <default> already taken
            singleton { DualRepo() } // DualRepo / <default>
                .bind<Repo>() // attempts to alias into Repo / <default>
        }
        assertFailsWith<IllegalStateException> { Stitch.register(module) }
    }

    @Test
    fun `factory alias produces new instances`() {
        val module = module {
            factory { DualRepo() } // primary key: DualRepo / <default>
                .bind<Repo>() // alias: Repo / <default>
        }
        Stitch.register(module)

        val r1 = Stitch.get<Repo>()
        val r2 = Stitch.get<Repo>()
        val i1 = Stitch.get<DualRepo>()
        val i2 = Stitch.get<DualRepo>()

        assertNotSame(r1, r2)
        assertNotSame(i1, i2)
    }

    @Test
    fun `eager singleton builds once even with aliases`() {
        var builds = 0
        val module = module {
            singleton(eager = true) {
                builds++
                DualRepo()
            }.bind<Repo>().bind<Auditable>()
        }
        Stitch.register(module)

        // eager warming already happened
        assertEquals(1, builds)

        // resolving via any alias should not rebuild
        Stitch.get<Repo>()
        Stitch.get<Auditable>()
        Stitch.get<DualRepo>()
        assertEquals(1, builds)
    }

    @Test
    fun `scoped get without scope should throw IllegalStateException`() {
        val activityScope = scope("activity")
        val module = module {
            scoped(activityScope) { RepoImpl() as Repo }
        }
        Stitch.register(module)

        assertFailsWith<IllegalStateException> {
            Stitch.get<Repo>() // no scope provided
        }
    }

    @Test
    fun `same scope instance returns same object`() {
        val screenScope = scope("screen")
        val module = module {
            scoped(screenScope) { RepoImpl() as Repo }
        }
        Stitch.register(module)

        val scopeInstance = screenScope.createScope()
        val firstRepo = Stitch.get<Repo>(scope = scopeInstance)
        val secondRepo = Stitch.get<Repo>(scope = scopeInstance)
        assertSame(firstRepo, secondRepo)
    }

    @Test
    fun `different scope instances are isolated`() {
        val screenScope = scope("screen")
        val module = module {
            scoped(screenScope) { RepoImpl() as Repo }
        }
        Stitch.register(module)

        val firstScopeInstance = screenScope.createScope()
        val secondScopeInstance = screenScope.createScope()

        val firstRepo = Stitch.get<Repo>(scope = firstScopeInstance)
        val secondRepo = Stitch.get<Repo>(scope = secondScopeInstance)
        assertNotSame(firstRepo, secondRepo)
    }

    @Test
    fun `close scope evicts cache and blocks further access`() {
        val screenScope = scope("screen")
        val module = module {
            scoped(screenScope) { RepoImpl() as Repo }
        }
        Stitch.register(module)

        val scopeInstance = screenScope.createScope()
        val repo = Stitch.get<Repo>(scope = scopeInstance)
        assertNotNull(repo)

        scopeInstance.close()
        assertFailsWith<ScopeClosedException> {
            Stitch.get<Repo>(scope = scopeInstance)
        }
    }

    @Test
    fun `wrong scope reference should throw IllegalStateException`() {
        val activityScope = scope("activity")
        val fragmentScope = scope("fragment")
        val module = module {
            scoped(activityScope) { RepoImpl() as Repo }
        }
        Stitch.register(module)

        val fragmentScopeInstance = fragmentScope.createScope()
        assertFailsWith<IllegalStateException> {
            Stitch.get<Repo>(scope = fragmentScopeInstance)
        }
    }

    @Test
    fun `scoped respects qualifiers`() {
        val screenScope = scope("screen")
        val module = module {
            scoped(screenScope, qualifier = named("prod")) { Logger() }
            scoped(screenScope, qualifier = named("staging")) { Logger() }
        }
        Stitch.register(module)

        val scopeInstance = screenScope.createScope()
        val firstProd = Stitch.get<Logger>(named("prod"), scope = scopeInstance)
        val secondProd = Stitch.get<Logger>(named("prod"), scope = scopeInstance)
        val firstStaging = Stitch.get<Logger>(named("staging"), scope = scopeInstance)

        assertSame(firstProd, secondProd)
        assertNotSame(firstProd, firstStaging)
    }

    @Test
    fun `scoped alias resolves to same instance`() {
        val screenScope = scope("screen")
        val module = module {
            scoped(screenScope) { DualRepo() }
                .bind<Repo>()
                .bind<Auditable>()
        }
        Stitch.register(module)

        val scopeInstance = screenScope.createScope()
        val asImpl = Stitch.get<DualRepo>(scope = scopeInstance)
        val asRepo = Stitch.get<Repo>(scope = scopeInstance)
        val asAuditable = Stitch.get<Auditable>(scope = scopeInstance)

        assertSame(asImpl, asRepo)
        assertTrue(asRepo === asAuditable)
    }

    @Test
    fun `scoped get during module creation should resolve dependencies`() {
        val activityScope = scope("activity")
        val viewModelScope = scope("viewModel")
        val module = module {
            scoped(activityScope) { Dao(get()) }
            scoped(activityScope) { RepoImpl() }.bind<Repo>()
            scoped(viewModelScope) { RepoImpl() }.bind<Repo>()
            singleton { Logger() }
            scoped(viewModelScope) { FetchUseCase(get()) }
            scoped(activityScope) { LoadUseCase(get()) }
        }
        Stitch.register(module)

        val activityScopeInstance = activityScope.createScope()
        val viewModelScopeInstance = viewModelScope.createScope()
        val dao = Stitch.get<Dao>(scope = activityScopeInstance)
        val logger = Stitch.get<Logger>(scope = activityScopeInstance)
        val fetchUseCase = Stitch.get<FetchUseCase>(scope = viewModelScopeInstance)
        val repo = viewModelScopeInstance.get<Repo>()
        val loadUseCase = Stitch.get<LoadUseCase>(scope = activityScopeInstance)

        assertSame(logger, dao.logger)
        assertSame(dao, loadUseCase.dao)
        assertSame(repo, fetchUseCase.repo)
    }

    @Test
    fun `scope should return different instance of the same type`() {
        val activityScope = scope("activity")
        val viewModelScope = scope("viewModel")
        val module = module {
            scoped(activityScope) { Logger() }
            scoped(viewModelScope) { Logger() }
        }
        Stitch.register(module)

        val activityScopeInstance = activityScope.createScope()
        val viewModelScopeInstance = viewModelScope.createScope()

        assertNotSame(Stitch.get<Logger>(scope = activityScopeInstance), Stitch.get<Logger>(scope = viewModelScopeInstance))
    }

    @Test
    fun `unregister removes definitions even if scope is open`() {
        val screenScope = scope("screen")
        val module = module {
            scoped(screenScope) { RepoImpl() as Repo }
        }
        Stitch.register(module)
        val scopeInstance = screenScope.createScope()
        assertNotNull(Stitch.get<Repo>(scope = scopeInstance))

        Stitch.unregisterAll()
        assertFailsWith<MissingBindingException> {
            Stitch.get<Repo>(scope = scopeInstance)
        }
    }

    @Test
    fun `unregister removes singleton bindings and instances`() {
        val module = module {
            singleton { Logger() }
            singleton { Dao(get()) }
        }
        Stitch.register(module)

        val daoBefore = Stitch.get<Dao>()
        val loggerBefore = Stitch.get<Logger>()
        Stitch.unregister(module)

        Stitch.register(module)
        val daoAfter = Stitch.get<Dao>()
        val loggerAfter = Stitch.get<Logger>()

        assertNotSame(daoBefore, daoAfter)
        assertNotSame(loggerBefore, loggerAfter)
    }

    @Test
    fun `unregister removes scoped bindings and their cached instances`() {
        val screenScope = scope("screen")
        val module = module {
            scoped(screenScope) { Logger() }
            scoped(screenScope) { Dao(get()) }
        }
        Stitch.register(module)

        val scopeInstance = screenScope.createScope()
        val daoBefore = Stitch.get<Dao>(scope = scopeInstance)
        val loggerBefore = Stitch.get<Logger>(scope = scopeInstance)
        Stitch.unregister(module)

        // Re-register, should build fresh instances
        Stitch.register(module)
        val daoAfter = Stitch.get<Dao>(scope = scopeInstance)
        val loggerAfter = Stitch.get<Logger>(scope = scopeInstance)

        assertNotSame(daoBefore, daoAfter)
        assertNotSame(loggerBefore, loggerAfter)
    }

    @Test
    fun `unregister one module keeps other modules intact`() {
        val loggerModule = module { singleton { Logger() } }
        val repoModule = module { singleton { RepoImpl() as Repo } }
        Stitch.register(loggerModule, repoModule)
        assertNotNull(Stitch.get<Logger>())

        val repoBefore = Stitch.get<Repo>()
        Stitch.unregister(loggerModule)

        // Repo still works
        val repoAfter = Stitch.get<Repo>()
        assertSame(repoBefore, repoAfter)

        // Logger binding is gone
        assertFailsWith<MissingBindingException> { Stitch.get<Logger>() }
    }

    @Test
    fun `unregister removes aliases belonging to same family`() {
        val module = module {
            singleton { DualRepo() }.bind<Repo>()
        }
        Stitch.register(module)

        val repo = Stitch.get<Repo>()
        val dual = Stitch.get<DualRepo>()
        assertSame(dual, repo)

        Stitch.unregister(module)

        assertFailsWith<MissingBindingException> { Stitch.get<Repo>() }
        assertFailsWith<MissingBindingException> { Stitch.get<DualRepo>() }
    }

    @Test
    fun `unregister scoped module clears only its own scope`() {
        val activityScope = scope("activity")
        val viewModelScope = scope("vm")

        val activityModule = module { scoped(activityScope) { Logger() } }
        val viewModelModule = module { scoped(viewModelScope) { Logger() } }
        Stitch.register(activityModule, viewModelModule)

        val activityScopeInstance = activityScope.createScope()
        val viewModelScopeInstance = viewModelScope.createScope()
        assertNotNull(Stitch.get<Logger>(scope = activityScopeInstance))

        val loggerBeforeUnregister = Stitch.get<Logger>(scope = viewModelScopeInstance)
        Stitch.unregister(activityModule)

        val loggerAfterUnregister = Stitch.get<Logger>(scope = viewModelScopeInstance)
        assertSame(loggerBeforeUnregister, loggerAfterUnregister)
        assertFailsWith<MissingBindingException> {
            Stitch.get<Logger>(scope = activityScopeInstance)
        }
    }

    @Test
    fun `dependsOn with cycle should throw IllegalStateException`() {
        scope("fragment").dependsOn(scope("activity"))

        assertFailsWith<IllegalStateException> {
            scope("activity").dependsOn(scope("fragment"))
        }
    }

    @Test
    fun `dependsOn with same scope more than once should throw IllegalArgumentException`() {
        scope("fragment").dependsOn(scope("activity"))

        assertFailsWith<IllegalArgumentException> {
            scope("fragment").dependsOn(scope("myActivity"))
        }
    }

    @Test
    fun `scoped get on parent's binding should return the parent's cached instance`() {
        val activityScope = scope("activity")
        val fragmentScope = scope("fragment").dependsOn(activityScope)
        module {
            scoped(activityScope) { Logger() }
        }.register()

        val activityScopeInstance = activityScope.createScope()
        val activityLogger = activityScopeInstance.get<Logger>()
        val fragmentScopeInstance = activityScopeInstance.createChildScope(fragmentScope)

        val fragmentLogger = fragmentScopeInstance.get<Logger>()
        assertSame(activityLogger, fragmentLogger)

        val anotherFragmentScopeInstance = fragmentScope.createScope(parent = activityScopeInstance)

        val anotherFragmentLogger = anotherFragmentScopeInstance.get<Logger>()
        assertSame(activityLogger, anotherFragmentLogger)
    }

    @Test
    fun `scoped get should prioritize current scope binding`() {
        val activityScope = scope("activity")
        val fragmentScope = scope("fragment").dependsOn(activityScope)
        module {
            scoped(activityScope) { Logger() }
            scoped(fragmentScope) { Logger() }
        }.register()

        val activityScopeInstance = activityScope.createScope()
        val activityLogger = activityScopeInstance.get<Logger>()
        val fragmentScopeInstance = fragmentScope.createScope()
        val fragmentLogger = fragmentScopeInstance.get<Logger>()
        assertNotSame(activityLogger, fragmentLogger)
    }

    @Test
    fun `scoped get on parent's binding alias should return the parent's cached instance`() {
        val activityScope = scope("activity")
        val fragmentScope = scope("fragment").dependsOn(activityScope)
        module {
            scoped(activityScope) { ActivityLifecycleTracker() }.bind<LifecycleTracker>()
        }.register()

        val activityScopeInstance = activityScope.createScope()
        val activityTracker = activityScopeInstance.get<LifecycleTracker>()
        val fragmentScopeInstance = activityScopeInstance.createChildScope(fragmentScope)

        val fragmentTracker = fragmentScopeInstance.get<LifecycleTracker>()
        assertSame(activityTracker, fragmentTracker)

        val anotherFragmentScopeInstance = fragmentScope.createScope(parent = activityScopeInstance)

        val anotherFragmentTracker = anotherFragmentScopeInstance.get<LifecycleTracker>()
        assertSame(activityTracker, anotherFragmentTracker)
    }

    @Test
    fun `scoped get on parent's binding without parent ref should throw MissingBindingException`() {
        val activityScope = scope("activity")
        val fragmentScope = scope("fragment").dependsOn(activityScope)
        module {
            scoped(activityScope) { Logger() }
        }.register()

        val fragmentScopeInstance = fragmentScope.createScope()
        assertFailsWith<MissingBindingException> { fragmentScopeInstance.get<Logger>() }
    }

    @Test
    fun `get inside scoped binding definitions should resolve upstream`() {
        val activityScope = scope("activity")
        val fragmentScope = scope("fragment").dependsOn(activityScope)
        module {
            singleton { A(null) }
            singleton { B(get()) }
            scoped(activityScope) { A(get()) }
            scoped(fragmentScope) { B(get()) }
            scoped(fragmentScope) { C(get(), get()) }
        }.register()

        val activityScopeInstance = activityScope.createScope()
        val fragmentScopeInstance = fragmentScope.createScope(activityScopeInstance)

        val fragmentScopedA = fragmentScopeInstance.get<A>()
        val singletonScopedA = Stitch.get<A>()
        assertNotSame(fragmentScopedA, singletonScopedA)

        val fragmentScopedC = fragmentScopeInstance.get<C>()
        assertSame(fragmentScopedC.a, activityScopeInstance.get<A>())
        assertSame(fragmentScopedC.b, fragmentScopeInstance.get<B>())
        assertSame(activityScopeInstance.get<B>(), Stitch.get<B>())
    }

    @Test
    fun `named enum qualifier should resolve the same instance as its string equivalent`() {
        module {
            singleton(qualifier = named(Environment.Prod)) { Logger() }
        }.register()

        val loggerFromEnum = Stitch.get<Logger>(named(Environment.Prod))
        val loggerFromString = Stitch.get<Logger>(named("Prod"))
        assertSame(loggerFromEnum, loggerFromString)
    }

    @Test
    fun `named enum qualifiers with different values should not clash`() {
        module {
            singleton(qualifier = named(Environment.Prod)) { Logger() }
            singleton(qualifier = named(Environment.Staging)) { Logger() }
        }.register()

        val prodLogger = Stitch.get<Logger>(named(Environment.Prod))
        val stagingLogger = Stitch.get<Logger>(named(Environment.Staging))
        assertNotSame(prodLogger, stagingLogger)
    }

    @Test
    fun `typed qualifier should resolve the same instance across calls`() {
        module {
            singleton(qualifier = typed<Environment>()) { Logger() }
        }.register()

        val first = Stitch.get<Logger>(typed<Environment>())
        val second = Stitch.get<Logger>(typed<Environment>())
        assertSame(first, second)
    }

    @Test
    fun `default singleton and typed qualified singleton should not clash`() {
        module {
            singleton { Logger() }
            singleton(qualifier = typed<Environment>()) { Logger() }
        }.register()

        val default = Stitch.get<Logger>()
        val typed = Stitch.get<Logger>(typed<Environment>())
        assertNotSame(default, typed)
    }

    @Test
    fun `typed and named qualifiers of same type should not clash`() {
        module {
            singleton(qualifier = typed<Environment>()) { Logger() }
            singleton(qualifier = named("Environment")) { Logger() }
        }.register()

        val typedLogger = Stitch.get<Logger>(typed<Environment>())
        val namedLogger = Stitch.get<Logger>(named("Environment"))
        assertNotSame(typedLogger, namedLogger)
    }
}

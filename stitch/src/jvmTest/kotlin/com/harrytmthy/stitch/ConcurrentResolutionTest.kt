/*
 * Copyright 2026 Harry Timothy Tumalewa
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
import com.harrytmthy.stitch.exception.CycleException
import com.harrytmthy.stitch.exception.ScopeClosedException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ConcurrentResolutionTest {

    @BeforeTest
    fun setUp() {
        Stitch.reset()
    }

    @Test
    fun `acyclic qualified singletons should initialize concurrently`() {
        val left = named("left")
        val right = named("right")
        val constructors = CyclicBarrier(2)
        Stitch.register(
            module {
                singleton(left) {
                    constructors.await(5, SECONDS)
                    First(get(left))
                }
                singleton(left) { Second() }
                singleton(right) {
                    constructors.await(5, SECONDS)
                    Second(get(right))
                }
                singleton(right) { First() }
            },
        )

        val instances = concurrently(
            { Stitch.get<First>(left) },
            { Stitch.get<Second>(right) },
        )

        assertSame(Stitch.get<First>(left), instances[0])
        assertSame(Stitch.get<Second>(right), instances[1])
        assertSame(Stitch.get<Second>(left), (instances[0] as First).second)
        assertSame(Stitch.get<First>(right), (instances[1] as Second).first)
    }

    @Test
    fun `acyclic qualified scoped bindings should initialize concurrently`() {
        val owner = scope("owner")
        val left = named("left")
        val right = named("right")
        val constructors = CyclicBarrier(2)
        Stitch.register(
            module {
                scoped(owner, left) {
                    constructors.await(5, SECONDS)
                    First(get(left))
                }
                scoped(owner, left) { Second() }
                scoped(owner, right) {
                    constructors.await(5, SECONDS)
                    Second(get(right))
                }
                scoped(owner, right) { First() }
            },
        )
        val instance = owner.createScope()

        val instances = concurrently(
            { instance.get<First>(left) },
            { instance.get<Second>(right) },
        )

        assertSame(instance.get<First>(left), instances[0])
        assertSame(instance.get<Second>(right), instances[1])
        assertSame(instance.get<Second>(left), (instances[0] as First).second)
        assertSame(instance.get<First>(right), (instances[1] as Second).first)
    }

    @Test
    fun `the same binding should initialize independently in different scopes`() {
        val owner = scope("owner")
        val constructors = CyclicBarrier(2)
        Stitch.register(
            module {
                scoped(owner) {
                    constructors.await(5, SECONDS)
                    First()
                }
            },
        )
        val firstScope = owner.createScope()
        val secondScope = owner.createScope()

        val instances = concurrently(
            { firstScope.get<First>() },
            { secondScope.get<First>() },
        )

        assertNotSame(instances[0], instances[1])
        assertSame(instances[0], firstScope.get<First>())
        assertSame(instances[1], secondScope.get<First>())
    }

    @Test
    fun `concurrent singleton aliases should share one construction`() {
        val builds = AtomicInteger()
        Stitch.register(
            module {
                singleton {
                    builds.incrementAndGet()
                    DualRepo()
                }.bind<Repo>().bind<Auditable>()
            },
        )

        val instances = concurrently(
            *Array(12) { index ->
                {
                    when (index % 3) {
                        0 -> Stitch.get<DualRepo>()
                        1 -> Stitch.get<Repo>()
                        else -> Stitch.get<Auditable>()
                    }
                }
            },
        )

        assertEquals(1, builds.get())
        instances.forEach { assertSame(instances[0], it) }
    }

    @Test
    fun `concurrent scoped aliases should share their owning scope instance`() {
        val owner = scope("owner")
        val child = scope("child").dependsOn(owner)
        val builds = AtomicInteger()
        Stitch.register(
            module {
                scoped(owner) {
                    builds.incrementAndGet()
                    DualRepo()
                }.bind<Repo>().bind<Auditable>()
            },
        )
        val parentScope = owner.createScope()
        val childScope = parentScope.createChildScope(child)

        val instances = concurrently(
            *Array(12) { index ->
                {
                    when (index % 3) {
                        0 -> parentScope.get<DualRepo>()
                        1 -> childScope.get<Repo>()
                        else -> childScope.get<Auditable>()
                    }
                }
            },
        )

        assertEquals(1, builds.get())
        instances.forEach { assertSame(instances[0], it) }
        assertNotSame(instances[0], owner.createScope().get<Repo>())
        assertEquals(2, builds.get())
    }

    @Test
    fun `singleton initialization should retry after a failed factory`() {
        val builds = AtomicInteger()
        val entered = CountDownLatch(1)
        val fail = CountDownLatch(1)
        val builder = AtomicReference<Thread>()
        Stitch.register(
            module {
                singleton {
                    if (builds.incrementAndGet() == 1) {
                        builder.set(Thread.currentThread())
                        entered.countDown()
                        assertTrue(fail.await(5, SECONDS))
                        error("first attempt")
                    }
                    First()
                }
            },
        )

        val results = concurrently(
            { runCatching { Stitch.get<First>() } },
            { runCatching { Stitch.get<First>() } },
            { runCatching { Stitch.get<First>() } },
            { runCatching { Stitch.get<First>() } },
            afterStart = { threads ->
                try {
                    assertTrue(entered.await(5, SECONDS))
                    threads.filter { it !== builder.get() }.forEach(::awaitBlocked)
                } finally {
                    fail.countDown()
                }
            },
        )

        assertEquals(1, results.count { it.isFailure })
        assertEquals("first attempt", results.single { it.isFailure }.exceptionOrNull()?.message)
        assertEquals(2, builds.get())
        val instanceAfterRetry = Stitch.get<First>()
        results.filter { it.isSuccess }.forEach { assertSame(instanceAfterRetry, it.getOrThrow()) }
    }

    @Test
    fun `scoped initialization should retry after a failed factory`() {
        val owner = scope("owner")
        val builds = AtomicInteger()
        val entered = CountDownLatch(1)
        val fail = CountDownLatch(1)
        val builder = AtomicReference<Thread>()
        Stitch.register(
            module {
                scoped(owner) {
                    if (builds.incrementAndGet() == 1) {
                        builder.set(Thread.currentThread())
                        entered.countDown()
                        assertTrue(fail.await(5, SECONDS))
                        error("first attempt")
                    }
                    First()
                }
            },
        )
        val instance = owner.createScope()

        val results = concurrently(
            { runCatching { instance.get<First>() } },
            { runCatching { instance.get<First>() } },
            { runCatching { instance.get<First>() } },
            { runCatching { instance.get<First>() } },
            afterStart = { threads ->
                try {
                    assertTrue(entered.await(5, SECONDS))
                    threads.filter { it !== builder.get() }.forEach(::awaitBlocked)
                } finally {
                    fail.countDown()
                }
            },
        )

        assertEquals(1, results.count { it.isFailure })
        assertEquals("first attempt", results.single { it.isFailure }.exceptionOrNull()?.message)
        assertEquals(2, builds.get())
        val instanceAfterRetry = instance.get<First>()
        results.filter { it.isSuccess }.forEach { assertSame(instanceAfterRetry, it.getOrThrow()) }
    }

    @Test
    fun `qualified singleton cycles should still throw`() {
        val left = named("left")
        val right = named("right")
        Stitch.register(
            module {
                singleton(left) { First(get(right)) }
                singleton(right) { Second(get(left)) }
            },
        )

        repeat(2) {
            assertFailsWith<CycleException> { Stitch.get<First>(left) }
        }
    }

    @Test
    fun `qualified scoped cycles should still throw`() {
        val owner = scope("owner")
        val left = named("left")
        val right = named("right")
        Stitch.register(
            module {
                scoped(owner, left) { First(get(right)) }
                scoped(owner, right) { Second(get(left)) }
            },
        )
        val instance = owner.createScope()

        repeat(2) {
            assertFailsWith<CycleException> { instance.get<First>(left) }
        }
    }

    @Test
    fun `closing a scope during construction should reject the result`() {
        val owner = scope("owner")
        val entered = CountDownLatch(1)
        val closed = CountDownLatch(1)
        Stitch.register(
            module {
                scoped(owner) {
                    entered.countDown()
                    assertTrue(closed.await(5, SECONDS))
                    First()
                }
            },
        )
        val instance = owner.createScope()

        concurrently(
            { assertFailsWith<ScopeClosedException> { instance.get<First>() } },
            {
                assertTrue(entered.await(5, SECONDS))
                instance.close()
                closed.countDown()
            },
        )
        assertFailsWith<ScopeClosedException> { instance.get<First>() }
    }

    @Test
    fun `concurrent acyclic chains should share a dependency`() {
        val constructors = CyclicBarrier(2)
        val loggerBuilds = AtomicInteger()
        val daoBuilds = AtomicInteger()
        Stitch.register(
            module {
                singleton {
                    loggerBuilds.incrementAndGet()
                    Logger()
                }
                singleton {
                    if (daoBuilds.incrementAndGet() == 1) constructors.await(5, SECONDS)
                    Dao(get())
                }
                singleton {
                    constructors.await(5, SECONDS)
                    LoadUseCase(get())
                }
            },
        )

        val instances = concurrently(
            { Stitch.get<Dao>() },
            { Stitch.get<LoadUseCase>() },
        )

        assertEquals(1, daoBuilds.get(), "Concurrent resolutions must construct the shared Dao only once")
        assertSame(instances[0], (instances[1] as LoadUseCase).dao)
        assertSame(Stitch.get<Logger>(), (instances[0] as Dao).logger)
        assertEquals(1, loggerBuilds.get())
    }

    @Test
    fun `acyclic scope-dependent graphs should initialize independently`() {
        val owner = scope("owner")
        val firstScope = owner.createScope()
        val secondScope = owner.createScope()
        val constructors = CyclicBarrier(2)
        Stitch.register(
            module {
                scoped(owner) {
                    if (scope === firstScope) {
                        constructors.await(5, SECONDS)
                        First(get())
                    } else {
                        First()
                    }
                }
                scoped(owner) {
                    if (scope === secondScope) {
                        constructors.await(5, SECONDS)
                        Second(get())
                    } else {
                        Second()
                    }
                }
            },
        )

        val instances = concurrently(
            { firstScope.get<First>() },
            { secondScope.get<Second>() },
        )

        assertSame(firstScope.get<Second>(), (instances[0] as First).second)
        assertSame(secondScope.get<First>(), (instances[1] as Second).first)
        assertNotSame(firstScope.get<First>(), secondScope.get<First>())
        assertNotSame(firstScope.get<Second>(), secondScope.get<Second>())
    }

    @Test
    fun `raced acyclic graphs should publish each binding once without false cycles`() {
        repeat(20) { seed ->
            Stitch.reset()
            val random = kotlin.random.Random(seed)
            val qualifiers = Array(12) { named("node-$it") }
            val dependencies = Array(12) { index -> (index + 1 until 12).filter { random.nextBoolean() } }
            val builds = Array(12) { AtomicInteger() }
            Stitch.register(
                module {
                    qualifiers.forEachIndexed { index, qualifier ->
                        singleton(qualifier) {
                            builds[index].incrementAndGet()
                            GraphNode(dependencies[index].map { get<GraphNode>(qualifiers[it]) })
                        }
                    }
                },
            )
            val orders = Array(8) { qualifiers.toList().shuffled(random) }
            concurrently(*orders.map { order -> { order.forEach { Stitch.get<GraphNode>(it) } } }.toTypedArray())
            builds.forEach { assertEquals(1, it.get(), "seed=$seed") }
            qualifiers.forEachIndexed { index, qualifier ->
                val instance = Stitch.get<GraphNode>(qualifier)
                dependencies[index].forEachIndexed { position, dependency ->
                    assertSame(Stitch.get<GraphNode>(qualifiers[dependency]), instance.dependencies[position])
                }
            }
        }
    }

    @Test
    fun `closing a scope should reject both its builder and waiting callers`() {
        val owner = scope("owner")
        val instance = owner.createScope()
        val entered = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val builder = AtomicReference<Thread>()
        val builds = AtomicInteger()
        Stitch.register(
            module {
                scoped(owner) {
                    builds.incrementAndGet()
                    builder.set(Thread.currentThread())
                    entered.countDown()
                    assertTrue(finish.await(5, SECONDS))
                    First()
                }
            },
        )
        val results = concurrently(
            { runCatching { instance.get<First>() } },
            { runCatching { instance.get<First>() } },
            afterStart = { threads ->
                try {
                    assertTrue(entered.await(5, SECONDS))
                    awaitBlocked(threads.single { it !== builder.get() })
                    instance.close()
                } finally {
                    finish.countDown()
                }
            },
        )
        results.forEach { assertIs<ScopeClosedException>(it.exceptionOrNull()) }
        assertEquals(1, builds.get())
    }

    private class GraphNode(val dependencies: List<GraphNode>)

    private fun awaitBlocked(thread: Thread) {
        val deadline = System.nanoTime() + SECONDS.toNanos(5)
        while (thread.state != Thread.State.BLOCKED && System.nanoTime() < deadline) {
            Thread.sleep(1)
        }
        assertEquals(Thread.State.BLOCKED, thread.state, "Caller must wait for initialization before releasing the builder")
    }

    private fun <T> concurrently(
        vararg actions: () -> T,
        afterStart: (List<Thread>) -> Unit = {},
    ): List<T> {
        val start = CyclicBarrier(actions.size)
        val threads = ArrayList<Thread>()
        val executor = Executors.newFixedThreadPool(actions.size) { runnable ->
            Thread(runnable, "stitch-resolution-test").apply { isDaemon = true }.also(threads::add)
        }
        val futures = actions.map { action ->
            executor.submit<T> {
                start.await(5, SECONDS)
                action()
            }
        }
        try {
            afterStart(threads)
            return futures.map { it.get(10, SECONDS) }
        } finally {
            futures.forEach { it.cancel(true) }
            executor.shutdownNow()
        }
    }

    private class First(val second: Second? = null)

    private class Second(val first: First? = null)
}

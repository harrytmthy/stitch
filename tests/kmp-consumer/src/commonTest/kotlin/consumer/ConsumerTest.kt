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

package consumer

import com.harrytmthy.stitch.api.Stitch
import com.harrytmthy.stitch.api.StitchInjector
import com.harrytmthy.stitch.api.get
import com.harrytmthy.stitch.api.module
import com.harrytmthy.stitch.api.named
import com.harrytmthy.stitch.api.scope
import com.harrytmthy.stitch.api.typed
import com.harrytmthy.stitch.exception.MissingBindingException
import io.github.harrytmthy.stitch.generated.StitchSingletonGraph
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class ConsumerTest {
    @Test
    fun runtimeBindings() {
        Stitch.reset()
        val session = scope("session")
        Stitch.register(module {
            singleton { Service() }
            singleton(named("other")) { Service() }
            factory { Consumer() }
            scoped(session) { SessionService(get()) }
        })
        assertSame(Stitch.get<Service>(), Stitch.get<Service>())
        assertNotSame(Stitch.get<Service>(), Stitch.get<Service>(named("other")))
        assertNotSame(Stitch.get<Consumer>(), Stitch.get<Consumer>())
        val first = session.createScope()
        val second = session.createScope()
        assertSame(Stitch.get<SessionService>(scope = first), Stitch.get<SessionService>(scope = first))
        assertNotSame(Stitch.get<SessionService>(scope = first), Stitch.get<SessionService>(scope = second))
        assertFailsWith<MissingBindingException> { Stitch.get<String>() }
        first.close()
        second.close()
        Stitch.reset()
    }

    @Test
    fun generatedBindings() {
        val graph = StitchSingletonGraph()
        StitchInjector.init(graph)
        val first = graph.createInjectorForChildScope("session")
        val second = graph.createInjectorForChildScope("session")
        val consumer = Consumer()
        first.inject(consumer)
        assertSame(graph.get<Service>(), consumer.service)
        assertSame(consumer.service, consumer.api)
        assertSame(graph.get<Service>(typed<Remote>()), consumer.remote)
        assertSame(graph.get<Service>(named("remote")), consumer.namedRemote)
        assertSame(graph.get<Service>(typed<Local>()), consumer.local)
        assertSame(graph.get<Service>(named("local")), consumer.namedLocal)
        assertNotSame(consumer.remote, consumer.namedRemote)
        val contributed = ContributedConsumer()
        first.inject(contributed)
        assertSame(consumer.service, contributed.service)
        assertSame(consumer.remote, contributed.remote)
        assertSame(consumer.namedRemote, contributed.namedRemote)
        val missing = assertFailsWith<IllegalStateException> {
            graph.get<Service>(typed<Consumer>())
        }
        assertContains(missing.message.orEmpty(), "Consumer")
        assertFailsWith<IllegalStateException> { graph.get<Service>(named("absent")) }
        assertSame(first.get<SessionService>(), consumer.session)
        assertSame(consumer.service, consumer.session.service)
        assertNotSame(first.get<SessionService>(), second.get<SessionService>())
    }

    @Test
    fun unmatchedQualifierOnSingleBinding() {
        val graph = StitchSingletonGraph()
        val session = graph.createInjectorForChildScope("session")
        assertSame(graph.get<Service>(), graph.get<ServiceApi>())
        assertFailsWith<IllegalStateException> { graph.get<ServiceApi>(named("missing")) }
        assertFailsWith<IllegalStateException> { session.get<SessionService>(typed<Remote>()) }
    }

    @Test
    fun nullableCachedBindings() {
        val graph = StitchSingletonGraph()
        val session = graph.createInjectorForChildScope("session")
        repeat(2) {
            val target = NullableTarget()
            graph.inject(target)
            assertNull(target.service)
            val sessionTarget = NullableSessionTarget()
            session.inject(sessionTarget)
            assertNull(sessionTarget.service)
        }
        assertEquals(1, nullableServiceProvisions)
        assertEquals(1, nullableSessionServiceProvisions)
    }

    @Test
    fun childScopeNameIgnoresCase() {
        val session = StitchSingletonGraph().createInjectorForChildScope("Session")
        assertEquals("session", session.currentScope)
        assertSame(session.get<SessionService>(), session.get<SessionService>())
    }

    @Test
    fun providerRequests() {
        val graph = StitchSingletonGraph()
        val holder = graph.get<ProviderHolder>()
        assertSame(graph.get<Service>(), holder.service.get())
        assertSame(graph.get<Service>(named("remote")), holder.remote.get())
        assertNotSame(holder.fresh.get(), holder.fresh.get())
        assertSame(graph.get<Service>(), graph.get<ServiceClient>().service.get())
        val start = graph.get<CycleStart>()
        assertSame(start, start.end.get().start)
        val contributed = graph.get<ContributedProviderHolder>()
        assertSame(contributed.service, contributed.serviceProvider.get())
        val contributedTarget = ContributedProviderTarget()
        graph.inject(contributedTarget)
        assertSame(graph.get<Service>(), contributedTarget.service.get())
        val session = graph.createInjectorForChildScope("session")
        val target = ProviderTarget()
        session.inject(target)
        assertSame(session.get<SessionService>(), target.session.get())
    }

    @Test
    fun reentrantInitialization() {
        val graph = StitchSingletonGraph()
        repeat(2) {
            val cycle = assertFailsWith<IllegalStateException> { graph.get<EagerCycleStart>() }
            assertEquals(
                "Binding with type 'EagerCycleStart' is requested during its own initialization",
                cycle.message,
            )
        }
        assertFailsWith<IllegalStateException> { graph.get<ReentrantCycleStart>() }
        assertEquals(1, reentrantCycleStarts)
        assertFailsWith<IllegalArgumentException> { graph.get<RetriedService>() }
        assertSame(graph.get<RetriedService>(), graph.get<RetriedService>())
    }
}

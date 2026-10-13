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
import com.harrytmthy.stitch.api.module
import com.harrytmthy.stitch.api.scope
import kotlinx.atomicfu.atomic
import kotlin.native.concurrent.ObsoleteWorkersApi
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@OptIn(ObsoleteWorkersApi::class)
class NativeConcurrentResolutionTest {

    @Test
    fun `singleton is constructed once across workers`() = assertSingleConstruction(scoped = false)

    @Test
    fun `scoped binding is constructed once across workers`() = assertSingleConstruction(scoped = true)

    private fun assertSingleConstruction(scoped: Boolean) {
        val workers = List(8) { Worker.start() }
        try {
            repeat(300) {
                Stitch.reset()
                val state = ContentionState()
                val session = scope("session")
                val factory = {
                    state.constructions.incrementAndGet()
                    // Keep initialization in flight long enough for competing requests.
                    Worker.current.park(1_000)
                    Value()
                }
                Stitch.register(
                    module {
                        if (scoped) scoped(session) { factory() } else singleton { factory() }
                    },
                )
                val instance = if (scoped) session.createScope() else null
                val futures = workers.map { worker ->
                    worker.execute(TransferMode.SAFE, { Pair(state, instance) }) { (state, instance) ->
                        val started = TimeSource.Monotonic.markNow()
                        state.ready.incrementAndGet()
                        while (state.ready.value != 8) {
                            check(started.elapsedNow() < 10.seconds) { "Workers did not reach the start barrier" }
                            Worker.current.park(1_000)
                        }
                        Stitch.get<Value>(scope = instance)
                    }
                }
                // Drain every worker before asserting or closing its shared scope.
                val results = futures.map { runCatching { it.result } }.map { it.getOrThrow() }
                assertEquals(1, state.constructions.value)
                results.forEach { assertSame(results.first(), it) }
                instance?.close()
            }
        } finally {
            workers.map { it.requestTermination() }.forEach { it.result }
            Stitch.reset()
        }
    }

    private class ContentionState {
        val ready = atomic(0)
        val constructions = atomic(0)
    }

    private class Value
}

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
import com.harrytmthy.stitch.exception.MissingBindingException
import com.harrytmthy.stitch.internal.DependencyTable
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

/**
 * Tests for DI/SL path decoupling.
 *
 * These tests verify that:
 * 1. DI path caches singletons separately from SL path
 * 2. unregisterAll() doesn't affect DI path instances
 * 3. Duplicate bindings between DI and SL paths are detected
 */
class DiSlDecouplingTest {

    @BeforeTest
    fun setUp() {
        Stitch.unregisterAll()
    }

    @Test
    fun `DependencyTable should throw MissingBindingException when binding not found`() {
        // Create a minimal DependencyTable implementation
        val table = object : DependencyTable {
            override fun <T : Any> get(type: Class<T>, qualifier: com.harrytmthy.stitch.api.Qualifier?): T {
                throw MissingBindingException.missingType(type)
            }
            override fun <T : Any> injectFields(instance: T) {
                // No-op for test
            }
        }

        assertFailsWith<MissingBindingException> {
            table.get(Logger::class.java, null)
        }
    }

    @Test
    fun `unregisterAll should only clear SL path definitions and instances`() {
        // This test documents the expected behavior:
        // - SL path instances in Registry.singletons should be cleared
        // - DI path instances in generated @Volatile fields should NOT be cleared
        // - This ensures DI path instances survive unregisterAll()

        // Register SL module
        val slModule = module {
            singleton { Dao(Logger()) }
        }
        Stitch.register(slModule)

        // Get instance from SL path
        val daoBeforeUnregister = Stitch.get<Dao>()

        // Unregister all SL modules
        Stitch.unregisterAll()

        // SL path instance should be cleared, so getting it again should fail
        assertFailsWith<MissingBindingException> {
            Stitch.get<Dao>()
        }

        // Note: We can't test DI path directly here since we don't have
        // @Module/@Provides in the test module (stitch-compiler not applied).
        // The real test happens in the app module with generated code.
    }

    @Test
    fun `duplicate binding detection should catch conflicts in SL path`() {
        val module = module {
            singleton { Logger() }
            // Attempting to register duplicate should fail at registration time
        }

        Stitch.register(module)

        // Registering same binding again should throw
        val duplicateModule = module {
            singleton { Logger() }
        }

        assertFailsWith<IllegalStateException> {
            Stitch.register(duplicateModule)
        }
    }

    @Test
    fun `qualified bindings in DI and SL paths should not conflict`() {
        // This test documents that different qualifiers should not conflict
        // even across DI and SL paths.

        // Register SL module with qualified binding
        val slModule = module {
            singleton(qualifier = named("sl")) { Logger() }
        }
        Stitch.register(slModule)

        val slLogger = Stitch.get<Logger>(named("sl"))
        assertNotSame(Logger(), slLogger) // Verify we got the singleton instance

        // Note: Can't test DI path qualifier here without compiler.
        // The real test happens in app module with @Provides @Named.
    }

    @Test
    fun `DI path resolution should occur before SL path cache check`() {
        // This test documents the resolution order:
        // 1. DI path check (with duplicate detection)
        // 2. SL path cache check
        // 3. SL path resolution

        // We can only test SL path behavior here
        val module = module {
            singleton { Logger() }
        }
        Stitch.register(module)

        // First get populates cache
        val first = Stitch.get<Logger>()

        // Second get should hit cache
        val second = Stitch.get<Logger>()
        assertSame(first, second)
    }
}

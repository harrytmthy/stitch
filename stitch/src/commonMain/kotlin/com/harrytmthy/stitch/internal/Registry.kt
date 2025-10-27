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

package com.harrytmthy.stitch.internal

import com.harrytmthy.stitch.api.Qualifier
import com.harrytmthy.stitch.engine.Node
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal object Registry {

    val definitions = IdentityHashMap<Class<*>, MutableMap<Qualifier?, Node>>()

    val singletons = ConcurrentHashMap<Class<*>, ConcurrentHashMap<Any, Any>>()

    val version = AtomicInteger(0)
}

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

package com.harrytmthy.stitch.annotations

/**
 * Marks a class as an entry point for member injection.
 *
 * Entry points are typically Android components (Activities, Fragments, Services, etc.)
 * or other classes that cannot use constructor injection because their instantiation
 * is controlled by external frameworks.
 *
 * Classes annotated with @EntryPoint can have @Inject fields that will be injected
 * when [com.harrytmthy.stitch.api.Stitch.inject] is called.
 *
 * Example:
 * ```
 * @EntryPoint
 * class MainActivity : AppCompatActivity() {
 *     @Inject
 *     lateinit var userRepository: UserRepository
 *
 *     @Inject
 *     @Named("analytics")
 *     lateinit var logger: Logger
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         Stitch.inject(this)  // Injects all @Inject fields
 *     }
 * }
 * ```
 *
 * **Requirements:**
 * - @Inject fields must be mutable (var or lateinit var)
 * - @Inject fields must not be private
 * - All field dependencies must be provided via @Provides or @Inject constructor
 *
 * @see Inject
 * @see com.harrytmthy.stitch.api.Stitch.inject
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class EntryPoint

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

/**
 * Base class for all generated Stitch components.
 *
 * Provides a default `inject(target: Any)` method that enables users to write
 * `component.inject(this)` before compilation, avoiding unresolved reference errors.
 * Once the project is compiled with `@Inject`-annotated fields, KSP generates typed
 * `inject(target: SpecificType)` methods that take precedence due to Kotlin's
 * overload resolution favoring more specific types.
 *
 * Example:
 * ```kotlin
 * class MainActivity : AppCompatActivity() {
 *     private val activityComponent = StitchDiComponent.createActivityScopeComponent()
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         activityComponent.inject(this)  // Resolves to inject(Any) before compilation
 *         super.onCreate(savedInstanceState)
 *     }
 * }
 * ```
 *
 * After adding `@Inject` fields and recompiling:
 * ```kotlin
 * // Generated in StitchActivityScopeComponent
 * override fun inject(target: MainActivity) {  // More specific, takes precedence
 *     target.someField = someProvider()
 *     StitchDiComponent.injectMainActivity(target)
 * }
 * ```
 */
abstract class StitchComponent {

    /**
     * No-op base inject method.
     *
     * This method does nothing and is intended to be shadowed by generated typed inject methods.
     * It allows `component.inject(this)` to compile before KSP generates the actual function.
     *
     * @param target The object to inject dependencies into (ignored in base implementation)
     */
    fun inject(target: Any) {
        // No-op: actual injection is performed by generated typed overloads
    }
}

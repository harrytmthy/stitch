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

package com.harrytmthy.stitch.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Contribute(
    val moduleKey: String,
    val bindings: Array<ContributedBinding>,
    val requesters: Array<BindingRequester>,
    val scopes: Array<RegisteredScope>,
)

/**
 * A meta-annotation which represents a binding that is provided and/or requested in each
 * contributor module. Each binding has a locally unique [id] (per contribution) which is
 * used by [dependsOn] to represent dependencies.
 *
 * [kind] represents the binding type:
 * 0 -> Requested binding
 * 1 -> Provided binding via @Inject-annotated constructor
 * 2 -> Provided binding via @Provides-annotated top-level function
 * 3 -> Provided binding via @Provides-annotated function inside an object
 * 4 -> Provided binding via @Provides-annotated function inside a class
 * 5 -> Provided alias via @Binds
 *
 * Example of a provided binding with `kind = 3`:
 *
 * ```
 *
 * package com.something.core.di // providerPackageName = "com.something.core.di"
 *
 * object CoreModule { // providerClassName = "CoreModule"
 *
 *     @Singleton
 *     @Provides
 *     fun provideLogger(): Logger // providerFunctionName = "provideLogger"
 * }
 * ```
 */
annotation class ContributedBinding(
    val id: Int,
    val type: String,
    val qualifier: String,
    val scope: String,
    val location: String,
    val kind: Int,
    val providerPackageName: String,
    val providerFunctionName: String,
    val providerClassName: String,
    val dependsOn: IntArray,
)

annotation class BindingRequester(val name: String, val fields: Array<RequestedField>)

annotation class RequestedField(val bindingId: Int, val fieldName: String)

/**
 * Represents a custom scope that is registered in a contributor module.
 * Each scope depends on Singleton by default (id = 0).
 *
 * The aggregator will register custom scopes and their dependencies using [canonicalName]
 * as the identifier, while enforcing these rules:
 * - A registered scope with a non-empty [qualifiedName] will be prioritized.
 * - If there are more than one scope with same [canonicalName] but different [qualifiedName],
 *   the aggregator will report them as duplicates + mention each [location] to ease debugging.
 */
annotation class RegisteredScope(
    val id: Int,
    val canonicalName: String,
    val qualifiedName: String,
    val location: String,
    val dependsOn: Int,
)

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
 * Meta-annotation that identifies a scope annotation for the precompiled path.
 *
 * Scope annotations control the lifecycle and sharing of dependencies within a specific scope
 * (e.g. Activity scope, Fragment scope). Combine with [DependsOn] to form a unidirectional
 * dependency chain.
 *
 * Example:
 * ```
 * @Scope
 * @Retention(AnnotationRetention.RUNTIME)
 * annotation class Activity
 *
 * @Scope(name = "myFragment")
 * @DependsOn(Activity::class)
 * @Retention(AnnotationRetention.RUNTIME)
 * annotation class Fragment
 * ```
 *
 * This creates a dependency chain: `@Fragment → @Activity → @Singleton`
 *
 * Scopes without [DependsOn] default to depending on [Singleton].
 *
 * @param name Optional custom name for this scope. When empty, the annotation's simple name
 *             is used (lowercased). Custom names are canonicalized to lowercase.
 *
 * @see DependsOn
 * @see Singleton
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Scope(val name: String = "")

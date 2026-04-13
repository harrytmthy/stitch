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
 * Marks an annotation as a qualifier annotation.
 *
 * Qualifier annotations are used to differentiate between multiple bindings
 * of the same type.
 *
 * Example:
 * ```
 * @Qualifier
 * @Retention(AnnotationRetention.BINARY)
 * annotation class Production
 *
 * @Qualifier
 * @Retention(AnnotationRetention.BINARY)
 * annotation class Staging
 *
 * @Module
 * class ConfigModule {
 *     @Provides
 *     @Production
 *     fun provideProdUrl(): String = "https://api.prod.com"
 *
 *     @Provides
 *     @Staging
 *     fun provideStagingUrl(): String = "https://api.staging.com"
 * }
 * ```
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Qualifier

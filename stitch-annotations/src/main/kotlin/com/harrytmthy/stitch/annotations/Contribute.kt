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

@Target(AnnotationTarget.FILE)
@Retention(AnnotationRetention.BINARY)
annotation class Contribute(
    val bindings: Array<ContributedBinding>,
    val requesters: Array<BindingRequester> = [],
)

/**
 * A meta-annotation which represents a binding that is provided and/or requested
 * in each contributor module.
 *
 * Each binding has a locally unique [id] (per contribution) which is used by [dependsOn]
 * to represent dependencies.
 */
annotation class ContributedBinding(
    val id: Int,
    val type: String,
    val qualifier: String = "",
    val scope: String = "",
    val provided: Boolean = false,
    val dependsOn: IntArray = [],
)

annotation class BindingRequester(val name: String, val fields: Array<RequestedField>)

annotation class RequestedField(val bindingId: Int, val fieldName: String)

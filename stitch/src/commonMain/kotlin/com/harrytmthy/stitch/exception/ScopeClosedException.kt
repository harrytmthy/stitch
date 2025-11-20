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

package com.harrytmthy.stitch.exception

import com.harrytmthy.stitch.api.Qualifier
import kotlin.reflect.KClass

/**
 * Thrown when trying to resolve a scoped binding using a scope that is not open.
 *
 * This guards against using instances after a lifecycle has ended, and against
 * accessing scoped bindings before the scope is opened.
 *
 * To fix:
 * - Call `scope.open()` before resolving.
 * - Avoid holding on to references after `scope.close()`.
 */
class ScopeClosedException internal constructor(
    type: KClass<*>,
    qualifier: Qualifier?,
    scopeId: Int,
) : GetFailedException(type, qualifier, explanation = "Scope with id '$scopeId' is not open!")

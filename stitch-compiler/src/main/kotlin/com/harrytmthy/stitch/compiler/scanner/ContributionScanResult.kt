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

package com.harrytmthy.stitch.compiler.scanner

import com.harrytmthy.stitch.compiler.model.Binding
import com.harrytmthy.stitch.compiler.model.ProvidedBinding
import com.harrytmthy.stitch.compiler.model.RequestedBinding

sealed class ContributionScanResult {

    data object Error : ContributionScanResult()

    data class Success(
        val providedBindings: Map<Binding, ProvidedBinding>,
        val requestedBindingsByModuleKey: Map<String, Map<String, List<RequestedBinding>>>,
    ) : ContributionScanResult()
}

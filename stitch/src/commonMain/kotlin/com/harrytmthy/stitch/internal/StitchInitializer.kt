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
 * Initializer interface implemented by KSP-generated code.
 *
 * The generated implementation provides the compile-time dependency table
 * and is automatically discovered via ServiceLoader.
 *
 * **Note**: This is a public API implemented by generated code. Do not implement manually.
 */
interface StitchInitializer {

    /**
     * Returns the compile-time generated dependency table.
     */
    fun getDependencyTable(): DependencyTable
}

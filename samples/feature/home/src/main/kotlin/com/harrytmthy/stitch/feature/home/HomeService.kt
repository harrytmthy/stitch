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

package com.harrytmthy.stitch.feature.home

import com.harrytmthy.stitch.annotations.Binds
import com.harrytmthy.stitch.annotations.Inject
import com.harrytmthy.stitch.annotations.Singleton
import com.harrytmthy.stitch.core.Logger

interface HomeService {
    fun fetch(): Result<Unit>
}

@Singleton
class HomeServiceImpl @Inject constructor(private val logger: Logger) : HomeService {

    override fun fetch(): Result<Unit> {
        logger.log("Fetch success!")
        return Result.success(Unit)
    }
}

// Another acceptable representation of @Binds usage
interface Binder {

    @Binds
    fun bindsHomeService(service: HomeServiceImpl): HomeService
}

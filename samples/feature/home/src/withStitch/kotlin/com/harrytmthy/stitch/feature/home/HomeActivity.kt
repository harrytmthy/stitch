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

package com.harrytmthy.stitch.feature.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.harrytmthy.stitch.annotations.Named
import com.harrytmthy.stitch.api.StitchInjector
import com.harrytmthy.stitch.api.get
import com.harrytmthy.stitch.api.named
import com.harrytmthy.stitch.api.typed
import com.harrytmthy.stitch.core.Logger
import com.harrytmthy.stitch.core.Production
import com.harrytmthy.stitch.core.Staging
import javax.inject.Inject

class HomeActivity : AppCompatActivity() {

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var viewModel: HomeViewModel

    @Production
    @Inject
    lateinit var homeService: HomeService

    @Production
    @Inject
    lateinit var homeServiceImpl: HomeServiceImpl

    @Staging
    @Inject
    lateinit var homeServiceStaging: HomeService

    @Staging
    @Inject
    lateinit var homeServiceImplStaging: HomeServiceStaging

    @Named("dev")
    @Inject
    lateinit var homeServiceDev: HomeService

    @Named("dev")
    @Inject
    lateinit var homeServiceDevImpl: HomeServiceDev

    @Inject
    lateinit var homeTracker: HomeTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        val activityInjector = StitchInjector.getSingletonGraph()
            .createInjectorForChildScope("activity")
        activityInjector.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(android.R.layout.list_content)
        assert(homeService === homeServiceImpl)
        assert(viewModel.homeService === homeServiceImpl)
        assert(homeServiceStaging === homeServiceImplStaging)
        assert(homeService !== homeServiceStaging)
        assert(homeServiceDev === homeServiceDevImpl)
        assert(homeService !== homeServiceDev)

        val stagingQualifier = typed<Staging>()
        assert(activityInjector.get<HomeService>(stagingQualifier) === homeServiceStaging)

        val devQualifier = named("dev")
        assert(activityInjector.get<HomeService>(devQualifier) === homeServiceDev)
    }
}

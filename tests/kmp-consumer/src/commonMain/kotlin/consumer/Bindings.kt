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

package consumer

import com.harrytmthy.stitch.annotations.Inject
import com.harrytmthy.stitch.annotations.Named
import com.harrytmthy.stitch.annotations.Provides
import com.harrytmthy.stitch.annotations.Qualifier
import com.harrytmthy.stitch.annotations.Singleton
import com.harrytmthy.stitch.annotations.StitchRoot

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Local

@Provides
@Singleton
@Local
fun provideLocalService(): Service = Service()

@Provides
@Singleton
@Named("local")
fun provideNamedLocalService(): Service = Service()

@Session
class SessionService @Inject constructor(val service: Service)

@Session
class Consumer {
    @Inject
    lateinit var service: Service
    @Inject
    lateinit var api: ServiceApi
    @Inject
    @Remote
    lateinit var remote: Service
    @Inject
    @Named("remote")
    lateinit var namedRemote: Service
    @Inject
    @Local
    lateinit var local: Service
    @Inject
    @Named("local")
    lateinit var namedLocal: Service
    @Inject
    lateinit var session: SessionService
}

class NullableService

class NullableSessionService

var nullableServiceProvisions = 0

var nullableSessionServiceProvisions = 0

@Provides
@Singleton
fun provideNullableService(): NullableService? {
    nullableServiceProvisions++
    return null
}

@Provides
@Session
fun provideNullableSessionService(): NullableSessionService? {
    nullableSessionServiceProvisions++
    return null
}

class NullableTarget {
    @Inject
    var service: NullableService? = NullableService()
}

@Session
class NullableSessionTarget {
    @Inject
    var service: NullableSessionService? = NullableSessionService()
}

@StitchRoot
object AppGraph

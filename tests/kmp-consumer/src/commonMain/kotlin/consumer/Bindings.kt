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

import com.harrytmthy.stitch.annotations.Binds
import com.harrytmthy.stitch.annotations.Inject
import com.harrytmthy.stitch.annotations.Named
import com.harrytmthy.stitch.annotations.Provides
import com.harrytmthy.stitch.annotations.Qualifier
import com.harrytmthy.stitch.annotations.Singleton
import com.harrytmthy.stitch.annotations.StitchRoot
import com.harrytmthy.stitch.api.Provider

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

class FreshService @Inject constructor()

@Singleton
class ProviderHolder @Inject constructor(
    val service: Provider<Service>,
    @Named("remote") val remote: Provider<Service>,
    val fresh: Provider<FreshService>,
)

class ServiceClient(val service: Provider<Service>)

@Provides
fun provideServiceClient(service: Provider<Service>): ServiceClient = ServiceClient(service)

@Singleton
class CycleStart @Inject constructor(val end: Provider<CycleEnd>)

@Singleton
class CycleEnd @Inject constructor(val start: CycleStart)

@Singleton
class EagerCycleStart @Inject constructor(end: Provider<EagerCycleEnd>) {
    val end = end.get()
}

class EagerCycleEnd @Inject constructor(val start: EagerCycleStart)

var reentrantCycleStarts = 0

@Singleton
class ReentrantCycleStart @Inject constructor(end: Provider<ReentrantCycleEnd>) {
    val end = if (reentrantCycleStarts++ == 0) end.get() else null
}

class ReentrantCycleEnd @Inject constructor(val start: ReentrantCycleStart)

var retriedServiceAttempts = 0

@Singleton
class RetriedService @Inject constructor() {
    init {
        require(retriedServiceAttempts++ > 0) { "First attempt fails" }
    }
}

@Session
class ProviderTarget {
    @Inject
    lateinit var session: Provider<SessionService>
}

class MissingLogger(val name: String)

interface DefaultedApi

@Singleton
@Binds(aliases = [DefaultedApi::class])
class DefaultedHolder @Inject constructor(
    val logger: MissingLogger = MissingLogger("constructor"),
    val service: Service,
    @Named("remote") val remote: Service = Service(),
    val lazyLogger: Provider<MissingLogger> = Provider { MissingLogger("provider") },
) : DefaultedApi

class DefaultedClient(val logger: MissingLogger, val service: Service)

@Provides
fun provideDefaultedClient(
    logger: MissingLogger = MissingLogger("provides"),
    service: Service,
): DefaultedClient = DefaultedClient(logger, service)

@StitchRoot
object AppGraph

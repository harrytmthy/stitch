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
import com.harrytmthy.stitch.annotations.DependsOn
import com.harrytmthy.stitch.annotations.Inject
import com.harrytmthy.stitch.annotations.Named
import com.harrytmthy.stitch.annotations.Provides
import com.harrytmthy.stitch.annotations.Qualifier
import com.harrytmthy.stitch.annotations.Scope
import com.harrytmthy.stitch.annotations.Singleton

interface ServiceApi

@Singleton
class Service @Inject constructor() : ServiceApi

abstract class ServiceBindings {
    @Binds
    abstract fun bindService(service: Service): ServiceApi
}

@Scope
@DependsOn
@Retention(AnnotationRetention.BINARY)
annotation class Session

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Remote

@Provides
@Singleton
@Remote
fun provideRemoteService(): Service = Service()

@Provides
@Singleton
@Named("remote")
fun provideNamedRemoteService(): Service = Service()

@Session
class ContributedConsumer {
    @Inject
    lateinit var service: Service
    @Inject
    @Remote
    lateinit var remote: Service
    @Inject
    @Named("remote")
    lateinit var namedRemote: Service
}

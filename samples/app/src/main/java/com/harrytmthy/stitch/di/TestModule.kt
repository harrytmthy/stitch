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

package com.harrytmthy.stitch.di

import com.harrytmthy.stitch.annotations.Binds
import com.harrytmthy.stitch.annotations.Inject
import com.harrytmthy.stitch.annotations.Module
import com.harrytmthy.stitch.annotations.Named
import com.harrytmthy.stitch.annotations.Provides
import com.harrytmthy.stitch.annotations.Singleton
import com.harrytmthy.stitch.core.Activity
import com.harrytmthy.stitch.core.Fragment
import com.harrytmthy.stitch.core.Logger

interface UserRepository {
    fun getUser(id: Int): String
}

interface UserReader {
    fun readUser(id: Int): String
}

@Binds(aliases = [UserRepository::class, UserReader::class])
@Singleton
class UserRepositoryImpl @Inject constructor(
    internal val logger: Logger,
    internal val apiService: ApiService,
) : UserRepository, UserReader {

    override fun getUser(id: Int): String {
        logger.log("Fetching user $id")
        return "User#$id"
    }

    override fun readUser(id: Int): String = getUser(id)
}

// Factory (unscoped) - new instance each time
class ApiService @Inject constructor(
    internal val logger: Logger,
    @param:Named("baseUrl") internal val baseUrl: String
) {
    fun fetch(endpoint: String): String {
        logger.log("Fetching $baseUrl$endpoint")
        return "Response from $endpoint"
    }
}

interface CacheService

class CacheServiceImpl : CacheService {
    fun get(key: String): String {
        return "cached_$key"
    }
}

@Activity
class ViewModel @Inject constructor(
    internal val repository: UserRepository,
    @param:Named("activity") internal val cacheService: CacheServiceImpl,
) {

    @Inject
    lateinit var logger: Logger
}

interface Processor {
    fun process(): String
}

// Mixed: Constructor + Field injection
@Singleton
class ComplexService @Inject constructor(
    private val logger: Logger
) : Processor {
    @Inject
    lateinit var cache: CacheServiceImpl

    @Inject
    @Named("baseUrl")
    var baseUrl: String = ""

    override fun process(): String {
        logger.log("Processing with cache and baseUrl: $baseUrl")
        return cache.get("key") ?: "default"
    }
}

@Module
object AppModule {

    internal const val BASE_URL = "https://api.example.com/"

    @Named("baseUrl")
    @Singleton
    @Provides
    fun provideBaseUrl(): String = BASE_URL

    @Named("null")
    @Singleton
    @Provides
    fun provideNullInt(): Int? = null

    @Singleton
    @Binds(aliases = [CacheService::class])
    @Provides
    fun provideSingletonCacheService(): CacheServiceImpl = CacheServiceImpl()

    @Activity
    @Named("activity")
    @Provides
    fun provideActivityScopedCacheService(): CacheServiceImpl = CacheServiceImpl()

    @Fragment
    @Named("fragment")
    @Provides
    fun provideFragmentScopedCacheService(): CacheServiceImpl = CacheServiceImpl()

    @Module
    interface Inner {

        @Binds
        fun bindProcessor(service: ComplexService): Processor
    }
}

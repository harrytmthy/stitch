package com.harrytmthy.stitch.di

import com.harrytmthy.stitch.annotations.Binds
import com.harrytmthy.stitch.annotations.Provides
import com.harrytmthy.stitch.core.Activity
import com.harrytmthy.stitch.core.Fragment
import com.harrytmthy.stitch.core.Logger
import com.harrytmthy.stitch.core.LoggerImpl
import com.harrytmthy.stitch.feature.home.HomeService
import com.harrytmthy.stitch.feature.home.HomeServiceImpl
import dagger.Module
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

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

@Activity
class ViewModel @Inject constructor(
    internal val repository: UserRepository,
    @param:Named("activity") internal val cacheService: CacheServiceImpl,
) {

    @Inject
    lateinit var logger: Logger
}

// Mixed: Constructor + Field injection
@Singleton
class ComplexService @Inject constructor(
    private val logger: Logger,
    internal val cache: CacheServiceImpl,
    @param:Named("baseUrl") private val baseUrl: String,
) : Processor {

    override fun process(): String {
        logger.log("Processing with cache and baseUrl: $baseUrl")
        return cache.get("key")
    }
}

@Module(includes = [AppModule.Inner::class])
class AppModule {

    @Named("baseUrl")
    @Singleton
    @Provides
    @dagger.Provides
    fun provideBaseUrl(): String = BASE_URL

    @Singleton
    @Binds(aliases = [CacheService::class])
    @Provides
    @dagger.Provides
    fun provideSingletonCacheService(): CacheServiceImpl = CacheServiceImpl()

    @Activity
    @Named("activity")
    @Provides
    @dagger.Provides
    fun provideActivityScopedCacheService(): CacheServiceImpl = CacheServiceImpl()

    @Fragment
    @Named("fragment")
    @Provides
    @dagger.Provides
    fun provideFragmentScopedCacheService(): CacheServiceImpl = CacheServiceImpl()

    @Module
    interface Inner {

        @Binds
        @dagger.Binds
        fun bindProcessor(service: ComplexService): Processor

        @Binds
        @dagger.Binds
        fun bindLogger(logger: LoggerImpl): Logger

        @Binds
        @dagger.Binds
        fun bindsHomeService(service: HomeServiceImpl): HomeService

        @dagger.Binds
        fun bindSingletonCacheService(service: CacheServiceImpl): CacheService
    }
}

package com.harrytmthy.stitch.di

import com.harrytmthy.stitch.core.Activity
import com.harrytmthy.stitch.core.Logger
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

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
    @Named("baseUrl") private val baseUrl: String,
) : Processor {

    override fun process(): String {
        logger.log("Processing with cache and baseUrl: $baseUrl")
        return cache.get("key")
    }
}

package com.harrytmthy.stitch.di

import com.harrytmthy.stitch.core.Activity
import com.harrytmthy.stitch.core.Logger
import com.harrytmthy.stitch.core.LoggerImpl
import com.harrytmthy.stitch.feature.home.HomeService
import com.harrytmthy.stitch.feature.home.HomeServiceImpl
import com.harrytmthy.stitch.feature.home.HomeViewModel
import com.harrytmthy.stitch.ui.MainActivity
import dagger.Binds
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import javax.inject.Named
import javax.inject.Singleton

@Module(subcomponents = [ActivitySubcomponent::class])
abstract class DaggerAppModule {

    @Binds
    @Singleton
    abstract fun bindLogger(impl: LoggerImpl): Logger

    @Binds
    @Singleton
    abstract fun bindHomeService(impl: HomeServiceImpl): HomeService

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    @Singleton
    abstract fun bindUserReader(impl: UserRepositoryImpl): UserReader

    @Binds
    @Singleton
    abstract fun bindProcessor(impl: ComplexService): Processor

    @Binds
    @Singleton
    abstract fun bindCacheService(impl: CacheServiceImpl): CacheService

    companion object {

        @Provides
        @Singleton
        @Named("baseUrl")
        fun provideBaseUrl(): String = BASE_URL

        @Provides
        @Singleton
        fun provideSingletonCacheService(): CacheServiceImpl = CacheServiceImpl()
    }
}

@Module
object ActivityModule {

    @Provides
    @Activity
    @Named("activity")
    fun provideActivityScopedCacheService(): CacheServiceImpl = CacheServiceImpl()
}

@Singleton
@Component(modules = [DaggerAppModule::class])
interface AppComponent {
    fun activitySubcomponent(): ActivitySubcomponent.Factory
}

@Activity
@Subcomponent(modules = [ActivityModule::class])
interface ActivitySubcomponent {
    fun homeViewModel(): HomeViewModel
    fun inject(activity: MainActivity)

    @Subcomponent.Factory
    interface Factory {
        fun create(): ActivitySubcomponent
    }
}

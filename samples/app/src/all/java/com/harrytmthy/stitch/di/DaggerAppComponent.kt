package com.harrytmthy.stitch.di

import com.harrytmthy.stitch.core.Activity
import com.harrytmthy.stitch.feature.home.HomeViewModel
import com.harrytmthy.stitch.ui.MainActivity
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import javax.inject.Named
import javax.inject.Singleton

@Module
object ActivityModule {

    @Provides
    @Activity
    @Named("activity")
    fun provideActivityScopedCacheService(): CacheServiceImpl = CacheServiceImpl()
}

@Singleton
@Component(modules = [AppModule::class])
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

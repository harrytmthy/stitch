package com.harrytmthy.stitch

import android.app.Application
import com.harrytmthy.stitch.fixture.KoinFixtureModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinApplication
import org.koin.core.context.startKoin

@KoinApplication(modules = [KoinFixtureModule::class])
class StitchSampleApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@StitchSampleApp)
        }
    }
}

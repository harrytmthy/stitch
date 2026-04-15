package com.harrytmthy.stitch

import android.app.Application
import com.harrytmthy.stitch.fixture.fixtureModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class StitchSampleApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@StitchSampleApp)
            modules(fixtureModule)
        }
    }
}

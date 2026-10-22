package com.harrytmthy.stitch

import android.app.Application
import com.harrytmthy.stitch.annotations.StitchRoot
import com.harrytmthy.stitch.api.StitchInjector
import io.github.harrytmthy.stitch.generated.StitchSingletonGraph

@StitchRoot
class StitchSampleApp : Application() {

    override fun onCreate() {
        StitchInjector.init(StitchSingletonGraph())
        super.onCreate()
    }
}

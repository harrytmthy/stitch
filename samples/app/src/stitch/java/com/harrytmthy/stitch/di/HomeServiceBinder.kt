package com.harrytmthy.stitch.di

import com.harrytmthy.stitch.annotations.Binds
import com.harrytmthy.stitch.feature.home.HomeService
import com.harrytmthy.stitch.feature.home.HomeServiceImpl

interface HomeServiceBinder {

    @Binds
    fun bindsHomeService(service: HomeServiceImpl): HomeService
}

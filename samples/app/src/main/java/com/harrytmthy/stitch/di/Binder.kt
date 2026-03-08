package com.harrytmthy.stitch.di

import com.harrytmthy.stitch.annotations.Binds
import com.harrytmthy.stitch.core.Logger
import com.harrytmthy.stitch.core.LoggerImpl

interface Binder {

    @Binds
    fun bindsLogger(logger: LoggerImpl): Logger
}
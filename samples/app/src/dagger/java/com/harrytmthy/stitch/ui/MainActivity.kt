package com.harrytmthy.stitch.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.harrytmthy.stitch.R
import com.harrytmthy.stitch.core.Logger
import com.harrytmthy.stitch.di.ApiService
import com.harrytmthy.stitch.di.BASE_URL
import com.harrytmthy.stitch.di.CacheServiceImpl
import com.harrytmthy.stitch.di.ComplexService
import com.harrytmthy.stitch.di.DaggerAppComponent
import com.harrytmthy.stitch.di.Processor
import com.harrytmthy.stitch.di.UserReader
import com.harrytmthy.stitch.di.UserRepository
import com.harrytmthy.stitch.di.UserRepositoryImpl
import com.harrytmthy.stitch.di.ViewModel
import javax.inject.Inject
import javax.inject.Named

class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var userReader: UserReader

    @Inject
    lateinit var userRepositoryImpl: UserRepositoryImpl

    @Inject
    @Named("baseUrl")
    lateinit var baseUrl: String

    @Inject
    lateinit var processor: Processor

    @Inject
    @Named("activity")
    lateinit var activityCacheService: CacheServiceImpl

    @Inject
    @Named("activity")
    lateinit var activityCacheService2: CacheServiceImpl

    @Inject
    lateinit var complexService: ComplexService

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var viewModel: ViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        renderFragment()
        DaggerAppComponent.create()
            .activitySubcomponent()
            .create()
            .inject(this)
        assertDagger()
    }

    private fun renderFragment() {
        val fragment = MainFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun assertDagger() {
        // Singleton objects
        check(logger === userRepositoryImpl.logger)
        check(userRepository === userRepositoryImpl)
        check(userReader === userRepository)
        check(userReader === userRepositoryImpl)
        check(userRepositoryImpl.logger === logger)
        check(viewModel.repository === userRepository)
        check(viewModel.cacheService === activityCacheService)
        check(activityCacheService === activityCacheService2)
        check(processor === complexService)
        check(complexService.cache !== activityCacheService)
        check(baseUrl === BASE_URL)

        // Factory objects
        check(apiService !== userRepositoryImpl.apiService)
        check(apiService.logger === logger)
    }
}

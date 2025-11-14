package com.harrytmthy.stitch

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.harrytmthy.stitch.annotations.Inject
import com.harrytmthy.stitch.annotations.Named
import com.harrytmthy.stitch.api.Stitch
import com.harrytmthy.stitch.api.named

/**
 * Only for testing convenience. Please ignore the weird architecture 😄
 */
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var logger: Logger

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var userRepositoryImpl: UserRepositoryImpl

    @Inject
    @Named("baseUrl")
    lateinit var baseUrl: String

    @Inject
    lateinit var cacheService: CacheService

    @javax.inject.Inject
    lateinit var complexService: ComplexService

    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var viewModel: ViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        Stitch.inject(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        assertStitch()
    }

    private fun assertStitch() {
        // Singleton objects
        check(logger === Stitch.get<Logger>())
        check(userRepository === userRepositoryImpl)
        check(userRepositoryImpl.logger === logger)
        check(viewModel.repository === Stitch.get<UserRepository>())
        check(viewModel.cache === cacheService)
        check(cacheService === Stitch.get<CacheService>())
        check(complexService.cache === cacheService)
        check(baseUrl === Stitch.get<String>(named("baseUrl")))

        // Factory objects
        check(apiService !== Stitch.get<ApiService>())
        check(Stitch.get<ApiService>() !== Stitch.get<ApiService>())
        check(apiService.logger === logger)
        check(Stitch.get<ApiService>().baseUrl === baseUrl)
    }
}
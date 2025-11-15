package com.harrytmthy.stitch

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.harrytmthy.stitch.AppModule.BASE_URL
import com.harrytmthy.stitch.annotations.Inject
import com.harrytmthy.stitch.annotations.Named
import com.harrytmthy.stitch.api.Stitch
import com.harrytmthy.stitch.exception.MissingBindingException
import com.harrytmthy.stitch.generated.StitchMainActivityInjector

/**
 * Only for testing convenience. Please ignore the weird architecture 😄
 */
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
    lateinit var cacheService: CacheService

    @javax.inject.Inject
    lateinit var complexService: ComplexService

    @Inject
    lateinit var apiService: ApiService

    @Named("null")
    @Inject
    var nullableInt: Int? = Int.MIN_VALUE // Should be replaced by null

    @Inject
    lateinit var viewModel: ViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        StitchMainActivityInjector.inject(this)
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
        check(logger === userRepositoryImpl.logger)
        check(userRepository === userRepositoryImpl)
        check(userReader === userRepository)
        check(userReader === userRepositoryImpl)
        check(userRepositoryImpl.logger === logger)
        check(viewModel.repository === userRepository)
        check(viewModel.cache === cacheService)
        check(processor === complexService)
        check(complexService.cache === cacheService)
        check(baseUrl === BASE_URL)
        check(nullableInt == null)

        // Factory objects
        check(apiService !== userRepositoryImpl.apiService)
        check(apiService.logger === logger)

        // SL path
        runCatching { Stitch.get<Logger>() }
            .exceptionOrNull()
            .let { check(it is MissingBindingException) }
    }
}
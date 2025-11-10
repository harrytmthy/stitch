package com.harrytmthy.stitch

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.harrytmthy.stitch.annotations.EntryPoint
import com.harrytmthy.stitch.annotations.Inject
import com.harrytmthy.stitch.api.Stitch
import com.harrytmthy.stitch.api.named

@EntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var logger: Logger

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

        demonstrateStitch()
    }

    private fun demonstrateStitch() {
        println("\n=== Stitch DI-SL Hybrid Demo ===\n")

        // Get singleton logger (from DI path - compile-time generated)
        val logger2 = Stitch.get<Logger>()
        println("✓ Singleton test: logger === logger2 = ${logger === logger2}")

        // Get repository with injected dependencies (from DI path)
        val repo = Stitch.get<UserRepositoryImpl>()
        println("✓ Repository test: ${repo.getUser(42)}")

        // Get factory instance - new object each time
        val api1 = Stitch.get<ApiService>()
        val api2 = Stitch.get<ApiService>()
        println("✓ Factory test: api1 === api2 = ${api1 === api2} (should be false)")

        // Get qualified dependency
        val baseUrl = Stitch.get<String>(named("baseUrl"))
        println("✓ Qualified dependency: baseUrl = $baseUrl")

        println("\n🚀 Stitch DI-SL Hybrid: Zero manual initialization!")
        println("   - Compile-time safety via @Module/@Provides")
        println("   - O(1) dependency lookup via generated code")
        println("   - Auto-discovery via ServiceLoader\n")
    }
}
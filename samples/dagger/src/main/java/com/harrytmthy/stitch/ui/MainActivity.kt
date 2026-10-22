package com.harrytmthy.stitch.ui

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.harrytmthy.stitch.fixture.DaggerDaggerFixtureComponent
import com.harrytmthy.stitch.fixture.InjectionTarget
import com.harrytmthy.stitch.fixture.InjectionTarget2
import com.harrytmthy.stitch.sample.R

class MainActivity : AppCompatActivity() {

    private val fixtureTarget = InjectionTarget()
    private val fixtureTarget2 = InjectionTarget2()

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
        DaggerDaggerFixtureComponent.create().apply {
            inject(fixtureTarget)
            inject(fixtureTarget2)
        }
    }

    private fun renderFragment() {
        val fragment = MainFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}

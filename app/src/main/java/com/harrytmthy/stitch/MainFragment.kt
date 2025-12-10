package com.harrytmthy.stitch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.harrytmthy.stitch.annotations.Inject
import com.harrytmthy.stitch.annotations.Named

class MainFragment : Fragment() {

    @Inject
    lateinit var logger: Logger

    @Named("activity")
    @Inject
    lateinit var activityCacheService: CacheServiceImpl

    @Named("fragment")
    @Inject
    lateinit var fragmentCacheService: CacheServiceImpl

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // TODO: Re-add this
    }
}
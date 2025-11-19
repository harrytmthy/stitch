package com.harrytmthy.stitch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.harrytmthy.stitch.annotations.Inject
import com.harrytmthy.stitch.annotations.Named
import com.harrytmthy.stitch.generated.StitchFragmentScopeComponent

class MainFragment : Fragment() {

    @Inject
    lateinit var logger: Logger

    @Named("activity")
    @Inject
    lateinit var activityCacheService: CacheService

    @Named("fragment")
    @Inject
    lateinit var fragmentCacheService: CacheService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_main, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val fragmentComponent = (requireActivity() as ActivityComponentProvider).activityComponent
            .createFragmentScopeComponent()
            .apply { inject(this@MainFragment) }
        logger.log("something")
        check(activityCacheService !== fragmentCacheService)
        renderDotView(view.findViewById(R.id.view_circle), fragmentComponent)
    }

    private fun renderDotView(view: CircleView, fragmentComponent: StitchFragmentScopeComponent) {
        view.inject(fragmentComponent)
    }
}
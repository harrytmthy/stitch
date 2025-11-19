package com.harrytmthy.stitch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.harrytmthy.stitch.annotations.Inject
import com.harrytmthy.stitch.generated.StitchFragmentScopeComponent

class CircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    @Inject
    lateinit var logger: Logger

    fun inject(fragmentComponent: StitchFragmentScopeComponent) {
        fragmentComponent.createViewWithFragmentScopeComponent().inject(this)
        logger.log("something")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val radius = (minOf(width, height) / 2f) * 0.8f
        val cx = width / 2f
        val cy = height / 2f

        paint.color = 0xFF008577.toInt()
        canvas.drawCircle(cx, cy, radius, paint)
    }
}
package com.beamng.remotecontrol

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.ProgressBar

class InverseProgressBar : ProgressBar {

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    @Synchronized
    override fun onDraw(canvas: Canvas) {
        canvas.scale(-1f, 1f, width * 0.5f, height * 0.5f)
        super.onDraw(canvas)
    }
}

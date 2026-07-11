package com.beamng.remotecontrol

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.widget.TextView

class CustomTextView : TextView {

    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context) : super(context) {
        init(null)
    }

    private fun init(attrs: AttributeSet?) {
        try {
            if (attrs != null) {
                val a = context.obtainStyledAttributes(attrs, R.styleable.CustomTextView)
                val fontName = a.getString(R.styleable.CustomTextView_fontName)
                if (fontName != null) {
                    typeface = Typeface.createFromAsset(context.assets, fontName)
                }
                a.recycle()
            }
        } catch (e: RuntimeException) {
            Log.e("CustomTextView", "Unable to create font", e)
        }
    }
}

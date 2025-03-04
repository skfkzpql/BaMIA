package com.example.bamia.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

class SquareFrameLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 너비로 높이를 결정하여 정사각형을 강제함
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}

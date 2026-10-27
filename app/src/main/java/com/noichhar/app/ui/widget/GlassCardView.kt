package com.noichhar.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.noichhar.app.R

/** A subtly bordered dark surface; its background never obscures its children. */
class GlassCardView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = 0) : FrameLayout(context, attrs, defStyleAttr) {
    init { background = ContextCompat.getDrawable(context, R.drawable.bg_glass_card) }
}

package com.noichhar.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.noichhar.app.R
import com.noichhar.app.util.HapticUtils

class SegmentedTimeSelector @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {
    enum class Window(val label: String, val hours: Int) { H1("1 hr", 1), H24("24 hr", 24), D7("7 day", 168) }
    private var selected = Window.H1
    private val labels = mutableMapOf<Window, TextView>()
    var onSelectionChanged: ((Window) -> Unit)? = null
    private val density = resources.displayMetrics.density
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER
        background = ContextCompat.getDrawable(context, R.drawable.bg_segmented_pill)
        val pad = (4 * density).toInt(); setPadding(pad, pad, pad, pad)
        Window.values().forEach { window ->
            val textView = TextView(context).apply {
                text = window.label; textSize = 14f; gravity = Gravity.CENTER
                typeface = ResourcesCompat.getFont(context, R.font.inter_semibold)
                isClickable = true; isFocusable = true
                setOnClickListener { HapticUtils.light(it); select(window) }
            }
            addView(textView, LayoutParams(0, (44 * density).toInt(), 1f)); labels[window] = textView
        }
        updateVisuals()
    }
    fun select(window: Window) {
        if (selected == window) return
        selected = window; updateVisuals(); onSelectionChanged?.invoke(window)
    }
    fun current() = selected
    private fun updateVisuals() = labels.forEach { (window, view) ->
        view.isSelected = window == selected
        view.background = if (view.isSelected) ContextCompat.getDrawable(context, R.drawable.bg_segmented_pill_active) else null
        view.setTextColor(ContextCompat.getColor(context, if (view.isSelected) R.color.text_white else R.color.text_secondary))
        view.contentDescription = "${window.label}${if (view.isSelected) ", selected" else ""}"
    }
}

package com.noichhar.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.noichhar.app.R
import com.noichhar.app.util.HapticUtils

class FloatingBottomNav @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {
    private data class Tab(val label: String, val icon: Int)
    private val tabs = listOf(Tab("Home", R.drawable.ic_nav_home), Tab("Stats", R.drawable.ic_nav_stats),
        Tab("RPN", R.drawable.ic_nav_rpn), Tab("Configure", R.drawable.ic_nav_configure), Tab("About", R.drawable.ic_nav_about))
    private val items = mutableListOf<LinearLayout>()
    private val density = resources.displayMetrics.density
    private var selected = 0
    var onTabSelected: ((Int) -> Unit)? = null
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER
        background = ContextCompat.getDrawable(context, R.drawable.bg_floating_nav)
        val pad = (6 * density).toInt(); setPadding(pad, pad, pad, pad)
        tabs.forEachIndexed { index, tab ->
            val cell = LinearLayout(context).apply {
                orientation = VERTICAL; gravity = Gravity.CENTER; isClickable = true; isFocusable = true
                val icon = ImageView(context).apply { setImageResource(tab.icon); importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
                addView(icon, LayoutParams((22 * density).toInt(), (22 * density).toInt()))
                val label = TextView(context).apply {
                    text = tab.label; textSize = 10f; gravity = Gravity.CENTER; maxLines = 1
                    typeface = ResourcesCompat.getFont(context, R.font.inter_medium)
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                }
                addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                setOnClickListener { if (selected != index) { HapticUtils.light(it); selectTab(index); onTabSelected?.invoke(index) } }
            }
            items += cell; addView(cell, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        selectTab(0)
    }
    fun selectTab(index: Int) {
        selected = index.coerceIn(tabs.indices)
        items.forEachIndexed { i, item ->
            val chosen = i == selected
            item.isSelected = chosen
            item.background = if (chosen) ContextCompat.getDrawable(context, R.drawable.bg_segmented_pill_active) else null
            val color = ContextCompat.getColor(context, if (chosen) R.color.text_white else R.color.text_secondary)
            (item.getChildAt(0) as ImageView).setColorFilter(color)
            (item.getChildAt(1) as TextView).setTextColor(color)
            item.contentDescription = "${tabs[i].label}${if (chosen) ", selected" else ""}"
        }
    }
}

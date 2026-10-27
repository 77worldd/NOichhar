package com.noichhar.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.noichhar.app.R
import com.noichhar.app.util.HapticUtils

class FloatingBottomNav @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr) {
    private val labels = listOf("Home", "Stats", "RPN", "Configure", "About")
    private val cells = mutableListOf<LinearLayout>()
    private val iconIds = listOf(R.id.nav_home_icon, R.id.nav_stats_icon, R.id.nav_rpn_icon,
        R.id.nav_configure_icon, R.id.nav_about_icon)
    private val labelIds = listOf(R.id.nav_home_label, R.id.nav_stats_label, R.id.nav_rpn_label,
        R.id.nav_configure_label, R.id.nav_about_label)
    private var selected = 0
    var onTabSelected: ((Int) -> Unit)? = null
    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER
        background = ContextCompat.getDrawable(context, R.drawable.bg_floating_nav)
        val pad = (6 * resources.displayMetrics.density).toInt(); setPadding(pad, pad, pad, pad)
        LayoutInflater.from(context).inflate(R.layout.widget_floating_nav, this, true)
        listOf(R.id.nav_home, R.id.nav_stats, R.id.nav_rpn, R.id.nav_configure, R.id.nav_about)
            .forEachIndexed { index, id ->
                val cell = findViewById<LinearLayout>(id)
                cell.setOnClickListener {
                    if (selected != index) { HapticUtils.light(it); selectTab(index); onTabSelected?.invoke(index) }
                }
                cells += cell
            }
        selectTab(0)
    }
    fun selectTab(index: Int) {
        selected = index.coerceIn(labels.indices)
        cells.forEachIndexed { i, cell ->
            val chosen = i == selected
            cell.isSelected = chosen
            cell.background = if (chosen) ContextCompat.getDrawable(context, R.drawable.bg_segmented_pill_active) else null
            val color = ContextCompat.getColor(context, if (chosen) R.color.nav_selected else R.color.nav_unselected)
            cell.findViewById<ImageView>(iconIds[i]).setColorFilter(color)
            cell.findViewById<TextView>(labelIds[i]).setTextColor(color)
            cell.contentDescription = "${labels[i]}${if (chosen) ", selected" else ""}"
        }
    }
}

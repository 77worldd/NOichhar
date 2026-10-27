package com.noichhar.app.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.noichhar.app.R

/** 120 actual twelve-minute buckets, ordered from 24 hours ago to now. */
class ActivityHeatmapView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    private var data = IntArray(120)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 10 * resources.displayMetrics.scaledDensity
        color = ContextCompat.getColor(context, R.color.text_secondary)
    }
    fun setData(values: IntArray) {
        data = values.copyOf(120)
        contentDescription = "Filtered requests in the last 24 hours: ${data.sum()}"
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val gap = 2f * density; val labelHeight = 20f * density
        val cellW = ((width - gap * 23) / 24f).coerceAtLeast(0f)
        val cellH = ((height - labelHeight - gap * 4) / 5f).coerceAtLeast(0f)
        val max = (data.maxOrNull() ?: 0).coerceAtLeast(1)
        val low = ContextCompat.getColor(context, R.color.obsidian_600)
        val high = ContextCompat.getColor(context, R.color.text_primary)
        for (col in 0 until 24) for (row in 0 until 5) {
            val amount = data[col * 5 + row].toFloat() / max
            paint.color = Color.rgb(
                (Color.red(low) + (Color.red(high) - Color.red(low)) * amount).toInt(),
                (Color.green(low) + (Color.green(high) - Color.green(low)) * amount).toInt(),
                (Color.blue(low) + (Color.blue(high) - Color.blue(low)) * amount).toInt())
            val x = col * (cellW + gap); val y = row * (cellH + gap)
            canvas.drawRoundRect(x, y, x + cellW, y + cellH, 2f * density, 2f * density, paint)
        }
        text.textAlign = Paint.Align.LEFT; canvas.drawText("24h ago", 0f, height - 2f, text)
        text.textAlign = Paint.Align.CENTER; canvas.drawText("12h", width / 2f, height - 2f, text)
        text.textAlign = Paint.Align.RIGHT; canvas.drawText("now", width.toFloat(), height - 2f, text)
    }
}

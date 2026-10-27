package com.noichhar.app.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.noichhar.app.R

data class ChartSegment(val value: Float, val color: Int, val label: String)

class RadialChartView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private var segments: List<ChartSegment> = emptyList()
    private var title = "0"; private var subtitle = "REQUESTS"; private var progress = 1f
    private var animator: ValueAnimator? = null

    fun setData(values: List<ChartSegment>, centerText: String, subText: String = "REQUESTS") {
        val cleaned = values.filter { it.value > 0f }
        if (segments == cleaned && title == centerText && subtitle == subText) return
        segments = cleaned; title = centerText; subtitle = subText
        contentDescription = "$centerText $subText"
        animator?.cancel()
        if (!isAttachedToWindow || cleaned.isEmpty()) { progress = 1f; invalidate(); return }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 550
            addUpdateListener { progress = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat(); val cx = width / 2f; val cy = height / 2f
        val stroke = size * .13f; paint.strokeWidth = stroke
        val radius = (size - stroke) / 2f - 2f
        rect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        paint.color = ContextCompat.getColor(context, R.color.glass_border)
        canvas.drawArc(rect, 0f, 360f, false, paint)
        val total = segments.sumOf { it.value.toDouble() }.toFloat()
        var angle = -90f
        if (total > 0) segments.forEach {
            val portion = it.value / total * 360f * progress
            paint.color = it.color
            canvas.drawArc(rect, angle, (portion - if (segments.size > 1) 3f else 0f).coerceAtLeast(0f), false, paint)
            angle += portion
        }
        titlePaint.color = ContextCompat.getColor(context, R.color.text_primary); titlePaint.textSize = size * .19f
        labelPaint.color = ContextCompat.getColor(context, R.color.text_tertiary); labelPaint.textSize = size * .065f
        canvas.drawText(title, cx, cy + size * .02f, titlePaint)
        canvas.drawText(subtitle, cx, cy + size * .15f, labelPaint)
    }
    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; super.onDetachedFromWindow() }
}

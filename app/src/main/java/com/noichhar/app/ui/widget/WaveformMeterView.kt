package com.noichhar.app.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.noichhar.app.R
import kotlin.math.ln
import kotlin.math.sin

/** Decorative wave amplitude is driven only by measured device-wide byte rates. */
class WaveformMeterView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    private val upPaint = paint(R.color.accent_mint)
    private val downPaint = paint(R.color.accent_cyan)
    private var up = 0f; private var down = 0f; private var phase = 0f
    private var active = false; private var animator: ValueAnimator? = null
    private fun paint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND; this.color = ContextCompat.getColor(context, color)
    }
    fun setRates(uploadBps: Long?, downloadBps: Long?) {
        fun amplitude(rate: Long?): Float = if (rate == null || rate <= 0) 0f
            else (ln(1.0 + rate) / ln(1.0 + 10 * 1024 * 1024)).toFloat().coerceIn(0f, 1f)
        up = amplitude(uploadBps); down = amplitude(downloadBps)
        val next = up > 0f || down > 0f
        if (active != next) { active = next; updateAnimation() }
        invalidate()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; super.onDetachedFromWindow() }
    private fun updateAnimation() {
        animator?.cancel(); animator = null
        if (!active || !isAttachedToWindow) return
        animator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            duration = 2400; repeatCount = ValueAnimator.INFINITE
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }; start()
        }
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0) return
        drawWave(canvas, down, downPaint, 0f); drawWave(canvas, up, upPaint, Math.PI.toFloat())
    }
    private fun drawWave(canvas: Canvas, amplitude: Float, paint: Paint, offset: Float) {
        val path = Path(); val mid = height / 2f
        path.moveTo(0f, mid)
        var x = 0f
        while (x <= width) {
            val y = mid + height * .38f * amplitude * sin((x / width * Math.PI * 4 + phase + offset)).toFloat()
            path.lineTo(x, y); x += 4f
        }
        canvas.drawPath(path, paint)
    }
}

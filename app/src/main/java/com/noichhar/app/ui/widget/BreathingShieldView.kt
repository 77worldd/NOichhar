package com.noichhar.app.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.noichhar.app.R

/** Raven emblem with lifecycle-aware protection pulses. */
class BreathingShieldView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val logo = ContextCompat.getDrawable(context, R.drawable.ic_brand_mark)?.mutate()
    private var active = false
    private var phase = 0f
    private var animator: ValueAnimator? = null

    fun setActive(value: Boolean) {
        if (active == value) return
        active = value
        updateAnimation()
        invalidate()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; super.onDetachedFromWindow() }

    private fun updateAnimation() {
        animator?.cancel(); animator = null
        if (!active || !isAttachedToWindow) { phase = 0f; return }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2800
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val r = minOf(width, height) * 0.25f
        val color = ContextCompat.getColor(context, if (active) R.color.accent_mint else R.color.text_dim)
        if (active) {
            glow.shader = RadialGradient(cx, cy, r * 1.8f, 0x3310B981, 0x0010B981, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r * 1.8f, glow)
            for (i in 0..1) {
                val p = (phase + i * 0.5f) % 1f
                stroke.color = color; stroke.alpha = ((1f - p) * 150).toInt()
                stroke.strokeWidth = resources.displayMetrics.density * if (i == 0) 2f else 1f
                canvas.drawCircle(cx, cy, r * (1.05f + p * 0.9f), stroke)
            }
        } else {
            stroke.color = color; stroke.alpha = 110; stroke.strokeWidth = resources.displayMetrics.density
            canvas.drawCircle(cx, cy, r * 1.35f, stroke)
        }
        val size = (r * 1.6f).toInt()
        logo?.setBounds((cx - size / 2).toInt(), (cy - size / 2).toInt(),
            (cx + size / 2).toInt(), (cy + size / 2).toInt())
        logo?.draw(canvas)
    }
}

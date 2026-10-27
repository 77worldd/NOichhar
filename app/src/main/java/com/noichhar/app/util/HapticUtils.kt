package com.noichhar.app.util

import android.view.HapticFeedbackConstants
import android.view.View

object HapticUtils {
    fun light(view: View) { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    fun strong(view: View) { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
}

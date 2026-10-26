package com.noichhar.app.util

import android.util.Log

object Logger {
    private const val TAG = "NOichhar"
    private var enabled = true

    fun d(message: String) {
        if (enabled) Log.d(TAG, message)
    }

    fun i(message: String) {
        if (enabled) Log.i(TAG, message)
    }

    fun w(message: String) {
        if (enabled) Log.w(TAG, message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (enabled) Log.e(TAG, message, throwable)
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }
}

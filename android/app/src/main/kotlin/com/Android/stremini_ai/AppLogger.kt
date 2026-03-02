package com.Android.stremini_ai

import android.util.Log

object AppLogger {
    fun d(tag: String, message: String) = Log.d(tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) = Log.e(tag, message, throwable)
}

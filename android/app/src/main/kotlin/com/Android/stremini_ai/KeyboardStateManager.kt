package com.Android.stremini_ai

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

class KeyboardStateManager(private val context: Context) {
    fun isKeyboardEnabled(): Boolean {
        val imeManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return false
        return imeManager.enabledInputMethodList.any { it.packageName == context.packageName }
    }

    fun isKeyboardSelected(): Boolean {
        val currentInputMethod = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        return currentInputMethod?.contains(context.packageName) == true
    }

    fun openKeyboardSettings() {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun showInputMethodPicker() {
        val imeManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imeManager?.showInputMethodPicker()
    }
}

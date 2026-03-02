package com.Android.stremini_ai

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class KeyboardSettingsActivity : AppCompatActivity() {

    private lateinit var toggleKeyboard: Switch
    private lateinit var btnEnableKeyboard: Button
    private lateinit var btnSelectKeyboard: Button
    private lateinit var themeGroup: RadioGroup
    private lateinit var layoutInfo: TextView
    private val keyboardStateManager by lazy { KeyboardStateManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keyboard_settings)

        initViews()
        setupListeners()
        updateStatus()
    }

    private fun initViews() {
        toggleKeyboard = findViewById(R.id.toggle_keyboard)
        btnEnableKeyboard = findViewById(R.id.btn_enable_keyboard)
        btnSelectKeyboard = findViewById(R.id.btn_select_keyboard)
        themeGroup = findViewById(R.id.theme_group)
        layoutInfo = findViewById(R.id.layout_info)

        // Back button
        findViewById<ImageButton>(R.id.btn_back)?.setOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        toggleKeyboard.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isKeyboardEnabled()) {
                Toast.makeText(
                    this,
                    "Please enable Stremini AI Keyboard in settings first",
                    Toast.LENGTH_SHORT
                ).show()
                toggleKeyboard.isChecked = false
                openKeyboardSettings()
            }
        }

        btnEnableKeyboard.setOnClickListener {
            openKeyboardSettings()
        }

        btnSelectKeyboard.setOnClickListener {
            showInputMethodPicker()
        }

        // Theme selection
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.theme_dark -> applyTheme("dark")
                R.id.theme_blue -> applyTheme("blue")
                R.id.theme_purple -> applyTheme("purple")
            }
        }
    }

    private fun updateStatus() {
        val enabled = isKeyboardEnabled()
        val selected = isKeyboardSelected()

        toggleKeyboard.isChecked = enabled && selected
        
        layoutInfo.text = buildString {
            append("Status:\n")
            append("• Enabled: ${if (enabled) "✓ Yes" else "✗ No"}\n")
            append("• Selected: ${if (selected) "✓ Yes" else "✗ No"}\n")
            append("\nFeatures:\n")
            append("• Smart Text Completion\n")
            append("• Grammar Correction\n")
            append("• Multi-language Translation\n")
            append("• Tone Adjustment\n")
            append("• Text Expansion\n")
            append("• Emoji Suggestions\n")
        }
    }

    private fun isKeyboardEnabled(): Boolean = keyboardStateManager.isKeyboardEnabled()

    private fun isKeyboardSelected(): Boolean = keyboardStateManager.isKeyboardSelected()

    private fun openKeyboardSettings() {
        keyboardStateManager.openKeyboardSettings()
        Toast.makeText(
            this,
            "Find 'Stremini AI Keyboard' and enable it",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun showInputMethodPicker() {
        keyboardStateManager.showInputMethodPicker()
    }

    private fun applyTheme(theme: String) {
        // Save theme preference
        getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
            .edit()
            .putString("theme", theme)
            .apply()
        
        Toast.makeText(
            this,
            "Theme changed to $theme",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}

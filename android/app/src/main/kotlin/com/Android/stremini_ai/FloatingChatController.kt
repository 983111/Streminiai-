package com.Android.stremini_ai

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FloatingChatController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val scope: CoroutineScope,
    private val backendClient: AIBackendClient,
    private val commandRouter: DeviceCommandRouter
) {
    private var floatingChatView: View? = null
    private var floatingChatParams: WindowManager.LayoutParams? = null
    var isVisible: Boolean = false
        private set

    fun show(onClose: () -> Unit) {
        if (isVisible) return
        floatingChatView = LayoutInflater.from(context).inflate(R.layout.floating_chatbot_layout, null)
        val typeParam = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        floatingChatParams = WindowManager.LayoutParams(
            dpToPx(300f), dpToPx(400f), typeParam,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        setupListeners(onClose)
        windowManager.addView(floatingChatView, floatingChatParams)
        isVisible = true
        addMessage("Hi! I'm Stremini AI. Ask anything ✨", isUser = false)
    }

    fun hide() {
        if (!isVisible) return
        floatingChatView?.let { windowManager.removeView(it) }
        floatingChatView = null
        floatingChatParams = null
        isVisible = false
    }

    fun addMessage(message: String, isUser: Boolean) {
        val view = floatingChatView ?: return
        val messagesContainer = view.findViewById<LinearLayout>(R.id.messages_container)
        val messageView = LayoutInflater.from(context).inflate(
            if (isUser) R.layout.message_bubble_user else R.layout.message_bubble_bot,
            messagesContainer,
            false
        )
        messageView.findViewById<TextView>(R.id.tv_message)?.text = message
        messagesContainer.addView(messageView)
        view.findViewById<ScrollView>(R.id.scroll_messages)?.post {
            view.findViewById<ScrollView>(R.id.scroll_messages)?.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun setupListeners(onClose: () -> Unit) {
        val view = floatingChatView ?: return
        val btnClose = view.findViewById<ImageView>(R.id.btn_close_chatbot)
        val btnSend = view.findViewById<ImageView>(R.id.btn_send_message)
        val inputMessage = view.findViewById<EditText>(R.id.et_input_message)

        btnClose.setOnClickListener { hide(); onClose() }
        btnSend.setOnClickListener {
            val userMessage = inputMessage.text.toString().trim()
            if (userMessage.isEmpty()) return@setOnClickListener
            addMessage(userMessage, isUser = true)
            inputMessage.text.clear()
            processUserCommand(userMessage)
        }
    }

    private fun processUserCommand(userMessage: String) {
        scope.launch {
            val direct = if (commandRouter.isLikelyDeviceCommand(userMessage)) {
                withContext(Dispatchers.IO) { commandRouter.tryDirectDeviceCommand(userMessage) }
            } else null

            if (direct != null && direct.executed) {
                addMessage("✅ Done! Command executed successfully.", isUser = false)
                return@launch
            }

            val result = if (direct != null) backendClient.sendDeviceCommandWithContext(userMessage)
                         else backendClient.sendChatMessage(userMessage)

            result.fold(
                onSuccess = { addMessage(it, isUser = false) },
                onFailure = { addMessage("⚠️ Network error: ${it.message}", isUser = false) }
            )
        }
    }

    private fun dpToPx(dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()
}

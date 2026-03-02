package com.Android.stremini_ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceController(
    private val context: Context,
    private val windowManager: WindowManager,
    private val scope: CoroutineScope,
    private val commandRouter: DeviceCommandRouter,
    private val backendClient: AIBackendClient
) {
    private var autoTaskerView: View? = null
    private var autoTaskerParams: WindowManager.LayoutParams? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var keepListeningLoop = false

    var isVisible: Boolean = false
        private set

    fun show(onClose: () -> Unit): Boolean {
        if (isVisible) return true
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Microphone permission required. Opening settings...", Toast.LENGTH_LONG).show()
            runCatching {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            return false
        }

        autoTaskerView = LayoutInflater.from(context).inflate(R.layout.auto_tasker_overlay, null)
        val typeParam = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        autoTaskerParams = WindowManager.LayoutParams(
            dpToPx(320f), dpToPx(480f), typeParam,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        autoTaskerView?.findViewById<ImageView>(R.id.btn_close_tasker)?.setOnClickListener {
            hide(); onClose()
        }
        autoTaskerView?.findViewById<ImageView>(R.id.btn_start_listening)?.setOnClickListener { startVoiceCapture() }

        windowManager.addView(autoTaskerView, autoTaskerParams)
        isVisible = true
        keepListeningLoop = true
        startVoiceCapture()
        return true
    }

    fun hide() {
        keepListeningLoop = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        autoTaskerView?.let { windowManager.removeView(it) }
        autoTaskerView = null
        autoTaskerParams = null
        isVisible = false
    }

    private fun startVoiceCapture() {
        val view = autoTaskerView ?: return
        val status = view.findViewById<TextView>(R.id.tv_tasker_status)
        status.text = "Listening..."
        speechRecognizer?.destroy()

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            status.text = "Speech recognition not available on this device"
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { status.text = "Speak now..." }
                override fun onBeginningOfSpeech() { status.text = "Listening..." }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { status.text = "Processing your command..." }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
                override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
                override fun onError(error: Int) {
                    status.text = "Voice capture failed ($error). Retrying..."
                    if (keepListeningLoop && isVisible) scope.launch { delay(700); startVoiceCapture() }
                }
                override fun onResults(results: android.os.Bundle?) {
                    val command = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    if (command.isNullOrBlank()) {
                        status.text = "Could not understand. Try again."
                        if (keepListeningLoop && isVisible) scope.launch { delay(500); startVoiceCapture() }
                    } else {
                        status.text = "Understood: $command"
                        view.findViewById<TextView>(R.id.tv_tasker_output).text = "🎙 Command: $command\n\n⚙️ Executing..."
                        executeVoiceCommand(command)
                    }
                }
            })
        }

        speechRecognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    private fun executeVoiceCommand(command: String) {
        scope.launch {
            val view = autoTaskerView ?: return@launch
            val statusView = view.findViewById<TextView>(R.id.tv_tasker_status)
            val outputView = view.findViewById<TextView>(R.id.tv_tasker_output)

            val direct = withContext(Dispatchers.IO) { commandRouter.tryDirectDeviceCommand(command) }
            if (direct.executed) {
                statusView.text = "✅ ${direct.statusMessage}"
                outputView.text = "🎙 Command: $command\n\n✅ ${direct.details}"
            } else {
                statusView.text = "🤖 Sending to AI..."
                outputView.text = "🎙 Command: $command\n\n🤖 Asking AI for execution plan..."
                val (aiStatus, aiOutput) = withContext(Dispatchers.IO) { backendClient.sendVoiceTaskCommandToAI(command) }
                statusView.text = aiStatus
                outputView.text = "🎙 Command: $command\n\n$aiOutput"
            }

            if (keepListeningLoop && isVisible) {
                delay(650)
                startVoiceCapture()
            }
        }
    }

    private fun dpToPx(dp: Float): Int = (dp * context.resources.displayMetrics.density).toInt()
}

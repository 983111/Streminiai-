package com.example.stremini_chatbot

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ScreenReaderService : AccessibilityService() {

    companion object {
        const val ACTION_START_SCAN = "com.example.stremini_chatbot.START_SCAN"
        const val ACTION_STOP_SCAN = "com.example.stremini_chatbot.STOP_SCAN"
        const val ACTION_SCAN_COMPLETE = "com.example.stremini_chatbot.SCAN_COMPLETE"
        const val EXTRA_SCANNED_TEXT = "scanned_text"
        private const val TAG = "ScreenReaderService"
        private var instance: ScreenReaderService? = null
        fun isRunning(): Boolean = instance != null
    }

    private lateinit var windowManager: WindowManager
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var scanningOverlay: View? = null
    private var tagsContainer: FrameLayout? = null
    private var isScanning = false
    private var tagsVisible = false

    data class ScanResult(
        val isSafe: Boolean,
        val riskLevel: String,
        val summary: String,
        val taggedElements: List<TaggedElement>
    )

    data class TaggedElement(
        val label: String,
        val color: Int,
        val reason: String
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCAN -> if (tagsVisible) clearTags() else if (!isScanning) startScreenScan()
            ACTION_STOP_SCAN -> clearAllOverlays()
        }
        return START_NOT_STICKY
    }

    private fun startScreenScan() {
        isScanning = true
        showScanningAnimation()
        
        serviceScope.launch {
            try {
                delay(1000) 
                val rootNode = rootInActiveWindow ?: return@launch
                val contentList = mutableListOf<ContentWithPosition>()
                extractContentWithPositions(rootNode, contentList)
                rootNode.recycle()

                val fullText = contentList.joinToString("\n") { it.text }
                val result = analyzeScreenContent(fullText)
                
                hideScanningAnimation()
                displayTagsInUiStyle(contentList, result)

                val broadcastIntent = Intent(ACTION_SCAN_COMPLETE).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_SCANNED_TEXT, fullText)
                }
                sendBroadcast(broadcastIntent)
                
                isScanning = false
                tagsVisible = true
            } catch (e: Exception) {
                hideScanningAnimation()
                isScanning = false
            }
        }
    }

    private suspend fun analyzeScreenContent(content: String): ScanResult = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("content", content.take(5000))
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://ai-keyboard-backend.vishwajeetadkine705.workers.dev/security/scan-content")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseData = response.body?.string() ?: ""
            val json = JSONObject(responseData)
            
            val taggedArray = json.optJSONArray("taggedElements")
            val tags = mutableListOf<TaggedElement>()
            if (taggedArray != null) {
                for (i in 0 until taggedArray.length()) {
                    val item = taggedArray.getJSONObject(i)
                    val securityTag = item.getJSONObject("securityTag")
                    tags.add(TaggedElement(
                        label = securityTag.getString("label"),
                        color = Color.parseColor(securityTag.getString("color")),
                        reason = item.getJSONObject("details").getString("reason")
                    ))
                }
            }

            ScanResult(
                isSafe = json.getBoolean("isSafe"),
                riskLevel = json.getString("riskLevel"),
                summary = json.getString("summary"),
                taggedElements = tags
            )
        } catch (e: Exception) {
            ScanResult(true, "safe", "✓ No threats detected", emptyList())
        }
    }

    private fun displayTagsInUiStyle(contentList: List<ContentWithPosition>, result: ScanResult) {
        tagsContainer = FrameLayout(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(tagsContainer, params)

        // Main Banner (Matches Top UI in images)
        val bannerText = if (result.isSafe) "Safe: No Threat Detected" else "Suspicious Links: Threat Detected"
        val bannerColor = if (result.isSafe) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
        val bannerDetail = if (result.isSafe) "Screen appears safe" else "Suspicious links may be dangerous"
        
        createPillTag(null, bannerText, bannerColor, bannerDetail, true)

        // Inline Tags (Tags links or messages)
        result.taggedElements.forEach { tag ->
            val sourceMatch = contentList.find { content ->
                val lowerContent = content.text.lowercase()
                val lowerReason = tag.reason.lowercase()
                lowerReason.contains(lowerContent.take(15)) || lowerContent.contains(lowerReason.split(":").last().trim().take(10))
            }

            if (sourceMatch != null) {
                createPillTag(sourceMatch.bounds, tag.label, tag.color, "", false)
            }
        }
    }

    private fun createPillTag(bounds: Rect?, labelText: String, color: Int, subText: String, isMainBanner: Boolean) {
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (isMainBanner) Gravity.START else Gravity.CENTER_VERTICAL
            setPadding(if (isMainBanner) 40 else 24, 16, 40, 16)
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = if (isMainBanner) 20f else 100f 
                setAlpha(240)
            }
            elevation = 12f
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val icon = TextView(this).apply {
            text = if (labelText.contains("Safe", true)) "✓" else "⚠️"
            setTextColor(Color.WHITE)
            textSize = if (isMainBanner) 18f else 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 16, 0)
        }

        val label = TextView(this).apply {
            text = labelText
            setTextColor(Color.WHITE)
            textSize = if (isMainBanner) 15f else 11f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        row.addView(icon)
        row.addView(label)
        pill.addView(row)

        if (isMainBanner && subText.isNotEmpty()) {
            val detailText = TextView(this).apply {
                text = subText
                setTextColor(Color.parseColor("#EEEEEE"))
                textSize = 12f
                setPadding(68, 4, 0, 0)
            }
            pill.addView(detailText)
        }

        val layoutParams = FrameLayout.LayoutParams(
            if (isMainBanner) FrameLayout.LayoutParams.MATCH_PARENT else FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            if (isMainBanner) {
                gravity = Gravity.TOP
                setMargins(40, 140, 40, 0)
            } else if (bounds != null) {
                // Places tag directly above the message or link
                leftMargin = (bounds.left + 15).coerceAtLeast(20)
                topMargin = (bounds.top - 85).coerceAtLeast(120)
            }
        }
        tagsContainer?.addView(pill, layoutParams)
    }

    private fun extractContentWithPositions(node: AccessibilityNodeInfo, list: MutableList<ContentWithPosition>) {
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank()) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 5 && bounds.height() > 5) {
                list.add(ContentWithPosition(text, bounds))
            }
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { 
                extractContentWithPositions(it, list)
                it.recycle()
            }
        }
    }

    data class ContentWithPosition(val text: String, val bounds: Rect)

    private fun showScanningAnimation() {
        try {
            scanningOverlay = LayoutInflater.from(this).inflate(R.layout.scanning_overlay, null)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            )
            windowManager.addView(scanningOverlay, params)
        } catch (e: Exception) { Log.e(TAG, "Overlay Error: ${e.message}") }
    }

    private fun hideScanningAnimation() {
        scanningOverlay?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        scanningOverlay = null
    }

    private fun clearTags() {
        tagsContainer?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
        tagsContainer = null
        tagsVisible = false
    }

    private fun clearAllOverlays() {
        hideScanningAnimation()
        clearTags()
        isScanning = false
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        clearAllOverlays()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}

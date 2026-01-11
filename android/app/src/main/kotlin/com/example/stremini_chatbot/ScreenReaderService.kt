package com.example.stremini_chatbot

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
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
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var scanningOverlay: View? = null
    private var tagsContainer: FrameLayout? = null
    private var isScanning = false
    private var tagsVisible = false

    data class ContentWithPosition(
        val text: String,
        val bounds: Rect,
        val nodeInfo: String
    )

    data class TagInfo(
        val text: String,
        val color: Int,
        val fullText: String,
        val reason: String,
        val threat: String
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        Log.d(TAG, "✅ Screen Reader Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Monitor window changes
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        clearAllOverlays()
        Log.d(TAG, "Service destroyed")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_SCAN -> {
                if (tagsVisible) {
                    clearTags()
                } else if (!isScanning) {
                    startScreenScan()
                }
            }
            ACTION_STOP_SCAN -> clearAllOverlays()
        }
        return START_NOT_STICKY
    }

    private fun startScreenScan() {
        Log.d(TAG, "🔍 Starting screen scan")
        isScanning = true
        showScanningAnimation()
        
        serviceScope.launch {
            try {
                delay(1500)
                
                val rootNode = rootInActiveWindow
                if (rootNode == null) {
                    Log.e(TAG, "❌ Cannot access screen")
                    showError("Cannot access screen. Ensure accessibility permission is granted.")
                    return@launch
                }
                
                val contentList = mutableListOf<ContentWithPosition>()
                extractContentWithPositions(rootNode, contentList)
                rootNode.recycle()
                
                if (contentList.isEmpty()) {
                    showInfo("No content found to analyze")
                    return@launch
                }
                
                val fullText = contentList.joinToString("\n") { it.text }
                val result = analyzeScreenContent(fullText)
                
                hideScanningAnimation()
                displayTagsNearContent(contentList, result)
                
                isScanning = false
                tagsVisible = true
                
                sendBroadcast(Intent(ACTION_SCAN_COMPLETE).apply {
                    putExtra(EXTRA_SCANNED_TEXT, "Scan complete")
                })
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Scan failed", e)
                hideScanningAnimation()
                showError("Scan failed: ${e.message}")
                isScanning = false
            }
        }
    }

    private fun extractContentWithPositions(
        node: AccessibilityNodeInfo,
        contentList: MutableList<ContentWithPosition>
    ) {
        try {
            val text = node.text?.toString() ?: node.contentDescription?.toString()
            
            if (!text.isNullOrBlank() && text.length > 3) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                
                if (bounds.width() > 0 && bounds.height() > 0 && bounds.top >= 0) {
                    val nodeInfo = buildString {
                        append(node.className?.toString()?.substringAfterLast('.') ?: "Unknown")
                        if (node.isClickable) append(" [Clickable]")
                    }
                    contentList.add(ContentWithPosition(text, bounds, nodeInfo))
                }
            }
            
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { 
                    extractContentWithPositions(it, contentList)
                    it.recycle()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting: ${e.message}")
        }
    }

    data class ScanResult(
        val isSafe: Boolean,
        val riskLevel: String,
        val tags: List<String>,
        val analysis: String,
        val flaggedItems: List<FlaggedItem>
    )

    data class FlaggedItem(
        val type: String,
        val content: String,
        val threat: String,
        val severity: String,
        val reason: String
    )

    private suspend fun analyzeScreenContent(content: String): ScanResult = withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put("content", content.take(5000))
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://ai-keyboard-backend.vishwajeetadkine705.workers.dev/security/scan-content")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: throw IOException("Empty response")

            if (!response.isSuccessful) {
                return@withContext ScanResult(true, "safe", emptyList(), "Unable to connect", emptyList())
            }

            val json = JSONObject(responseBody)
            val isSafe = json.optBoolean("isSafe", true)
            val riskLevel = json.optString("riskLevel", "safe")
            val analysis = json.optString("analysis", "Content analyzed")
            
            val tagsArray = json.optJSONArray("tags") ?: JSONArray()
            val tags = (0 until tagsArray.length()).map { tagsArray.getString(it) }

            val flaggedItemsArray = json.optJSONArray("flaggedItems") ?: JSONArray()
            val flaggedItems = (0 until flaggedItemsArray.length()).map { i ->
                val item = flaggedItemsArray.getJSONObject(i)
                FlaggedItem(
                    type = item.optString("type", "message"),
                    content = item.optString("content", ""),
                    threat = item.optString("threat", "Unknown"),
                    severity = item.optString("severity", "medium"),
                    reason = item.optString("reason", "Suspicious pattern")
                )
            }

            ScanResult(isSafe, riskLevel, tags, analysis, flaggedItems)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Analysis error", e)
            ScanResult(true, "safe", listOf("Error"), "Analysis failed", emptyList())
        }
    }

    private fun displayTagsNearContent(
        contentList: List<ContentWithPosition>,
        result: ScanResult
    ) {
        tagsContainer = FrameLayout(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(tagsContainer, params)
        
        if (result.riskLevel == "safe") {
            showSafeIndicator()
            return
        }
        
        val taggedBounds = mutableSetOf<Rect>()
        var tagCount = 0
        val maxTags = 10
        
        result.flaggedItems.forEach { item ->
            if (tagCount >= maxTags) return@forEach
            
            val matchingContent = contentList.find { 
                it.text.contains(item.content, ignoreCase = true) 
            } ?: return@forEach
            
            if (taggedBounds.any { Math.abs(it.top - matchingContent.bounds.top) < 100 }) {
                return@forEach
            }
            
            createModernTag(
                matchingContent.bounds,
                item.threat,
                when (item.severity) {
                    "high" -> android.graphics.Color.parseColor("#FF1744")
                    "medium" -> android.graphics.Color.parseColor("#FF9800")
                    else -> android.graphics.Color.parseColor("#2196F3")
                },
                matchingContent.text,
                item.reason,
                item.threat
            )
            taggedBounds.add(matchingContent.bounds)
            tagCount++
        }
        
        if (tagCount > 0) {
            showStatusBanner(result, tagCount)
            autoHideTags(result.riskLevel)
        }
    }

    private fun createModernTag(
        bounds: Rect,
        tagText: String,
        color: Int,
        fullText: String,
        reason: String,
        threat: String
    ) {
        // Container with shadow
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createModernTagBackground(color)
            elevation = 12f
            setPadding(16, 12, 16, 12)
        }
        
        // Main tag row
        val mainRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        // Warning icon
        val icon = TextView(this).apply {
            text = when (threat) {
                "Scam", "Phishing" -> "⚠️"
                "Suspicious Link" -> "🔗"
                else -> "❗"
            }
            textSize = 16f
            setPadding(0, 0, 8, 0)
        }
        
        // Tag text
        val label = TextView(this).apply {
            text = when (threat) {
                "Scam" -> "SCAM DETECTED"
                "Phishing" -> "PHISHING ATTEMPT"
                "Suspicious Link" -> "SUSPICIOUS LINK"
                else -> "THREAT DETECTED"
            }
            textSize = 13f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        mainRow.addView(icon)
        mainRow.addView(label)
        
        // "Learn more" button
        val learnMore = TextView(this).apply {
            text = "Learn more"
            textSize = 11f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(12, 8, 12, 8)
            alpha = 0.9f
            background = createLearnMoreBackground(color)
        }
        
        container.addView(mainRow)
        container.addView(learnMore)
        
        // Position tag
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val tagWidth = 280
        
        val layoutParams = FrameLayout.LayoutParams(
            tagWidth,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = when {
                bounds.left + tagWidth < screenWidth - 40 -> bounds.left + 10
                else -> (screenWidth - tagWidth) / 2
            }.coerceIn(20, screenWidth - tagWidth - 20)
            
            topMargin = when {
                bounds.top > 100 -> bounds.top - 80
                else -> bounds.bottom + 10
            }.coerceIn(80, screenHeight - 200)
        }

        // Click to show details
        container.setOnClickListener {
            serviceScope.launch(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    this@ScreenReaderService,
                    "⚠️ $threat\n\n$reason\n\nContent: \"${fullText.take(80)}...\"",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }

        tagsContainer?.addView(container, layoutParams)
    }
    
    private fun createModernTagBackground(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = 12f
            setStroke(2, android.graphics.Color.WHITE)
        }
    }
    
    private fun createLearnMoreBackground(baseColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.WHITE)
            cornerRadius = 8f
            alpha = 40
        }
    }

    private fun showSafeIndicator() {
        val safeView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(32, 16, 32, 16)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(android.graphics.Color.parseColor("#4CAF50"))
                cornerRadius = 16f
            }
            elevation = 12f
        }
        
        val iconText = TextView(this).apply {
            text = "✓"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            setPadding(0, 0, 16, 0)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        val messageText = TextView(this).apply {
            text = "No Threats Detected"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        safeView.addView(iconText)
        safeView.addView(messageText)
        
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            topMargin = 120
        }
        
        tagsContainer?.addView(safeView, layoutParams)
        
        serviceScope.launch {
            delay(3000)
            if (tagsVisible) clearTags()
        }
    }

    private fun showStatusBanner(result: ScanResult, tagCount: Int) {
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 12, 24, 12)
            val bgColor = when (result.riskLevel) {
                "danger" -> android.graphics.Color.parseColor("#D32F2F")
                "warning" -> android.graphics.Color.parseColor("#F57C00")
                else -> android.graphics.Color.parseColor("#388E3C")
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(bgColor)
                cornerRadius = 12f
            }
            elevation = 16f
        }
        
        val statusText = TextView(this).apply {
            text = "🛡️ $tagCount THREATS FOUND"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        
        banner.addView(statusText)
        
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            bottomMargin = 100
        }
        
        tagsContainer?.addView(banner, layoutParams)
    }

    private fun autoHideTags(riskLevel: String) {
        serviceScope.launch {
            val hideDelay = when (riskLevel) {
                "danger" -> 90000L
                "warning" -> 45000L
                else -> 10000L
            }
            delay(hideDelay)
            if (tagsVisible) clearTags()
        }
    }

    private fun showScanningAnimation() {
        if (scanningOverlay != null) return

        scanningOverlay = LayoutInflater.from(this).inflate(R.layout.scanning_overlay, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(scanningOverlay, params)
    }

    private fun hideScanningAnimation() {
        scanningOverlay?.let { view ->
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay", e)
            }
        }
        scanningOverlay = null
    }

    private fun showInfo(message: String) {
        hideScanningAnimation()
        serviceScope.launch(Dispatchers.Main) {
            android.widget.Toast.makeText(this@ScreenReaderService, message, android.widget.Toast.LENGTH_LONG).show()
        }
        isScanning = false
    }

    private fun showError(message: String) {
        sendBroadcast(Intent(ACTION_SCAN_COMPLETE).apply { putExtra("error", message) })
        hideScanningAnimation()
        isScanning = false
        
        serviceScope.launch(Dispatchers.Main) {
            android.widget.Toast.makeText(this@ScreenReaderService, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun clearTags() {
        tagsContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing tags", e)
            }
        }
        tagsContainer = null
        tagsVisible = false
    }

    private fun clearAllOverlays() {
        hideScanningAnimation()
        clearTags()
        isScanning = false
        tagsVisible = false
    }
}

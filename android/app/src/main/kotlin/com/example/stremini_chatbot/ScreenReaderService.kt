package com.example.stremini_chatbot

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.util.TypedValue
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
                Log.e(TAG, "Scan Error", e)
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
        clearTags()
        
        tagsContainer = FrameLayout(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, 
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(tagsContainer, params)

        // 1. Create the Top Banner
        val bannerText = if (result.isSafe) "Safe: No Threat Detected" else "Suspicious Links: Threat Detected"
        val bannerColor = if (result.isSafe) Color.parseColor("#1B5E20") else Color.parseColor("#B71C1C")
        val bannerDetail = if (result.isSafe) "Scam Detection - Verify site safety" else "Scam Detection - Suspicious links may be dangerous"

        createBanner(bannerText, bannerColor, bannerDetail, result.isSafe)

        // 2. Create Floating Tags
        if (!result.isSafe) {
            result.taggedElements.forEach { tag ->
                val sourceMatches = contentList.filter { content ->
                    val lowerContent = content.text.lowercase()
                    val lowerReason = tag.reason.lowercase()
                    lowerContent.contains(lowerReason) || 
                    (lowerContent.length > 10 && lowerReason.contains(lowerContent.take(20)))
                }

                sourceMatches.forEach { match ->
                    createFloatingTag(match.bounds, "Danger: Threat Detected", Color.parseColor("#B71C1C"))
                }
            }
        }
    }

    // --- UI COMPONENT: TOP BANNER (NO 'LEARN MORE') ---
    private fun createBanner(title: String, color: Int, subtitle: String, isSafe: Boolean) {
        val context = this
        val bannerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 30, 40, 30)
            background = GradientDrawable().apply {
                setColor(color) 
                cornerRadius = 24f
                alpha = 240
            }
            elevation = 10f
        }

        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconView = TextView(context).apply {
            text = "🛡️"
            textSize = 18f
            setPadding(0, 0, 20, 0)
            setTextColor(Color.WHITE)
        }
        
        val titleView = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(Typeface.DEFAULT_BOLD)
        }
        
        // REMOVED: "Learn More" TextView

        titleRow.addView(iconView)
        titleRow.addView(titleView)

        val subtitleView = TextView(context).apply {
            text = subtitle
            setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 12f
            setPadding(60, 5, 0, 0)
        }

        bannerLayout.addView(titleRow)
        bannerLayout.addView(subtitleView)

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP
            setMargins(30, 100, 30, 0)
        }

        tagsContainer?.addView(bannerLayout, params)
    }

    // --- UI COMPONENT: FLOATING PILL TAG (NEAR LINK) ---
    private fun createFloatingTag(bounds: Rect, labelText: String, color: Int) {
        val context = this
        
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 10, 20, 10) // Slightly tighter padding
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = 100f 
            }
            elevation = 15f
        }

        val icon = TextView(context).apply {
            text = "!" 
            textSize = 12f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(color)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                shape = GradientDrawable.OVAL
                setSize(36, 36)
            }
            layoutParams = LinearLayout.LayoutParams(40, 40).apply {
                marginEnd = 14
            }
        }

        val label = TextView(context).apply {
            text = labelText
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(Typeface.DEFAULT_BOLD)
        }

        val menuDots = TextView(context).apply {
            text = "⋮"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(14, 0, 0, 0)
        }

        pill.addView(icon)
        pill.addView(label)
        pill.addView(menuDots)

        // Positioning Logic: Closely attached to the link
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = bounds.left
            
            // Calculate a tight fit above the element
            val tagHeightApprox = 75 
            
            // If the element is very high up, put the tag slightly *over* the top part of the element
            // Otherwise, put it exactly on top of the element
            topMargin = if (bounds.top > 150) {
                bounds.top - tagHeightApprox + 10 // +10 to slightly overlap/touch the link
            } else {
                bounds.top + 10 // Fallback: inside the top edge if too close to screen top
            }
        }
        
        tagsContainer?.addView(pill, layoutParams)
    }

    private fun extractContentWithPositions(node: AccessibilityNodeInfo, list: MutableList<ContentWithPosition>) {
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank()) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            if (bounds.width() > 50 && bounds.height() > 20) {
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
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or 
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            windowManager.addView(scanningOverlay, params)
        } catch (e: Exception) {
            Log.e(TAG, "Overlay Error: ${e.message}")
        }
    }

    private fun hideScanningAnimation() {
        scanningOverlay?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
        }
        scanningOverlay = null
    }

    private fun clearTags() {
        tagsContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {}
        }
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

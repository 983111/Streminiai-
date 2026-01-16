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
        val reason: String,
        val url: String?
    )

    data class ContentWithPosition(
        val text: String, 
        val bounds: Rect,
        val isUrl: Boolean = false
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
                displayTagsForAllThreats(contentList, result)

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
                    val details = item.getJSONObject("details")
                    tags.add(TaggedElement(
                        label = securityTag.getString("label"),
                        color = Color.parseColor(securityTag.getString("color")),
                        reason = details.getString("reason"),
                        url = details.optString("url", null)
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
            Log.e(TAG, "Analysis error", e)
            ScanResult(true, "safe", "✓ No threats detected", emptyList())
        }
    }

    private fun displayTagsForAllThreats(contentList: List<ContentWithPosition>, result: ScanResult) {
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
        val bannerDetail = if (result.isSafe) 
            "Scam Detection - Verify site safety" 
        else 
            "⚠️ ${result.taggedElements.size} threat(s) found - Be careful"

        createBanner(bannerText, bannerColor, bannerDetail, result.isSafe)

        // 2. Create Individual Tags for EACH Suspicious URL/Threat
        if (!result.isSafe) {
            // Track tagged positions to avoid exact duplicates at same location
            val taggedPositions = mutableListOf<Pair<Int, Int>>()
            
            result.taggedElements.forEach { tag ->
                // For URL-specific threats, find the exact URL location
                if (tag.url != null && tag.url.isNotEmpty()) {
                    Log.d(TAG, "Looking for URL: ${tag.url}")
                    
                    // Find all content items that contain this URL (prioritize exact matches)
                    val exactMatches = contentList.filter { content ->
                        content.text.equals(tag.url, ignoreCase = true)
                    }
                    
                    val partialMatches = if (exactMatches.isEmpty()) {
                        contentList.filter { content ->
                            content.text.contains(tag.url, ignoreCase = true) ||
                            tag.url.contains(content.text, ignoreCase = true)
                        }
                    } else emptyList()
                    
                    val urlMatches = exactMatches.ifEmpty { partialMatches }

                    if (urlMatches.isNotEmpty()) {
                        Log.d(TAG, "Found ${urlMatches.size} matches for ${tag.url}")
                        
                        // Tag EACH occurrence of this URL
                        urlMatches.forEachIndexed { index, match ->
                            val posKey = Pair(match.bounds.left, match.bounds.top)
                            
                            // Check if we already tagged this exact position
                            val alreadyTagged = taggedPositions.any { 
                                Math.abs(it.first - posKey.first) < 50 && 
                                Math.abs(it.second - posKey.second) < 50 
                            }
                            
                            if (!alreadyTagged) {
                                Log.d(TAG, "Creating tag at bounds: ${match.bounds}")
                                createFloatingTag(
                                    match.bounds, 
                                    tag.label,
                                    tag.color,
                                    tag.reason,
                                    tag.url
                                )
                                taggedPositions.add(posKey)
                            } else {
                                Log.d(TAG, "Skipping duplicate tag at position $posKey")
                            }
                        }
                    } else {
                        Log.d(TAG, "No matches found for ${tag.url}, searching for URLs in content")
                        
                        // Try to find any URL-like content
                        val urlLikeContent = contentList.filter { it.isUrl }
                        if (urlLikeContent.isNotEmpty()) {
                            val match = urlLikeContent.first()
                            val posKey = Pair(match.bounds.left, match.bounds.top)
                            if (!taggedPositions.contains(posKey)) {
                                createFloatingTag(match.bounds, tag.label, tag.color, tag.reason, tag.url)
                                taggedPositions.add(posKey)
                            }
                        } else {
                            // Fallback: create tag at a default position
                            createGeneralThreatTag(tag, taggedPositions.size)
                        }
                    }
                } else {
                    // General threat (not URL-specific)
                    Log.d(TAG, "Processing general threat: ${tag.reason}")
                    
                    // Try to find related content by keyword matching
                    val keywords = tag.reason.lowercase().split(" ")
                        .filter { it.length > 4 }
                        .filter { !it.matches(Regex(".*[0-9%].*")) } // Skip words with numbers
                    
                    val relatedContent = contentList.find { content ->
                        val lowerContent = content.text.lowercase()
                        keywords.any { keyword -> lowerContent.contains(keyword) }
                    }

                    if (relatedContent != null) {
                        val posKey = Pair(relatedContent.bounds.left, relatedContent.bounds.top)
                        val alreadyTagged = taggedPositions.any { 
                            Math.abs(it.first - posKey.first) < 50 && 
                            Math.abs(it.second - posKey.second) < 50 
                        }
                        
                        if (!alreadyTagged) {
                            createFloatingTag(relatedContent.bounds, tag.label, tag.color, tag.reason, null)
                            taggedPositions.add(posKey)
                        }
                    } else {
                        createGeneralThreatTag(tag, taggedPositions.size)
                    }
                }
            }
            
            Log.d(TAG, "Total tags created: ${taggedPositions.size}")
        }
    }

    // Create a tag when we can't find exact position
    private fun createGeneralThreatTag(tag: TaggedElement, contentCount: Int) {
        val context = this
        
        // Position in middle-right area of screen
        val screenHeight = resources.displayMetrics.heightPixels
        val screenWidth = resources.displayMetrics.widthPixels
        
        val fakeBounds = Rect(
            screenWidth / 2,
            screenHeight / 3,
            screenWidth - 50,
            screenHeight / 3 + 100
        )
        
        createFloatingTag(fakeBounds, tag.label, tag.color, tag.reason, tag.url)
    }

    // --- UI COMPONENT: TOP BANNER ---
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
            text = if (isSafe) "✓" else "⚠️"
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

    // --- UI COMPONENT: FLOATING PILL TAG (NEAR EACH LINK) ---
    private fun createFloatingTag(bounds: Rect, labelText: String, color: Int, reason: String, url: String?) {
        val context = this
        
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16, 8, 16, 8)
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = 100f 
            }
            elevation = 20f
        }

        val icon = TextView(context).apply {
            text = "⚠" 
            textSize = 14f
            setTypeface(Typeface.DEFAULT_BOLD)
            setTextColor(color)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                shape = GradientDrawable.OVAL
                setSize(32, 32)
            }
            layoutParams = LinearLayout.LayoutParams(36, 36).apply {
                marginEnd = 12
            }
        }

        // Truncate label if too long
        val displayLabel = if (labelText.length > 20) {
            labelText.substring(0, 17) + "..."
        } else labelText

        val label = TextView(context).apply {
            text = displayLabel
            setTextColor(Color.WHITE)
            textSize = 12f
            setTypeface(Typeface.DEFAULT_BOLD)
            maxLines = 1
        }

        pill.addView(icon)
        pill.addView(label)

        // Calculate precise positioning DIRECTLY above the link
        val layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels
            
            // Tag height (approximate) 
            val tagHeight = 60
            
            // Horizontal positioning: align with left edge of link, but ensure it fits on screen
            val desiredLeft = bounds.left
            val maxLeft = screenWidth - 280 // Reserve space for tag width
            leftMargin = desiredLeft.coerceIn(10, maxLeft.coerceAtLeast(10))
            
            // Vertical positioning: Place DIRECTLY above the link
            val spaceAbove = bounds.top - 180 // Space above link (account for banner)
            
            if (spaceAbove >= tagHeight) {
                // Enough space above: position tag directly above link with small gap
                topMargin = bounds.top - tagHeight - 5
            } else if (bounds.bottom + tagHeight + 20 < screenHeight) {
                // Not enough space above: position below link
                topMargin = bounds.bottom + 5
            } else {
                // Overlapping scenario: position at top edge of link
                topMargin = bounds.top + 5
            }
            
            // Ensure tag is visible on screen
            topMargin = topMargin.coerceIn(180, screenHeight - tagHeight - 20)
        }
        
        tagsContainer?.addView(pill, layoutParams)
        
        // Log for debugging
        Log.d(TAG, "Tag '$labelText' - Link at (${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}) - Tag at (${layoutParams.leftMargin}, ${layoutParams.topMargin})")
    }

    private fun extractContentWithPositions(node: AccessibilityNodeInfo, list: MutableList<ContentWithPosition>) {
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank() && text.trim().length > 2) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            // Only add if element is visible and has reasonable size
            if (bounds.width() > 30 && bounds.height() > 15 && 
                bounds.top >= 0 && bounds.left >= 0) {
                
                // Check if this looks like a URL or contains URL
                val isUrl = text.contains("http://", ignoreCase = true) || 
                           text.contains("https://", ignoreCase = true) ||
                           text.contains("www.", ignoreCase = true) || 
                           text.matches(Regex(".*[a-z0-9-]+\\.(com|net|org|xyz|top|link|icu|tk|ml|ga|cf|gq|site|online|click).*", RegexOption.IGNORE_CASE))
                
                list.add(ContentWithPosition(text.trim(), bounds, isUrl))
                
                if (isUrl) {
                    Log.d(TAG, "Found URL-like content: '$text' at bounds: $bounds")
                }
            }
        }
        
        // Recursively extract from children
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                extractContentWithPositions(it, list)
                it.recycle()
            }
        }
    }

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

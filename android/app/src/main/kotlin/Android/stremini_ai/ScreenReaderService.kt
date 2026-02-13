package Android.stremini_ai

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
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
import java.net.IDN
import java.net.URI
import java.util.concurrent.TimeUnit

class ScreenReaderService : AccessibilityService() {

    companion object {
        const val ACTION_START_SCAN = "Android.stremini_ai.START_SCAN"
        const val ACTION_STOP_SCAN = "Android.stremini_ai.STOP_SCAN"
        const val ACTION_SCAN_COMPLETE = "Android.stremini_ai.SCAN_COMPLETE"
        const val EXTRA_SCANNED_TEXT = "scanned_text"
        private const val TAG = "ScreenReaderService"
        
        private var instance: ScreenReaderService? = null
        fun isRunning(): Boolean = instance != null
        fun isScanningActive(): Boolean = instance?.isScanning == true
        
        // --- THEME COLORS ---
        // Safe Theme (Green)
        private val SAFE_BG_COLOR = Color.parseColor("#1A3826")      // Dark Green Background
        private val SAFE_BORDER_COLOR = Color.parseColor("#2D5C43")  // Lighter Green Border
        private val SAFE_TEXT_COLOR = Color.parseColor("#6DD58C")    // Bright Green Text
        
        // Danger Theme (Brown/Red)
        private val DANGER_BG_COLOR = Color.parseColor("#38261A")    // Dark Brown/Red Background
        private val DANGER_BORDER_COLOR = Color.parseColor("#5C432D") // Lighter Brown Border
        private val DANGER_TEXT_COLOR = Color.parseColor("#FFD580")  // Orange/Gold Text
        private val ERROR_RED = Color.parseColor("#FF8080")          // Bright Red for critical icons
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

    // Data Models
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
        val url: String?,
        val message: String?
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
            ACTION_START_SCAN -> {
                if (tagsVisible) clearTags()
                if (!isScanning) startScreenScan()
            }
            ACTION_STOP_SCAN -> clearAllOverlays()
        }
        return START_NOT_STICKY
    }

    private fun startScreenScan() {
        isScanning = true
        showScanningAnimation()

        serviceScope.launch {
            try {
                // Wait for overlay to appear / app switching
                delay(800) 
                
                val rootNode = rootInActiveWindow
                if (rootNode == null) {
                    hideScanningAnimation()
                    isScanning = false
                    return@launch
                }

                val contentList = mutableListOf<ContentWithPosition>()
                extractContentWithPositions(rootNode, contentList)
                rootNode.recycle()

                val fullText = contentList.joinToString("\n") { it.text }
                
                // 1. ANALYZE (API Call with Fallback)
                val result = analyzeScreenContent(fullText)
                
                // 2. DISPLAY RESULTS
                hideScanningAnimation()
                displayTagsForAllThreats(contentList, result)

                // 3. BROADCAST TO FLUTTER
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
    
    // Fallback analysis if API fails or for offline testing
    private fun performLocalAnalysis(text: String): ScanResult {
        val lower = text.lowercase()
        val tags = mutableListOf<TaggedElement>()
        var isSafe = true
        
        // Simple keywords for demonstration
        val threats = listOf("scam", "winner", "prize", "urgent", "password", "bank", "verify", "pirate", "crack")
        
        threats.forEach { threat ->
            if (lower.contains(threat)) {
                isSafe = false
                tags.add(TaggedElement(
                    label = "Suspicious Keyword", 
                    color = DANGER_TEXT_COLOR, 
                    reason = threat, 
                    url = null, 
                    message = threat
                ))
            }
        }
        
        return ScanResult(isSafe, if(isSafe) "safe" else "warning", "Analysis Complete", tags)
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
            
            if (!response.isSuccessful) {
                 return@withContext performLocalAnalysis(content)
            }

            val responseData = response.body?.string() ?: ""
            val json = JSONObject(responseData)

            val taggedArray = json.optJSONArray("taggedElements")
            val tags = mutableListOf<TaggedElement>()
            if (taggedArray != null) {
                for (i in 0 until taggedArray.length()) {
                    val item = taggedArray.getJSONObject(i)
                    
                    val label = item.optString("label", "Threat Detected")
                    val matchedText = item.optString("matchedText", "Suspicious Content")
                    
                    tags.add(TaggedElement(
                        label = label,
                        color = DANGER_TEXT_COLOR,
                        reason = matchedText,
                        url = null,
                        message = matchedText
                    ))
                }
            }

            ScanResult(
                isSafe = json.optBoolean("isSafe", true),
                riskLevel = json.optString("riskLevel", "safe"),
                summary = json.optString("summary", ""),
                taggedElements = tags
            )
        } catch (e: Exception) {
            Log.e(TAG, "Analysis error", e)
            performLocalAnalysis(content)
        }
    }

    // ==========================================
    // UI DISPLAY LOGIC
    // ==========================================
    private fun displayTagsForAllThreats(contentList: List<ContentWithPosition>, result: ScanResult) {
        clearTags()
        
        // 1. Setup Full Screen Container
        tagsContainer = FrameLayout(this)
        val containerParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, 
            PixelFormat.TRANSLUCENT
        )
        containerParams.gravity = Gravity.TOP or Gravity.START
        containerParams.x = 0
        containerParams.y = 0
        windowManager.addView(tagsContainer, containerParams)

        // 2. Banner Logic
        val bannerTitle = if (result.isSafe) "Safe: No Threat Detected" else "Warning: Potential Threats"
        val bannerBg = if (result.isSafe) SAFE_BG_COLOR else DANGER_BG_COLOR
        val bannerBorder = if (result.isSafe) SAFE_BORDER_COLOR else DANGER_BORDER_COLOR
        val bannerText = if (result.isSafe) SAFE_TEXT_COLOR else DANGER_TEXT_COLOR
        
        createBanner(bannerTitle, bannerBg, bannerBorder, bannerText)

        // 3. Tag Logic
        if (!result.isSafe) {
            result.taggedElements.forEach { tag ->
                // Basic matching: find screen element containing the threat text
                val keyword = tag.reason.lowercase()
                val match = contentList.find { content -> 
                    content.text.lowercase().contains(keyword) 
                }

                if (match != null) {
                    createFloatingTag(match.bounds, tag.label, DANGER_BG_COLOR, DANGER_BORDER_COLOR, DANGER_TEXT_COLOR)
                }
            }
        } else {
            // Optional: Tag known safe elements to reassure user
            contentList.filter { it.text.contains("google", ignoreCase = true) || it.text.contains("wikipedia", ignoreCase = true) }.forEach {
                createFloatingTag(it.bounds, "Verified Safe", SAFE_BG_COLOR, SAFE_BORDER_COLOR, SAFE_TEXT_COLOR)
            }
        }
    }

    // ==========================================
    // UI COMPONENTS
    // ==========================================

    private fun createBanner(title: String, bgColor: Int, borderColor: Int, textColor: Int) {
        val context = this
        val bannerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12))
            
            background = GradientDrawable().apply {
                setColor(bgColor) 
                setStroke(dpToPx(2), borderColor)
                cornerRadius = dpToPx(24).toFloat()
            }
            elevation = 10f
        }

        val iconView = TextView(context).apply {
            text = if(title.contains("Safe")) "🛡️" else "⚠️"
            textSize = 18f
            setPadding(0, 0, dpToPx(8), 0)
            setTextColor(Color.WHITE)
        }
        
        val titleView = TextView(context).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(Typeface.DEFAULT_BOLD)
        }
        
        val statusView = TextView(context).apply {
            text = if(title.contains("Safe")) "SECURE" else "RISK"
            setTextColor(textColor)
            textSize = 12f
            setPadding(dpToPx(10), 0, 0, 0)
            setTypeface(Typeface.DEFAULT_BOLD)
        }
        
        bannerLayout.addView(iconView)
        bannerLayout.addView(titleView)
        bannerLayout.addView(statusView)

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dpToPx(40) // Status bar clear
        }

        tagsContainer?.addView(bannerLayout, params)
    }

    private fun createFloatingTag(bounds: Rect, labelText: String, bgColor: Int, borderColor: Int, textColor: Int) {
        val context = this
        
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dpToPx(20).toFloat()
                setStroke(dpToPx(1), borderColor)
            }
            elevation = 15f
        }
        
        val icon = TextView(context).apply {
            text = if (bgColor == SAFE_BG_COLOR) "✓" else "!"
            setTextColor(textColor)
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, dpToPx(6), 0)
        }

        val label = TextView(context).apply {
            text = labelText
            setTextColor(textColor)
            textSize = 12f
            setTypeface(Typeface.DEFAULT_BOLD)
            maxLines = 1
        }

        pill.addView(icon)
        pill.addView(label)

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            
            // Positioning Logic
            leftMargin = bounds.left.coerceAtLeast(10)
            // Position above the element, but ensure it doesn't overlap the top banner area
            topMargin = (bounds.top - dpToPx(35)).coerceAtLeast(dpToPx(90))
        }
        
        tagsContainer?.addView(pill, params)
    }

    private fun extractContentWithPositions(node: AccessibilityNodeInfo, list: MutableList<ContentWithPosition>) {
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank() && text.trim().length > 2) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            
            if (bounds.width() > 10 && bounds.height() > 10 && bounds.left >= 0 && bounds.top >= 0) {
                 list.add(ContentWithPosition(text.trim(), bounds))
            }
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let {
                extractContentWithPositions(it, list)
                it.recycle()
            }
        }
    }

    private fun showScanningAnimation() {
        try {
            val scanView = FrameLayout(this).apply {
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#60000000")) // Dim background
                }
            }
            
            val loadingText = TextView(this).apply {
                 text = "Scanning..."
                 setTextColor(Color.WHITE)
                 textSize = 20f
                 gravity = Gravity.CENTER
            }
            val textParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.CENTER }
            scanView.addView(loadingText, textParams)
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or 
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            
            windowManager.addView(scanView, params)
            scanningOverlay = scanView
        } catch (e: Exception) { Log.e(TAG, "Overlay Error", e) }
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

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
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

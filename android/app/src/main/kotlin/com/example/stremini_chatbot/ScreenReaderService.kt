package com.example.stremini_chatbot

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray

class ScreenReaderService : AccessibilityService() {

    companion object {
        const val ACTION_START_SCAN = "com.example.stremini_chatbot.START_SCAN"
        const val ACTION_STOP_SCAN = "com.example.stremini_chatbot.STOP_SCAN"
        
        // CORRECT API ENDPOINT FOR SCAM DETECTION
        private const val BASE_URL = "https://ai-keyboard-backend.vishwajeetadkine705.workers.dev"
        private const val SCAN_ENDPOINT = "$BASE_URL/security/analyze/text"
        
        private var isScanning = false
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var scanJob: Job? = null
    
    // Optimized OkHttp Client
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_SCAN -> {
                isScanning = true
                Log.d("StreminiScanner", "Screen Detection Started")
                // Launch coroutine to start scanning
                serviceScope.launch {
                    performGlobalScan()
                }
            }
            ACTION_STOP_SCAN -> {
                isScanning = false
                scanJob?.cancel()
                Log.d("StreminiScanner", "Screen Detection Stopped")
            }
        }
        return START_STICKY
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isScanning) return

        // Trigger scan on content changes
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || 
            event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            
            // Debounce: Wait 2 seconds for screen to settle
            scanJob?.cancel()
            scanJob = serviceScope.launch {
                delay(2000) 
                if (isScanning) {
                    performGlobalScan()
                }
            }
        }
    }

    private suspend fun performGlobalScan() {
        try {
            val rootNode = rootInActiveWindow ?: return
            val foundTexts = mutableListOf<String>()
            extractText(rootNode, foundTexts)
            
            val screenContent = foundTexts.joinToString(" ")
            
            // Only scan if we have meaningful content
            if (screenContent.trim().length > 20) {
                checkContentWithApi(screenContent)
            }
        } catch (e: Exception) {
            Log.e("StreminiScanner", "Scan error: ${e.message}")
        }
    }

    private suspend fun checkContentWithApi(content: String) {
        try {
            // Build request body matching your API format
            val jsonBody = JSONObject().apply {
                put("text", content)
            }
            
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(SCAN_ENDPOINT)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            Log.d("StreminiScanner", "Sending request to: $SCAN_ENDPOINT")
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseString = response.body?.string()
                Log.d("StreminiScanner", "Response: $responseString")
                
                if (!responseString.isNullOrEmpty()) {
                    val jsonResponse = JSONObject(responseString)
                    
                    // Parse response from your API
                    val isThreat = jsonResponse.optBoolean("is_threat", false)
                    val type = jsonResponse.optString("type", "safe")
                    val message = jsonResponse.optString("message", "")
                    val confidence = jsonResponse.optDouble("confidence", 0.0)
                    
                    // Get details array
                    val detailsArray = jsonResponse.optJSONArray("details")
                    val details = mutableListOf<String>()
                    if (detailsArray != null) {
                        for (i in 0 until detailsArray.length()) {
                            details.add(detailsArray.getString(i))
                        }
                    }
                    
                    // Show alert if threat detected
                    if (isThreat) {
                        val emoji = when (type) {
                            "danger" -> "🚨"
                            "warning" -> "⚠️"
                            else -> "ℹ️"
                        }
                        
                        val detailsText = if (details.isNotEmpty()) {
                            "\n\nDetails:\n• " + details.joinToString("\n• ")
                        } else {
                            ""
                        }
                        
                        val alertMessage = "$emoji THREAT DETECTED\n\n$message$detailsText\n\nConfidence: ${(confidence * 100).toInt()}%"
                        
                        // Send to ChatOverlayService
                        withContext(Dispatchers.Main) {
                            val intent = Intent(ChatOverlayService.ACTION_SEND_MESSAGE).apply {
                                putExtra(ChatOverlayService.EXTRA_MESSAGE, alertMessage)
                            }
                            sendBroadcast(intent)
                        }
                        
                        // Wait before next scan to avoid spam
                        delay(10000)
                    } else {
                        Log.d("StreminiScanner", "No threats detected")
                        // Optional: Notify user scan completed
                        // withContext(Dispatchers.Main) {
                        //     val intent = Intent(ChatOverlayService.ACTION_SEND_MESSAGE).apply {
                        //         putExtra(ChatOverlayService.EXTRA_MESSAGE, "✅ Screen scan complete - No threats found")
                        //     }
                        //     sendBroadcast(intent)
                        // }
                    }
                }
            } else {
                Log.e("StreminiScanner", "API Error: ${response.code} - ${response.message}")
            }
        } catch (e: Exception) {
            Log.e("StreminiScanner", "API Request Failed: ${e.message}", e)
        }
    }

    private fun extractText(node: AccessibilityNodeInfo, textList: MutableList<String>) {
        try {
            // Get text from current node
            if (node.text != null && node.text.isNotEmpty()) {
                textList.add(node.text.toString())
            }
            
            // Get content description if available
            if (node.contentDescription != null && node.contentDescription.isNotEmpty()) {
                textList.add(node.contentDescription.toString())
            }
            
            // Recursively get text from children
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    extractText(child, textList)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e("StreminiScanner", "Error extracting text: ${e.message}")
        }
    }

    override fun onInterrupt() {
        isScanning = false
        scanJob?.cancel()
        Log.d("StreminiScanner", "Service Interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isScanning = false
        scanJob?.cancel()
        serviceScope.cancel()
        Log.d("StreminiScanner", "Service Destroyed")
    }
}

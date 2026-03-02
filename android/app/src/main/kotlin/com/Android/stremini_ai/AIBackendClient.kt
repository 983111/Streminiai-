package com.Android.stremini_ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class AIBackendClient(
    private val client: OkHttpClient
) {
    suspend fun sendChatMessage(userMessage: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val requestBody = JSONObject().apply { put("message", userMessage) }
                .toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://ai-keyboard-backend.vishwajeetadkine705.workers.dev/chat/message")
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Server error: ${response.code}")
                val json = JSONObject(response.body?.string() ?: "")
                json.optString("reply", json.optString("response", json.optString("message", "No response")))
            }
        }
    }

    suspend fun sendDeviceCommandWithContext(command: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val screenText = try {
                ScreenReaderService.getInstance()?.let { service ->
                    val root = service.rootInActiveWindow
                    val sb = StringBuilder()
                    fun traverse(node: android.view.accessibility.AccessibilityNodeInfo) {
                        val text = node.text?.toString() ?: node.contentDescription?.toString()
                        if (!text.isNullOrBlank()) sb.appendLine(text.trim())
                        for (i in 0 until node.childCount) node.getChild(i)?.let { traverse(it) }
                    }
                    root?.let { traverse(it) }
                    sb.toString().take(1000)
                } ?: ""
            } catch (_: Exception) {
                ""
            }

            val requestJson = JSONObject().apply {
                put("message", command)
                put("screen_context", screenText)
                put("mode", "device_control")
            }

            val request = Request.Builder()
                .url("https://ai-keyboard-backend.vishwajeetadkine705.workers.dev/chat/message")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Could not process command (${response.code})")
                val json = JSONObject(response.body?.string() ?: "")
                json.optString("reply", json.optString("response", json.optString("message", "Command processed")))
            }
        }
    }

    suspend fun sendVoiceTaskCommandToAI(command: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val service = ScreenReaderService.getInstance()
            ?: return@withContext "❌ Accessibility service unavailable" to "Enable Stremini Screen Reader in Accessibility settings."

        val maxAgentSteps = 8
        var payload = JSONObject().apply {
            put("query", command)
            put("command", command)
            put("step_index", 0)
            put("screen_state", service.getVisibleScreenState())
        }

        repeat(maxAgentSteps) { index ->
            val request = Request.Builder()
                .url("https://ai-keyboard-backend.vishwajeetadkine705.workers.dev/classify-task")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext "❌ Server error ${response.code}" to "Failed to classify task."

            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrElse { return@withContext "❌ Invalid backend response" to raw }

            val steps = json.optJSONArray("steps")
            if (steps != null && steps.length() > 0) {
                val result = service.executeBackendSteps(steps)
                val status = if (result.success) "✅ Task completed" else "⚠️ Task partially completed"
                val output = buildString {
                    appendLine("Task: ${json.optString("task", "unknown")}")
                    appendLine("✅ Executed: ${result.completedSteps}")
                    appendLine("❌ Failed: ${result.failedSteps}")
                    appendLine()
                    append(result.message)
                }
                return@withContext status to output
            }

            val nextStep = json.optJSONObject("next_step") ?: json.optJSONObject("action")
            if (nextStep != null) {
                val result = service.executeBackendSteps(org.json.JSONArray().put(nextStep))
                if (!result.success) return@withContext "❌ Step failed" to result.message
            }

            if (json.optBoolean("done") || json.optBoolean("completed")) {
                return@withContext "✅ Task completed" to json.optString("summary", "Agentic loop completed")
            }

            payload = JSONObject().apply {
                put("query", command)
                put("command", command)
                put("step_index", index + 1)
                put("screen_state", service.getVisibleScreenState())
                put("previous_response", json)
            }
        }

        "⚠️ Max steps reached" to "Stopped after MAX_AGENT_STEPS without completion."
    }
}

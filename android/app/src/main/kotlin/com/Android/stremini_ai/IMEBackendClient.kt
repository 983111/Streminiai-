package com.Android.stremini_ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class LanguageOption(
    val code: String,
    val name: String,
)

class IMEBackendClient(
    private val baseUrl: String = "https://ai-keyboard-backend.vishwajeetadkine705.workers.dev"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun requestKeyboardAction(
        originalText: String,
        appContext: String,
        actionType: String,
        selectedTone: String,
    ): Result<String> = runCatching {
        val json = JSONObject().apply {
            put("text", originalText)
            put("appContext", appContext)
            if (actionType == "tone") put("tone", selectedTone)
        }

        val endpoint = when (actionType) {
            "complete" -> "complete"
            "tone" -> "tone"
            else -> "correct"
        }

        val request = Request.Builder()
            .url("$baseUrl/keyboard/$endpoint")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) return@use ""
            val resultJson = JSONObject(body)
            when (actionType) {
                "complete" -> resultJson.optString("completion")
                "tone" -> resultJson.optString("rewritten")
                    .ifBlank { resultJson.optString("result") }
                    .ifBlank { resultJson.optString("text") }
                    .ifBlank { resultJson.optString("corrected") }
                else -> resultJson.optString("corrected")
            }
        }
    }

    fun getTranslateLanguages(): Result<List<LanguageOption>> = runCatching {
        val request = Request.Builder()
            .url("$baseUrl/translate/languages")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) return@use emptyList()

            val payload = JSONObject(body)
            val array = payload.optJSONArray("languages")
                ?: payload.optJSONArray("data")
                ?: if (body.trim().startsWith("[")) JSONArray(body) else JSONArray()

            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val code = item.optString("code").trim()
                    val name = item.optString("name").trim()
                    if (code.isNotBlank() && name.isNotBlank()) {
                        add(LanguageOption(code = code, name = name))
                    }
                }
            }
        }
    }

    fun translateText(
        text: String,
        targetLanguage: String,
    ): Result<String> = runCatching {
        val json = JSONObject().apply {
            put("text", text)
            put("targetLanguage", targetLanguage)
        }

        val request = Request.Builder()
            .url("$baseUrl/translate")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) return@use ""

            val payload = JSONObject(body)
            payload.optString("translation")
                .ifBlank {
                    payload.optJSONArray("translations")
                        ?.optJSONObject(0)
                        ?.optString("translation")
                        .orEmpty()
                }
        }
    }
}

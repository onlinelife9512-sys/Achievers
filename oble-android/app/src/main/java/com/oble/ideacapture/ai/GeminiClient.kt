package com.oble.ideacapture.ai

import android.util.Base64
import com.oble.core.DetectedIdea
import com.oble.core.ExistingNote
import com.oble.core.IdeaJsonParser
import com.oble.core.IdeaPrompts
import com.oble.ideacapture.data.AppSettings
import com.oble.ideacapture.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

/** Failure talking to the AI. [retryable] = worth queueing and trying again later. */
class AiException(message: String, val retryable: Boolean) : Exception(message)

/**
 * Minimal REST client for the Gemini API (generativelanguage.googleapis.com).
 * The API key is read at call time from encrypted settings; it is never logged.
 */
class GeminiClient(
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun isConfigured(): Boolean = settings.geminiApiKey().isNotBlank()

    /** Speech-to-text for one temporary audio chunk (Gujarati / Hindi / English / mixed). */
    suspend fun transcribe(audio: File, mimeType: String = "audio/aac"): Pair<String, String?> {
        val data = withContext(Dispatchers.IO) { Base64.encodeToString(audio.readBytes(), Base64.NO_WRAP) }
        val body = buildJsonObject {
            putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", IdeaPrompts.TRANSCRIBE_SYSTEM) } } }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", IdeaPrompts.TRANSCRIBE_USER) }
                        addJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", mimeType)
                                put("data", data)
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.0)
                put("responseMimeType", "application/json")
            }
        }
        val text = generate(body)
        return IdeaJsonParser.parseTranscript(text)
    }

    /** Asks the model which useful ideas the new conversation contains. */
    suspend fun analyze(contextText: String, newText: String, existing: List<ExistingNote>): List<DetectedIdea> {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", IdeaPrompts.ANALYZE_SYSTEM) } } }
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", IdeaPrompts.analyzeUser(contextText, newText, existing)) }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", 0.2)
                put("responseMimeType", "application/json")
            }
        }
        return IdeaJsonParser.parse(generate(body))
    }

    /** Semantic embedding for duplicate detection. Returns null on any failure. */
    suspend fun embed(text: String): FloatArray? {
        if (!isConfigured()) return null
        val body = buildJsonObject {
            put("model", "models/${AppSettings.EMBEDDING_MODEL}")
            putJsonObject("content") { putJsonArray("parts") { addJsonObject { put("text", text) } } }
            put("taskType", "SEMANTIC_SIMILARITY")
            put("outputDimensionality", 256)
        }
        return try {
            val resp = post("models/${AppSettings.EMBEDDING_MODEL}:embedContent", body)
            val values = json.parseToJsonElement(resp).jsonObject["embedding"]?.jsonObject?.get("values")?.jsonArray
            values?.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }?.toFloatArray()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun generate(body: JsonObject): String {
        val model = settings.current.geminiModel.trim().ifBlank { AppSettings.DEFAULT_MODEL }
        val resp = post("models/$model:generateContent", body)
        val root = runCatching { json.parseToJsonElement(resp).jsonObject }
            .getOrElse { throw AiException("Unreadable AI response", retryable = true) }
        val candidate = (root["candidates"] as? JsonArray)?.firstOrNull()?.jsonObject
        if (candidate == null) {
            val blocked = root["promptFeedback"]?.jsonObject?.get("blockReason")
            throw AiException(if (blocked != null) "AI blocked the request: $blocked" else "Empty AI response", retryable = blocked == null)
        }
        val parts = candidate["content"]?.jsonObject?.get("parts") as? JsonArray
        return parts?.joinToString("") { (it.jsonObject["text"] as? JsonPrimitive)?.content.orEmpty() }.orEmpty()
    }

    private suspend fun post(path: String, body: JsonObject): String = withContext(Dispatchers.IO) {
        val key = settings.geminiApiKey()
        if (key.isBlank()) throw AiException("Gemini API key missing. Add it in Settings.", retryable = false)
        val request = Request.Builder()
            .url("$BASE/$path")
            .header("x-goog-api-key", key)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        try {
            http.newCall(request).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val retryable = r.code == 408 || r.code == 429 || r.code >= 500
                    throw AiException("Gemini HTTP ${r.code}: ${errorMessage(text)}", retryable)
                }
                text
            }
        } catch (e: IOException) {
            throw AiException("Network unavailable: ${e.message}", retryable = true)
        }
    }

    private fun errorMessage(body: String): String = runCatching {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.let { (it as JsonPrimitive).content }
    }.getOrNull() ?: body.take(160)

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    }
}

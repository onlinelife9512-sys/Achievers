package com.oble.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Parses the structured JSON returned by the model. Tolerant by design: models
 * occasionally wrap JSON in markdown fences, return a single object instead of a
 * list, or use slightly different key names. Anything unparseable yields an empty
 * list rather than an exception, so a bad response never crashes a session.
 */
object IdeaJsonParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String): List<DetectedIdea> {
        val element = parseElement(raw) ?: return emptyList()
        val items: List<JsonElement> = when (element) {
            is JsonArray -> element
            is JsonObject -> {
                val list = element["items"] ?: element["ideas"] ?: element["notes"]
                if (list is JsonArray) list else listOf(element)
            }
            else -> emptyList()
        }
        return items.mapNotNull { (it as? JsonObject)?.let(::toIdea) }
    }

    /** Extracts JSON from a model response that may contain fences or prose. */
    fun parseElement(raw: String): JsonElement? {
        val cleaned = raw.trim()
            .removePrefix("```json").removePrefix("```JSON").removePrefix("```")
            .removeSuffix("```").trim()
        runCatching { return json.parseToJsonElement(cleaned) }
        val start = cleaned.indexOfFirst { it == '{' || it == '[' }
        val end = cleaned.indexOfLast { it == '}' || it == ']' }
        if (start < 0 || end <= start) return null
        return runCatching { json.parseToJsonElement(cleaned.substring(start, end + 1)) }.getOrNull()
    }

    private fun toIdea(o: JsonObject): DetectedIdea? {
        val title = o.str("title").orEmpty().trim()
        val summary = o.str("summary").orEmpty().trim()
        val isIdea = o.bool("is_idea") ?: o.bool("isIdea") ?: (title.isNotEmpty())
        val action = o.str("action")?.lowercase()
        if (action == "skip") return null
        val mergeId = o.long("merge_into_id") ?: o.long("merge_with_id") ?: o.long("mergeIntoId")
        val confidence = (o.double("confidence") ?: 0.0).let { if (it > 1.0) it / 100.0 else it }
        return DetectedIdea(
            isIdea = isIdea,
            title = title,
            summary = summary,
            category = o.str("category").orEmpty().trim().ifEmpty { "General" },
            actionItems = o.list("action_items").ifEmpty { o.list("actionItems") },
            confidence = confidence.coerceIn(0.0, 1.0),
            type = NoteType.parse(o.str("type")),
            mergeIntoId = if (action == "create") null else mergeId,
            language = o.str("language"),
        )
    }

    private fun JsonObject.prim(key: String): JsonPrimitive? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

    private fun JsonObject.str(key: String): String? = prim(key)?.content
    private fun JsonObject.bool(key: String): Boolean? =
        prim(key)?.let { it.booleanOrNull ?: it.content.lowercase().toBooleanStrictOrNull() }
    private fun JsonObject.double(key: String): Double? =
        prim(key)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }
    private fun JsonObject.long(key: String): Long? =
        prim(key)?.let { it.longOrNull ?: it.content.toLongOrNull() }
    private fun JsonObject.list(key: String): List<String> = when (val v = this[key]) {
        is JsonArray -> v.mapNotNull { (it as? JsonPrimitive)?.content?.trim() }.filter { it.isNotEmpty() }
        is JsonPrimitive -> if (v is JsonNull || v.content.isBlank()) emptyList() else listOf(v.content.trim())
        else -> emptyList()
    }

    /** Parses the transcription response: {"text": "...", "language": "gu-en"}. */
    fun parseTranscript(raw: String): Pair<String, String?> {
        val el = parseElement(raw)
        if (el is JsonObject) {
            val text = (el["text"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content.orEmpty()
            val lang = (el["language"] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.content
            return text.trim() to lang
        }
        // Model ignored the JSON instruction: treat the whole response as text.
        return raw.trim() to null
    }
}

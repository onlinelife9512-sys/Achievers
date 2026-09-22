package com.oble.core

import kotlin.math.sqrt

/**
 * Similarity helpers used for duplicate detection.
 *
 * Semantic similarity is preferred (Gemini embeddings, compared with [cosine]).
 * When embeddings are unavailable (offline, API failure) we fall back to a lexical
 * score that works across Gujarati / Devanagari / Latin text: character trigrams
 * plus word-level Jaccard.
 */
object TextSimilarity {

    private val stopWords = setOf(
        "the", "a", "an", "and", "or", "to", "of", "for", "in", "on", "with", "is", "are",
        "be", "can", "should", "we", "it", "this", "that", "by", "using", "their", "its"
    )

    fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[\\p{Punct}\\p{S}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun tokens(text: String): Set<String> =
        normalize(text).split(' ').filter { it.length > 1 && it !in stopWords }.toSet()

    fun trigrams(text: String): Set<String> {
        val t = " " + normalize(text) + " "
        if (t.length < 3) return setOf(t)
        return (0..t.length - 3).map { t.substring(it, it + 3) }.toSet()
    }

    fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val inter = a.count { it in b }
        return inter.toDouble() / (a.size + b.size - inter)
    }

    /** Lexical similarity in [0,1]. */
    fun lexical(a: String, b: String): Double {
        val tri = jaccard(trigrams(a), trigrams(b))
        val tok = jaccard(tokens(a), tokens(b))
        return 0.6 * tri + 0.4 * tok
    }

    fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.isEmpty() || a.size != b.size) return 0.0
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            na += a[i] * a[i]
            nb += b[i] * b[i]
        }
        if (na == 0.0 || nb == 0.0) return 0.0
        return dot / (sqrt(na) * sqrt(nb))
    }

    fun encodeVector(v: FloatArray): String = v.joinToString(",")

    fun decodeVector(s: String?): FloatArray? {
        if (s.isNullOrBlank()) return null
        return try {
            s.split(',').map { it.toFloat() }.toFloatArray()
        } catch (_: NumberFormatException) {
            null
        }
    }
}

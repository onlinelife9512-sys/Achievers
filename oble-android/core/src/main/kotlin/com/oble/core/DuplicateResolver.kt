package com.oble.core

/**
 * Decides what to do with a freshly detected idea:
 * create a new note, merge it into an existing one, or drop it as a duplicate.
 *
 * Order of evidence:
 *  1. The AI explicitly asked to merge into a known note id  -> [Decision.Merge].
 *  2. Semantic similarity (embeddings) against recent notes:
 *       >= duplicateThreshold  -> [Decision.Duplicate] (same idea, nothing new)
 *       >= mergeThreshold      -> [Decision.Merge]     (same concept, extend it)
 *  3. Lexical fallback when embeddings are unavailable.
 */
class DuplicateResolver(
    private val duplicateThreshold: Double = 0.92,
    private val mergeThreshold: Double = 0.84,
    private val lexicalDuplicateThreshold: Double = 0.55,
    private val lexicalMergeThreshold: Double = 0.38,
) {
    sealed interface Decision {
        data object Create : Decision
        data class Merge(val targetId: Long, val score: Double) : Decision
        data class Duplicate(val targetId: Long, val score: Double) : Decision
    }

    fun resolve(
        candidate: DetectedIdea,
        candidateEmbedding: FloatArray?,
        existing: List<ExistingNote>,
    ): Decision {
        candidate.mergeIntoId?.let { id ->
            if (existing.any { it.id == id }) return Decision.Merge(id, 1.0)
        }
        if (existing.isEmpty()) return Decision.Create

        val candidateText = "${candidate.title}. ${candidate.summary}"
        var best: ExistingNote? = null
        var bestScore = 0.0
        var semantic = false
        for (note in existing) {
            val emb = note.embedding
            val (score, isSemantic) = if (candidateEmbedding != null && emb != null && emb.size == candidateEmbedding.size) {
                TextSimilarity.cosine(candidateEmbedding, emb) to true
            } else {
                TextSimilarity.lexical(candidateText, note.text) to false
            }
            if (score > bestScore) {
                bestScore = score; best = note; semantic = isSemantic
            }
        }
        val target = best ?: return Decision.Create
        val dup = if (semantic) duplicateThreshold else lexicalDuplicateThreshold
        val merge = if (semantic) mergeThreshold else lexicalMergeThreshold
        return when {
            bestScore >= dup -> Decision.Duplicate(target.id, bestScore)
            bestScore >= merge && sameArea(candidate, target) -> Decision.Merge(target.id, bestScore)
            else -> Decision.Create
        }
    }

    private fun sameArea(candidate: DetectedIdea, note: ExistingNote): Boolean {
        if (candidate.category.equals(note.category, ignoreCase = true)) return true
        return TextSimilarity.jaccard(
            TextSimilarity.tokens(candidate.category), TextSimilarity.tokens(note.category)
        ) > 0.0 || TextSimilarity.jaccard(
            TextSimilarity.tokens(candidate.title), TextSimilarity.tokens(note.title)
        ) >= 0.25
    }

    companion object {
        /** Merge action items without duplicates (case/whitespace-insensitive). */
        fun mergeActionItems(a: List<String>, b: List<String>): List<String> {
            val seen = LinkedHashMap<String, String>()
            (a + b).forEach { item ->
                val key = TextSimilarity.normalize(item)
                if (key.isNotEmpty() && seen.keys.none { TextSimilarity.lexical(it, key) > 0.8 }) {
                    seen[key] = item.trim()
                }
            }
            return seen.values.toList()
        }
    }
}

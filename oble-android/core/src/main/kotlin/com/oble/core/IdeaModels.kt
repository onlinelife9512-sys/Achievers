package com.oble.core

/** What kind of useful point was captured. */
enum class NoteType(val label: String, val emoji: String) {
    IDEA("IDEA", "💡"),
    OPPORTUNITY("OPPORTUNITY", "🚀"),
    FEATURE("FEATURE", "🧩"),
    DECISION("DECISION", "🧭"),
    TASK("TASK", "✅"),
    PROBLEM("PROBLEM", "🔍"),
    REMINDER("REMINDER", "⏰"),
    FOLLOW_UP("FOLLOW-UP", "🔁");

    companion object {
        fun parse(raw: String?): NoteType {
            val key = raw?.trim()?.uppercase()?.replace('-', '_')?.replace(' ', '_') ?: return IDEA
            return entries.firstOrNull { it.name == key } ?: when {
                key.contains("OPPORTUN") -> OPPORTUNITY
                key.contains("FEATURE") -> FEATURE
                key.contains("DECISION") -> DECISION
                key.contains("TASK") || key.contains("TODO") -> TASK
                key.contains("PROBLEM") || key.contains("PAIN") -> PROBLEM
                key.contains("REMIND") -> REMINDER
                key.contains("FOLLOW") -> FOLLOW_UP
                else -> IDEA
            }
        }
    }
}

/** One item the AI extracted from the conversation (internal structured JSON). */
data class DetectedIdea(
    val isIdea: Boolean,
    val title: String,
    val summary: String,
    val category: String,
    val actionItems: List<String>,
    val confidence: Double,
    val type: NoteType = NoteType.IDEA,
    /** Id of an existing note this item extends, when the AI decided to merge. */
    val mergeIntoId: Long? = null,
    val language: String? = null,
)

/** Minimal view of an already stored note, given to the AI and to the dedup logic. */
data class ExistingNote(
    val id: Long,
    val title: String,
    val summary: String,
    val category: String,
    val actionItems: List<String> = emptyList(),
    val embedding: FloatArray? = null,
) {
    val text: String get() = "$title. $summary"
}

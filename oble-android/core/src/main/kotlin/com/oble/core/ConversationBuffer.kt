package com.oble.core

/**
 * A rolling buffer of transcript segments for one listening session.
 *
 * Speech arrives in small fragments. Calling the LLM for every fragment would be
 * expensive and would lose context, so the buffer only signals [shouldAnalyze] once
 * enough *new* speech has accumulated AND a minimum interval has elapsed, or when
 * a lot of speech piles up, or when the conversation goes quiet after something
 * meaningful was said. Already-analysed text is retained as context so an idea
 * spread across several minutes can still be recognised as one concept.
 */
class ConversationBuffer(
    private val policy: Policy = Policy(),
) {
    data class Policy(
        /** Minimum characters of new speech before analysing on the interval. */
        val minNewChars: Int = 160,
        /** Analyse at most this often (unless [maxNewChars] is exceeded). */
        val minIntervalMs: Long = 45_000,
        /** Force analysis once this much unanalysed speech is buffered. */
        val maxNewChars: Int = 1_800,
        /** Analyse after this much silence if at least [minCharsOnSilence] is pending. */
        val silenceFlushMs: Long = 20_000,
        val minCharsOnSilence: Int = 60,
        /** How much already-analysed text to keep as context. */
        val contextChars: Int = 1_200,
    )

    data class Segment(val id: Long, val text: String, val timestamp: Long)

    data class Window(
        /** Recent, already analysed speech that gives context. */
        val context: List<Segment>,
        /** New speech that must be analysed now. */
        val pending: List<Segment>,
    ) {
        val pendingText: String get() = pending.joinToString("\n") { it.text }
        val contextText: String get() = context.joinToString("\n") { it.text }
    }

    private val context = ArrayDeque<Segment>()
    private val pending = mutableListOf<Segment>()
    private var lastAnalysisAt: Long = 0
    private var lastSpeechAt: Long = 0
    private var inFlight: List<Segment> = emptyList()

    @Synchronized
    fun add(segment: Segment) {
        if (segment.text.isBlank()) return
        if (lastAnalysisAt == 0L) lastAnalysisAt = segment.timestamp
        pending += segment
        lastSpeechAt = segment.timestamp
    }

    @Synchronized
    fun pendingChars(): Int = pending.sumOf { it.text.length }

    @Synchronized
    fun shouldAnalyze(now: Long): Boolean {
        if (inFlight.isNotEmpty() || pending.isEmpty()) return false
        val chars = pendingChars()
        if (chars >= policy.maxNewChars) return true
        if (chars >= policy.minNewChars && now - lastAnalysisAt >= policy.minIntervalMs) return true
        if (chars >= policy.minCharsOnSilence && now - lastSpeechAt >= policy.silenceFlushMs) return true
        return false
    }

    /** Takes the pending speech for analysis. Call [commit] or [rollback] afterwards. */
    @Synchronized
    fun take(now: Long): Window? {
        if (inFlight.isNotEmpty() || pending.isEmpty()) return null
        inFlight = pending.toList()
        pending.clear()
        lastAnalysisAt = now
        return Window(context.toList(), inFlight)
    }

    /** Analysis succeeded: move the analysed speech into the context window. */
    @Synchronized
    fun commit() {
        inFlight.forEach { context.addLast(it) }
        inFlight = emptyList()
        while (context.sumOf { it.text.length } > policy.contextChars && context.size > 1) {
            context.removeFirst()
        }
    }

    /** Analysis failed: put the speech back so nothing is lost. */
    @Synchronized
    fun rollback() {
        pending.addAll(0, inFlight)
        inFlight = emptyList()
    }

    @Synchronized
    fun clear() {
        context.clear(); pending.clear(); inFlight = emptyList()
        lastAnalysisAt = 0; lastSpeechAt = 0
    }
}

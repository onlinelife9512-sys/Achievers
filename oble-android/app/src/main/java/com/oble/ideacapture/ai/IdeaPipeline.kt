package com.oble.ideacapture.ai

import android.util.Log
import com.oble.core.ConversationBuffer
import com.oble.core.DetectedIdea
import com.oble.core.DuplicateResolver
import com.oble.core.ExistingNote
import com.oble.core.LanguageDetector
import com.oble.core.TextSimilarity
import com.oble.ideacapture.data.IdeaEntity
import com.oble.ideacapture.data.IdeaRepository
import com.oble.ideacapture.data.SegmentEntity
import com.oble.ideacapture.data.SettingsRepository
import com.oble.ideacapture.data.WhatsAppStatus
import com.oble.ideacapture.whatsapp.WhatsAppDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * The heart of OBLE:
 *
 *   transcript segment -> rolling buffer -> (periodically) Gemini analysis
 *   -> confidence filter -> duplicate / merge resolution -> Room -> WhatsApp dispatch
 *
 * Every segment is persisted *before* analysis with `analyzed = false`, so nothing is
 * lost if the AI call fails, the network drops or the app is killed: the
 * [com.oble.ideacapture.work.PendingAnalysisWorker] picks those segments up later.
 */
class IdeaPipeline(
    private val repo: IdeaRepository,
    private val gemini: GeminiClient,
    private val settings: SettingsRepository,
    private val dispatcher: WhatsAppDispatcher,
) {
    sealed interface Event {
        data class Created(val idea: IdeaEntity) : Event
        data class Merged(val idea: IdeaEntity) : Event
        data class Duplicate(val idea: IdeaEntity) : Event
        data class Failed(val message: String, val retryable: Boolean) : Event
    }

    private val buffers = ConcurrentHashMap<Long, ConversationBuffer>()
    private val analysisLock = Mutex()
    private var retryNotBefore = 0L
    private val resolver = DuplicateResolver()

    private val _lastEvent = MutableStateFlow<Event?>(null)
    val lastEvent: StateFlow<Event?> = _lastEvent.asStateFlow()

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    /** Every pipeline event, unconflated (for counters). */
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private fun emit(event: Event) {
        _lastEvent.value = event
        _events.tryEmit(event)
    }

    private val _analyzing = MutableStateFlow(false)
    val analyzing: StateFlow<Boolean> = _analyzing.asStateFlow()

    fun openSession(sessionId: Long) {
        val interval = settings.current.analysisIntervalSec.coerceIn(15, 300) * 1000L
        buffers[sessionId] = ConversationBuffer(ConversationBuffer.Policy(minIntervalMs = interval))
    }

    fun isLive(sessionId: Long) = buffers.containsKey(sessionId)

    /** A new piece of recognised speech. Returns quickly; analysis only when due. */
    suspend fun onSpeech(
        sessionId: Long,
        text: String,
        languageHint: String?,
        now: Long = System.currentTimeMillis(),
    ) {
        val clean = text.trim()
        if (clean.length < 2) return
        val lang = languageHint?.takeIf { it.isNotBlank() } ?: LanguageDetector.detect(clean).code
        val id = repo.addSegment(SegmentEntity(sessionId = sessionId, text = clean, language = lang, timestamp = now))
        buffers[sessionId]?.add(ConversationBuffer.Segment(id, clean, now))
        tick(sessionId)
    }

    /** Called periodically by the service so silence-based and time-based triggers fire. */
    suspend fun tick(sessionId: Long) {
        val buffer = buffers[sessionId] ?: return
        val now = System.currentTimeMillis()
        if (now < retryNotBefore) return
        if (buffer.shouldAnalyze(now)) analyzeBuffer(sessionId, buffer, now)
    }

    /** Idea Mode stopped: analyse whatever is left, then forget the live buffer. */
    suspend fun closeSession(sessionId: Long): Boolean {
        val buffer = buffers[sessionId] ?: return true
        val ok = if (buffer.pendingChars() >= MIN_FLUSH_CHARS) {
            analyzeBuffer(sessionId, buffer, System.currentTimeMillis())
        } else true
        buffers.remove(sessionId)
        return ok
    }

    private suspend fun analyzeBuffer(sessionId: Long, buffer: ConversationBuffer, now: Long): Boolean {
        val window = buffer.take(now) ?: return true
        return try {
            analyzeWindow(sessionId, window.contextText, window.pendingText)
            repo.markAnalyzed(window.pending.map { it.id }, settings.current.keepTranscripts)
            buffer.commit()
            true
        } catch (e: AiException) {
            buffer.rollback()
            retryNotBefore = System.currentTimeMillis() + RETRY_BACKOFF_MS
            emit(Event.Failed(e.message ?: "AI error", e.retryable))
            Log.w(TAG, "Analysis failed (retryable=${e.retryable}): ${e.message}")
            false
        }
    }

    /**
     * Processes segments that were stored but never analysed (offline, crash, app
     * killed). Skips sessions that are currently live. Returns false if a retryable
     * failure occurred so WorkManager can retry with backoff.
     */
    suspend fun processPending(): Boolean {
        val pending = repo.unanalyzedSegments().filterNot { isLive(it.sessionId) }
        if (pending.isEmpty()) return true
        if (!gemini.isConfigured()) return true // Nothing to do until a key is added.
        for ((sessionId, segs) in pending.groupBy { it.sessionId }) {
            val context = repo.recentAnalyzedSegments(sessionId, 8).reversed().joinToString("\n") { it.text }
            for (window in windows(segs)) {
                try {
                    analyzeWindow(sessionId, context, window.joinToString("\n") { it.text })
                    repo.markAnalyzed(window.map { it.id }, settings.current.keepTranscripts)
                } catch (e: AiException) {
                    emit(Event.Failed(e.message ?: "AI error", e.retryable))
                    return !e.retryable
                }
            }
        }
        return true
    }

    private fun windows(segs: List<SegmentEntity>): List<List<SegmentEntity>> {
        val out = mutableListOf<List<SegmentEntity>>()
        var cur = mutableListOf<SegmentEntity>()
        var chars = 0
        for (s in segs) {
            if (chars + s.text.length > WINDOW_CHARS && cur.isNotEmpty()) {
                out += cur; cur = mutableListOf(); chars = 0
            }
            cur += s; chars += s.text.length
        }
        if (cur.isNotEmpty()) out += cur
        return out
    }

    private suspend fun analyzeWindow(sessionId: Long, contextText: String, newText: String) = analysisLock.withLock {
        if (newText.isBlank()) return@withLock
        _analyzing.value = true
        try {
            val since = System.currentTimeMillis() - EXISTING_LOOKBACK_MS
            val existing = repo.recentIdeas(since, EXISTING_LIMIT).map { it.toExisting() }.toMutableList()
            val detected = gemini.analyze(contextText, newText, existing)
            val threshold = settings.current.confidenceThreshold.toDouble()
            detected
                .filter { it.isIdea && it.title.isNotBlank() && it.confidence >= threshold }
                .forEach { handle(sessionId, it, existing) }
        } finally {
            _analyzing.value = false
        }
    }

    private suspend fun handle(sessionId: Long, d: DetectedIdea, existing: MutableList<ExistingNote>) {
        val embedding = gemini.embed("${d.title}. ${d.summary}")
        val now = System.currentTimeMillis()
        when (val decision = resolver.resolve(d, embedding, existing)) {
            DuplicateResolver.Decision.Create -> {
                val entity = IdeaEntity(
                    sessionId = sessionId,
                    type = d.type.name,
                    title = d.title,
                    summary = d.summary,
                    category = d.category,
                    actionItems = d.actionItems,
                    confidence = d.confidence,
                    language = d.language,
                    embedding = embedding?.let(TextSimilarity::encodeVector),
                    createdAt = now,
                    updatedAt = now,
                )
                val id = repo.insertIdea(entity)
                val saved = entity.copy(id = id)
                existing += saved.toExisting()
                emit(Event.Created(saved))
                dispatcher.onNewIdea(saved)
            }
            is DuplicateResolver.Decision.Merge -> {
                val target = repo.getIdea(decision.targetId) ?: return
                val aiMerged = d.mergeIntoId == decision.targetId
                val summary = when {
                    aiMerged -> d.summary.ifBlank { target.summary }
                    TextSimilarity.lexical(target.summary, d.summary) > 0.5 -> target.summary
                    else -> "${target.summary.trimEnd('.', ' ')}. ${d.summary}"
                }
                val title = if (aiMerged) d.title.ifBlank { target.title } else target.title
                val wasDelivered = target.whatsappStatus == WhatsAppStatus.SENT || target.whatsappStatus == WhatsAppStatus.OPENED
                val mergedEmbedding = gemini.embed("$title. $summary") ?: embedding
                val updated = target.copy(
                    title = title,
                    summary = summary,
                    category = if (aiMerged) d.category else target.category,
                    type = if (aiMerged) d.type.name else target.type,
                    actionItems = DuplicateResolver.mergeActionItems(target.actionItems, d.actionItems),
                    confidence = maxOf(target.confidence, d.confidence),
                    embedding = mergedEmbedding?.let(TextSimilarity::encodeVector) ?: target.embedding,
                    mergeCount = target.mergeCount + 1,
                    updatedAt = now,
                    updatedSinceSent = wasDelivered || target.updatedSinceSent,
                )
                repo.updateIdea(updated)
                existing.replaceAll { if (it.id == updated.id) updated.toExisting() else it }
                emit(Event.Merged(updated))
                dispatcher.onIdeaMerged(updated, wasDelivered)
            }
            is DuplicateResolver.Decision.Duplicate -> {
                val target = repo.getIdea(decision.targetId) ?: return
                val updated = target.copy(
                    mentionCount = target.mentionCount + 1,
                    actionItems = DuplicateResolver.mergeActionItems(target.actionItems, d.actionItems),
                    updatedAt = now,
                )
                repo.updateIdea(updated)
                emit(Event.Duplicate(updated))
            }
        }
    }

    private fun IdeaEntity.toExisting() = ExistingNote(
        id = id, title = title, summary = summary, category = category,
        actionItems = actionItems, embedding = TextSimilarity.decodeVector(embedding),
    )

    companion object {
        private const val TAG = "IdeaPipeline"
        private const val MIN_FLUSH_CHARS = 25
        private const val RETRY_BACKOFF_MS = 60_000L
        private const val WINDOW_CHARS = 2_000
        private const val EXISTING_LOOKBACK_MS = 7L * 24 * 60 * 60 * 1000
        private const val EXISTING_LIMIT = 25
    }
}

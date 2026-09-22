package com.oble.ideacapture.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class IdeaRepository(private val db: AppDatabase) {
    private val ideas = db.ideaDao()
    private val sessions = db.sessionDao()
    private val segments = db.segmentDao()

    fun observeIdeas(query: String): Flow<List<IdeaEntity>> =
        if (query.isBlank()) ideas.observeAll() else ideas.search(query.trim())

    fun observeIdea(id: Long): Flow<IdeaEntity?> = ideas.observe(id)

    fun observeTodayCount(): Flow<Int> = ideas.observeCountSince(startOfToday())

    fun observeSessions(): Flow<List<SessionSummary>> = sessions.observeSummaries()

    fun observeTranscript(sessionId: Long): Flow<List<SegmentEntity>> = segments.observeForSession(sessionId)

    fun observePendingAnalysis(): Flow<Int> = segments.observeUnanalyzedCount()

    fun observeUnsent(): Flow<List<IdeaEntity>> =
        ideas.observeWithStatus(listOf(WhatsAppStatus.READY.name, WhatsAppStatus.FAILED.name, WhatsAppStatus.QUEUED.name))

    suspend fun getIdea(id: Long) = ideas.get(id)
    suspend fun insertIdea(idea: IdeaEntity) = ideas.insert(idea)
    suspend fun updateIdea(idea: IdeaEntity) = ideas.update(idea)
    suspend fun deleteIdea(id: Long) = ideas.delete(id)
    suspend fun recentIdeas(since: Long, limit: Int) = ideas.recent(since, limit)
    suspend fun ideasWithStatus(vararg status: WhatsAppStatus) = ideas.withStatus(status.map { it.name })

    suspend fun updateWhatsApp(id: Long, status: WhatsAppStatus, error: String? = null, countAttempt: Boolean = false) {
        val idea = ideas.get(id) ?: return
        ideas.update(
            idea.copy(
                whatsappStatus = status,
                whatsappUpdatedAt = System.currentTimeMillis(),
                whatsappError = error,
                whatsappAttempts = idea.whatsappAttempts + if (countAttempt) 1 else 0,
                updatedSinceSent = if (status == WhatsAppStatus.SENT || status == WhatsAppStatus.OPENED) false else idea.updatedSinceSent,
            )
        )
    }

    suspend fun startSession(engine: String, language: String, consent: Boolean): Long =
        sessions.insert(SessionEntity(startedAt = System.currentTimeMillis(), engine = engine, languageSetting = language, consentConfirmed = consent))

    suspend fun endSession(id: Long) = sessions.end(id, System.currentTimeMillis())
    suspend fun closeDanglingSessions() = sessions.closeDangling(System.currentTimeMillis())

    suspend fun addSegment(segment: SegmentEntity): Long = segments.insert(segment)
    suspend fun unanalyzedSegments() = segments.unanalyzed()
    suspend fun recentAnalyzedSegments(sessionId: Long, limit: Int) = segments.recentAnalyzed(sessionId, limit)
    suspend fun markAnalyzed(ids: List<Long>, keepTranscripts: Boolean) {
        if (ids.isEmpty()) return
        if (keepTranscripts) segments.markAnalyzed(ids) else segments.delete(ids)
    }

    /** Deletes a conversation (session + transcript). Ideas captured from it are kept. */
    suspend fun deleteSession(id: Long) = db.withTransaction {
        segments.deleteForSession(id)
        sessions.delete(id)
    }

    suspend fun clearAll() = db.withTransaction {
        ideas.deleteAll()
        segments.deleteAll()
        sessions.deleteAll()
    }

    companion object {
        fun startOfToday(): Long = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

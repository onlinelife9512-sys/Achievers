package com.oble.ideacapture.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface IdeaDao {
    @Query("SELECT * FROM ideas ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<IdeaEntity>>

    @Query(
        """SELECT * FROM ideas WHERE title LIKE '%' || :q || '%' OR summary LIKE '%' || :q || '%'
           OR category LIKE '%' || :q || '%' OR actionItems LIKE '%' || :q || '%' ORDER BY updatedAt DESC"""
    )
    fun search(q: String): Flow<List<IdeaEntity>>

    @Query("SELECT COUNT(*) FROM ideas WHERE createdAt >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("SELECT * FROM ideas WHERE id = :id")
    suspend fun get(id: Long): IdeaEntity?

    @Query("SELECT * FROM ideas WHERE id = :id")
    fun observe(id: Long): Flow<IdeaEntity?>

    @Query("SELECT * FROM ideas WHERE updatedAt >= :since ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(since: Long, limit: Int): List<IdeaEntity>

    @Query("SELECT * FROM ideas WHERE whatsappStatus IN (:statuses) ORDER BY createdAt ASC")
    suspend fun withStatus(statuses: List<String>): List<IdeaEntity>

    @Query("SELECT * FROM ideas WHERE whatsappStatus IN (:statuses) ORDER BY createdAt ASC")
    fun observeWithStatus(statuses: List<String>): Flow<List<IdeaEntity>>

    @Insert
    suspend fun insert(idea: IdeaEntity): Long

    @Update
    suspend fun update(idea: IdeaEntity)

    @Query("DELETE FROM ideas WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM ideas")
    suspend fun deleteAll()
}

@Dao
interface SessionDao {
    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("UPDATE sessions SET endedAt = :endedAt WHERE id = :id")
    suspend fun end(id: Long, endedAt: Long)

    @Query("UPDATE sessions SET endedAt = :now WHERE endedAt IS NULL")
    suspend fun closeDangling(now: Long)

    @Query(
        """SELECT s.id, s.startedAt, s.endedAt, s.engine,
                  COUNT(g.id) AS segmentCount, COALESCE(SUM(LENGTH(g.text)), 0) AS charCount
           FROM sessions s LEFT JOIN segments g ON g.sessionId = s.id
           GROUP BY s.id ORDER BY s.startedAt DESC"""
    )
    fun observeSummaries(): Flow<List<SessionSummary>>

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM sessions")
    suspend fun deleteAll()
}

@Dao
interface SegmentDao {
    @Insert
    suspend fun insert(segment: SegmentEntity): Long

    @Query("SELECT * FROM segments WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun observeForSession(sessionId: Long): Flow<List<SegmentEntity>>

    @Query("SELECT * FROM segments WHERE analyzed = 0 ORDER BY sessionId, timestamp ASC")
    suspend fun unanalyzed(): List<SegmentEntity>

    @Query("SELECT * FROM segments WHERE sessionId = :sessionId AND analyzed = 1 ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recentAnalyzed(sessionId: Long, limit: Int): List<SegmentEntity>

    @Query("UPDATE segments SET analyzed = 1 WHERE id IN (:ids)")
    suspend fun markAnalyzed(ids: List<Long>)

    @Query("DELETE FROM segments WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM segments WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Query("DELETE FROM segments")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM segments WHERE analyzed = 0")
    fun observeUnanalyzedCount(): Flow<Int>
}

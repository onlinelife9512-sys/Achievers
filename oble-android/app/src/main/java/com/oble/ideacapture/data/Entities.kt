package com.oble.ideacapture.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Delivery state of a note towards the selected WhatsApp destination. */
enum class WhatsAppStatus(val label: String) {
    NOT_SENT("Not sent"),
    /** Message prepared; waiting for the user to tap "Send" (one-tap mode). */
    READY("Ready to send"),
    /** WhatsApp was opened with the message pre-filled; the user presses send there. */
    OPENED("Opened in WhatsApp"),
    /** Waiting for network / retry (WhatsApp Cloud API mode). */
    QUEUED("Queued"),
    /** Confirmed by WhatsApp Cloud API, or marked as sent by the user. */
    SENT("Sent"),
    FAILED("Failed");

    companion object {
        fun parse(v: String?): WhatsAppStatus = entries.firstOrNull { it.name == v } ?: NOT_SENT
    }
}

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val engine: String,
    val languageSetting: String,
    val consentConfirmed: Boolean,
)

@Entity(
    tableName = "segments",
    foreignKeys = [ForeignKey(
        entity = SessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("sessionId"), Index("analyzed")],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val text: String,
    val language: String,
    val timestamp: Long,
    val analyzed: Boolean = false,
)

@Entity(tableName = "ideas", indices = [Index("createdAt"), Index("whatsappStatus")])
data class IdeaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Source session; kept (nullable) when the conversation itself is deleted. */
    val sessionId: Long? = null,
    val type: String,
    val title: String,
    val summary: String,
    val category: String,
    val actionItems: List<String> = emptyList(),
    val confidence: Double,
    val status: String = "New Idea",
    val language: String? = null,
    /** Semantic embedding (comma separated floats) used for duplicate detection. */
    val embedding: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    /** How many times new conversation points were merged into this note. */
    val mergeCount: Int = 0,
    /** How many times the same idea was mentioned again (duplicates suppressed). */
    val mentionCount: Int = 1,
    val whatsappStatus: WhatsAppStatus = WhatsAppStatus.NOT_SENT,
    val whatsappUpdatedAt: Long? = null,
    val whatsappError: String? = null,
    val whatsappAttempts: Int = 0,
    /** The note changed (merge) after it was already delivered. */
    val updatedSinceSent: Boolean = false,
)

data class SessionSummary(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val engine: String,
    val segmentCount: Int,
    val charCount: Int,
)

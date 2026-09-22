package com.oble.ideacapture.speech

import android.content.Context
import com.oble.ideacapture.ai.AiException
import com.oble.ideacapture.ai.GeminiClient
import com.oble.ideacapture.ai.IdeaPipeline
import java.io.File

/**
 * Holds speech chunks that could not be transcribed yet (no internet / API error).
 * They stay in private app storage, are retried by WorkManager when online, and are
 * deleted right after transcription — or after [MAX_AGE_MS] if never processed.
 */
class PendingAudioStore(
    context: Context,
    private val gemini: GeminiClient,
    private val pipeline: IdeaPipeline,
) {
    private val dir = File(context.noBackupFilesDir, "pending_audio").apply { mkdirs() }

    fun park(chunk: File, sessionId: Long, startedAt: Long) {
        val target = File(dir, "s${sessionId}_$startedAt.aac")
        if (!chunk.renameTo(target)) {
            chunk.copyTo(target, overwrite = true)
            chunk.delete()
        }
    }

    fun count(): Int = dir.listFiles()?.size ?: 0

    /** Returns true when everything was processed (or dropped). */
    suspend fun transcribeAll(): Boolean {
        val files = dir.listFiles()?.sortedBy { it.name }.orEmpty()
        val now = System.currentTimeMillis()
        for (f in files) {
            val match = NAME.matchEntire(f.name)
            if (match == null || now - f.lastModified() > MAX_AGE_MS) {
                f.delete(); continue
            }
            if (!gemini.isConfigured()) return true
            val (sessionId, startedAt) = match.destructured
            try {
                val (text, lang) = gemini.transcribe(f)
                f.delete()
                if (text.isNotBlank()) pipeline.onSpeech(sessionId.toLong(), text, lang, startedAt.toLong())
            } catch (e: AiException) {
                if (!e.retryable) f.delete() else throw e
            }
        }
        return true
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        private val NAME = Regex("s(\\d+)_(\\d+)\\.aac")
        private const val MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}

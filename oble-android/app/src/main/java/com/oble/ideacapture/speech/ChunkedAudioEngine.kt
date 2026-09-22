package com.oble.ideacapture.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Records short, temporary AAC chunks for cloud transcription (Gemini).
 *
 * Privacy by design:
 *  • Chunks live in the app's private cache directory only.
 *  • Chunks without speech (by amplitude) are deleted immediately and never uploaded.
 *  • Chunks with speech are handed to [onChunk]; the consumer deletes them right after
 *    transcription. Raw audio is never stored permanently.
 *
 * Chunks are cut every [chunkSeconds], or earlier at a natural pause once enough
 * speech has been captured, so sentences are rarely split.
 */
class ChunkedAudioEngine(
    private val context: Context,
    private val chunkSeconds: Int,
    private val listener: SpeechListener,
    private val onChunk: (file: File, startedAt: Long) -> Unit,
) : SpeechEngine {

    override val label: String = "Gemini audio · Gujarati / Hindi / English"

    private val executor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
    private var loop: Job? = null
    @Volatile private var recorder: MediaRecorder? = null
    @Volatile private var paused = false
    @Volatile private var stopped = false

    private val dir: File get() = File(context.cacheDir, "audio_chunks").apply { mkdirs() }

    override fun start() {
        stopped = false
        paused = false
        dir.listFiles()?.forEach { it.delete() } // leftovers from a crash are never uploaded
        startLoop()
    }

    override fun pause() {
        paused = true
        listener.onLevel(0f)
    }

    override fun resume() {
        if (stopped) return
        paused = false
        if (loop?.isActive != true) startLoop()
    }

    override fun stop() {
        stopped = true
        // Release the microphone right now, from the calling thread.
        runBlocking { loop?.cancelAndJoin() }
        releaseRecorder(keepFile = null)
        listener.onLevel(0f)
        executor.shutdown()
    }

    private fun startLoop() {
        loop = scope.launch {
            var failures = 0
            while (isActive && !paused && !stopped) {
                val ok = recordOneChunk()
                if (ok) failures = 0 else {
                    failures++
                    listener.onStatus("Microphone unavailable (call or another app?) — retrying…")
                    delay(min(15_000L, 1_000L * failures))
                }
            }
            listener.onLevel(0f)
        }
    }

    /** Records until the chunk is full, a natural pause, pause/stop. Returns false if the mic failed. */
    private suspend fun recordOneChunk(): Boolean {
        val file = File(dir, "chunk_${System.currentTimeMillis()}.aac")
        val startedAt = System.currentTimeMillis()
        val rec = try {
            newRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioSamplingRate(16_000)
                setAudioEncodingBitRate(32_000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: Exception) {
            file.delete()
            return false
        }
        recorder = rec
        listener.onStatus(null)

        val maxMs = chunkSeconds.coerceIn(10, 60) * 1000L
        var voicedFrames = 0
        var silentFramesInRow = 0
        var zeroFrames = 0
        var frames = 0
        try {
            while (!paused && !stopped) {
                delay(FRAME_MS)
                frames++
                val amp = try { rec.maxAmplitude } catch (_: Exception) { 0 }
                if (amp == 0) zeroFrames++
                listener.onLevel(sqrt(amp / 32767f).coerceIn(0f, 1f))
                if (amp > SPEECH_AMPLITUDE) {
                    voicedFrames++; silentFramesInRow = 0
                } else silentFramesInRow++

                val elapsed = System.currentTimeMillis() - startedAt
                val naturalPause = elapsed >= MIN_CHUNK_MS && voicedFrames >= MIN_VOICED_FRAMES &&
                    silentFramesInRow * FRAME_MS >= PAUSE_CUT_MS
                if (elapsed >= maxMs || naturalPause) break
            }
        } finally {
            val hasSpeech = voicedFrames >= MIN_VOICED_FRAMES
            releaseRecorder(keepFile = if (hasSpeech) file else null)
            if (!hasSpeech) file.delete()
            else if (file.exists() && file.length() > 0) onChunk(file, startedAt)
        }
        if (frames > 20 && zeroFrames == frames) {
            listener.onStatus("No sound from the microphone — another app may be using it.")
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()

    private fun releaseRecorder(keepFile: File?) {
        val rec = recorder ?: return
        recorder = null
        try {
            rec.stop()
        } catch (_: RuntimeException) {
            keepFile?.delete() // stop() throws when no valid audio was captured
        }
        rec.release()
    }

    companion object {
        private const val FRAME_MS = 100L
        private const val SPEECH_AMPLITUDE = 1_800
        private const val MIN_VOICED_FRAMES = 6 // ~0.6 s of voice
        private const val MIN_CHUNK_MS = 8_000L
        private const val PAUSE_CUT_MS = 1_500L
    }
}

package com.oble.ideacapture.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.oble.ideacapture.ObleApp
import com.oble.ideacapture.ai.AiException
import com.oble.ideacapture.ai.IdeaPipeline
import com.oble.ideacapture.data.SpeechEngineType
import com.oble.ideacapture.speech.AndroidSpeechEngine
import com.oble.ideacapture.speech.ChunkedAudioEngine
import com.oble.ideacapture.speech.SpeechEngine
import com.oble.ideacapture.speech.SpeechListener
import com.oble.ideacapture.work.WorkScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Idea Mode. A foreground service of type `microphone`:
 *  • It can only be started from the visible app after the user taps
 *    "Start Idea Mode" and confirms everyone present consents.
 *  • While it runs, Android shows the persistent "● Listening for ideas" notification
 *    and the system microphone privacy indicator; neither can be hidden.
 *  • Pause releases the microphone; Stop releases it immediately and ends the service.
 *  • START_NOT_STICKY: if Android kills the process, listening is NOT silently
 *    restarted. The user must start Idea Mode again (no covert recording).
 */
class IdeaListeningService : LifecycleService() {

    private val container by lazy { (application as ObleApp).container }
    private var engine: SpeechEngine? = null
    private var sessionId: Long? = null
    private var ticker: Job? = null
    private var chunkJob: Job? = null
    private var chunks: Channel<Pair<File, Long>>? = null
    @Volatile private var stopRequested = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> start(intent.getBooleanExtra(EXTRA_CONSENT, false))
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stopListening()
            else -> if (engine == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(consent: Boolean) {
        if (engine != null || ListeningStateHolder.state.value.status == ListeningStatus.STARTING) return
        stopRequested = false
        ListeningStateHolder.update { ListeningState(status = ListeningStatus.STARTING, startedAt = System.currentTimeMillis()) }

        // Must call startForeground promptly after startForegroundService().
        val fgType = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        try {
            ServiceCompat.startForeground(this, Notifications.LISTENING_ID, Notifications.listening(this, ListeningStateHolder.state.value), fgType)
        } catch (e: Exception) {
            // e.g. ForegroundServiceStartNotAllowedException / missing mic permission.
            ListeningStateHolder.reset(error = "Android did not allow Idea Mode to start: ${e.message}")
            stopSelf()
            return
        }

        if (!consent || ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ListeningStateHolder.reset(error = if (!consent) "Consent is required before listening." else "Microphone permission is required.")
            shutdown()
            return
        }

        val settings = container.settings.current
        val useGemini = settings.speechEngine == SpeechEngineType.GEMINI_AUDIO && container.gemini.isConfigured()
        val fallbackNote = when {
            settings.speechEngine == SpeechEngineType.GEMINI_AUDIO && !useGemini ->
                "No Gemini key — using the Android recognizer. Add a key in Settings for mixed-language transcription."
            else -> null
        }
        if (!useGemini && !SpeechRecognizer.isRecognitionAvailable(this)) {
            ListeningStateHolder.reset(error = "No speech engine available. Add a Gemini API key in Settings.")
            shutdown()
            return
        }

        lifecycleScope.launch {
            container.repository.closeDanglingSessions()
            val id = container.repository.startSession(
                engine = if (useGemini) "gemini" else "android",
                language = settings.recognizerLanguage,
                consent = true,
            )
            if (stopRequested) {
                container.repository.endSession(id)
                return@launch
            }
            sessionId = id
            container.pipeline.openSession(id)

            val listener = listener(id)
            engine = if (useGemini) {
                val channel = Channel<Pair<File, Long>>(Channel.UNLIMITED)
                chunks = channel
                chunkJob = container.appScope.launch {
                    for ((file, startedAt) in channel) transcribeChunk(id, file, startedAt)
                }
                ChunkedAudioEngine(this@IdeaListeningService, settings.chunkSeconds, listener) { file, startedAt ->
                    channel.trySend(file to startedAt)
                }
            } else {
                AndroidSpeechEngine(this@IdeaListeningService, settings.recognizerLanguage, listener)
            }
            ListeningStateHolder.update {
                it.copy(status = ListeningStatus.LISTENING, sessionId = id, engineLabel = engine!!.label, message = fallbackNote, error = null)
            }
            engine!!.start()
            refreshNotification()
            startTicker(id)
        }
    }

    private fun listener(sessionId: Long) = object : SpeechListener {
        override fun onText(text: String, languageHint: String?) {
            ListeningStateHolder.update { it.copy(lastHeard = text, partial = "") }
            container.appScope.launch { container.pipeline.onSpeech(sessionId, text, languageHint) }
        }
        override fun onPartial(text: String) = ListeningStateHolder.update { it.copy(partial = text) }
        override fun onLevel(level: Float) = ListeningStateHolder.update { it.copy(level = level) }
        override fun onStatus(message: String?) = ListeningStateHolder.update { it.copy(message = message) }
        override fun onFatal(message: String) {
            ListeningStateHolder.update { it.copy(error = message) }
            lifecycleScope.launch { stopListening(error = message) }
        }
    }

    /** Temporary chunk -> Gemini transcription -> delete audio -> pipeline. */
    private suspend fun transcribeChunk(sessionId: Long, file: File, startedAt: Long) {
        try {
            val (text, lang) = container.gemini.transcribe(file)
            file.delete()
            if (text.isNotBlank()) {
                ListeningStateHolder.update { it.copy(lastHeard = text) }
                container.pipeline.onSpeech(sessionId, text, lang, startedAt)
            }
        } catch (e: AiException) {
            if (e.retryable) {
                // Offline / temporary error: keep the chunk privately and retry later.
                container.pendingAudio.park(file, sessionId, startedAt)
                WorkScheduler.enqueuePendingProcessing(this)
                ListeningStateHolder.update { it.copy(message = "Offline — speech saved, will be processed when back online.") }
            } else {
                file.delete()
                ListeningStateHolder.update { it.copy(message = e.message) }
            }
        } catch (e: Exception) {
            file.delete()
        }
    }

    private fun startTicker(sessionId: Long) {
        ticker = lifecycleScope.launch {
            var ideas = 0
            launch {
                container.pipeline.events.collect { ev ->
                    if (ev is IdeaPipeline.Event.Created && ev.idea.sessionId == sessionId) {
                        ideas++
                        ListeningStateHolder.update { it.copy(ideasThisSession = ideas) }
                        refreshNotification()
                    }
                }
            }
            while (isActive) {
                delay(5_000)
                container.appScope.launch { container.pipeline.tick(sessionId) }
            }
        }
    }

    private fun pause() {
        val e = engine ?: return
        e.pause()
        ListeningStateHolder.update { it.copy(status = ListeningStatus.PAUSED, level = 0f, partial = "") }
        refreshNotification()
    }

    private fun resume() {
        val e = engine ?: return
        e.resume()
        ListeningStateHolder.update { it.copy(status = ListeningStatus.LISTENING, message = null) }
        refreshNotification()
    }

    private fun stopListening(error: String? = null) {
        // 1. Microphone off immediately.
        stopRequested = true
        engine?.stop()
        engine = null
        ticker?.cancel()
        ListeningStateHolder.update { it.copy(status = ListeningStatus.STOPPING, level = 0f) }

        // 2. Finish processing in the app scope (outlives this service).
        val id = sessionId
        val channel = chunks
        val job = chunkJob
        sessionId = null; chunks = null; chunkJob = null
        if (id != null) {
            container.appScope.launch {
                channel?.close()
                job?.join() // transcribe the last chunk(s)
                val ok = container.pipeline.closeSession(id)
                container.repository.endSession(id)
                if (!ok) WorkScheduler.enqueuePendingProcessing(this@IdeaListeningService.applicationContext)
            }
        }
        ListeningStateHolder.reset(error = error)
        shutdown()
    }

    private fun shutdown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun refreshNotification() {
        if (engine == null) return
        try {
            NotificationManagerCompat.from(this)
                .notify(Notifications.LISTENING_ID, Notifications.listening(this, ListeningStateHolder.state.value))
        } catch (_: SecurityException) {
            // Notification permission denied: the foreground service notification is still
            // shown by the system in the task manager, and the status bar mic indicator stays on.
        }
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 may time-limit some FGS types; microphone is not time-limited, but be safe.
        stopListening(error = "Android stopped Idea Mode.")
    }

    override fun onDestroy() {
        if (engine != null || sessionId != null) stopListening()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.oble.action.START"
        const val ACTION_PAUSE = "com.oble.action.PAUSE"
        const val ACTION_RESUME = "com.oble.action.RESUME"
        const val ACTION_STOP = "com.oble.action.STOP"
        const val EXTRA_CONSENT = "consent"

        fun start(context: Context, consent: Boolean) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, IdeaListeningService::class.java).setAction(ACTION_START).putExtra(EXTRA_CONSENT, consent),
            )
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, IdeaListeningService::class.java).setAction(action))
        }
    }
}

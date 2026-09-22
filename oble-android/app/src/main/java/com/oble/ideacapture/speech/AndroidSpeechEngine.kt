package com.oble.ideacapture.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.os.HandlerCompat
import kotlin.math.min

/**
 * Continuous listening on top of Android's [SpeechRecognizer].
 *
 * The platform recogniser is designed for single utterances, so we restart it after
 * every result or recoverable error, with exponential backoff for network / audio
 * failures. No audio file is ever written by this engine.
 *
 * Language: one primary locale per session (gu-IN, hi-IN or en-IN). Google's gu-IN and
 * hi-IN models transcribe embedded English words well. With "auto" on Android 14+
 * we additionally enable the platform's language detection / switching between
 * Gujarati, Hindi and English.
 *
 * Note: some devices play a short system "beep" each time recognition restarts;
 * that sound is controlled by the recogniser app and cannot be disabled
 * reliably without muting system audio, which OBLE does not do.
 */
class AndroidSpeechEngine(
    private val context: Context,
    private val languageSetting: String,
    private val listener: SpeechListener,
) : SpeechEngine {

    override val label: String = "Android recognizer · ${languageLabel(languageSetting)}"

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var active = false
    private var paused = false
    private var consecutiveErrors = 0
    private var fallbackToEnglish = false

    override fun start() {
        main.post { doStart() }
    }

    private fun doStart() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            listener.onFatal("Speech recognition is not available on this phone. Install/enable Google speech services, or use the Gemini audio engine.")
            return
        }
        active = true
        paused = false
        createRecognizer()
        listen()
    }

    override fun pause() {
        main.post {
            paused = true
            main.removeCallbacksAndMessages(RESTART_TOKEN)
            recognizer?.cancel()
            listener.onLevel(0f)
        }
    }

    override fun resume() {
        main.post {
            if (active) {
                paused = false
                consecutiveErrors = 0
                listen()
            }
        }
    }

    override fun stop() {
        active = false
        val work = Runnable {
            main.removeCallbacksAndMessages(RESTART_TOKEN)
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
            listener.onLevel(0f)
        }
        if (Looper.myLooper() == Looper.getMainLooper()) work.run() else main.post(work)
    }

    private fun createRecognizer() {
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply { setRecognitionListener(callbacks) }
    }

    private fun recognizerIntent(): Intent {
        val primary = when {
            fallbackToEnglish -> "en-IN"
            languageSetting == "auto" -> "gu-IN"
            else -> languageSetting
        }
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, primary)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, primary)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 10_000L)
            if (languageSetting == "auto" && Build.VERSION.SDK_INT >= 34) {
                val allowed = arrayListOf("gu-IN", "hi-IN", "en-IN")
                putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
                putStringArrayListExtra("android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES", allowed)
                putExtra("android.speech.extra.ENABLE_LANGUAGE_SWITCH", "balanced")
                putStringArrayListExtra("android.speech.extra.LANGUAGE_SWITCH_ALLOWED_LANGUAGES", allowed)
            }
        }
    }

    private fun listen() {
        if (!active || paused) return
        try {
            recognizer?.startListening(recognizerIntent())
        } catch (e: Exception) {
            scheduleRestart(1_000, recreate = true)
        }
    }

    private fun scheduleRestart(delayMs: Long, recreate: Boolean = false) {
        if (!active || paused) return
        main.removeCallbacksAndMessages(RESTART_TOKEN)
        HandlerCompat.postDelayed(main, {
            if (recreate) createRecognizer()
            listen()
        }, RESTART_TOKEN, delayMs)
    }

    private fun backoff(): Long = min(15_000L, 500L * (1L shl min(consecutiveErrors, 5)))

    private val callbacks = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = listener.onStatus(null)
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) = listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() = listener.onLevel(0f)
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.let { if (it.isNotBlank()) listener.onPartial(it) }
        }

        override fun onResults(results: Bundle?) {
            consecutiveErrors = 0
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!text.isNullOrBlank()) listener.onText(text, null)
            listener.onPartial("")
            scheduleRestart(150)
        }

        override fun onError(error: Int) {
            if (!active) return
            consecutiveErrors++
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    consecutiveErrors = 0
                    scheduleRestart(150)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    listener.onFatal("Microphone permission was revoked.")
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER -> {
                    listener.onStatus("Speech service offline — retrying…")
                    scheduleRestart(backoff())
                }
                SpeechRecognizer.ERROR_AUDIO -> {
                    listener.onStatus("Microphone busy (call or another app) — retrying…")
                    scheduleRestart(backoff(), recreate = true)
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> scheduleRestart(backoff(), recreate = true)
                ERROR_LANGUAGE_NOT_SUPPORTED, ERROR_LANGUAGE_UNAVAILABLE -> {
                    if (!fallbackToEnglish) {
                        fallbackToEnglish = true
                        listener.onStatus("${languageLabel(languageSetting)} not available on this phone — using English (India). Download the language in Google app settings, or switch to the Gemini audio engine.")
                    }
                    scheduleRestart(500, recreate = true)
                }
                else -> scheduleRestart(backoff(), recreate = true)
            }
        }
    }

    companion object {
        private val RESTART_TOKEN = Any()
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13

        fun languageLabel(tag: String) = when (tag) {
            "gu-IN" -> "Gujarati"
            "hi-IN" -> "Hindi"
            "en-IN" -> "English (India)"
            "auto" -> "Auto (gu/hi/en)"
            else -> tag
        }
    }
}

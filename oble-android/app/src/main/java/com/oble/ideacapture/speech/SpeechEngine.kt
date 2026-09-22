package com.oble.ideacapture.speech

/** A source of recognised speech. Implementations only touch the mic between start() and stop(). */
interface SpeechEngine {
    val label: String
    fun start()
    /** Releases the microphone until [resume]. */
    fun pause()
    fun resume()
    /** Stops immediately and releases the microphone. */
    fun stop()
}

interface SpeechListener {
    /** Final recognised text (Android engine) — chunk files go through [onAudioChunk]. */
    fun onText(text: String, languageHint: String?)
    fun onPartial(text: String) {}
    /** Input level 0..1 for the waveform. */
    fun onLevel(level: Float) {}
    /** Non-fatal, user-visible status (e.g. "Waiting for network"). Null clears it. */
    fun onStatus(message: String?) {}
    /** The engine cannot continue (e.g. permission revoked). */
    fun onFatal(message: String)
}

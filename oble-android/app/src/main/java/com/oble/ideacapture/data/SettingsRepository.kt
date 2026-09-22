package com.oble.ideacapture.data

import android.content.Context
import com.oble.ideacapture.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DeliveryMode(val label: String, val description: String) {
    ONE_TAP(
        "One-tap (WhatsApp app)",
        "OBLE prepares the message and shows a notification. One tap opens WhatsApp with the note ready; you press Send.",
    ),
    CLOUD_API(
        "Automatic (WhatsApp Cloud API)",
        "Fully automatic sending through Meta's official WhatsApp Business Cloud API. Requires your own Business number, token and an approved template.",
    ),
}

enum class SpeechEngineType(val label: String, val description: String) {
    GEMINI_AUDIO(
        "Gemini audio (best for mixed languages)",
        "Short temporary audio chunks are transcribed by Gemini and deleted immediately. Handles Gujarati + Hindi + English in one sentence.",
    ),
    ANDROID(
        "Android speech recognizer",
        "Uses the phone's built-in recognizer (Google). No audio file is created. One primary language at a time.",
    ),
}

data class AppSettings(
    val destinationName: String = "",
    val destinationPhone: String = "",
    val deliveryMode: DeliveryMode = DeliveryMode.ONE_TAP,
    val autoPrepare: Boolean = true,
    val sendMergedUpdates: Boolean = false,
    val waPhoneNumberId: String = "",
    val waUseTemplate: Boolean = true,
    val waTemplateName: String = "",
    val waTemplateLanguage: String = "en",
    val geminiModel: String = DEFAULT_MODEL,
    val confidenceThreshold: Float = 0.7f,
    val analysisIntervalSec: Int = 45,
    val speechEngine: SpeechEngineType = SpeechEngineType.GEMINI_AUDIO,
    val recognizerLanguage: String = "gu-IN",
    val chunkSeconds: Int = 30,
    val askConsentEachTime: Boolean = true,
    val keepTranscripts: Boolean = true,
    val hasGeminiKey: Boolean = false,
    val hasWaToken: Boolean = false,
) {
    val hasDestination: Boolean get() = destinationPhone.isNotBlank()

    companion object {
        const val DEFAULT_MODEL = "gemini-2.5-flash"
        const val EMBEDDING_MODEL = "gemini-embedding-001"
    }
}

/**
 * App settings. Plain preferences for non-sensitive options; secrets go through
 * [SecretStore] (Android Keystore encryption).
 */
class SettingsRepository(context: Context, private val secrets: SecretStore) {
    private val prefs = context.getSharedPreferences("oble_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    val current: AppSettings get() = _settings.value

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            destinationName = prefs.getString("destinationName", d.destinationName)!!,
            destinationPhone = prefs.getString("destinationPhone", d.destinationPhone)!!,
            deliveryMode = enumOr(prefs.getString("deliveryMode", null), d.deliveryMode),
            autoPrepare = prefs.getBoolean("autoPrepare", d.autoPrepare),
            sendMergedUpdates = prefs.getBoolean("sendMergedUpdates", d.sendMergedUpdates),
            waPhoneNumberId = prefs.getString("waPhoneNumberId", d.waPhoneNumberId)!!,
            waUseTemplate = prefs.getBoolean("waUseTemplate", d.waUseTemplate),
            waTemplateName = prefs.getString("waTemplateName", d.waTemplateName)!!,
            waTemplateLanguage = prefs.getString("waTemplateLanguage", d.waTemplateLanguage)!!,
            geminiModel = prefs.getString("geminiModel", d.geminiModel)!!,
            confidenceThreshold = prefs.getFloat("confidenceThreshold", d.confidenceThreshold),
            analysisIntervalSec = prefs.getInt("analysisIntervalSec", d.analysisIntervalSec),
            speechEngine = enumOr(prefs.getString("speechEngine", null), d.speechEngine),
            recognizerLanguage = prefs.getString("recognizerLanguage", d.recognizerLanguage)!!,
            chunkSeconds = prefs.getInt("chunkSeconds", d.chunkSeconds),
            askConsentEachTime = prefs.getBoolean("askConsentEachTime", d.askConsentEachTime),
            keepTranscripts = prefs.getBoolean("keepTranscripts", d.keepTranscripts),
            hasGeminiKey = geminiApiKey().isNotBlank(),
            hasWaToken = !secrets.get(SecretStore.WA_CLOUD_TOKEN).isNullOrBlank(),
        )
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putString("destinationName", s.destinationName)
            .putString("destinationPhone", s.destinationPhone)
            .putString("deliveryMode", s.deliveryMode.name)
            .putBoolean("autoPrepare", s.autoPrepare)
            .putBoolean("sendMergedUpdates", s.sendMergedUpdates)
            .putString("waPhoneNumberId", s.waPhoneNumberId)
            .putBoolean("waUseTemplate", s.waUseTemplate)
            .putString("waTemplateName", s.waTemplateName)
            .putString("waTemplateLanguage", s.waTemplateLanguage)
            .putString("geminiModel", s.geminiModel)
            .putFloat("confidenceThreshold", s.confidenceThreshold)
            .putInt("analysisIntervalSec", s.analysisIntervalSec)
            .putString("speechEngine", s.speechEngine.name)
            .putString("recognizerLanguage", s.recognizerLanguage)
            .putInt("chunkSeconds", s.chunkSeconds)
            .putBoolean("askConsentEachTime", s.askConsentEachTime)
            .putBoolean("keepTranscripts", s.keepTranscripts)
            .apply()
        _settings.value = load()
    }

    /** User-entered key (encrypted) takes priority over an optional build-time default. */
    fun geminiApiKey(): String =
        secrets.get(SecretStore.GEMINI_API_KEY)?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_GEMINI_API_KEY

    fun setGeminiApiKey(key: String?) {
        secrets.put(SecretStore.GEMINI_API_KEY, key?.trim())
        _settings.value = load()
    }

    fun waCloudToken(): String? = secrets.get(SecretStore.WA_CLOUD_TOKEN)

    fun setWaCloudToken(token: String?) {
        secrets.put(SecretStore.WA_CLOUD_TOKEN, token?.trim())
        _settings.value = load()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        secrets.clear()
        _settings.value = load()
    }
}

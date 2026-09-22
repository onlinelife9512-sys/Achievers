package com.oble.ideacapture

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import com.oble.ideacapture.ai.GeminiClient
import com.oble.ideacapture.ai.IdeaPipeline
import com.oble.ideacapture.data.AppDatabase
import com.oble.ideacapture.data.IdeaRepository
import com.oble.ideacapture.data.SecretStore
import com.oble.ideacapture.data.SettingsRepository
import com.oble.ideacapture.speech.PendingAudioStore
import com.oble.ideacapture.whatsapp.CloudApiClient
import com.oble.ideacapture.whatsapp.WhatsAppDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Manual dependency container (small app, no DI framework needed). */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val db = AppDatabase.build(context)
    val repository = IdeaRepository(db)
    private val secretStore = SecretStore(context)
    val settings = SettingsRepository(context, secretStore)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    val gemini = GeminiClient(http, settings)
    val cloudApi = CloudApiClient(http, settings)
    val whatsApp = WhatsAppDispatcher(context, repository, settings)
    val pipeline = IdeaPipeline(repository, gemini, settings, whatsApp)
    val pendingAudio = PendingAudioStore(context, gemini, pipeline)

    /** "Clear all local data": ideas, conversations, pending audio, settings and keys. */
    suspend fun clearAllData() {
        WorkManager.getInstance(context).cancelAllWork()
        repository.clearAll()
        pendingAudio.clear()
        File(context.cacheDir, "audio_chunks").deleteRecursively()
        settings.clearAll()
        NotificationManagerCompat.from(context).cancelAll()
    }
}

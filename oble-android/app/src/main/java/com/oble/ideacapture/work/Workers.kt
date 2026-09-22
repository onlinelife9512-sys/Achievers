package com.oble.ideacapture.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.oble.ideacapture.ObleApp
import com.oble.ideacapture.ai.AiException
import com.oble.ideacapture.data.WhatsAppStatus
import com.oble.ideacapture.service.Notifications
import java.util.concurrent.TimeUnit

/** Central place for enqueueing background retries. All work requires network. */
object WorkScheduler {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun enqueuePendingProcessing(context: Context) {
        val request = OneTimeWorkRequestBuilder<PendingProcessingWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("pending-processing", ExistingWorkPolicy.KEEP, request)
    }

    fun enqueueCloudSend(context: Context, ideaId: Long) {
        val request = OneTimeWorkRequestBuilder<CloudSendWorker>()
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInputData(workDataOf(CloudSendWorker.KEY_ID to ideaId))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("cloud-send-$ideaId", ExistingWorkPolicy.REPLACE, request)
    }
}

/**
 * Transcribes audio chunks that were recorded while offline, then analyses every
 * transcript segment that has not been analysed yet (offline, AI failure, crash).
 */
class PendingProcessingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as ObleApp).container
        val audioOk = try {
            container.pendingAudio.transcribeAll()
        } catch (e: AiException) {
            !e.retryable
        }
        val analysisOk = container.pipeline.processPending()
        return if (audioOk && analysisOk) Result.success()
        else if (runAttemptCount < 8) Result.retry() else Result.failure()
    }
}

/** Sends one note through the WhatsApp Cloud API with retry + backoff. */
class CloudSendWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as ObleApp).container
        val id = inputData.getLong(KEY_ID, -1)
        val idea = container.repository.getIdea(id) ?: return Result.success()
        if (idea.whatsappStatus == WhatsAppStatus.SENT && !idea.updatedSinceSent) return Result.success()

        val result = container.cloudApi.send(container.whatsApp.message(idea))
        return when {
            result.success -> {
                container.repository.updateWhatsApp(id, WhatsAppStatus.SENT, countAttempt = true)
                Result.success()
            }
            result.retryable && runAttemptCount < MAX_ATTEMPTS -> {
                container.repository.updateWhatsApp(id, WhatsAppStatus.QUEUED, result.error, countAttempt = true)
                Result.retry()
            }
            else -> {
                container.repository.updateWhatsApp(id, WhatsAppStatus.FAILED, result.error, countAttempt = true)
                // Fall back to the one-tap flow so the note still reaches WhatsApp.
                Notifications.showIdea(applicationContext, idea, sendable = true, failed = true)
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_ID = "idea_id"
        private const val MAX_ATTEMPTS = 6
    }
}

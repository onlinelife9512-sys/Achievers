package com.oble.ideacapture.whatsapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.oble.core.NoteType
import com.oble.core.WhatsAppFormatter
import com.oble.ideacapture.data.DeliveryMode
import com.oble.ideacapture.data.IdeaEntity
import com.oble.ideacapture.data.IdeaRepository
import com.oble.ideacapture.data.SettingsRepository
import com.oble.ideacapture.data.WhatsAppStatus
import com.oble.ideacapture.service.Notifications
import com.oble.ideacapture.work.WorkScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Delivers captured notes to the selected WhatsApp destination.
 *
 * ─── What Android / WhatsApp actually allow (read this) ─────────────────────────
 * • A normal (personal) WhatsApp account has NO public API that lets a third-party
 *   app send a message silently. The official mechanisms are:
 *     1. Click-to-chat links (https://wa.me/<number>?text=...) and the standard
 *        Android share intent. Both open WhatsApp with the message pre-filled; the
 *        user must press Send. OBLE uses this as "One-tap" mode: it prepares the
 *        message and shows a notification; one tap opens the chat, one tap sends.
 *     2. The WhatsApp Business Platform **Cloud API** (Meta). This is the only
 *        official way to send fully automatically. It needs a WhatsApp Business
 *        phone number, a permanent access token and, for messages outside a 24-hour
 *        customer-service window, a pre-approved message template. OBLE supports it
 *        as "Automatic" mode (see [CloudApiClient]).
 * • Android 10+ forbids apps from launching activities from the background, so
 *   OBLE cannot pop WhatsApp open by itself while you are talking; the tappable
 *   notification is the compliant substitute.
 * • OBLE deliberately does NOT use an AccessibilityService to press WhatsApp's
 *   Send button. That would violate Google Play policy and WhatsApp's terms.
 * • In One-tap mode OBLE cannot know whether you actually pressed Send in WhatsApp,
 *   so the status becomes "Opened in WhatsApp"; you can mark it as sent.
 * ───────────────────────────────────────────────────────────────────────────────
 */
class WhatsAppDispatcher(
    private val context: Context,
    private val repo: IdeaRepository,
    private val settings: SettingsRepository,
) {
    private val timeFormat = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

    enum class OpenResult { OPENED, OPENED_WITHOUT_DESTINATION, WHATSAPP_NOT_INSTALLED, FAILED }

    fun message(idea: IdeaEntity): String = WhatsAppFormatter.format(idea.toNote())

    private fun IdeaEntity.toNote() = WhatsAppFormatter.Note(
        type = NoteType.parse(type),
        title = title,
        summary = summary,
        category = category,
        status = status,
        actionItems = actionItems,
        time = timeFormat.format(Date(createdAt)),
    )

    /** A new note was confirmed by the pipeline. */
    suspend fun onNewIdea(idea: IdeaEntity) {
        val s = settings.current
        when {
            !s.autoPrepare || !s.hasDestination -> {
                Notifications.showIdea(context, idea, sendable = s.hasDestination)
            }
            s.deliveryMode == DeliveryMode.CLOUD_API && isCloudConfigured() -> {
                repo.updateWhatsApp(idea.id, WhatsAppStatus.QUEUED)
                WorkScheduler.enqueueCloudSend(context, idea.id)
                Notifications.showIdea(context, idea, sendable = false)
            }
            else -> {
                repo.updateWhatsApp(idea.id, WhatsAppStatus.READY)
                Notifications.showIdea(context, idea, sendable = true)
            }
        }
    }

    /** Points were merged into an existing note. Re-send only if the user wants updates. */
    suspend fun onIdeaMerged(idea: IdeaEntity, wasDelivered: Boolean) {
        val s = settings.current
        if (!wasDelivered || !s.sendMergedUpdates || !s.hasDestination) return
        if (s.deliveryMode == DeliveryMode.CLOUD_API && isCloudConfigured()) {
            repo.updateWhatsApp(idea.id, WhatsAppStatus.QUEUED)
            WorkScheduler.enqueueCloudSend(context, idea.id)
        } else {
            repo.updateWhatsApp(idea.id, WhatsAppStatus.READY)
            Notifications.showIdea(context, idea, sendable = true, updated = true)
        }
    }

    fun isCloudConfigured(): Boolean {
        val s = settings.current
        return s.waPhoneNumberId.isNotBlank() && !settings.waCloudToken().isNullOrBlank() &&
            (!s.waUseTemplate || s.waTemplateName.isNotBlank())
    }

    /** Package of the installed WhatsApp (personal preferred, then Business), or null. */
    fun whatsappPackage(): String? = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { pkg ->
        try {
            context.packageManager.getPackageInfo(pkg, 0); true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Opens WhatsApp with one or more notes pre-filled for the saved destination
     * (official click-to-chat). Must be called from a foreground UI context
     * (activity or notification tap). Marks the notes as "Opened in WhatsApp".
     */
    suspend fun openInWhatsApp(launcher: Context, ideas: List<IdeaEntity>): OpenResult {
        if (ideas.isEmpty()) return OpenResult.FAILED
        val text = WhatsAppFormatter.formatBatch(ideas.map { it.toNote() })
        val phone = WhatsAppFormatter.normalizePhone(settings.current.destinationPhone)
        val pkg = whatsappPackage()
        val result = try {
            if (phone != null) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WhatsAppFormatter.clickToChatUrl(phone, text)))
                if (pkg != null) intent.setPackage(pkg)
                launcher.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                OpenResult.OPENED
            } else {
                if (pkg == null) return OpenResult.WHATSAPP_NOT_INSTALLED
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text).setPackage(pkg)
                launcher.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                OpenResult.OPENED_WITHOUT_DESTINATION
            }
        } catch (_: ActivityNotFoundException) {
            if (pkg == null) OpenResult.WHATSAPP_NOT_INSTALLED else OpenResult.FAILED
        }
        if (result == OpenResult.OPENED || result == OpenResult.OPENED_WITHOUT_DESTINATION) {
            ideas.forEach {
                repo.updateWhatsApp(it.id, WhatsAppStatus.OPENED)
                Notifications.cancelIdea(context, it.id)
            }
        } else {
            ideas.forEach { repo.updateWhatsApp(it.id, WhatsAppStatus.FAILED, error = "WhatsApp is not available on this phone") }
        }
        return result
    }

    /** Generic Android share sheet (any app). */
    fun share(launcher: Context, idea: IdeaEntity) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, idea.title)
            .putExtra(Intent.EXTRA_TEXT, message(idea))
        launcher.startActivity(Intent.createChooser(intent, "Share idea").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Queues a note for automatic sending (Cloud API mode). */
    suspend fun queueCloudSend(idea: IdeaEntity) {
        repo.updateWhatsApp(idea.id, WhatsAppStatus.QUEUED)
        WorkScheduler.enqueueCloudSend(context, idea.id)
    }
}

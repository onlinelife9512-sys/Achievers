package com.oble.ideacapture.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.oble.core.NoteType
import com.oble.ideacapture.MainActivity
import com.oble.ideacapture.R
import com.oble.ideacapture.data.IdeaEntity
import com.oble.ideacapture.whatsapp.WhatsAppHandoffActivity

object Notifications {
    const val CHANNEL_LISTENING = "listening"
    const val CHANNEL_IDEAS = "ideas"
    const val LISTENING_ID = 1001
    private const val IDEA_BASE_ID = 20_000
    private const val GREEN = 0xFF3DDC84.toInt()

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LISTENING, "Idea Mode (microphone active)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown the whole time OBLE is listening. Cannot be hidden while the microphone is on."
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_IDEAS, "Captured ideas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New ideas, ready to send to WhatsApp with one tap."
            }
        )
    }

    private fun openApp(context: Context, route: String? = null): PendingIntent =
        PendingIntent.getActivity(
            context, route?.hashCode() ?: 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .apply { if (route != null) putExtra(MainActivity.EXTRA_ROUTE, route) },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun serviceAction(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            context, code,
            Intent(context, IdeaListeningService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun listening(context: Context, state: ListeningState) =
        NotificationCompat.Builder(context, CHANNEL_LISTENING)
            .setSmallIcon(R.drawable.ic_stat_listening)
            .setColor(GREEN)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context))
            .setUsesChronometer(state.startedAt != null)
            .setWhen(state.startedAt ?: System.currentTimeMillis())
            .apply {
                if (state.status == ListeningStatus.PAUSED) {
                    setContentTitle("Idea Mode paused")
                    setContentText("Microphone is OFF · ${state.ideasThisSession} ideas this session")
                    addAction(0, "Resume", serviceAction(context, IdeaListeningService.ACTION_RESUME, 2))
                } else {
                    setContentTitle("● Listening for ideas")
                    setContentText("Microphone is ON · ${state.ideasThisSession} ideas this session")
                    addAction(0, "Pause", serviceAction(context, IdeaListeningService.ACTION_PAUSE, 1))
                }
                addAction(0, "Stop", serviceAction(context, IdeaListeningService.ACTION_STOP, 3))
            }
            .build()

    fun showIdea(
        context: Context,
        idea: IdeaEntity,
        sendable: Boolean,
        updated: Boolean = false,
        failed: Boolean = false,
    ) {
        if (!canNotify(context)) return
        val type = NoteType.parse(idea.type)
        val title = when {
            failed -> "Automatic send failed · ${idea.title}"
            updated -> "Idea updated · ${idea.title}"
            else -> "${type.emoji} ${idea.title}"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_IDEAS)
            .setSmallIcon(R.drawable.ic_stat_idea)
            .setColor(GREEN)
            .setContentTitle(title)
            .setContentText(idea.summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(idea.summary))
            .setAutoCancel(true)
            .setGroup("oble_ideas")
            .setContentIntent(openApp(context, "ideas"))
        if (sendable) {
            val send = PendingIntent.getActivity(
                context, IDEA_BASE_ID + idea.id.toInt(),
                WhatsAppHandoffActivity.intent(context, longArrayOf(idea.id)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, "Send to WhatsApp", send)
        }
        try {
            NotificationManagerCompat.from(context).notify(IDEA_BASE_ID + idea.id.toInt(), builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelIdea(context: Context, id: Long) =
        NotificationManagerCompat.from(context).cancel(IDEA_BASE_ID + id.toInt())

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
}

package com.oble.ideacapture.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oble.core.NoteType
import com.oble.ideacapture.data.IdeaEntity
import com.oble.ideacapture.data.WhatsAppStatus
import com.oble.ideacapture.ui.theme.Oble
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val cardTime = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

fun formatTime(ms: Long): String = cardTime.format(Date(ms))

fun WhatsAppStatus.color(): Color = when (this) {
    WhatsAppStatus.SENT -> Oble.Green
    WhatsAppStatus.OPENED -> Oble.Green.copy(alpha = 0.75f)
    WhatsAppStatus.READY, WhatsAppStatus.QUEUED -> Oble.Amber
    WhatsAppStatus.FAILED -> Oble.Red
    WhatsAppStatus.NOT_SENT -> Oble.TextFaint
}

data class IdeaActions(
    val onCopy: (IdeaEntity) -> Unit,
    val onShare: (IdeaEntity) -> Unit,
    val onWhatsApp: (IdeaEntity) -> Unit,
    val onEdit: (IdeaEntity) -> Unit,
    val onDelete: (IdeaEntity) -> Unit,
    val onMarkSent: (IdeaEntity) -> Unit,
)

@Composable
fun IdeaCard(idea: IdeaEntity, actions: IdeaActions?, compact: Boolean = false, onClick: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(!compact) }
    val type = NoteType.parse(idea.type)
    Surface(
        Modifier
            .animateContentSize()
            .clickable { if (onClick != null) onClick() else expanded = !expanded },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${type.emoji}  ${type.label}", style = MaterialTheme.typography.labelSmall, color = Oble.Green)
            Spacer(Modifier.weight(1f))
            Text(formatTime(idea.createdAt), style = MaterialTheme.typography.bodySmall, color = Oble.TextFaint)
        }
        Spacer(Modifier.height(10.dp))
        Text(idea.title, style = MaterialTheme.typography.titleMedium, color = Oble.Text)
        Spacer(Modifier.height(4.dp))
        Text(
            idea.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = Oble.TextMuted,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (expanded && idea.actionItems.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            idea.actionItems.forEach {
                Text("•  $it", style = MaterialTheme.typography.bodyMedium, color = Oble.Text.copy(alpha = 0.85f), modifier = Modifier.padding(vertical = 1.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(idea.category)
            if (idea.mergeCount > 0) Tag("Merged ${idea.mergeCount}×", Oble.Green)
            if (idea.mentionCount > 1) Tag("Mentioned ${idea.mentionCount}×")
            Spacer(Modifier.weight(1f))
            StatusDot(idea.whatsappStatus.color(), pulsing = idea.whatsappStatus == WhatsAppStatus.QUEUED, size = 6.dp)
            Spacer(Modifier.width(2.dp))
            Text(
                if (idea.updatedSinceSent) "Updated since sent" else idea.whatsappStatus.label,
                style = MaterialTheme.typography.bodySmall,
                color = idea.whatsappStatus.color(),
            )
        }
        if (expanded && idea.whatsappStatus == WhatsAppStatus.FAILED && !idea.whatsappError.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(idea.whatsappError, style = MaterialTheme.typography.bodySmall, color = Oble.Red.copy(alpha = 0.8f))
        }
        if (actions != null && expanded) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CardAction(Icons.Outlined.ContentCopy, "Copy") { actions.onCopy(idea) }
                CardAction(Icons.Outlined.Share, "Share") { actions.onShare(idea) }
                CardAction(Icons.AutoMirrored.Outlined.Send, "Send to WhatsApp", tint = Oble.Green) { actions.onWhatsApp(idea) }
                if (idea.whatsappStatus == WhatsAppStatus.OPENED) {
                    CardAction(Icons.Outlined.DoneAll, "Mark as sent") { actions.onMarkSent(idea) }
                }
                CardAction(Icons.Outlined.Edit, "Edit") { actions.onEdit(idea) }
                CardAction(Icons.Outlined.DeleteOutline, "Delete") { actions.onDelete(idea) }
            }
        }
    }
}

@Composable
private fun CardAction(icon: ImageVector, label: String, tint: Color = Oble.TextMuted, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        }
    }
}

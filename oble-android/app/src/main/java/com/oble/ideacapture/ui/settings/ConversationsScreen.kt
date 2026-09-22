package com.oble.ideacapture.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oble.core.SpokenLanguage
import com.oble.ideacapture.data.SessionSummary
import com.oble.ideacapture.ui.components.Surface
import com.oble.ideacapture.ui.components.Tag
import com.oble.ideacapture.ui.components.formatTime
import com.oble.ideacapture.ui.theme.Oble

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(Modifier.padding(top = 20.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Oble.Text) }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun ConversationsScreen(vm: SettingsViewModel, onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<SessionSummary?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("Conversations", onBack)
        Text(
            "Transcripts only — raw audio is never stored. Deleting a conversation keeps the ideas captured from it.",
            style = MaterialTheme.typography.bodySmall, color = Oble.TextFaint, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        if (sessions.isEmpty()) Text("No conversations yet.", color = Oble.TextFaint, modifier = Modifier.padding(6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(sessions, key = { it.id }) { s ->
                Surface(Modifier.clickable { onOpen(s.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(formatTime(s.startedAt), style = MaterialTheme.typography.titleMedium)
                            val mins = ((s.endedAt ?: s.startedAt) - s.startedAt) / 60_000
                            Text(
                                "${mins} min · ${s.segmentCount} segments · ${if (s.engine == "gemini") "Gemini audio" else "Android recognizer"}",
                                style = MaterialTheme.typography.bodySmall, color = Oble.TextMuted,
                            )
                        }
                        IconButton(onClick = { deleting = s }) { Icon(Icons.Outlined.DeleteOutline, "Delete conversation", tint = Oble.TextMuted) }
                    }
                }
            }
        }
    }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Oble.Charcoal,
            title = { Text("Delete conversation?") },
            text = { Text("The transcript from ${formatTime(s.startedAt)} will be permanently deleted.", color = Oble.TextMuted) },
            confirmButton = { TextButton(onClick = { vm.deleteSession(s.id); deleting = null }) { Text("Delete", color = Oble.Red) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = Oble.TextMuted) } },
        )
    }
}

@Composable
fun TranscriptScreen(vm: SettingsViewModel, sessionId: Long, onBack: () -> Unit) {
    val flow = remember(sessionId) { vm.transcript(sessionId) }
    val segments by flow.collectAsState(initial = emptyList())
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header("Transcript", onBack)
        if (segments.isEmpty()) Text("No transcript stored for this conversation.", color = Oble.TextFaint, modifier = Modifier.padding(6.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(segments, key = { it.id }) { seg ->
                Surface(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Tag(SpokenLanguage.fromCode(seg.language).label)
                        Spacer(Modifier.weight(1f))
                        Text(formatTime(seg.timestamp), style = MaterialTheme.typography.bodySmall, color = Oble.TextFaint)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(seg.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

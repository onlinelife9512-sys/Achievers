package com.oble.ideacapture.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oble.ideacapture.data.AppSettings
import com.oble.ideacapture.data.SpeechEngineType
import com.oble.ideacapture.service.ListeningStatus
import com.oble.ideacapture.ui.components.IdeaCard
import com.oble.ideacapture.ui.components.SectionLabel
import com.oble.ideacapture.ui.components.StatusPill
import com.oble.ideacapture.ui.components.Surface
import com.oble.ideacapture.ui.components.Waveform
import com.oble.ideacapture.ui.theme.Oble
import com.oble.ideacapture.whatsapp.WhatsAppDispatcher

@Composable
fun HomeScreen(vm: HomeViewModel, onViewIdeas: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val state by vm.listening.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val today by vm.todayCount.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val unsent by vm.unsent.collectAsStateWithLifecycle()
    val pending by vm.pendingAnalysis.collectAsStateWithLifecycle()
    val analyzing by vm.analyzing.collectAsStateWithLifecycle()
    val failure by vm.lastFailure.collectAsStateWithLifecycle()

    var showConsent by rememberSaveable { mutableStateOf(false) }
    var showPermissionHelp by rememberSaveable { mutableStateOf(false) }

    val permissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.toTypedArray()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) showConsent = true
        else showPermissionHelp = true
    }

    fun requestStart() {
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!micGranted) permissionLauncher.launch(permissions)
        else if (settings.askConsentEachTime || !consentGiven(context)) showConsent = true
        else vm.start(context)
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(top = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("OBLE", style = MaterialTheme.typography.displayLarge, color = Oble.Text)
                Spacer(Modifier.height(2.dp))
                Text("Idea Capture Assistant", style = MaterialTheme.typography.bodyMedium, color = Oble.TextMuted)
                Spacer(Modifier.height(22.dp))
                val (label, color, pulsing) = when (state.status) {
                    ListeningStatus.IDLE -> Triple("Ready", Oble.TextMuted, false)
                    ListeningStatus.STARTING -> Triple("Starting…", Oble.Amber, true)
                    ListeningStatus.LISTENING -> Triple("Listening for Ideas", Oble.Green, true)
                    ListeningStatus.PAUSED -> Triple("Paused · microphone off", Oble.Amber, false)
                    ListeningStatus.STOPPING -> Triple("Finishing…", Oble.TextMuted, true)
                }
                AnimatedContent(label, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "status") {
                    StatusPill(it, color, pulsing)
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(state.running) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Waveform(state.level, active = state.status == ListeningStatus.LISTENING)
                        Spacer(Modifier.height(12.dp))
                        val heard = state.partial.ifBlank { state.lastHeard }
                        Text(
                            heard.ifBlank { state.engineLabel },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (heard.isBlank()) Oble.TextFaint else Oble.TextMuted,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        Spacer(Modifier.height(18.dp))
                    }
                }
                if (!state.running) {
                    Button(
                        onClick = { requestStart() },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Oble.Green, contentColor = Oble.Black),
                    ) {
                        Text("START IDEA MODE", style = MaterialTheme.typography.labelLarge, fontSize = 15.sp)
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val paused = state.status == ListeningStatus.PAUSED
                        OutlinedButton(
                            onClick = { if (paused) vm.resume(context) else vm.pause(context) },
                            modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Oble.Line),
                        ) {
                            Icon(if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null, tint = Oble.Text)
                            Spacer(Modifier.width(8.dp))
                            Text(if (paused) "Resume" else "Pause", color = Oble.Text)
                        }
                        Button(
                            onClick = { vm.stop(context) },
                            modifier = Modifier.weight(1f).height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Oble.Raised, contentColor = Oble.Red),
                        ) {
                            Icon(Icons.Outlined.Stop, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop")
                        }
                    }
                }
                val info = state.error ?: state.message ?: failure?.takeIf { state.running }
                if (info != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(info, style = MaterialTheme.typography.bodySmall,
                        color = if (state.error != null) Oble.Red else Oble.Amber, textAlign = TextAlign.Center)
                }
                if (analyzing) {
                    Spacer(Modifier.height(8.dp))
                    Text("Understanding conversation…", style = MaterialTheme.typography.bodySmall, color = Oble.TextFaint)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(Modifier.weight(1f)) {
                    Text("Today's Ideas", style = MaterialTheme.typography.bodySmall, color = Oble.TextMuted)
                    Spacer(Modifier.height(6.dp))
                    Text("$today", style = MaterialTheme.typography.headlineSmall, color = Oble.Text)
                }
                Surface(Modifier.weight(1f).clickable { onViewIdeas() }) {
                    Text("View Ideas", style = MaterialTheme.typography.bodySmall, color = Oble.TextMuted)
                    Spacer(Modifier.height(6.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, "View ideas", tint = Oble.Green)
                }
            }
        }

        setupHints(settings, pending, onOpenSettings)

        if (unsent.isNotEmpty()) item {
            Surface {
                Text("${unsent.size} ${if (unsent.size == 1) "note" else "notes"} ready for WhatsApp", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (settings.hasDestination) "Send to ${settings.destinationName.ifBlank { "+" + settings.destinationPhone }} in one message."
                    else "Choose a destination in Settings, or pick a chat in WhatsApp.",
                    style = MaterialTheme.typography.bodySmall, color = Oble.TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        vm.sendAll(context, unsent) { r ->
                            if (r == WhatsAppDispatcher.OpenResult.WHATSAPP_NOT_INSTALLED)
                                Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Oble.GreenDeep, contentColor = Oble.Green),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Send all to WhatsApp") }
            }
        }

        item { SectionLabel("Recent Ideas", Modifier.padding(top = 8.dp)) }
        if (recent.isEmpty()) item {
            Text(
                "Ideas you discuss will appear here — only the useful parts, never every sentence.",
                style = MaterialTheme.typography.bodyMedium, color = Oble.TextFaint,
            )
        }
        items(recent, key = { it.id }) { IdeaCard(it, actions = null, compact = true, onClick = onViewIdeas) }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showConsent) {
        ConsentDialog(
            settings = settings,
            onConfirm = {
                showConsent = false
                rememberConsent(context)
                vm.start(context)
            },
            onDismiss = { showConsent = false },
        )
    }
    if (showPermissionHelp) {
        AlertDialog(
            onDismissRequest = { showPermissionHelp = false },
            title = { Text("Microphone permission needed") },
            text = { Text("OBLE only uses the microphone while Idea Mode is on. Allow microphone access in Android settings to continue.") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionHelp = false
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { showPermissionHelp = false }) { Text("Not now") } },
            containerColor = Oble.Charcoal,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.setupHints(settings: AppSettings, pending: Int, onOpenSettings: () -> Unit) {
    val hints = buildList {
        if (!settings.hasGeminiKey) add("Add your Gemini API key to detect ideas" to "AI is not configured")
        if (!settings.hasDestination) add("Choose the WhatsApp contact for your notes" to "No WhatsApp destination")
        if (pending > 0 && settings.hasGeminiKey) add("$pending speech segments waiting — they'll be analysed when online" to "Queued")
    }
    hints.forEach { (text, title) ->
        item {
            Surface(Modifier.clickable { onOpenSettings() }) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = Oble.Amber)
                Spacer(Modifier.height(4.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = Oble.TextMuted)
            }
        }
    }
}

@Composable
private fun ConsentDialog(settings: AppSettings, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var agreed by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Oble.Charcoal,
        title = { Text("Everyone knows OBLE is listening?") },
        text = {
            Column {
                Text(
                    "Idea Mode turns the microphone on until you press Stop. A notification stays visible the whole time.",
                    style = MaterialTheme.typography.bodyMedium, color = Oble.TextMuted,
                )
                Spacer(Modifier.height(10.dp))
                val how = if (settings.speechEngine == SpeechEngineType.GEMINI_AUDIO && settings.hasGeminiKey)
                    "Speech is sent to Google Gemini in short temporary clips for transcription; clips are deleted right after. No recording is kept."
                else "Speech is transcribed by the phone's speech recognizer. No recording is kept."
                Text(how, style = MaterialTheme.typography.bodyMedium, color = Oble.TextMuted)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Please tell everyone in the conversation before you start.",
                    style = MaterialTheme.typography.bodyMedium, color = Oble.Text,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { agreed = !agreed }) {
                    Checkbox(agreed, { agreed = it }, colors = CheckboxDefaults.colors(checkedColor = Oble.Green, checkmarkColor = Oble.Black))
                    Text("Everyone here has agreed", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm, enabled = agreed,
                colors = ButtonDefaults.buttonColors(containerColor = Oble.Green, contentColor = Oble.Black),
            ) { Text("Start listening") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Oble.TextMuted) } },
    )
}

private fun consentGiven(context: android.content.Context) =
    context.getSharedPreferences("oble_consent", 0).getBoolean("given", false)

private fun rememberConsent(context: android.content.Context) =
    context.getSharedPreferences("oble_consent", 0).edit().putBoolean("given", true).apply()

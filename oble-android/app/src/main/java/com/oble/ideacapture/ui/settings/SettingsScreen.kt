package com.oble.ideacapture.ui.settings

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oble.ideacapture.data.DeliveryMode
import com.oble.ideacapture.data.SpeechEngineType
import com.oble.ideacapture.speech.AndroidSpeechEngine
import com.oble.ideacapture.ui.components.SectionLabel
import com.oble.ideacapture.ui.components.Surface
import com.oble.ideacapture.ui.ideas.obleFieldColors
import com.oble.ideacapture.ui.theme.Oble
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(vm: SettingsViewModel, onOpenConversations: () -> Unit) {
    val context = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    var phoneInput by remember(s.destinationPhone) { mutableStateOf(if (s.destinationPhone.isBlank()) "" else "+" + s.destinationPhone) }
    var nameInput by remember(s.destinationName) { mutableStateOf(s.destinationName) }
    var keyInput by remember { mutableStateOf("") }
    var tokenInput by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    // Picking from the phone-number list grants temporary read access to just that
    // contact — no READ_CONTACTS permission needed.
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val number = cursor.getString(0).orEmpty()
                val name = cursor.getString(1).orEmpty()
                if (vm.setDestination(name, number)) toast("WhatsApp destination: $name")
                else toast("That number needs a country code, e.g. +91…")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 28.dp, bottom = 4.dp))

        // ─── WhatsApp ───
        SectionLabel("WhatsApp destination")
        Surface {
            Text(
                if (s.hasDestination) "${s.destinationName.ifBlank { "Saved number" }} · +${s.destinationPhone}" else "No destination selected",
                style = MaterialTheme.typography.titleMedium,
                color = if (s.hasDestination) Oble.Text else Oble.TextMuted,
            )
            Spacer(Modifier.height(4.dp))
            Text("Tip: use your own number to save notes into WhatsApp's “Message yourself” chat.", style = MaterialTheme.typography.bodySmall, color = Oble.TextFaint)
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = {
                    pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                },
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Outlined.Contacts, null, tint = Oble.Green)
                Spacer(Modifier.width(8.dp))
                Text("Pick from contacts", color = Oble.Text)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(nameInput, { nameInput = it }, label = { Text("Name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = obleFieldColors())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                phoneInput, { phoneInput = it }, label = { Text("WhatsApp number with country code") },
                placeholder = { Text("+91 98765 43210") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), colors = obleFieldColors(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("Save") {
                    if (vm.setDestination(nameInput, phoneInput)) toast("Saved") else toast("Enter a valid number with country code")
                }
                if (s.hasDestination) TextButton(onClick = { vm.clearDestination() }) { Text("Remove", color = Oble.TextMuted) }
            }
            if (!vm.whatsappInstalled()) {
                Spacer(Modifier.height(8.dp))
                Text("WhatsApp is not installed on this phone. Notes can still be shared with any app.", style = MaterialTheme.typography.bodySmall, color = Oble.Amber)
            }
        }

        SectionLabel("Delivery")
        Surface {
            DeliveryMode.entries.forEach { mode ->
                RadioRow(mode.label, mode.description, s.deliveryMode == mode) { vm.update { it.copy(deliveryMode = mode) } }
            }
            HorizontalDivider(color = Oble.Line, modifier = Modifier.padding(vertical = 8.dp))
            SwitchRow("Prepare every new idea automatically", "Queue it for WhatsApp as soon as it's detected", s.autoPrepare) { v -> vm.update { it.copy(autoPrepare = v) } }
            SwitchRow("Re-send merged updates", "When new points extend an already-sent idea", s.sendMergedUpdates) { v -> vm.update { it.copy(sendMergedUpdates = v) } }
        }

        if (s.deliveryMode == DeliveryMode.CLOUD_API) {
            SectionLabel("WhatsApp Cloud API (official, automatic)")
            Surface {
                Text(
                    "Requires a WhatsApp Business Platform account (Meta). Your personal WhatsApp cannot send automatically — this is Meta's rule, not an OBLE limit.",
                    style = MaterialTheme.typography.bodySmall, color = Oble.TextMuted,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(s.waPhoneNumberId, { v -> vm.update { it.copy(waPhoneNumberId = v) } }, label = { Text("Phone number ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = obleFieldColors())
                Spacer(Modifier.height(8.dp))
                SecretField(
                    value = tokenInput, onValue = { tokenInput = it },
                    label = if (s.hasWaToken) "Access token (saved · encrypted)" else "Permanent access token",
                    onSave = { vm.setWaToken(tokenInput); tokenInput = ""; toast("Token saved securely") },
                    onClear = if (s.hasWaToken) ({ vm.setWaToken("") }) else null,
                )
                Spacer(Modifier.height(8.dp))
                SwitchRow("Use approved template", "Required outside WhatsApp's 24-hour service window", s.waUseTemplate) { v -> vm.update { it.copy(waUseTemplate = v) } }
                if (s.waUseTemplate) {
                    OutlinedTextField(s.waTemplateName, { v -> vm.update { it.copy(waTemplateName = v) } }, label = { Text("Template name (1 body variable)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = obleFieldColors())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(s.waTemplateLanguage, { v -> vm.update { it.copy(waTemplateLanguage = v) } }, label = { Text("Template language code") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = obleFieldColors())
                }
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Send test message", enabled = vm.waCloudReady() && s.hasDestination) { vm.sendTest { toast(it) } }
            }
        }

        // ─── AI ───
        SectionLabel("AI · Gemini")
        Surface {
            SecretField(
                value = keyInput, onValue = { keyInput = it },
                label = if (s.hasGeminiKey) "API key (saved · encrypted)" else "Gemini API key",
                onSave = { vm.setGeminiKey(keyInput); keyInput = ""; toast("Key saved securely") },
                onClear = if (s.hasGeminiKey) ({ vm.setGeminiKey("") }) else null,
            )
            Spacer(Modifier.height(4.dp))
            Text("Get a key at aistudio.google.com. Stored with Android Keystore encryption, never in the app code.", style = MaterialTheme.typography.bodySmall, color = Oble.TextFaint)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(s.geminiModel, { v -> vm.update { it.copy(geminiModel = v) } }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = obleFieldColors())
            Spacer(Modifier.height(12.dp))
            SliderRow("Idea confidence threshold", "${(s.confidenceThreshold * 100).roundToInt()}%", s.confidenceThreshold, 0.4f..0.95f) { v -> vm.update { it.copy(confidenceThreshold = v) } }
            Text("Higher = fewer, stronger ideas. Random conversation scores low and is ignored.", style = MaterialTheme.typography.bodySmall, color = Oble.TextFaint)
            Spacer(Modifier.height(8.dp))
            SliderRow("Analyse conversation every", "${s.analysisIntervalSec}s", s.analysisIntervalSec.toFloat(), 20f..180f) { v -> vm.update { it.copy(analysisIntervalSec = v.roundToInt()) } }
        }

        // ─── Speech ───
        SectionLabel("Speech")
        Surface {
            SpeechEngineType.entries.forEach { e ->
                RadioRow(e.label, e.description, s.speechEngine == e) { vm.update { it.copy(speechEngine = e) } }
            }
            HorizontalDivider(color = Oble.Line, modifier = Modifier.padding(vertical = 8.dp))
            if (s.speechEngine == SpeechEngineType.ANDROID) {
                Text("Recognizer language", style = MaterialTheme.typography.titleMedium)
                listOf("gu-IN", "hi-IN", "en-IN", "auto").forEach { tag ->
                    RadioRow(AndroidSpeechEngine.languageLabel(tag), if (tag == "auto") "Android 14+: switches between Gujarati, Hindi and English" else null, s.recognizerLanguage == tag) {
                        vm.update { it.copy(recognizerLanguage = tag) }
                    }
                }
            } else {
                SliderRow("Max clip length", "${s.chunkSeconds}s", s.chunkSeconds.toFloat(), 10f..60f) { v -> vm.update { it.copy(chunkSeconds = v.roundToInt()) } }
                Text("Clips are cut at natural pauses and deleted right after transcription. Silent clips are never uploaded.", style = MaterialTheme.typography.bodySmall, color = Oble.TextFaint)
            }
        }

        // ─── Privacy ───
        SectionLabel("Privacy")
        Surface {
            SwitchRow("Ask for consent every session", "Confirm everyone agreed before the mic turns on", s.askConsentEachTime) { v -> vm.update { it.copy(askConsentEachTime = v) } }
            SwitchRow("Keep conversation transcripts", "Off = transcripts are deleted after ideas are extracted", s.keepTranscripts) { v -> vm.update { it.copy(keepTranscripts = v) } }
            HorizontalDivider(color = Oble.Line, modifier = Modifier.padding(vertical = 8.dp))
            Row(
                Modifier.fillMaxWidth().clickable { onOpenConversations() }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Conversations", style = MaterialTheme.typography.titleMedium)
                    Text("View or delete transcripts", style = MaterialTheme.typography.bodySmall, color = Oble.TextMuted)
                }
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Oble.TextFaint)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { confirmClear = true }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Clear all local data", color = Oble.Red)
            }
        }

        SectionLabel("What's automatic")
        Surface {
            Text(LIMITATIONS, style = MaterialTheme.typography.bodySmall, color = Oble.TextMuted)
        }
        Spacer(Modifier.height(32.dp))
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = Oble.Charcoal,
            title = { Text("Clear all local data?") },
            text = { Text("Stops the microphone and permanently deletes all ideas, conversations, queued audio, settings and saved keys on this phone.", color = Oble.TextMuted) },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearAll(context) { toast("All local data cleared") } }) { Text("Delete everything", color = Oble.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = Oble.TextMuted) } },
        )
    }
}

private const val LIMITATIONS =
    "Automatic: listening while Idea Mode is on, transcription, language handling, idea detection, merging, duplicate removal, saving, and preparing the WhatsApp message.\n\n" +
        "One tap: sending through your personal WhatsApp. Android and WhatsApp don't allow apps to press Send for you, so OBLE opens the chat with the note ready.\n\n" +
        "Fully automatic sending: only with the official WhatsApp Cloud API, which needs a Business number set up in Meta."

@Composable
private fun PrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Oble.GreenDeep, contentColor = Oble.Green),
    ) { Text(text) }
}

@Composable
private fun SecretField(value: String, onValue: (String) -> Unit, label: String, onSave: () -> Unit, onClear: (() -> Unit)?) {
    OutlinedTextField(
        value, onValue, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), colors = obleFieldColors(),
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrimaryButton("Save", enabled = value.isNotBlank(), onClick = onSave)
        if (onClear != null) TextButton(onClick = onClear) { Text("Remove", color = Oble.TextMuted) }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Oble.TextMuted)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Oble.Black, checkedTrackColor = Oble.Green,
                uncheckedThumbColor = Oble.TextMuted, uncheckedTrackColor = Oble.Raised, uncheckedBorderColor = Oble.Line,
            ),
        )
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        RadioButton(selected, onClick, colors = RadioButtonDefaults.colors(selectedColor = Oble.Green, unselectedColor = Oble.TextFaint))
        Column(Modifier.weight(1f).padding(top = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Oble.TextMuted)
        }
    }
}

@Composable
private fun SliderRow(title: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.labelMedium, color = Oble.Green)
    }
    Slider(
        current, onChange, valueRange = range,
        colors = SliderDefaults.colors(thumbColor = Oble.Green, activeTrackColor = Oble.Green, inactiveTrackColor = Oble.Raised),
    )
}

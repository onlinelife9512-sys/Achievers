package com.oble.ideacapture.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oble.core.WhatsAppFormatter
import com.oble.ideacapture.AppContainer
import com.oble.ideacapture.data.AppSettings
import com.oble.ideacapture.service.IdeaListeningService
import com.oble.ideacapture.service.ListeningStateHolder
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings
    val sessions = c.repository.observeSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun update(transform: (AppSettings) -> AppSettings) = c.settings.update(transform)

    /** Returns false when the number is not a valid international number. */
    fun setDestination(name: String, phone: String): Boolean {
        val normalized = WhatsAppFormatter.normalizePhone(phone) ?: return false
        c.settings.update { it.copy(destinationName = name.trim(), destinationPhone = normalized) }
        return true
    }

    fun clearDestination() = c.settings.update { it.copy(destinationName = "", destinationPhone = "") }
    fun setGeminiKey(key: String) = c.settings.setGeminiApiKey(key)
    fun setWaToken(token: String) = c.settings.setWaCloudToken(token)
    fun waCloudReady() = c.whatsApp.isCloudConfigured()
    fun whatsappInstalled() = c.whatsApp.whatsappPackage() != null

    fun sendTest(onResult: (String) -> Unit) = viewModelScope.launch {
        val r = c.cloudApi.send("✅ OBLE test message — WhatsApp Cloud API is connected.")
        onResult(if (r.success) "Test message sent." else r.error ?: "Failed")
    }

    fun deleteSession(id: Long) = viewModelScope.launch { c.repository.deleteSession(id) }

    fun clearAll(context: Context, done: () -> Unit) {
        if (ListeningStateHolder.state.value.running) {
            IdeaListeningService.send(context, IdeaListeningService.ACTION_STOP)
        }
        viewModelScope.launch {
            c.clearAllData()
            context.getSharedPreferences("oble_consent", 0).edit().clear().apply()
            done()
        }
    }

    fun transcript(sessionId: Long) = c.repository.observeTranscript(sessionId)
}

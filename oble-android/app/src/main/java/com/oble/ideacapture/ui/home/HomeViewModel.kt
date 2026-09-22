package com.oble.ideacapture.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oble.ideacapture.AppContainer
import com.oble.ideacapture.ai.IdeaPipeline
import com.oble.ideacapture.data.AppSettings
import com.oble.ideacapture.data.IdeaEntity
import com.oble.ideacapture.service.IdeaListeningService
import com.oble.ideacapture.service.ListeningState
import com.oble.ideacapture.service.ListeningStateHolder
import com.oble.ideacapture.whatsapp.WhatsAppDispatcher
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    val listening: StateFlow<ListeningState> = ListeningStateHolder.state
    val settings: StateFlow<AppSettings> = c.settings.settings
    val todayCount = c.repository.observeTodayCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val recent = c.repository.observeIdeas("").map { it.take(4) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val unsent = c.repository.observeUnsent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val pendingAnalysis = c.repository.observePendingAnalysis().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val analyzing = c.pipeline.analyzing
    val lastFailure = c.pipeline.lastEvent.map { (it as? IdeaPipeline.Event.Failed)?.message }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun start(context: Context) = IdeaListeningService.start(context, consent = true)
    fun pause(context: Context) = IdeaListeningService.send(context, IdeaListeningService.ACTION_PAUSE)
    fun resume(context: Context) = IdeaListeningService.send(context, IdeaListeningService.ACTION_RESUME)
    fun stop(context: Context) = IdeaListeningService.send(context, IdeaListeningService.ACTION_STOP)

    fun sendAll(context: Context, ideas: List<IdeaEntity>, onResult: (WhatsAppDispatcher.OpenResult) -> Unit) {
        viewModelScope.launch { onResult(c.whatsApp.openInWhatsApp(context, ideas)) }
    }

    fun clearError() = ListeningStateHolder.update { it.copy(error = null) }
}

package com.oble.ideacapture.ui.ideas

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oble.ideacapture.AppContainer
import com.oble.ideacapture.data.DeliveryMode
import com.oble.ideacapture.data.IdeaEntity
import com.oble.ideacapture.data.WhatsAppStatus
import com.oble.ideacapture.whatsapp.WhatsAppDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class IdeaFilter(val label: String) { ALL("All"), UNSENT("Not sent"), SENT("Sent") }

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class IdeasViewModel(private val c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val filter = MutableStateFlow(IdeaFilter.ALL)

    val ideas = combine(
        query.debounce(150).flatMapLatest { c.repository.observeIdeas(it) },
        filter,
    ) { list, f ->
        when (f) {
            IdeaFilter.ALL -> list
            IdeaFilter.UNSENT -> list.filter { it.whatsappStatus != WhatsAppStatus.SENT && it.whatsappStatus != WhatsAppStatus.OPENED }
            IdeaFilter.SENT -> list.filter { it.whatsappStatus == WhatsAppStatus.SENT || it.whatsappStatus == WhatsAppStatus.OPENED }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun copy(context: Context, idea: IdeaEntity) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText(idea.title, c.whatsApp.message(idea)))
    }

    fun share(context: Context, idea: IdeaEntity) = c.whatsApp.share(context, idea)

    /** Cloud API mode sends automatically; otherwise opens WhatsApp pre-filled. */
    fun sendToWhatsApp(context: Context, idea: IdeaEntity, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            if (c.settings.current.deliveryMode == DeliveryMode.CLOUD_API && c.whatsApp.isCloudConfigured()) {
                c.whatsApp.queueCloudSend(idea)
                onResult("Sending automatically via WhatsApp Cloud API…")
            } else {
                val r = c.whatsApp.openInWhatsApp(context, listOf(idea))
                onResult(if (r == WhatsAppDispatcher.OpenResult.WHATSAPP_NOT_INSTALLED) "WhatsApp is not installed — use Share instead." else null)
            }
        }
    }

    fun markSent(idea: IdeaEntity) = viewModelScope.launch {
        c.repository.updateWhatsApp(idea.id, WhatsAppStatus.SENT)
    }

    fun save(idea: IdeaEntity) = viewModelScope.launch {
        c.repository.updateIdea(idea.copy(updatedAt = System.currentTimeMillis(), embedding = null).let {
            // Recompute the embedding so duplicate detection reflects the edit.
            val emb = c.gemini.embed("${it.title}. ${it.summary}")
            it.copy(embedding = emb?.let(com.oble.core.TextSimilarity::encodeVector))
        })
    }

    fun delete(idea: IdeaEntity) = viewModelScope.launch { c.repository.deleteIdea(idea.id) }
}

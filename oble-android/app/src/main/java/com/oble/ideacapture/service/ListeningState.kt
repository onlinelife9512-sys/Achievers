package com.oble.ideacapture.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ListeningStatus { IDLE, STARTING, LISTENING, PAUSED, STOPPING }

data class ListeningState(
    val status: ListeningStatus = ListeningStatus.IDLE,
    val sessionId: Long? = null,
    val startedAt: Long? = null,
    val engineLabel: String = "",
    val level: Float = 0f,
    val partial: String = "",
    val lastHeard: String = "",
    val message: String? = null,
    val error: String? = null,
    val ideasThisSession: Int = 0,
) {
    val micActive: Boolean get() = status == ListeningStatus.LISTENING || status == ListeningStatus.STARTING
    val running: Boolean get() = status != ListeningStatus.IDLE
}

/** Process-wide listening state observed by the UI and updated by the service. */
object ListeningStateHolder {
    private val _state = MutableStateFlow(ListeningState())
    val state: StateFlow<ListeningState> = _state.asStateFlow()

    fun update(transform: (ListeningState) -> ListeningState) = _state.update(transform)
    fun reset(error: String? = null) { _state.value = ListeningState(error = error) }
}

package com.naaammme.bbspace.feature.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.player.PlayerSettings
import com.naaammme.bbspace.core.domain.player.StreamPlaybackSession
import com.naaammme.bbspace.core.model.LivePlaybackError
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.LiveRouteTool
import com.naaammme.bbspace.core.model.PlayerSettingsState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class LiveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playbackSession: StreamPlaybackSession,
    playerSettings: PlayerSettings
) : ViewModel() {

    private val route: LiveRoute? = savedStateHandle.toLiveRoute()
    val player = playbackSession.player
    val playbackState: StateFlow<LivePlaybackViewState> = playbackSession.liveState
    val settingsState: StateFlow<PlayerSettingsState> = playerSettings.state
    val uiState: StateFlow<LiveUiState> = combine(
        playbackState,
        settingsState
    ) { playbackState, settingsState ->
        LiveUiState(
            route = route,
            playbackState = playbackState,
            backgroundPlaybackEnabled = settingsState.playback.backgroundPlayback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LiveUiState(route = route)
    )

    private var startJob: Job? = null

    fun ensureStarted() {
        val target = route ?: return
        if (playbackState.value.playbackSource?.roomId == target.roomId) return
        if (startJob?.isActive == true) return
        startJob = viewModelScope.launch {
            playbackSession.openLive(
                route = target,
                preferredQuality = playbackState.value.playbackSource?.currentQn ?: 0
            )
        }
    }

    fun togglePlayPause() {
        if (playbackState.value.isPlaying) {
            playbackSession.pause()
        } else {
            playbackSession.play()
        }
    }

    fun switchQuality(qn: Int) {
        playbackSession.switchLiveQuality(qn)
    }

    fun retry() {
        val target = route ?: return
        startJob?.cancel()
        startJob = viewModelScope.launch {
            playbackSession.openLive(
                route = target,
                preferredQuality = playbackState.value.playbackSource?.currentQn ?: 0,
                reportEntry = false
            )
        }
    }

    fun pause() {
        playbackSession.pause()
    }

    override fun onCleared() {
        startJob?.cancel()
        startJob = null
        super.onCleared()
    }
}

private fun SavedStateHandle.toLiveRoute(): LiveRoute? {
    val roomId = get<Long>("roomId")?.takeIf { it > 0L } ?: return null
    return LiveRoute(
        roomId = roomId,
        title = get<String>("title")?.takeIf(String::isNotBlank),
        cover = get<String>("cover")?.takeIf(String::isNotBlank),
        ownerName = get<String>("ownerName")?.takeIf(String::isNotBlank),
        onlineText = get<String>("onlineText")?.takeIf(String::isNotBlank),
        jumpFrom = LiveRouteTool.normalizeJumpFrom(get<Int>("jumpFrom"))
    )
}

internal fun LivePlaybackError.toUiMessage(): String {
    return when (this) {
        is LivePlaybackError.NoPlayableStream -> message
        is LivePlaybackError.RequestFailed -> message
    }
}

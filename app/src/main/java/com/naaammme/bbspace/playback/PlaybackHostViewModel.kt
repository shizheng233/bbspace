package com.naaammme.bbspace.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.naaammme.bbspace.core.domain.player.PlayerSettings
import com.naaammme.bbspace.core.domain.player.StreamPlaybackSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaybackHostViewModel @Inject constructor(
    private val playbackSession: StreamPlaybackSession,
    playerSettings: PlayerSettings
) : ViewModel() {

    val player = playbackSession.player
    val currentTarget = playbackSession.currentTarget
    val sessionState = playbackSession.sessionState
    val pageMeta = playbackSession.pageMeta
    val miniPlayerEnabled = playerSettings.state
        .combine(playbackSession.currentTarget) { settings, target ->
            settings.playback.inAppMiniPlayer && target != null
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    var isMiniMode by mutableStateOf(false)
        private set

    init {
        viewModelScope.launch {
            combine(currentTarget, miniPlayerEnabled) { target, enabled ->
                target != null && enabled
            }.collect { canShow ->
                if (!canShow) {
                    isMiniMode = false
                }
            }
        }
    }

    fun expand() {
        isMiniMode = false
    }

    fun minimize() {
        if (!miniPlayerEnabled.value) return
        if (currentTarget.value == null) return
        isMiniMode = true
    }

    fun togglePlayPause() {
        if (sessionState.value.isPlaying) {
            playbackSession.pause()
        } else {
            playbackSession.play()
        }
    }

    fun close() {
        isMiniMode = false
        playbackSession.close()
    }
}

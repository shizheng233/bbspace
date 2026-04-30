package com.naaammme.bbspace.core.domain.player

import androidx.media3.common.Player
import com.naaammme.bbspace.core.model.LivePlaybackViewState
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.PlaybackHistoryMeta
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlaybackViewState
import com.naaammme.bbspace.core.model.StreamPlaybackSessionState
import com.naaammme.bbspace.core.model.StreamPlaybackTarget
import com.naaammme.bbspace.core.model.VideoRoute
import kotlinx.coroutines.flow.StateFlow

interface StreamPlaybackSession {
    val player: StateFlow<Player?>
    val currentTarget: StateFlow<StreamPlaybackTarget?>
    val sessionState: StateFlow<StreamPlaybackSessionState>
    val videoState: StateFlow<PlaybackViewState>
    val liveState: StateFlow<LivePlaybackViewState>
    val pageMeta: StateFlow<PlaybackHistoryMeta?>

    suspend fun prepare()

    suspend fun openVideo(
        route: VideoRoute,
        request: PlaybackRequest
    )

    suspend fun openLive(
        route: LiveRoute,
        preferredQuality: Int = 0,
        reportEntry: Boolean = true
    )

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun switchVideoQuality(quality: Int)
    fun switchVideoAudio(audioId: Int)
    fun switchVideoCdn(index: Int)
    fun switchVideoPage(cid: Long)
    fun switchLiveQuality(quality: Int)
    fun updatePlaybackMeta(meta: PlaybackHistoryMeta?)
    fun close()
}

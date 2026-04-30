package com.naaammme.bbspace.feature.video

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.naaammme.bbspace.core.domain.danmaku.VodDanmakuRepository
import com.naaammme.bbspace.core.domain.player.PlayerSettings
import com.naaammme.bbspace.core.domain.player.StreamPlaybackSession
import com.naaammme.bbspace.core.domain.video.VideoDetailRepository
import com.naaammme.bbspace.core.model.CommentSubject
import com.naaammme.bbspace.core.model.CommentSubjectTool
import com.naaammme.bbspace.core.model.DanmakuConfig
import com.naaammme.bbspace.core.model.PlayBiz
import com.naaammme.bbspace.core.model.PlaybackError
import com.naaammme.bbspace.core.model.PlaybackHistoryMeta
import com.naaammme.bbspace.core.model.PlaybackRequest
import com.naaammme.bbspace.core.model.PlaybackViewState
import com.naaammme.bbspace.core.model.PlayerBufferSettings
import com.naaammme.bbspace.core.model.PlayerPlaybackPrefs
import com.naaammme.bbspace.core.model.VideoDetail
import com.naaammme.bbspace.core.model.VideoDownloadKind
import com.naaammme.bbspace.core.model.VideoDownloadMeta
import com.naaammme.bbspace.core.model.VideoDownloadRequest
import com.naaammme.bbspace.core.model.VideoRoute
import com.naaammme.bbspace.core.model.VideoRouteTool
import com.naaammme.bbspace.core.model.toPlayableParams
import com.naaammme.bbspace.feature.video.player.VodDanmakuSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class VideoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playbackSession: StreamPlaybackSession,
    private val playerSettings: PlayerSettings,
    private val detailRepo: VideoDetailRepository,
    vodDanmakuRepository: VodDanmakuRepository
) : ViewModel() {

    private val src = VideoRouteTool.custom(
        from = savedStateHandle.get<String>("from"),
        fromSpmid = savedStateHandle.get<String>("fromSpmid"),
        trackId = savedStateHandle.get<String>("trackId"),
        reportFlowData = savedStateHandle.get<String>("report")
    )
    private val route = savedStateHandle.toVideoRoute(src)
    private val ugcRoute = route as? VideoRoute.Ugc
    private val _detail = MutableStateFlow<VideoDetail?>(null)
    private val _detailLoading = MutableStateFlow(
        route is VideoRoute.Ugc || route is VideoRoute.Pgc || route is VideoRoute.Pugv
    )
    private val _detailError = MutableStateFlow(
        when (route) {
            null -> "视频路由无效"
            else -> null
        }
    )
    private val initReq = route
        ?.toPlayableParams()
        ?.getResolveParams()
    private val _req = MutableStateFlow(initReq)
    private val danmakuSession = VodDanmakuSession(
        scope = viewModelScope,
        repository = vodDanmakuRepository
    )
    private var startJob: Job? = null
    private var openingRequest: PlaybackRequest? = null

    val player: StateFlow<Player?> = playbackSession.player
    val playerState: StateFlow<PlaybackViewState> = playbackSession.videoState
    val settingsState = playerSettings.state

    val pageState = combine(
        playerState,
        _req,
        _detail,
        _detailLoading,
        _detailError
    ) { playbackState, req, detail, detailLoading, detailError ->
        VideoPageState(
            detail = detail,
            detailLoading = detailLoading,
            detailError = detailError,
            curCid = playbackState.playbackSource?.videoId?.cid ?: req?.videoId?.cid
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VideoPageState(curCid = initReq?.videoId?.cid)
    )

    val commentSubject: CommentSubject?
        get() {
            val targetAid = playerState.value.playbackSource?.videoId?.aid?.takeIf { it > 0L }
                ?: ugcRoute?.aid
                ?: return null
            return CommentSubjectTool.video(targetAid, src)
        }

    internal val danmakuState = danmakuSession.state

    init {
        bindPlaybackMeta()
        bindPgcDetail()
        loadInitialDetail()
    }

    fun ensureStarted() {
        danmakuSession.bind(
            playbackStateFlow = playerState,
            enabledFlow = settingsState.map { it.danmaku.enabled }
        )
        val request = _req.value ?: return
        if (openingRequest == request && startJob?.isActive == true) return
        startPlayback(routeFor(request), request)
    }

    fun togglePlayPause() {
        if (playerState.value.isPlaying) {
            playbackSession.pause()
        } else {
            playbackSession.play()
        }
    }

    fun onDanmakuTick(positionMs: Long) {
        danmakuSession.onTick(positionMs)
    }

    fun switchQuality(quality: Int) {
        playbackSession.switchVideoQuality(quality)
    }

    fun switchAudio(audioId: Int) {
        playbackSession.switchVideoAudio(audioId)
    }

    fun switchCdn(cdnIndex: Int) {
        playbackSession.switchVideoCdn(cdnIndex)
    }

    fun pause() {
        playbackSession.pause()
    }

    fun seekTo(positionMs: Long) {
        playbackSession.seekTo(positionMs)
    }

    fun setSpeed(speed: Float) {
        playbackSession.setSpeed(speed)
    }

    fun updateBackgroundPlayback(enabled: Boolean) {
        updatePlayback { copy(backgroundPlayback = enabled) }
    }

    fun updateInAppMiniPlayer(enabled: Boolean) {
        updatePlayback { copy(inAppMiniPlayer = enabled) }
    }

    fun updateReportPlayback(enabled: Boolean) {
        updatePlayback { copy(reportPlayback = enabled) }
    }

    fun updateMinBufferMs(value: Int) {
        updateBuffer { copy(minBufferMs = value) }
    }

    fun updateMaxBufferMs(value: Int) {
        updateBuffer { copy(maxBufferMs = value) }
    }

    fun updatePlaybackBufferMs(value: Int) {
        updateBuffer { copy(playbackBufferMs = value) }
    }

    fun updateRebufferMs(value: Int) {
        updateBuffer { copy(rebufferMs = value) }
    }

    fun updateBackBufferMs(value: Int) {
        updateBuffer { copy(backBufferMs = value) }
    }

    fun updatePreferSoftwareDecode(enabled: Boolean) {
        updatePlayback { copy(preferSoftwareDecode = enabled) }
    }

    fun updateDecoderFallback(enabled: Boolean) {
        updatePlayback { copy(decoderFallback = enabled) }
    }

    fun updateDanmaku(config: DanmakuConfig) {
        viewModelScope.launch {
            playerSettings.updateDanmaku(config)
        }
    }

    fun switchPage(cid: Long) {
        val request = _req.value ?: return
        if (request.videoId.cid == cid) return
        val next = request.copy(
            playable = request.playable.copy(
                videoId = request.videoId.copy(cid = cid)
            ),
            seekToMs = null
        )
        _req.value = next
        startPlayback(routeFor(next), next)
    }

    fun currentDownloadRequest(
        kind: VideoDownloadKind,
        videoQuality: Int,
        audioQuality: Int
    ): VideoDownloadRequest? {
        val curRoute = route ?: return null
        val id = playerState.value.playbackSource?.videoId ?: _req.value?.videoId
        val meta = buildDownloadMeta()
        return when (curRoute) {
            is VideoRoute.Ugc -> {
                val aid = id?.aid?.takeIf { it > 0L } ?: curRoute.aid
                val cid = id?.cid?.takeIf { it > 0L } ?: curRoute.cid
                if (aid <= 0L || cid <= 0L) return null
                VideoDownloadRequest(
                    biz = PlayBiz.UGC,
                    aid = aid,
                    cid = cid,
                    bvid = id?.bvid?.takeIf(String::isNotBlank) ?: curRoute.bvid,
                    kind = kind,
                    videoQuality = videoQuality,
                    audioQuality = audioQuality,
                    meta = meta
                )
            }

            is VideoRoute.Pgc -> VideoDownloadRequest(
                biz = PlayBiz.PGC,
                epId = curRoute.epId,
                seasonId = curRoute.seasonId ?: 0L,
                kind = kind,
                videoQuality = videoQuality,
                audioQuality = audioQuality,
                meta = meta
            )

            is VideoRoute.Pugv -> VideoDownloadRequest(
                biz = PlayBiz.PUGV,
                epId = curRoute.epId,
                seasonId = curRoute.seasonId ?: 0L,
                kind = kind,
                videoQuality = videoQuality,
                audioQuality = audioQuality,
                meta = meta
            )
        }
    }

    fun releaseUi() {
        danmakuSession.clear()
    }

    override fun onCleared() {
        startJob?.cancel()
        startJob = null
        openingRequest = null
        danmakuSession.clear()
        super.onCleared()
    }

    private fun startPlayback(
        targetRoute: VideoRoute?,
        request: PlaybackRequest
    ) {
        val videoRoute = targetRoute ?: return
        if (openingRequest == request && startJob?.isActive == true) return
        startJob?.cancel()
        openingRequest = request
        startJob = viewModelScope.launch {
            try {
                playbackSession.openVideo(videoRoute, request)
            } finally {
                if (openingRequest == request) {
                    openingRequest = null
                }
            }
        }
    }

    private fun bindPlaybackMeta() {
        viewModelScope.launch {
            combine(_detail, _req, playerState) { detail, req, playbackState ->
                detail.toPlaybackHistoryMeta(
                    playbackState.playbackSource?.videoId?.cid ?: req?.videoId?.cid
                )
            }.collect { meta ->
                playbackSession.updatePlaybackMeta(meta)
            }
        }
    }

    private fun bindPgcDetail() {
        if (route !is VideoRoute.Pgc && route !is VideoRoute.Pugv) return
        viewModelScope.launch {
            var loadedAid = _detail.value?.aid?.takeIf { it > 0L } ?: 0L
            var loadedBvid = _detail.value?.bvid.orEmpty()
            playerState.collect { playbackState ->
                val videoId = playbackState.playbackSource?.videoId
                val aid = videoId?.aid?.takeIf { it > 0L }
                if (aid != null) {
                    val bvid = videoId.bvid.orEmpty()
                    if (aid == loadedAid && bvid == loadedBvid) return@collect
                    loadedAid = aid
                    loadedBvid = bvid
                    fetchDetail(aid, videoId.bvid, route.src)
                    return@collect
                }
                val error = playbackState.error
                if (error != null && _detail.value == null) {
                    _detailError.value = error.toUiMsg()
                    _detailLoading.value = false
                }
            }
        }
    }

    private fun loadInitialDetail() {
        when (val route = route) {
            is VideoRoute.Ugc -> {
                viewModelScope.launch {
                    fetchDetail(route.aid, route.bvid, route.src)
                }
            }

            is VideoRoute.Pgc, is VideoRoute.Pugv -> Unit
            else -> _detailLoading.value = false
        }
    }

    private fun updateBuffer(transform: PlayerBufferSettings.() -> PlayerBufferSettings) {
        viewModelScope.launch {
            playerSettings.updateBuffer(settingsState.value.buffer.transform())
        }
    }

    private fun updatePlayback(transform: PlayerPlaybackPrefs.() -> PlayerPlaybackPrefs) {
        viewModelScope.launch {
            playerSettings.updatePlayback(settingsState.value.playback.transform())
        }
    }

    private fun buildDownloadMeta(): VideoDownloadMeta {
        val detail = _detail.value
        val cid = playerState.value.playbackSource?.videoId?.cid ?: _req.value?.videoId?.cid
        val part = cid?.let { target -> detail?.pages?.firstOrNull { it.cid == target } }
        val title = detail?.let {
            listOfNotNull(
                it.title.takeIf(String::isNotBlank),
                part?.part?.takeIf(String::isNotBlank)
            ).joinToString(" - ").takeIf(String::isNotBlank)
        }
        return VideoDownloadMeta(
            title = title,
            cover = detail?.cover,
            ownerUid = detail?.owner?.mid?.takeIf { it > 0L },
            ownerName = detail?.owner?.name?.takeIf(String::isNotBlank)
        )
    }

    private suspend fun fetchDetail(
        aid: Long,
        bvid: String?,
        src: com.naaammme.bbspace.core.model.VideoSrc
    ) {
        _detailError.value = null
        _detailLoading.value = true
        val result = runCatching {
            detailRepo.fetchVideoDetail(
                aid = aid,
                bvid = bvid,
                src = src
            )
        }
        _detail.value = result.getOrNull()
        _detailError.value = result.exceptionOrNull()?.message
            ?: if (result.isFailure) "加载视频详情失败" else null
        _detailLoading.value = false
    }

    private fun routeFor(request: PlaybackRequest): VideoRoute? {
        val base = route ?: return null
        return when (base) {
            is VideoRoute.Ugc -> base.copy(
                aid = request.videoId.aid,
                cid = request.videoId.cid,
                bvid = request.videoId.bvid ?: base.bvid
            )
            is VideoRoute.Pgc -> base
            is VideoRoute.Pugv -> base
        }
    }
}

private fun SavedStateHandle.optLong(key: String): Long? {
    return get<Long>(key)?.takeIf { it > 0L }
}

private fun SavedStateHandle.optInt(key: String): Int? {
    return get<Int>(key)?.takeIf { it >= 0 }
}

private fun SavedStateHandle.toVideoRoute(src: com.naaammme.bbspace.core.model.VideoSrc): VideoRoute? {
    return when (PlayBiz.from(get<String>("biz"))) {
        PlayBiz.UGC -> {
            val aid = get<Long>("aid")?.takeIf { it > 0L } ?: return null
            val cid = get<Long>("cid") ?: 0L
            VideoRoute.Ugc(
                aid = aid,
                cid = cid,
                bvid = get<String>("bvid")?.takeIf(String::isNotBlank),
                src = src
            )
        }

        PlayBiz.PGC -> {
            optLong("epId")?.let {
                VideoRoute.Pgc(
                    epId = it,
                    seasonId = optLong("seasonId"),
                    subType = optInt("subType"),
                    src = src
                )
            }
        }

        PlayBiz.PUGV -> {
            optLong("epId")?.let {
                VideoRoute.Pugv(
                    epId = it,
                    seasonId = optLong("seasonId"),
                    src = src
                )
            }
        }
    }
}

private fun PlaybackError.toUiMsg(): String {
    return when (this) {
        is PlaybackError.RequestFailed -> message
        is PlaybackError.NoPlayableStream -> message
    }
}

private fun VideoDetail?.toPlaybackHistoryMeta(cid: Long?): PlaybackHistoryMeta? {
    this ?: return null
    val idx = cid?.let { target -> pages.indexOfFirst { it.cid == target } } ?: -1
    val part = if (idx >= 0) pages[idx] else null
    return PlaybackHistoryMeta(
        title = title,
        cover = cover,
        ownerUid = owner?.mid?.takeIf { it > 0L },
        ownerName = owner?.name,
        part = if (idx >= 0) idx + 1 else null,
        partTitle = part?.part
    )
}

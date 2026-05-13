package com.naaammme.bbspace.core.model

import androidx.compose.runtime.Immutable

enum class ImSessionTab(
    val title: String
) {
    DEFAULT("全部"),
    FOLLOW("关注"),
    STRANGER("陌生人")
}

@Immutable
data class ImPaginationOffset(
    val normalOffset: Long,
    val topOffset: Long
)

@Immutable
data class ImPaginationParams(
    val offsets: Map<Int, ImPaginationOffset> = emptyMap(),
    val hasMore: Boolean = false
)

@Immutable
data class ImPage(
    val tabs: List<ImSessionTab>,
    val currentTab: ImSessionTab,
    val paginationParams: ImPaginationParams? = null,
    val sessions: List<ImSessionItem>
)

@Immutable
data class ImSessionItem(
    val key: String,
    val talkerId: Long?,
    val sessionTypeLabel: String?,
    val name: String,
    val avatar: String?,
    val summary: String,
    val unreadText: String?,
    val unreadCount: Long,
    val timeMicros: Long,
    val isPinned: Boolean,
    val isMuted: Boolean
)

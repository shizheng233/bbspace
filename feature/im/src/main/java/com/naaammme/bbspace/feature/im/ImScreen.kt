package com.naaammme.bbspace.feature.im

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.naaammme.bbspace.core.designsystem.component.AvatarImage
import com.naaammme.bbspace.core.designsystem.component.BiliPullToRefreshBox
import com.naaammme.bbspace.core.designsystem.component.CollapsingTopBarScaffold
import com.naaammme.bbspace.core.designsystem.component.FilledTabRow
import com.naaammme.bbspace.core.model.ImSessionItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImScreen(
    vm: ImViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    CollapsingTopBarScaffold(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = { Text("消息") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            FilledTabRow(
                tabs = state.tabs.map { it.title },
                selectedIndex = state.tabs.indexOf(state.currentTab).coerceAtLeast(0),
                onSelect = { index -> state.tabs.getOrNull(index)?.let(vm::selectTab) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
            )

            BiliPullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { vm.refresh() },
                modifier = Modifier.weight(1f)
            ) {
                when {
                    !state.isLoggedIn -> {
                        ImCenterState(
                            text = "请先登录后查看消息"
                        )
                    }

                    state.isLoading && state.sessions.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    !state.errorMessage.isNullOrBlank() && state.sessions.isEmpty() -> {
                        ImCenterState(
                            text = state.errorMessage.orEmpty()
                        )
                    }

                    state.sessions.isEmpty() -> {
                        ImCenterState(
                            text = "暂无消息"
                        )
                    }

                    else -> {
                        val listState = rememberLazyListState()
                        val shouldLoadMore by remember(
                            state.sessions.size,
                            state.canLoadMore,
                            state.isLoadingMore,
                            state.loadMoreError,
                            listState
                        ) {
                            derivedStateOf {
                                val total = listState.layoutInfo.totalItemsCount
                                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                                state.canLoadMore &&
                                    state.loadMoreError.isNullOrBlank() &&
                                    total > 0 &&
                                    last >= total - 4
                            }
                        }
                        LaunchedEffect(shouldLoadMore) {
                            if (shouldLoadMore) vm.loadMore()
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = state.sessions,
                                key = { it.key }
                            ) { item ->
                                ImSessionCard(item = item)
                            }

                            if (!state.errorMessage.isNullOrBlank()) {
                                item(key = "im_error_footer") {
                                    ImInlineError(
                                        text = state.errorMessage.orEmpty()
                                    )
                                }
                            }

                            if (state.isLoadingMore) {
                                item(key = "im_loading_more") {
                                    ImLoadingMore()
                                }
                            } else if (!state.loadMoreError.isNullOrBlank()) {
                                item(key = "im_load_more_error") {
                                    ImLoadMoreError(
                                        text = state.loadMoreError.orEmpty(),
                                        onRetry = vm::loadMore
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImSessionCard(
    item: ImSessionItem,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarImage(
                url = item.avatar,
                contentDescription = item.name,
                modifier = Modifier.size(48.dp),
                fallbackText = item.name.take(1)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatImTime(item.timeMicros),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val summary = buildSummary(item)
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            item.unreadText?.let { unread ->
                Text(
                    text = unread,
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.extraLarge
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1
                )
            }
        }
    }
}

private fun buildSummary(item: ImSessionItem): String {
    val prefix = buildList {
        if (item.isPinned) add("置顶")
        if (item.isMuted) add("免打扰")
        item.sessionTypeLabel?.let(::add)
    }.joinToString(" · ")
    return if (prefix.isBlank()) item.summary else "$prefix · ${item.summary}"
}

@Composable
private fun ImCenterState(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ImInlineError(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun ImLoadingMore(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ImLoadMoreError(
    text: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onRetry),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "重试",
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun formatImTime(micros: Long): String {
    if (micros <= 0L) return ""
    val instant = Instant.ofEpochMilli(micros / 1000L)
    return IM_TIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()))
}

private val IM_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

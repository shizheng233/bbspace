package com.naaammme.bbspace.feature.bbspace.aicucomment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AicuCommentPane(
    modifier: Modifier = Modifier,
    vm: AicuCommentViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(listState, state) {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            state.items.isNotEmpty() &&
                    !state.isLoading &&
                    !state.isLoadingMore &&
                    state.appendError == null &&
                    !state.queryPending &&
                    !state.isEnd &&
                    total > 0 &&
                    last >= total - LOAD_MORE_TRIGGER_OFFSET
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            vm.loadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "AICU 评论",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = state.uidInput,
                        onValueChange = vm::updateUidInput,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        label = { Text("UID") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = state.keywordInput,
                        onValueChange = vm::updateKeywordInput,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        label = { Text("关键词") },
                        singleLine = true
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AicuCommentMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.mode == mode,
                                onClick = { vm.selectMode(mode) },
                                label = { Text(mode.title) }
                            )
                        }
                    }
                    Button(
                        onClick = vm::query,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isLoading && !state.isLoadingMore
                    ) {
                        Text(if (state.isLoading) "查询中" else "查询")
                    }
                }
            }
        }

        state.allCount?.let { allCount ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "共 $allCount 条，已加载 ${state.items.size} 条",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val tipText = when {
                            state.queryPending -> "筛选条件已修改，点击查询更新结果"
                            !state.isEnd -> "上滑继续加载"
                            state.isEnd -> "已到末页"
                            else -> "暂无更多"
                        }
                        Text(
                            text = tipText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (state.allCount != null && state.items.isEmpty() && state.error == null && !state.isLoading) {
            item {
                AicuCommentStateCard(text = "没有匹配结果")
            }
        }

        if (state.items.isNotEmpty()) {
            items(
                items = state.items,
                key = { it.rpid }
            ) { item ->
                AicuCommentCard(item = item)
            }
        }

        if (state.isLoadingMore) {
            item {
                AicuCommentStateCard(text = "加载更多中")
            }
        }

        state.appendError?.let { message ->
            item {
                AicuCommentRetryCard(
                    text = message,
                    buttonText = "重试加载更多",
                    onClick = vm::loadMore
                )
            }
        }

        state.error?.let { message ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AicuCommentCard(item: AicuCommentItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = item.message.ifBlank { "(空评论)" },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "oid ${item.oid} · type ${item.type} · rpid ${item.rpid}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = item.timeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AicuCommentStateCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AicuCommentRetryCard(
    text: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            OutlinedButton(onClick = onClick) {
                Text(buttonText)
            }
        }
    }
}

private const val LOAD_MORE_TRIGGER_OFFSET = 3

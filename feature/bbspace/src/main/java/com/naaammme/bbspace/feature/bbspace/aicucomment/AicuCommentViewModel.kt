package com.naaammme.bbspace.feature.bbspace.aicucomment

import android.text.format.DateFormat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class AicuCommentUiState(
    val uidInput: String = "",
    val keywordInput: String = "",
    val mode: AicuCommentMode = AicuCommentMode.ALL,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val allCount: Int? = null,
    val isEnd: Boolean = false,
    val queryPending: Boolean = false,
    val items: List<AicuCommentItem> = emptyList(),
    val error: String? = null,
    val appendError: String? = null
)

enum class AicuCommentMode(
    val value: Int,
    val title: String
) {
    ALL(0, "全部"),
    ROOT(1, "一级"),
    CHILD(2, "二级")
}

data class AicuCommentItem(
    val rpid: String,
    val message: String,
    val timeText: String,
    val oid: String,
    val type: Int
)

@HiltViewModel
class AicuCommentViewModel @Inject constructor(
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(AicuCommentUiState())
    val uiState: StateFlow<AicuCommentUiState> = _uiState.asStateFlow()

    private var activeQuery: AicuCommentQuery? = null
    private var nextPage = FIRST_PAGE
    private var reqJob: Job? = null

    fun updateUidInput(value: String) {
        updateQueryInput {
            it.copy(uidInput = value.filter(Char::isDigit))
        }
    }

    fun updateKeywordInput(value: String) {
        updateQueryInput {
            it.copy(keywordInput = value)
        }
    }

    fun selectMode(mode: AicuCommentMode) {
        if (uiState.value.mode == mode) return
        updateQueryInput {
            it.copy(mode = mode)
        }
    }

    fun query() {
        val query = buildCurrentQuery() ?: run {
            reqJob?.cancel()
            activeQuery = null
            nextPage = FIRST_PAGE
            _uiState.update {
                it.copy(
                    allCount = null,
                    isEnd = false,
                    queryPending = false,
                    items = emptyList(),
                    error = "请输入有效 UID",
                    appendError = null
                )
            }
            return
        }
        request(query = query, pageNum = FIRST_PAGE, append = false)
    }

    fun loadMore() {
        val query = activeQuery ?: return
        val state = uiState.value
        if (
            state.isLoading ||
            state.isLoadingMore ||
            state.queryPending ||
            state.isEnd ||
            state.items.isEmpty()
        ) {
            return
        }
        request(query = query, pageNum = nextPage, append = true)
    }

    private fun updateQueryInput(
        transform: (AicuCommentUiState) -> AicuCommentUiState
    ) {
        _uiState.update { state ->
            val next = transform(state)
            val queryPending = activeQuery?.let { hasQueryChanged(it, next) } ?: false
            next.copy(
                error = null,
                appendError = null,
                queryPending = queryPending
            )
        }
    }

    private fun request(
        query: AicuCommentQuery,
        pageNum: Int,
        append: Boolean
    ) {
        if (!append) {
            reqJob?.cancel()
            activeQuery = query
            nextPage = FIRST_PAGE
        }
        reqJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !append,
                    isLoadingMore = append,
                    queryPending = false,
                    error = if (append) it.error else null,
                    appendError = null
                )
            }
            val page = try {
                fetchAicuComments(query = query, pageNum = pageNum)
            } catch (err: CancellationException) {
                throw err
            } catch (err: Throwable) {
                if (!append) {
                    activeQuery = null
                    nextPage = FIRST_PAGE
                }
                _uiState.update {
                    if (append) {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            appendError = err.message ?: "加载更多失败"
                        )
                    } else {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            allCount = null,
                            isEnd = false,
                            queryPending = false,
                            items = emptyList(),
                            error = err.message ?: "查询失败",
                            appendError = null
                        )
                    }
                }
                return@launch
            }
            nextPage = pageNum + 1
            _uiState.update { state ->
                val queryPending = hasQueryChanged(query, state)
                val items = when {
                    !append -> page.items
                    queryPending -> state.items
                    else -> mergeItems(state.items, page.items)
                }
                state.copy(
                    isLoading = false,
                    isLoadingMore = false,
                    allCount = page.allCount,
                    isEnd = page.isEnd,
                    queryPending = queryPending,
                    items = items,
                    error = null,
                    appendError = null
                )
            }
        }
    }

    private suspend fun fetchAicuComments(
        query: AicuCommentQuery,
        pageNum: Int
    ): AicuCommentPage {
        val url = API_ENDPOINT.toHttpUrl()
            .newBuilder()
            .addQueryParameter("uid", query.uid.toString())
            .addQueryParameter("pn", pageNum.toString())
            .addQueryParameter("ps", PAGE_SIZE.toString())
            .addQueryParameter("mode", query.mode.value.toString())
            .addQueryParameter("keyword", query.keyword)
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("accept", "*/*")
            .addHeader("origin", ORIGIN)
            .addHeader("user-agent", USER_AGENT)
            .build()
        return withContext(Dispatchers.IO) {
            okHttpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "请求失败 HTTP ${response.code}" }
                val body = response.body?.string().orEmpty()
                check(body.isNotBlank()) { "响应为空" }
                val json = JSONObject(body)
                check(json.optInt("code", -1) == 0) { json.optString("message", "查询失败") }
                val data = json.optJSONObject("data") ?: error("响应缺少 data")
                val cursor = data.optJSONObject("cursor")
                val replies = data.optJSONArray("replies")
                val items = buildList {
                    if (replies == null) return@buildList
                    for (i in 0 until replies.length()) {
                        val item = replies.optJSONObject(i) ?: continue
                        val dyn = item.optJSONObject("dyn") ?: continue
                        val timeSec = item.optLong("time")
                        add(
                            AicuCommentItem(
                                rpid = item.optString("rpid"),
                                message = item.optString("message"),
                                timeText = DateFormat.format(
                                    "yyyy-MM-dd HH:mm:ss",
                                    timeSec * 1000L
                                ).toString(),
                                oid = dyn.optString("oid"),
                                type = dyn.optInt("type")
                            )
                        )
                    }
                }
                AicuCommentPage(
                    allCount = cursor?.optInt("all_count") ?: items.size,
                    isEnd = cursor?.optBoolean("is_end") ?: true,
                    items = items
                )
            }
        }
    }

    private fun buildCurrentQuery(): AicuCommentQuery? {
        val state = uiState.value
        val uid = state.uidInput.toLongOrNull()
        if (uid == null || uid <= 0L) return null
        return AicuCommentQuery(
            uid = uid,
            mode = state.mode,
            keyword = state.keywordInput.trim()
        )
    }

    private fun hasQueryChanged(
        query: AicuCommentQuery,
        state: AicuCommentUiState
    ): Boolean {
        return state.uidInput != query.uid.toString() ||
                state.mode != query.mode ||
                state.keywordInput.trim() != query.keyword
    }

    private fun mergeItems(
        old: List<AicuCommentItem>,
        more: List<AicuCommentItem>
    ): List<AicuCommentItem> {
        if (old.isEmpty()) return more
        if (more.isEmpty()) return old
        val ids = HashSet<String>(old.size + more.size)
        return buildList(old.size + more.size) {
            old.forEach { item ->
                ids.add(item.rpid)
                add(item)
            }
            more.forEach { item ->
                if (ids.add(item.rpid)) {
                    add(item)
                }
            }
        }
    }

    private companion object {
        const val API_ENDPOINT = "https://api.aicu.cc/api/v3/search/getreply"
        const val ORIGIN = "https://www.aicu.cc"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36 Edg/148.0.0.0"
        const val FIRST_PAGE = 1
        const val PAGE_SIZE = 100
    }

    private data class AicuCommentQuery(
        val uid: Long,
        val mode: AicuCommentMode,
        val keyword: String
    )

    private data class AicuCommentPage(
        val allCount: Int,
        val isEnd: Boolean,
        val items: List<AicuCommentItem>
    )
}

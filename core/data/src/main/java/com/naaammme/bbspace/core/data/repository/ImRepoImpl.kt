package com.naaammme.bbspace.core.data.repository

import com.bapis.bilibili.app.im.v1.MsgSummary
import com.bapis.bilibili.app.im.v1.Offset
import com.bapis.bilibili.app.im.v1.PaginationParams
import com.bapis.bilibili.app.im.v1.Session
import com.bapis.bilibili.app.im.v1.SessionFilterType
import com.bapis.bilibili.app.im.v1.SessionMainReply
import com.bapis.bilibili.app.im.v1.SessionMainReq
import com.bapis.bilibili.app.im.v1.SessionPageType
import com.bapis.bilibili.app.im.v1.SessionSecondaryReply
import com.bapis.bilibili.app.im.v1.SessionSecondaryReq
import com.bapis.bilibili.app.im.v1.SessionType
import com.bapis.bilibili.app.im.v1.Unread
import com.bapis.bilibili.app.im.v1.UnreadStyle
import com.bapis.bilibili.dagw.component.avatar.common.ResourceSource
import com.bapis.bilibili.dagw.component.avatar.v1.AvatarItem
import com.naaammme.bbspace.core.domain.ImRepository
import com.naaammme.bbspace.core.model.ImPage
import com.naaammme.bbspace.core.model.ImPaginationOffset
import com.naaammme.bbspace.core.model.ImPaginationParams
import com.naaammme.bbspace.core.model.ImSessionItem
import com.naaammme.bbspace.core.model.ImSessionTab
import com.naaammme.bbspace.infra.grpc.BiliGrpcClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ImRepoImpl @Inject constructor(
    private val grpcClient: BiliGrpcClient
) : ImRepository {

    override suspend fun fetchSessions(
        tab: ImSessionTab,
        paginationParams: ImPaginationParams?
    ): ImPage {
        return when (tab) {
            ImSessionTab.DEFAULT,
            ImSessionTab.FOLLOW -> fetchMain(tab, paginationParams)
            ImSessionTab.STRANGER -> fetchSecondary(tab, paginationParams)
        }
    }

    private suspend fun fetchMain(
        tab: ImSessionTab,
        paginationParams: ImPaginationParams?
    ): ImPage {
        val reply = grpcClient.call(
            endpoint = MAIN_ENDPOINT,
            requestBytes = buildMainReq(tab, paginationParams).toByteArray(),
            parser = SessionMainReply.parser()
        )
        return withContext(Dispatchers.Default) {
            ImPage(
                tabs = IM_TABS,
                currentTab = tab,
                paginationParams = reply.paginationParams.toModel(),
                sessions = reply.sessionsList.map(::mapSession)
            )
        }
    }

    private suspend fun fetchSecondary(
        tab: ImSessionTab,
        paginationParams: ImPaginationParams?
    ): ImPage {
        val reply = grpcClient.call(
            endpoint = SECONDARY_ENDPOINT,
            requestBytes = buildSecondaryReq(tab, paginationParams).toByteArray(),
            parser = SessionSecondaryReply.parser()
        )
        return withContext(Dispatchers.Default) {
            ImPage(
                tabs = IM_TABS,
                currentTab = tab,
                paginationParams = reply.paginationParams.toModel(),
                sessions = reply.sessionsList.map(::mapSession)
            )
        }
    }

    private fun buildMainReq(
        tab: ImSessionTab,
        paginationParams: ImPaginationParams?
    ): SessionMainReq {
        return SessionMainReq.newBuilder()
            .setFilterType(tab.toFilterType())
            .apply {
                paginationParams?.toProto()?.let(::setPaginationParams)
            }
            .build()
    }

    private fun buildSecondaryReq(
        tab: ImSessionTab,
        paginationParams: ImPaginationParams?
    ): SessionSecondaryReq {
        return SessionSecondaryReq.newBuilder()
            .setPageType(tab.toPageType())
            .apply {
                paginationParams?.toProto()?.let(::setPaginationParams)
            }
            .build()
    }

    private fun mapSession(session: Session): ImSessionItem {
        val talkerId = when (session.id.idCase) {
            com.bapis.bilibili.app.im.v1.SessionId.IdCase.PRIVATE_ID -> session.id.privateId.talkerUid
            else -> null
        }
        return ImSessionItem(
            key = buildKey(session, talkerId),
            talkerId = talkerId,
            sessionTypeLabel = sessionTypeLabel(session),
            name = session.sessionInfo.sessionName.ifBlank { "未命名会话" },
            avatar = session.sessionInfo.avatar.avatarUrl(),
            summary = session.msgSummary.summaryText(),
            unreadText = session.unread.unreadText(),
            unreadCount = session.unread.number,
            timeMicros = session.timestamp,
            isPinned = session.isPinned,
            isMuted = session.isMuted
        )
    }

    private fun buildKey(
        session: Session,
        talkerId: Long?
    ): String {
        return when (session.id.idCase) {
            com.bapis.bilibili.app.im.v1.SessionId.IdCase.PRIVATE_ID -> "private:${talkerId ?: 0L}"
            com.bapis.bilibili.app.im.v1.SessionId.IdCase.GROUP_ID -> "group:${session.id.groupId.groupId}"
            com.bapis.bilibili.app.im.v1.SessionId.IdCase.FOLD_ID -> "fold:${session.id.foldId.typeValue}"
            com.bapis.bilibili.app.im.v1.SessionId.IdCase.SYSTEM_ID -> "system:${session.id.systemId.typeValue}"
            com.bapis.bilibili.app.im.v1.SessionId.IdCase.CUSTOMER_ID -> {
                "customer:${session.id.customerId.shopType}:${session.id.customerId.shopId}"
            }
            com.bapis.bilibili.app.im.v1.SessionId.IdCase.ID_NOT_SET,
            null -> "unknown:${session.sequenceNumber}:${session.timestamp}"
        }
    }

    private fun sessionTypeLabel(session: Session): String? {
        val type = when (session.id.idCase) {
            com.bapis.bilibili.app.im.v1.SessionId.IdCase.SYSTEM_ID -> session.id.systemId.type
            com.bapis.bilibili.app.im.v1.SessionId.IdCase.FOLD_ID -> session.id.foldId.type
            else -> SessionType.SESSION_TYPE_UNKNOWN
        }
        return when (type) {
            SessionType.SESSION_TYPE_SYSTEM -> "系统"
            SessionType.SESSION_TYPE_GROUP,
            SessionType.SESSION_TYPE_GROUP_FOLD -> "群聊"
            SessionType.SESSION_TYPE_CUSTOMER_ACCOUNT,
            SessionType.SESSION_TYPE_CUSTOMER_FOLD -> "客服"
            SessionType.SESSION_TYPE_AI_FOLD -> "AI"
            else -> null
        }
    }

    private fun MsgSummary.summaryText(): String {
        val prefix = prefixText.ifBlank { "" }
        val body = rawMsg.ifBlank { "暂无消息" }
        return if (prefix.isBlank()) body else "$prefix $body"
    }

    private fun Unread.unreadText(): String? {
        if (style == UnreadStyle.UNREAD_STYLE_DOT) return "•"
        if (number <= 0L) return null
        return numberShow.ifBlank {
            if (number > MAX_UNREAD_COUNT) {
                "${MAX_UNREAD_COUNT}+"
            } else {
                number.toString()
            }
        }
    }

    private fun AvatarItem.avatarUrl(): String? {
        layersList.firstNotNullOfOrNull { it.imageUrl() }?.let { return it }
        fallbackLayers.imageUrl()?.let { return it }
        return null
    }

    private fun com.bapis.bilibili.dagw.component.avatar.v1.LayerGroup.imageUrl(): String? {
        return layersList.firstNotNullOfOrNull { layer ->
            val resource = layer.resource
            if (resource.resType != com.bapis.bilibili.dagw.component.avatar.v1.BasicLayerResource.ResType.RES_TYPE_IMAGE) {
                return@firstNotNullOfOrNull null
            }
            val imageSrc = resource.resImage.imageSrc
            if (imageSrc.srcType != ResourceSource.SourceType.SRC_TYPE_URL) {
                return@firstNotNullOfOrNull null
            }
            imageSrc.remote.url.takeIf { it.isNotBlank() }?.replace("http://", "https://")
        }
    }

    private fun ImSessionTab.toFilterType(): SessionFilterType {
        return when (this) {
            ImSessionTab.DEFAULT -> SessionFilterType.FILTER_DEFAULT
            ImSessionTab.FOLLOW -> SessionFilterType.FILTER_FOLLOW
            ImSessionTab.STRANGER -> error("陌生人页不走 SessionMain")
        }
    }

    private fun ImSessionTab.toPageType(): SessionPageType {
        return when (this) {
            ImSessionTab.STRANGER -> SessionPageType.SESSION_PAGE_TYPE_STRANGER
            else -> error("$title 不走 SessionSecondary")
        }
    }

    private fun PaginationParams.toModel(): ImPaginationParams {
        return ImPaginationParams(
            offsets = offsetsMap.mapValues { (_, offset) ->
                ImPaginationOffset(
                    normalOffset = offset.normalOffset,
                    topOffset = offset.topOffset
                )
            },
            hasMore = hasMore
        )
    }

    private fun ImPaginationParams.toProto(): PaginationParams {
        return PaginationParams.newBuilder()
            .putAllOffsets(
                offsets.mapValues { (_, offset) ->
                    Offset.newBuilder()
                        .setNormalOffset(offset.normalOffset)
                        .setTopOffset(offset.topOffset)
                        .build()
                }
            )
            .setHasMore(hasMore)
            .build()
    }

    private companion object {
        const val MAIN_ENDPOINT = "bilibili.app.im.v1.im/SessionMain"
        const val SECONDARY_ENDPOINT = "bilibili.app.im.v1.im/SessionSecondary"
        const val MAX_UNREAD_COUNT = 99
        val IM_TABS = listOf(
            ImSessionTab.DEFAULT,
            ImSessionTab.FOLLOW,
            ImSessionTab.STRANGER
        )
    }
}

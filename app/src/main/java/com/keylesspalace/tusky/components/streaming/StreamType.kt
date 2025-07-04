package com.keylesspalace.tusky.components.streaming

import com.keylesspalace.tusky.BOOKMARKS
import com.keylesspalace.tusky.DIRECT
import com.keylesspalace.tusky.FEDERATED
import com.keylesspalace.tusky.HASHTAG
import com.keylesspalace.tusky.HOME
import com.keylesspalace.tusky.LIST
import com.keylesspalace.tusky.LOCAL
import com.keylesspalace.tusky.NOTIFICATIONS
import com.keylesspalace.tusky.TRENDING_STATUSES
import com.keylesspalace.tusky.TRENDING_TAGS
import com.keylesspalace.tusky.TabData
import com.keylesspalace.tusky.components.timeline.viewmodel.TimelineViewModel
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
sealed class StreamType(
    val stream: String,
    val list: String? = null,
    val tag: String? = null,
) {
    object User : StreamType(stream = "user")
    object PublicLocal : StreamType(stream = "public:local")
    object Public : StreamType(stream = "public")
    object Direct : StreamType(stream = "direct")

    data class Hashtag(
        val hashtag: String,
    ) : StreamType(
        stream = "hashtag",
        tag = hashtag,
    )

    data class List(
        val id: String,
    ) : StreamType(
        stream = "list",
        list = id,
    )

    fun subscribeMessage(): OutgoingMessage {
        return OutgoingMessage(
            type = "subscribe",
            stream = stream,
            list = list,
            tag = tag,
        )
    }

    fun unsubscribeMessage(): OutgoingMessage {
        return OutgoingMessage(
            type = "unsubscribe",
            stream = stream,
            list = list,
            tag = tag,
        )
    }

    companion object {
        fun fromServer(args: kotlin.collections.List<String>): StreamType? {
            val type = args.firstOrNull() ?: return null
            return when (type) {
                "user" -> User
                "public:local" -> PublicLocal
                "public" -> Public
                "direct" -> Direct
                "hashtag" -> {
                    val tag = args.getOrNull(1) ?: return null
                    Hashtag(tag)
                }
                "list" -> {
                    val id = args.getOrNull(1) ?: return null
                    List(id)
                }
                else -> null
            }
        }

        fun fromTabData(tabData: TabData): kotlin.collections.List<StreamType> {
            if (!tabData.isStreamingEnabled) {
                return emptyList()
            }

            return when (tabData.id) {
                HOME -> listOf(User)
                NOTIFICATIONS -> listOf(User)
                LOCAL -> listOf(PublicLocal)
                FEDERATED -> listOf(Public)
                DIRECT -> listOf(Direct)
                HASHTAG -> tabData.arguments.map { Hashtag(it) }
                LIST -> listOf(List(tabData.arguments[0]))
                TRENDING_TAGS, TRENDING_STATUSES, BOOKMARKS -> emptyList()
                else -> throw IllegalArgumentException("unknown tab type")
            }
        }

        fun fromKind(kind: TimelineViewModel.Kind, id: String?, tags: kotlin.collections.List<String>): kotlin.collections.List<StreamType> {
            return when (kind) {
                TimelineViewModel.Kind.HOME -> listOf(User)
                TimelineViewModel.Kind.PUBLIC_LOCAL -> listOf(PublicLocal)
                TimelineViewModel.Kind.PUBLIC_FEDERATED -> listOf(Public)
                TimelineViewModel.Kind.TAG -> tags.map { Hashtag(it) }
                TimelineViewModel.Kind.LIST -> listOf(List(id!!))
                TimelineViewModel.Kind.USER,
                TimelineViewModel.Kind.USER_PINNED,
                TimelineViewModel.Kind.USER_WITH_REPLIES,
                TimelineViewModel.Kind.FAVOURITES,
                TimelineViewModel.Kind.BOOKMARKS,
                TimelineViewModel.Kind.PUBLIC_TRENDING_STATUSES -> emptyList()
            }
        }
    }
}

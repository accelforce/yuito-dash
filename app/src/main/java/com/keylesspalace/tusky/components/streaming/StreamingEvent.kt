package com.keylesspalace.tusky.components.streaming

import com.keylesspalace.tusky.entity.Status
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
sealed class StreamingEvent(open val stream: StreamType) {
    data class Update(
        override val stream: StreamType,
        val status: Status,
    ) : StreamingEvent(stream)

    data class Conversation(
        val conversation: com.keylesspalace.tusky.entity.Conversation,
    ) : StreamingEvent(StreamType.Direct)

    data class Notification(
        val notification: com.keylesspalace.tusky.entity.Notification,
    ) : StreamingEvent(StreamType.User)

    data class Announcement(
        val announcement: com.keylesspalace.tusky.entity.Announcement,
    ) : StreamingEvent(StreamType.User)

    data class AnnouncementDeleted(
        val id: String,
    ) : StreamingEvent(StreamType.User)

    object FilterUpdated : StreamingEvent(StreamType.User)
}

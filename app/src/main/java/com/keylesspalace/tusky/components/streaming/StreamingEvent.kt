package com.keylesspalace.tusky.components.streaming

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
sealed class StreamingEvent(val stream: StreamType) {
    data class Notification(
        val notification: com.keylesspalace.tusky.entity.Notification,
    ) : StreamingEvent(StreamType.User)

    data class Announcement(
        val announcement: com.keylesspalace.tusky.entity.Announcement,
    ) : StreamingEvent(StreamType.User)

    data class AnnouncementDeleted(
        val id: String,
    ) : StreamingEvent(StreamType.User)
}

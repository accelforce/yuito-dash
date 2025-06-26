package com.keylesspalace.tusky.components.streaming

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
sealed class StreamingEvent(val stream: StreamType) {
    data class Notification(
        val notification: com.keylesspalace.tusky.entity.Notification,
    ) : StreamingEvent(StreamType.User)
}

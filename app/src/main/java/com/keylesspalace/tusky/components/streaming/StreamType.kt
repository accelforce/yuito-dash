package com.keylesspalace.tusky.components.streaming

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = false)
sealed class StreamType(
    val stream: String,
    val list: String? = null,
    val tag: String? = null,
) {
    object User : StreamType(stream = "user")

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

    override fun toString(): String {
        return when (this) {
            is User -> "User"
        }
    }
}

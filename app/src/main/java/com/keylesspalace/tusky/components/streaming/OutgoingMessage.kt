package com.keylesspalace.tusky.components.streaming

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OutgoingMessage(
    val type: String,
    val stream: String,
    val list: String? = null,
    val tag: String? = null,
)

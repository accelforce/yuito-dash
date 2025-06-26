package com.keylesspalace.tusky.components.streaming

import com.keylesspalace.tusky.entity.Announcement
import com.keylesspalace.tusky.entity.Notification
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import java.lang.reflect.Type

class StreamingEventAdapter(private val moshi: Moshi) : JsonAdapter<StreamingEvent>() {

    @JsonClass(generateAdapter = true)
    data class RawEvent(
        val stream: List<String>,
        val event: String,
        val payload: String? = null,
    )

    override fun fromJson(reader: JsonReader): StreamingEvent? {
        val delegate = moshi.adapter(RawEvent::class.java)
        val raw = delegate.fromJson(reader) ?: return null

        return when (raw.event) {
            "notification" -> {
                val inner = moshi.adapter(Notification::class.java)
                val notification = inner.fromJson(raw.payload ?: return null)
                    ?: return null
                StreamingEvent.Notification(notification)
            }
            "announcement" -> {
                val inner = moshi.adapter(Announcement::class.java)
                val announcement = inner.fromJson(raw.payload ?: return null)
                    ?: return null
                StreamingEvent.Announcement(announcement)
            }
            "announcement.delete" -> {
                StreamingEvent.AnnouncementDeleted(
                    id = raw.payload ?: return null,
                )
            }
            else -> {
                null
            }
        }
    }

    override fun toJson(writer: JsonWriter, value: StreamingEvent?) {
        error("unreachable")
    }

    object Factory : JsonAdapter.Factory {
        override fun create(
            type: Type,
            annotations: Set<Annotation?>,
            moshi: Moshi,
        ): JsonAdapter<*>? {
            if (type != StreamingEvent::class.java) {
                return null
            }

            return StreamingEventAdapter(moshi)
        }
    }
}

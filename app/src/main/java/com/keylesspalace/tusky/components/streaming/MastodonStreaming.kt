package com.keylesspalace.tusky.components.streaming

import com.tinder.scarlet.websocket.WebSocketEvent
import com.tinder.scarlet.ws.Receive
import com.tinder.scarlet.ws.Send
import kotlinx.coroutines.flow.Flow

interface MastodonStreaming {
    @Receive
    fun webSocketEvents(): Flow<WebSocketEvent>

    @Receive
    fun events(): Flow<StreamingEvent>

    @Send
    fun send(message: OutgoingMessage)
}

package com.keylesspalace.tusky.components.streaming

import android.app.Application
import android.content.SharedPreferences
import android.util.Log
import at.connyduck.calladapter.networkresult.getOrElse
import at.connyduck.calladapter.networkresult.map
import com.keylesspalace.tusky.appstore.EventHub
import com.keylesspalace.tusky.appstore.PreferenceChangedEvent
import com.keylesspalace.tusky.db.AccountManager
import com.keylesspalace.tusky.db.entity.AccountEntity
import com.keylesspalace.tusky.di.ApplicationScope
import com.keylesspalace.tusky.network.MastodonApi
import com.keylesspalace.tusky.settings.PrefKeys
import com.squareup.moshi.Moshi
import com.tinder.scarlet.LifecycleState
import com.tinder.scarlet.Scarlet
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import com.tinder.scarlet.lifecycle.android.AndroidLifecycle
import com.tinder.scarlet.messageadapter.moshi.MoshiMessageAdapter
import com.tinder.scarlet.websocket.ShutdownReason
import com.tinder.scarlet.websocket.WebSocketEvent
import com.tinder.scarlet.websocket.okhttp.OkHttpWebSocket
import com.tinder.streamadapter.coroutines.CoroutinesStreamAdapterFactory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class ProvidesMastodonStreaming @Inject constructor(
    application: Application,
    api: MastodonApi,
    okHttpClient: OkHttpClient,
    moshi: Moshi,
    private val accountManager: AccountManager,
    private val sharedPreferences: SharedPreferences,
    private val eventHub: EventHub,
    @ApplicationScope private val externalScope: CoroutineScope,
) {

    private val activeAccount = accountManager.activeAccount!!

    private val request = externalScope.async(start = CoroutineStart.LAZY) {
        val baseUrl = api.getInstance()
            .map { it.configuration?.urls?.streaming ?: "wss://${it.domain}" }
            .getOrElse { "wss://${activeAccount.domain}" }

        Request.Builder()
            .url("$baseUrl/api/v1/streaming")
            .header("Authorization", "Bearer ${activeAccount.accessToken}")
            .build()
    }

    val lifecycle = LifecycleRegistry()

    private val streaming = Scarlet(
        OkHttpWebSocket(
            okHttpClient = okHttpClient,
            requestFactory = OkHttpWebSocket.SimpleRequestFactory(
                { runBlocking { request.await() } },
                { ShutdownReason.GRACEFUL },
            ),
        ),
        Scarlet.Configuration(
            lifecycle = AndroidLifecycle.ofApplicationForeground(application).combineWith(lifecycle),
            messageAdapterFactories = listOf(MoshiMessageAdapter.Factory(moshi)),
            streamAdapterFactories = listOf(CoroutinesStreamAdapterFactory()),
        )
    ).create<MastodonStreaming>()

    private var subscribed: Set<StreamType> = emptySet()

    fun get() = streaming

    init {
        updateState()
        subscribeToPreferenceChanges()

        subscribeToWebSocketEvents()
        subscribeToAccountChanges()
    }

    private fun updateState() {
        val state = when (sharedPreferences.getBoolean(PrefKeys.GLOBAL_STREAMING_ENABLED, false)) {
            true -> LifecycleState.Started
            false -> LifecycleState.Stopped
        }
        lifecycle.onNext(state)
    }

    private fun subscribeToPreferenceChanges() {
        externalScope.launch {
            eventHub.events
                .filter { it is PreferenceChangedEvent }
                .collect { updateState() }
        }
    }

    private fun updateSubscriptions(account: AccountEntity) {
        val expected = setOf(StreamType.User)

        (subscribed - expected).forEach { type ->
            Log.d(TAG, "Unsubscribing from ${type.stream}")
            streaming.send(type.unsubscribeMessage())
        }

        (expected - subscribed).forEach { type ->
            Log.d(TAG, "Subscribing to ${type.stream}")
            streaming.send(type.subscribeMessage())
        }

        subscribed = expected
    }

    private fun subscribeToWebSocketEvents() {
        externalScope.launch {
            streaming.webSocketEvents()
                .collect {
                    when (it) {
                        is WebSocketEvent.OnConnectionOpened -> {
                            Log.d(TAG, "WebSocket connection opened")
                            updateSubscriptions(activeAccount)
                        }
                        is WebSocketEvent.OnConnectionClosed -> {
                            Log.d(TAG, "WebSocket connection closed: ${it.shutdownReason.code} - ${it.shutdownReason.reason}")
                            subscribed = emptySet()
                        }
                        is WebSocketEvent.OnConnectionFailed -> {
                            Log.w(TAG, "WebSocket connection failed", it.throwable)
                            subscribed = emptySet()
                        }
                        else -> {}
                    }
                }
        }
    }

    private fun subscribeToAccountChanges() {
        externalScope.launch {
            accountManager.activeAccount(externalScope)
                .collect {
                    if (it != null) {
                        updateSubscriptions(it)
                    }
                }
        }
    }

    companion object {
        private const val TAG = "MastodonStreaming"
    }
}

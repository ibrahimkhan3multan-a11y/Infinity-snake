package com.ncorti.kotlin.template.app.net

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/** Where the game logic sends relay frames. Faked in unit tests. */
interface NetLink {
    fun send(frame: JSONObject)
    fun close()
}

/**
 * WebSocket connection to the relay server. Frames arrive on OkHttp's threads and are parked in
 * [inbox]; the game loop drains it on the main thread, so no game code needs locking.
 * Besides relay frames, the inbox gets `{t:"_open"}` and `{t:"_closed", reason}` markers.
 */
class RelayClient(url: String) : NetLink {
    val inbox = ConcurrentLinkedQueue<JSONObject>()

    @Volatile
    var closed = false
        private set

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var ws: WebSocket? = null
    private val request = Request.Builder().url(normalize(url)).build()

    fun connect() {
        ws = http.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    inbox.add(JSONObject().put("t", "_open"))
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        inbox.add(JSONObject(text))
                    } catch (_: Exception) {
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = markClosed("Connection closed")

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) =
                    markClosed(t.message ?: "Connection failed")
            },
        )
    }

    private fun markClosed(reason: String) {
        if (closed) return
        closed = true
        inbox.add(JSONObject().put("t", "_closed").put("reason", reason))
    }

    override fun send(frame: JSONObject) {
        ws?.send(frame.toString())
    }

    override fun close() {
        closed = true
        ws?.close(1000, null)
        http.dispatcher.executorService.shutdown()
    }

    companion object {
        /** Accepts "host:8080", "ws://host", "https://host" and makes it a ws/wss URL. */
        fun normalize(raw: String): String {
            val s = raw.trim()
            return when {
                s.startsWith("ws://") || s.startsWith("wss://") -> s
                s.startsWith("https://") -> "wss://" + s.removePrefix("https://")
                s.startsWith("http://") -> "ws://" + s.removePrefix("http://")
                else -> "ws://$s"
            }
        }
    }
}

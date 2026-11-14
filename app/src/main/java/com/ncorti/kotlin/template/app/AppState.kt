package com.ncorti.kotlin.template.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ncorti.kotlin.template.app.audio.AudioEngine
import com.ncorti.kotlin.template.app.audio.Haptics
import com.ncorti.kotlin.template.app.audio.MusicMode
import com.ncorti.kotlin.template.app.audio.Sfx
import com.ncorti.kotlin.template.app.data.Settings
import com.ncorti.kotlin.template.app.game.Powers
import com.ncorti.kotlin.template.app.game.Skins
import com.ncorti.kotlin.template.app.net.LocalRelay
import com.ncorti.kotlin.template.app.net.Protocol
import com.ncorti.kotlin.template.app.net.RelayClient
import org.json.JSONObject

enum class Screen { MENU, PLAYING, SKINS, SHOP, SETTINGS, HOWTO, ONLINE }

/**
 * Process-wide UI state. The activity handles configuration changes itself (see the manifest), so
 * this object, and the running [GameSession] inside it, survive rotation untouched.
 */
class AppState(
    val settings: Settings,
    val audio: AudioEngine,
    private val haptics: Haptics,
) {
    var screen by mutableStateOf(Screen.MENU)
        private set
    var session by mutableStateOf<GameSession?>(null)
        private set

    init {
        audio.soundEnabled = settings.sound
        audio.musicEnabled = settings.music
    }

    // ---- Online rooms

    private class Pending(
        val client: RelayClient,
        val request: JSONObject,
        val kind: String,
        val bots: Int,
        val address: String? = null,
    )

    private var pending: Pending? = null
    private var lan: LocalRelay? = null

    /** What the Online screen shows while connecting, or why the last attempt failed. */
    var onlineStatus by mutableStateOf("")
        private set
    var onlineBusy by mutableStateOf(false)
        private set

    /** kind: "host" (private room), "quick" (public room) or "join" (needs [code]). [url] defaults to the saved server. */
    fun connectOnline(kind: String, code: String = "", bots: Int = 0, url: String = settings.serverUrl) {
        if (onlineBusy) return
        if (url.isBlank()) {
            onlineStatus = "Enter the server address first."
            return
        }
        val request = JSONObject().put("name", settings.playerName)
        when (kind) {
            "host" -> request.put("t", "host").put("public", false)
            "quick" -> request.put("t", "quick")
            else -> request.put("t", "join").put("code", code.trim().uppercase())
        }
        begin(url, request, kind, bots, null)
    }

    /** Hosts a room on this phone itself: friends on the same Wi-Fi or hotspot join by address and code. */
    fun hostOnWifi(bots: Int) {
        if (onlineBusy) return
        val ip = LocalRelay.localAddress()
        if (ip == null) {
            onlineStatus = "No Wi-Fi or hotspot found. Connect to Wi-Fi (or turn on your hotspot) first."
            return
        }
        val relay = try {
            LocalRelay().also { it.start() }
        } catch (e: Exception) {
            try {
                LocalRelay(0).also { it.start() }
            } catch (e2: Exception) {
                onlineStatus = "Couldn't start hosting on this phone."
                return
            }
        }
        lan = relay
        val request = JSONObject().put("name", settings.playerName).put("t", "host").put("public", false)
        val address = if (relay.port == LocalRelay.DEFAULT_PORT) ip else "$ip:${relay.port}"
        begin("127.0.0.1:${relay.port}", request, "host", bots, address)
    }

    private fun begin(url: String, request: JSONObject, kind: String, bots: Int, address: String?) {
        val client = try {
            RelayClient(url)
        } catch (e: Exception) {
            stopLan()
            onlineStatus = "That server address doesn't look right."
            return
        }
        onlineBusy = true
        onlineStatus = "Connecting…"
        pending = Pending(client, request, kind, bots, address)
        client.connect()
    }

    private fun stopLan() {
        lan?.stop()
        lan = null
    }

    fun cancelOnline() {
        val p = pending
        p?.client?.close()
        pending = null
        onlineBusy = false
        if (p != null) stopLan()
    }

    /** Called ~20x a second by the Online screen while a connection attempt is running. */
    fun pollOnline() {
        val p = pending ?: return
        while (true) {
            val f = p.client.inbox.poll() ?: return
            when (f.optString("t")) {
                "_open" -> {
                    onlineStatus = "Connected, entering room…"
                    p.client.send(p.request)
                }
                "hosted" -> {
                    pending = null
                    onlineBusy = false
                    startOnline(OnlineRoom(p.client, f.optString("code"), true, bots = p.bots, address = p.address))
                    return
                }
                "joined" -> {
                    pending = null
                    onlineBusy = false
                    val skin = Skins.byId(settings.selectedSkin)
                    val hello = Protocol.hello(
                        settings.playerName, skin.head, skin.tail, skin.glow, skin.rainbow, settings.tankLevel,
                        IntArray(Powers.all.size) { settings.powerLevel(Powers.all[it].id) },
                    )
                    startOnline(OnlineRoom(p.client, f.optString("code"), false, hello = hello))
                    return
                }
                "error" -> {
                    onlineStatus = f.optString("msg", "Error")
                    cancelOnline()
                    return
                }
                "_closed" -> {
                    onlineStatus = "Couldn't connect: ${f.optString("reason")}"
                    cancelOnline()
                    return
                }
            }
        }
    }

    private fun startOnline(room: OnlineRoom) {
        session?.close()
        audio.musicMode = MusicMode.GAME
        audio.play(Sfx.START)
        session = GameSession(this, false, room)
        screen = Screen.PLAYING
    }

    fun startGame(solo: Boolean) {
        session?.close()
        stopLan()
        settings.updateSoloMode(solo)
        audio.musicMode = MusicMode.GAME
        audio.play(Sfx.START)
        session = GameSession(this, solo)
        screen = Screen.PLAYING
    }

    fun toMenu() {
        session?.close()
        stopLan()
        audio.musicMode = MusicMode.MENU
        session = null
        screen = Screen.MENU
    }

    fun open(target: Screen) {
        screen = target
    }

    /** Back button on the secondary screens. */
    fun back() {
        if (screen != Screen.MENU && screen != Screen.PLAYING) screen = Screen.MENU
    }

    fun vibrate(ms: Long) {
        if (settings.vibration) haptics.buzz(ms)
    }

    fun setSound(v: Boolean) {
        settings.updateSound(v)
        audio.soundEnabled = v
    }

    fun setMusic(v: Boolean) {
        settings.updateMusic(v)
        audio.musicEnabled = v
    }

    /** App went to the background or lost focus: pause a running game. */
    fun onBackground() {
        audio.foreground = false
        session?.let { if (!it.over && !it.isOnline) it.paused = true }
    }

    fun onForeground() {
        audio.foreground = true
    }
}

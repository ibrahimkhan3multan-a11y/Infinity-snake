package com.ncorti.kotlin.template.app.net

import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import kotlin.concurrent.thread
import okio.ByteString.Companion.encodeUtf8
import org.json.JSONObject

/**
 * The same room-code relay as server/relay.js, but running inside the app on the host's phone.
 * It lets two phones on the same Wi-Fi (or hotspot) play together with no internet server at all:
 * the host's own game connects to it through localhost and friends connect to the phone's address.
 */
class LocalRelay(port: Int = DEFAULT_PORT) {
    private class Room(val code: String, val host: Client) {
        val guests = LinkedHashMap<Int, Client>()
        var nextId = 1
    }

    private class Client(val socket: Socket) {
        var room: Room? = null
        var id = -1
        var name = ""
        private val out: OutputStream = socket.getOutputStream()

        fun sendText(text: String) = sendFrame(0x1, text.toByteArray(Charsets.UTF_8))

        fun sendFrame(opcode: Int, payload: ByteArray) {
            synchronized(out) {
                try {
                    val len = payload.size
                    out.write(0x80 or opcode)
                    when {
                        len < 126 -> out.write(len)
                        len < 65536 -> {
                            out.write(126)
                            out.write(len shr 8)
                            out.write(len and 0xFF)
                        }
                        else -> {
                            out.write(127)
                            for (shift in 56 downTo 0 step 8) out.write((len.toLong() shr shift).toInt() and 0xFF)
                        }
                    }
                    out.write(payload)
                    out.flush()
                } catch (_: Exception) {
                    runCatching { socket.close() }
                }
            }
        }
    }

    private val server = ServerSocket(port)
    val port: Int get() = server.localPort

    private val lock = Any()
    private val rooms = HashMap<String, Room>()
    private val clients = HashSet<Client>()
    @Volatile private var stopped = false
    private val rnd = SecureRandom()

    fun start() {
        thread(name = "local-relay-accept", isDaemon = true) {
            while (!stopped) {
                val s = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                thread(name = "local-relay-client", isDaemon = true) { serve(s) }
            }
        }
    }

    fun stop() {
        stopped = true
        runCatching { server.close() }
        synchronized(lock) { clients.toList() }.forEach { runCatching { it.socket.close() } }
    }

    // ---------------------------------------------------------------- connection

    private fun serve(socket: Socket) {
        val client = Client(socket)
        synchronized(lock) { clients.add(client) }
        try {
            socket.tcpNoDelay = true
            val input = socket.getInputStream()
            if (!handshake(input, client)) return
            readFrames(input, client)
        } catch (_: Exception) {
        } finally {
            synchronized(lock) {
                clients.remove(client)
                leave(client)
            }
            runCatching { socket.close() }
        }
    }

    private fun handshake(input: InputStream, client: Client): Boolean {
        val sb = StringBuilder()
        while (!sb.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b < 0 || sb.length > 8192) return false
            sb.append(b.toChar())
        }
        val key = sb.lines().firstOrNull { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
            ?.substringAfter(":")?.trim() ?: return false
        val accept = (key + GUID).encodeUtf8().sha1().base64()
        val reply = "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: $accept\r\n\r\n"
        synchronized(client.socket.getOutputStream()) {
            client.socket.getOutputStream().write(reply.toByteArray())
            client.socket.getOutputStream().flush()
        }
        return true
    }

    private fun readFully(input: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n)
        var off = 0
        while (off < n) {
            val r = input.read(buf, off, n - off)
            if (r < 0) throw java.io.EOFException()
            off += r
        }
        return buf
    }

    private fun readFrames(input: InputStream, client: Client) {
        var message = java.io.ByteArrayOutputStream()
        while (true) {
            val h = readFully(input, 2)
            val fin = (h[0].toInt() and 0x80) != 0
            val opcode = h[0].toInt() and 0x0F
            val masked = (h[1].toInt() and 0x80) != 0
            var len = (h[1].toInt() and 0x7F).toLong()
            if (len == 126L) {
                val e = readFully(input, 2)
                len = (((e[0].toInt() and 0xFF) shl 8) or (e[1].toInt() and 0xFF)).toLong()
            } else if (len == 127L) {
                val e = readFully(input, 8)
                len = 0
                for (b in e) len = (len shl 8) or (b.toLong() and 0xFF)
            }
            if (!masked || len > MAX_MESSAGE) return
            val mask = readFully(input, 4)
            val data = readFully(input, len.toInt())
            for (i in data.indices) data[i] = (data[i].toInt() xor mask[i and 3].toInt()).toByte()
            when (opcode) {
                0x8 -> {
                    client.sendFrame(0x8, ByteArray(0))
                    return
                }
                0x9 -> client.sendFrame(0xA, data)
                0xA -> {}
                0x1, 0x0 -> {
                    if (opcode == 0x1) message = java.io.ByteArrayOutputStream()
                    message.write(data)
                    if (message.size() > MAX_MESSAGE) return
                    if (fin) {
                        val text = message.toString("UTF-8")
                        message = java.io.ByteArrayOutputStream()
                        synchronized(lock) { handle(client, text) }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- rooms (mirrors server/relay.js)

    private fun send(c: Client, obj: JSONObject) = c.sendText(obj.toString())

    private fun newCode(): String {
        while (true) {
            val c = String(CharArray(4) { CODE_CHARS[rnd.nextInt(CODE_CHARS.length)] })
            if (!rooms.containsKey(c)) return c
        }
    }

    private fun cleanName(n: String?): String =
        (n ?: "Player").filter { it.isLetterOrDigit() || it in " -._" }.trim().take(14).ifEmpty { "Player" }

    private fun leave(c: Client) {
        val room = c.room ?: return
        c.room = null
        if (room.host === c) {
            rooms.remove(room.code)
            for (g in room.guests.values) {
                g.room = null
                send(g, JSONObject().put("t", "closed"))
            }
        } else {
            room.guests.remove(c.id)
            send(room.host, JSONObject().put("t", "peer").put("id", c.id).put("name", c.name).put("join", false))
        }
    }

    private fun createRoom(c: Client, name: String) {
        val room = Room(newCode(), c)
        rooms[room.code] = room
        c.room = room
        c.id = 0
        c.name = name
        send(c, JSONObject().put("t", "hosted").put("code", room.code))
    }

    private fun joinRoom(c: Client, room: Room, name: String) {
        if (room.guests.size + 1 >= MAX_PLAYERS) return send(c, error("Room is full"))
        c.room = room
        c.id = room.nextId++
        c.name = name
        room.guests[c.id] = c
        send(c, JSONObject().put("t", "joined").put("id", c.id).put("code", room.code).put("hostName", room.host.name))
        send(room.host, JSONObject().put("t", "peer").put("id", c.id).put("name", name).put("join", true))
    }

    private fun error(msg: String) = JSONObject().put("t", "error").put("msg", msg)

    private fun handle(c: Client, text: String) {
        val msg = try {
            JSONObject(text)
        } catch (_: Exception) {
            return
        }
        when (msg.optString("t")) {
            "host" -> {
                if (c.room != null) return send(c, error("Already in a room"))
                createRoom(c, cleanName(msg.optString("name")))
            }
            "join" -> {
                if (c.room != null) return send(c, error("Already in a room"))
                val room = rooms[msg.optString("code").trim().uppercase()] ?: return send(c, error("No room with that code"))
                joinRoom(c, room, cleanName(msg.optString("name")))
            }
            "quick" -> {
                if (c.room != null) return send(c, error("Already in a room"))
                val name = cleanName(msg.optString("name"))
                val open = rooms.values.firstOrNull { it.guests.size + 1 < MAX_PLAYERS }
                if (open != null) joinRoom(c, open, name) else createRoom(c, name)
            }
            "m" -> {
                val room = c.room ?: return
                val payload = msg.opt("m")
                if (room.host === c) {
                    val to = msg.opt("to")
                    if (to == "all") {
                        val out = JSONObject().put("t", "m").put("m", payload).toString()
                        for (g in room.guests.values) g.sendText(out)
                    } else if (to is Int) {
                        room.guests[to]?.let { send(it, JSONObject().put("t", "m").put("m", payload)) }
                    }
                } else {
                    send(room.host, JSONObject().put("t", "m").put("from", c.id).put("m", payload))
                }
            }
        }
    }

    companion object {
        const val DEFAULT_PORT = 8765
        const val MAX_PLAYERS = 8
        private const val MAX_MESSAGE = 256 * 1024
        private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

        /** This phone's address on the local Wi-Fi / hotspot, or null when it has none. */
        fun localAddress(): String? {
            val found = ArrayList<Pair<String, String>>()
            try {
                for (nif in NetworkInterface.getNetworkInterfaces()) {
                    if (!nif.isUp || nif.isLoopback) continue
                    for (a in nif.inetAddresses) {
                        if (a is Inet4Address && a.isSiteLocalAddress) found.add(nif.name to a.hostAddress.orEmpty())
                    }
                }
            } catch (_: Exception) {
            }
            val wifi = found.firstOrNull { it.first.startsWith("wlan") || it.first.startsWith("ap") || it.first.startsWith("swlan") }
            return (wifi ?: found.firstOrNull())?.second?.takeIf { it.isNotEmpty() }
        }
    }
}

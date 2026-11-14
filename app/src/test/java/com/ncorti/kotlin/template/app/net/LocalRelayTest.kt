package com.ncorti.kotlin.template.app.net

import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The in-app relay used for same-Wi-Fi play, exercised over real sockets with the real client. */
class LocalRelayTest {
    private lateinit var relay: LocalRelay
    private val clients = ArrayList<RelayClient>()

    @Before
    fun start() {
        relay = LocalRelay(0)
        relay.start()
    }

    @After
    fun stop() {
        clients.forEach { runCatching { it.close() } }
        relay.stop()
    }

    private fun connect(): RelayClient {
        val c = RelayClient("127.0.0.1:${relay.port}")
        clients.add(c)
        c.connect()
        next(c, "_open")
        return c
    }

    private fun next(c: RelayClient, type: String, ms: Long = 5000): JSONObject {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            val f = c.inbox.poll()
            if (f != null && f.optString("t") == type) return f
            if (f == null) Thread.sleep(10)
        }
        throw AssertionError("no '$type' frame")
    }

    @Test
    fun hostAndGuestTalkThroughThePhoneRelay() {
        val host = connect()
        host.send(JSONObject().put("t", "host").put("name", "Me"))
        val code = next(host, "hosted").getString("code")
        assertTrue(Regex("[A-Z2-9]{4}").matches(code))

        val guest = connect()
        guest.send(JSONObject().put("t", "join").put("code", code.lowercase()).put("name", "Bro"))
        val joined = next(guest, "joined")
        assertEquals(1, joined.getInt("id"))
        val peer = next(host, "peer")
        assertEquals("Bro", peer.getString("name"))

        guest.send(JSONObject().put("t", "m").put("m", JSONObject().put("a", 2)))
        val got = next(host, "m")
        assertEquals(1, got.getInt("from"))
        assertEquals(2, got.getJSONObject("m").getInt("a"))

        host.send(JSONObject().put("t", "m").put("to", 1).put("m", JSONObject().put("x", "y")))
        assertEquals("y", next(guest, "m").getJSONObject("m").getString("x"))

        // A large message (like a snapshot) must survive framing.
        val big = "z".repeat(70_000)
        host.send(JSONObject().put("t", "m").put("to", "all").put("m", big))
        assertEquals(big, next(guest, "m").getString("m"))

        host.close()
        next(guest, "closed")
    }

    @Test
    fun wrongCodeGivesAnError() {
        val guest = connect()
        guest.send(JSONObject().put("t", "join").put("code", "ZZZZ").put("name", "Bro"))
        assertEquals("No room with that code", next(guest, "error").getString("msg"))
    }

    @Test
    fun aPlainIpAddressGetsTheWifiPort() {
        assertEquals("ws://192.168.1.5:${LocalRelay.DEFAULT_PORT}", RelayClient.normalize("192.168.1.5"))
        assertEquals("ws://192.168.1.5:9000", RelayClient.normalize("192.168.1.5:9000"))
    }
}

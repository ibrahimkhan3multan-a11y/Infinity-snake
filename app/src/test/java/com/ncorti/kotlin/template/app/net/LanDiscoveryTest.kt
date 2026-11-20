package com.ncorti.kotlin.template.app.net

import java.net.DatagramSocket
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanDiscoveryTest {
    @Test
    fun packetsRoundTrip() {
        val bytes = LanDiscovery.encode("Ali|x", 8765)
        assertEquals("Ali x" to 8765, LanDiscovery.decode(bytes, bytes.size))
        val junk = "hello".toByteArray()
        assertNull(LanDiscovery.decode(junk, junk.size))
    }

    @Test
    fun listenerFindsAnnouncer() {
        val port = DatagramSocket(0).use { it.localPort }
        val listener = LanDiscovery.Listener(port)
        listener.start()
        val announcer = LanDiscovery.Announcer("Brother", 8765, port, listOf(InetAddress.getByName("127.0.0.1")))
        announcer.start()
        try {
            val end = System.currentTimeMillis() + 8000
            var found = listener.hosts()
            while (found.isEmpty() && System.currentTimeMillis() < end) {
                Thread.sleep(50)
                found = listener.hosts()
            }
            assertTrue("host should be found", found.isNotEmpty())
            assertEquals("Brother", found[0].name)
            assertEquals("127.0.0.1", found[0].address)
        } finally {
            announcer.stop()
            listener.stop()
        }
    }
}

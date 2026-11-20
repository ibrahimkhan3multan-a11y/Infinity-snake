package com.ncorti.kotlin.template.app.net

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/** A game found on the local network. [address] is what the join button needs. */
data class FoundHost(val name: String, val address: String)

/**
 * Finds games on the same Wi-Fi with no typing. The host's phone shouts "I'm here" over UDP once a
 * second ([Announcer]); phones on the join screen listen for it ([Listener]).
 */
object LanDiscovery {
    const val PORT = 8766
    private const val MAGIC = "INFSNAKE1"

    internal fun encode(name: String, gamePort: Int): ByteArray =
        "$MAGIC|${name.replace('|', ' ')}|$gamePort".toByteArray(Charsets.UTF_8)

    /** Returns (name, gamePort) or null when the packet is not ours. */
    internal fun decode(data: ByteArray, len: Int): Pair<String, Int>? {
        val parts = String(data, 0, len, Charsets.UTF_8).split('|')
        if (parts.size != 3 || parts[0] != MAGIC) return null
        val port = parts[2].toIntOrNull() ?: return null
        return parts[1].take(14).ifEmpty { "Player" } to port
    }

    private fun broadcastTargets(): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        try {
            for (nif in NetworkInterface.getNetworkInterfaces()) {
                if (!nif.isUp || nif.isLoopback) continue
                for (ia in nif.interfaceAddresses) ia.broadcast?.let { out.add(it) }
            }
        } catch (_: Exception) {
        }
        runCatching { out.add(InetAddress.getByName("255.255.255.255")) }
        return out.toList()
    }

    class Announcer(
        private val name: String,
        private val gamePort: Int,
        private val discoveryPort: Int = PORT,
        private val extraTargets: List<InetAddress> = emptyList(),
    ) {
        @Volatile private var stopped = false

        fun start() {
            thread(name = "lan-announce", isDaemon = true) {
                val socket = try {
                    DatagramSocket().apply { broadcast = true }
                } catch (_: Exception) {
                    return@thread
                }
                try {
                    val payload = encode(name, gamePort)
                    while (!stopped) {
                        for (t in broadcastTargets() + extraTargets) {
                            try {
                                socket.send(DatagramPacket(payload, payload.size, t, discoveryPort))
                            } catch (_: Exception) {
                            }
                        }
                        Thread.sleep(1000)
                    }
                } catch (_: InterruptedException) {
                } finally {
                    socket.close()
                }
            }
        }

        fun stop() {
            stopped = true
        }
    }

    class Listener(private val discoveryPort: Int = PORT, private val ownAddress: () -> String? = { null }) {
        private class Seen(val host: FoundHost, var at: Long)

        private val seen = ConcurrentHashMap<String, Seen>()
        @Volatile private var stopped = false
        @Volatile private var socket: DatagramSocket? = null

        fun start() {
            thread(name = "lan-listen", isDaemon = true) {
                val s = try {
                    DatagramSocket(null).apply {
                        reuseAddress = true
                        bind(java.net.InetSocketAddress(discoveryPort))
                        soTimeout = 500
                    }
                } catch (_: Exception) {
                    return@thread
                }
                socket = s
                val buf = ByteArray(256)
                try {
                    while (!stopped) {
                        val p = DatagramPacket(buf, buf.size)
                        try {
                            s.receive(p)
                        } catch (_: java.net.SocketTimeoutException) {
                            continue
                        }
                        val (name, port) = decode(p.data, p.length) ?: continue
                        val ip = p.address.hostAddress ?: continue
                        if (ip == ownAddress()) continue
                        val address = if (port == LocalRelay.DEFAULT_PORT) ip else "$ip:$port"
                        seen[ip] = Seen(FoundHost(name, address), System.currentTimeMillis())
                    }
                } catch (_: Exception) {
                } finally {
                    s.close()
                }
            }
        }

        /** Hosts heard in the last few seconds, newest information first. */
        fun hosts(): List<FoundHost> {
            val now = System.currentTimeMillis()
            seen.entries.removeIf { now - it.value.at > 4000 }
            return seen.values.map { it.host }.sortedBy { it.name }
        }

        fun stop() {
            stopped = true
            socket?.close()
        }
    }
}

package com.ncorti.kotlin.template.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.ncorti.kotlin.template.app.game.Cfg
import com.ncorti.kotlin.template.app.game.Skins
import com.ncorti.kotlin.template.app.game.Snake
import com.ncorti.kotlin.template.app.game.World
import com.ncorti.kotlin.template.app.game.WorldListener
import com.ncorti.kotlin.template.app.net.GuestLogic
import com.ncorti.kotlin.template.app.net.HostLogic
import com.ncorti.kotlin.template.app.net.Protocol
import com.ncorti.kotlin.template.app.net.RelayClient
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.random.Random
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real network round trips through the real relay server (CI starts `node server/relay.js` on the
 * runner; the emulator reaches it at 10.0.2.2). Skipped when no relay is reachable.
 */
@RunWith(AndroidJUnit4::class)
class OnlineFlowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val server = "10.0.2.2:8080"

    @Before
    fun setUp() {
        assumeTrue("relay server reachable", relayReachable())
        device.setOrientationNatural()
        context.getSharedPreferences("infinity_snake", Context.MODE_PRIVATE).edit().clear().commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("PLAY")
    }

    @After
    fun tearDown() {
        if (this::scenario.isInitialized) runCatching { scenario.close() }
        device.unfreezeRotation()
    }

    private fun relayReachable(): Boolean = try {
        Socket().use { it.connect(InetSocketAddress("10.0.2.2", 8080), 1500) }
        true
    } catch (e: Exception) {
        false
    }

    private fun waitFor(text: String, timeoutMs: Long = 15_000) {
        assertTrue("'$text' should be on screen", device.wait(Until.hasObject(By.text(text)), timeoutMs))
    }

    private fun click(text: String) {
        waitFor(text)
        repeat(5) {
            try {
                device.findObject(By.text(text)).click()
                return
            } catch (e: androidx.test.uiautomator.StaleObjectException) {
                Thread.sleep(300) // the screen recomposed between find and click; look again
            }
        }
        throw AssertionError("could not click '$text'")
    }

    private fun <T> onApp(block: (AppState) -> T): T {
        var result: Any? = null
        scenario.onActivity { result = block(it.app) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun shot(name: String) {
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: "/sdcard/Android/media/${context.packageName}/additional_test_output"
        File(dir).mkdirs()
        device.takeScreenshot(File(dir, "$name.png"))
    }

    private fun await(client: RelayClient, type: String, ms: Long = 10_000): JSONObject {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            val f = client.inbox.poll()
            if (f != null) {
                if (f.optString("t") == type) return f
                if (f.optString("t") == "error" || f.optString("t") == "_closed") throw AssertionError("relay said $f")
            }
            Thread.sleep(20)
        }
        throw AssertionError("no '$type' frame from relay within ${ms}ms")
    }

    private fun connectClient(): RelayClient {
        val c = RelayClient(server)
        c.connect()
        await(c, "_open")
        return c
    }

    @Test
    fun appHostsARoomAndARealGuestJoinsByCode() {
        onApp { it.settings.updateServerUrl(server) }
        click("ONLINE")
        waitFor("CREATE ROOM")
        shot("20_online_screen")
        click("CREATE ROOM")
        waitFor("BOOST")
        val code = onApp { it.session!!.room!!.code }
        assertTrue("4-letter code: $code", Regex("[A-Z2-9]{4}").matches(code))
        assertTrue("room label visible", device.wait(Until.hasObject(By.textStartsWith("ROOM $code")), 10_000))

        // A second player, over the real relay, using the same guest code the app ships.
        val client = connectClient()
        client.send(JSONObject().put("t", "join").put("code", code).put("name", "Friend"))
        await(client, "joined")
        val world = World(Random(1), 0, false, Skins.all[2], ambientFood = 0)
        val logic = GuestLogic(
            world, client,
            Protocol.hello("Friend", Skins.all[2].head, Skins.all[2].tail, false, false, 0, IntArray(5)),
            object : GuestLogic.Listener {
                override fun onKilled(victim: String) {}
                override fun onOver(score: Int, rank: Int, length: Int, kills: Int) {}
                override fun onRespawned() {}
            },
        )
        logic.sendHello()
        val end = System.currentTimeMillis() + 5_000
        var wasAlive = false
        while (System.currentTimeMillis() < end) {
            while (true) logic.handle(client.inbox.poll() ?: break)
            if (world.player.alive) wasAlive = true
            logic.update(0.016f)
            logic.sendInput(0.5f, false, 0.016f)
            Thread.sleep(16)
        }
        assertTrue("snapshots received over the network: ${logic.snapshots}", logic.snapshots > 20)
        assertTrue("guest snake was alive in the host arena", wasAlive)
        assertTrue("leaderboard mirrored", logic.board.isNotEmpty())
        assertTrue("host sees one guest", onApp { it.session!!.peerCount } == 1)
        assertTrue("roster mirrored (host + bots)", world.snakes.size > 1)
        assertTrue("guest steering reached the host", onApp { app ->
            app.session!!.world.snakes.firstOrNull { it.remote }?.let { kotlin.math.abs(it.angle - 0.5f) < 0.5f } ?: false
        })
        assertTrue("host label shows 2 online", device.wait(Until.hasObject(By.textContains("2 online")), 5_000))
        shot("21_hosting_with_guest")
        client.close()
        pad(1000)
        assertEquals("guest left, host sees nobody", 0, onApp { it.session!!.peerCount })
    }

    @Test
    fun appJoinsARoomHostedByAnotherPlayerAndCanPlay() {
        onApp { it.settings.updateServerUrl(server) }

        // Test process plays the host over the real relay, running a real arena.
        val hostClient = connectClient()
        hostClient.send(JSONObject().put("t", "host").put("name", "Hosty").put("public", false))
        val code = await(hostClient, "hosted").getString("code")
        val hostWorld = World(Random(7), 3, false, Skins.all[1])
        val host = HostLogic(hostWorld, hostClient)
        hostWorld.listener = object : WorldListener {
            override fun onSnakeDied(snake: Snake, killer: Snake?) = host.snakeDied(snake, killer)
        }
        val running = java.util.concurrent.atomic.AtomicBoolean(true)
        val loop = Thread {
            var last = System.nanoTime()
            var acc = 0f
            while (running.get()) {
                val now = System.nanoTime()
                val dt = ((now - last) / 1e9f).coerceAtMost(0.1f)
                last = now
                while (true) host.handle(hostClient.inbox.poll() ?: break)
                host.beforeStep()
                acc += dt
                while (acc >= Cfg.STEP) {
                    hostWorld.step(Cfg.STEP)
                    acc -= Cfg.STEP
                }
                host.afterFrame(dt)
                Thread.sleep(8)
            }
        }
        loop.start()
        try {
            click("ONLINE")
            waitFor("JOIN")
            typeCode(code)
            click("JOIN")
            waitFor("BOOST", 20_000)
            assertFalse("app is a guest", onApp { it.session!!.room!!.isHost })

            val deadline = System.currentTimeMillis() + 15_000
            var ready = false
            while (System.currentTimeMillis() < deadline && !ready) {
                ready = onApp { app ->
                    val s = app.session!!
                    s.world.player.alive && s.hudBoard.isNotEmpty() && s.world.snakes.size > 1
                }
                if (!ready) Thread.sleep(200)
            }
            assertTrue("guest app shows the host's arena", ready)
            assertTrue("host arena has the app's player", retry { hostWorld.snakes.any { it.remote && it.name.isNotEmpty() } })
            shot("22_guest_in_room")

            // Steering from the app reaches the host's copy of this snake.
            onApp { it.session!!.input.angle = 1.2f }
            Thread.sleep(1500)
            val remote = retry { hostWorld.snakes.first { it.remote } }
            assertTrue("host snake follows the app's steering: ${remote.angle}", kotlin.math.abs(remote.angle - 1.2f) < 0.5f)

            // Leaving closes the connection; the host sees the guest go.
            onApp { it.toMenu() }
            Thread.sleep(1500)
            assertTrue("host dropped the guest", retry { hostWorld.snakes.none { it.remote } })
        } finally {
            running.set(false)
            loop.join(2000)
            hostClient.close()
        }
    }

    @Test
    fun appHostsOnWifiWithoutAnyServerAndAGuestJoinsItsAddress() {
        assumeTrue("device has a Wi-Fi style address", com.ncorti.kotlin.template.app.net.LocalRelay.localAddress() != null)
        click("ONLINE")
        waitFor("HOST ON WI-FI") // no server saved, so the Wi-Fi tab is shown
        val myIp = com.ncorti.kotlin.template.app.net.LocalRelay.localAddress()!!
        assertTrue("host panel shows this phone's address ($myIp) before starting", device.wait(Until.hasObject(By.textContains(myIp)), 10_000))
        shot("24_wifi_tab")
        click("HOST ON WI-FI")
        waitFor("BOOST")
        val room = onApp { it.session!!.room!! }
        assertTrue("address shown for friends: ${room.address}", !room.address.isNullOrEmpty())
        assertTrue("room label shows address", device.wait(Until.hasObject(By.textContains(room.address!!)), 10_000))

        // The guest connects the way a second phone would, by address (here via localhost on the same device).
        val client = RelayClient("127.0.0.1:${com.ncorti.kotlin.template.app.net.LocalRelay.DEFAULT_PORT}")
        client.connect()
        await(client, "_open")
        client.send(JSONObject().put("t", "quick").put("name", "Bro")) // address only, no room code
        await(client, "joined")
        val world = World(Random(1), 0, false, Skins.all[2], ambientFood = 0)
        val logic = GuestLogic(
            world, client,
            Protocol.hello("Bro", Skins.all[2].head, Skins.all[2].tail, false, false, 0, IntArray(5)),
            object : GuestLogic.Listener {
                override fun onKilled(victim: String) {}
                override fun onOver(score: Int, rank: Int, length: Int, kills: Int) {}
                override fun onRespawned() {}
            },
        )
        logic.sendHello()
        val end = System.currentTimeMillis() + 4_000
        while (System.currentTimeMillis() < end) {
            while (true) logic.handle(client.inbox.poll() ?: break)
            logic.update(0.016f)
            logic.sendInput(0f, false, 0.016f)
            Thread.sleep(16)
        }
        assertTrue("snapshots over the phone's own relay: ${logic.snapshots}", logic.snapshots > 20)
        assertEquals("host counts the guest", 1, onApp { it.session!!.peerCount })
        shot("25_wifi_hosting")
        client.close()
    }

    @Test
    fun joiningAWifiAddressWhereNobodyHostsExplainsWhy() {
        val relay = com.ncorti.kotlin.template.app.net.LocalRelay(0)
        relay.start()
        try {
            click("ONLINE")
            waitFor("JOIN ON WI-FI")
            onApp { it.joinOnWifi("127.0.0.1:${relay.port}") }
            assertTrue("explains nobody is hosting", device.wait(Until.hasObject(By.textContains("No game is running")), 15_000))
        } finally {
            relay.stop()
        }
    }

    @Test
    fun joiningAnUnknownCodeShowsAnError() {
        onApp { it.settings.updateServerUrl(server) }
        click("ONLINE")
        waitFor("JOIN")
        typeCode("ZZZZ")
        click("JOIN")
        waitFor("No room with that code")
        shot("23_unknown_code")
    }

    /** Types into the room-code field (the second text field), re-finding it if the UI refreshed. */
    private fun typeCode(code: String) {
        repeat(5) {
            try {
                val fields = device.wait(Until.findObjects(By.clazz("android.widget.EditText")), 8_000)
                assertTrue("server + code fields", fields.size >= 2)
                fields[1].text = code
                return
            } catch (e: androidx.test.uiautomator.StaleObjectException) {
                Thread.sleep(300)
            }
        }
        throw AssertionError("could not type the room code")
    }

    private fun pad(ms: Long) = Thread.sleep(ms)

    /** The test reads a world that another thread is stepping; retry if its list changes mid-read. */
    private fun <T> retry(block: () -> T): T {
        repeat(10) {
            try {
                return block()
            } catch (e: ConcurrentModificationException) {
                Thread.sleep(10)
            }
        }
        return block()
    }
}

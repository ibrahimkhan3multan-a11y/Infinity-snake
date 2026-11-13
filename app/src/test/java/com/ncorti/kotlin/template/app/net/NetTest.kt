package com.ncorti.kotlin.template.app.net

import com.ncorti.kotlin.template.app.game.Cfg
import com.ncorti.kotlin.template.app.game.Powers
import com.ncorti.kotlin.template.app.game.Skins
import com.ncorti.kotlin.template.app.game.Snake
import com.ncorti.kotlin.template.app.game.World
import com.ncorti.kotlin.template.app.game.WorldListener
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host and guest wired together in memory (no sockets), exactly as the relay would connect them. */
class NetTest {
    private class Room(bots: Int = 2, walls: Boolean = false) {
        val hostWorld = World(Random(3), botCount = bots, deadlyWalls = walls, playerSkin = Skins.all[0])
        val guestWorld = World(Random(4), botCount = 0, deadlyWalls = false, playerSkin = Skins.all[1], ambientFood = 0)
        lateinit var guest: GuestLogic
        val killed = ArrayList<String>()
        var over: IntArray? = null
        var respawned = 0

        val host = HostLogic(
            hostWorld,
            object : NetLink {
                override fun send(frame: JSONObject) {
                    // relay: {t:"m", to:1, m} -> guest receives {t:"m", m}
                    guest.handle(JSONObject().put("t", "m").put("m", frame.getJSONObject("m")))
                }
                override fun close() {}
            },
        )

        init {
            hostWorld.listener = object : WorldListener {
                override fun onSnakeDied(snake: Snake, killer: Snake?) = host.snakeDied(snake, killer)
            }
            guest = GuestLogic(
                guestWorld,
                object : NetLink {
                    override fun send(frame: JSONObject) {
                        // relay: guest {t:"m", m} -> host receives {t:"m", from:1, m}
                        host.handle(JSONObject().put("t", "m").put("from", 1).put("m", frame.getJSONObject("m")))
                    }
                    override fun close() {}
                },
                Protocol.hello("Bob", 0xFF00FF00.toInt(), 0xFF004400.toInt(), true, false, 2, IntArray(5) { 3 }),
                object : GuestLogic.Listener {
                    override fun onKilled(victim: String) { killed.add(victim) }
                    override fun onOver(score: Int, rank: Int, length: Int, kills: Int) { over = intArrayOf(score, rank, length, kills) }
                    override fun onRespawned() { respawned++ }
                },
            )
            host.handle(JSONObject().put("t", "peer").put("id", 1).put("name", "Bob").put("join", true))
            guest.sendHello()
        }

        fun run(seconds: Float, angle: Float? = null, boost: Boolean = false) {
            val dt = 1f / 60f
            repeat((seconds * 60).toInt()) {
                if (angle != null) guest.sendInput(angle, boost, dt)
                host.beforeStep()
                hostWorld.step(Cfg.STEP)
                host.afterFrame(dt)
                guest.update(dt)
            }
        }

        val remote: Snake get() = hostWorld.snakes.first { it.remote }
    }

    @Test
    fun guestJoinsAndSeesTheArena() {
        val r = Room()
        r.run(2f)
        assertTrue("snapshots arrive", r.guest.snapshots > 10)
        assertTrue(r.guestWorld.player.alive)
        assertEquals("start size is the standard size for everyone", Cfg.START_MASS, r.remote.mass, 6f)
        assertEquals(2, r.hostWorld.snakes.count { !it.isPlayer && !it.remote })
        // Guest sees its own body where the host has it.
        val me = r.guestWorld.player
        val host = r.remote
        assertTrue("positions match: ${me.xs[0]},${me.ys[0]} vs ${host.xs[0]},${host.ys[0]}", hypot(me.xs[0] - host.xs[0], me.ys[0] - host.ys[0]) < 25f)
        assertEquals(host.n, me.n)
        assertTrue("food was mirrored", r.guestWorld.aliveFood > 0)
        assertTrue("leaderboard mirrored", r.guest.board.isNotEmpty())
    }

    @Test
    fun guestSteeringAndBoostReachTheHostSnake() {
        val r = Room()
        r.run(1f)
        val target = 1.0f
        r.run(1.5f, angle = target, boost = true)
        assertTrue("host snake turned to the requested angle: ${r.remote.angle}", abs(r.remote.angle - target) < 0.3f)
        assertTrue("boost applied on host", r.remote.energy < r.remote.maxEnergy - 1f)
        assertEquals("tank upgrade level 2 applied", Powers.tankCapacity(2), r.remote.maxEnergy, 0.01f)
    }

    @Test
    fun guestPowersWorkWithHostSideCooldowns() {
        val r = Room()
        r.run(0.5f)
        r.guest.requestPower(0) // shield, level 3 in the hello
        r.run(0.5f)
        assertTrue("shield on host snake", r.remote.shield > 0f)
        val expected = Powers.SHIELD.duration(3)
        assertEquals(expected, r.remote.shield, 1.2f)
        assertTrue("guest HUD sees it active", r.guest.powers[0].first > 0)
        // Using it again right away does nothing (cooldown).
        val before = r.remote.shield
        r.guest.requestPower(0)
        r.run(0.1f)
        assertTrue(r.remote.shield <= before)
        r.run(Powers.SHIELD.duration(3) + 1f)
        assertTrue("now cooling down", r.guest.powers[0].second > 0)
    }

    @Test
    fun eliminationReportsToGuestAndRespawnBringsThemBack() {
        val r = Room(bots = 0, walls = true)
        r.run(0.2f)
        r.remote.grace = 0f
        r.remote.xs[0] = Cfg.ARENA_RADIUS + 20f
        r.run(0.5f)
        assertFalse(r.remote.alive)
        val o = r.over
        assertNotNull("guest told it was eliminated", o)
        r.guest.requestRespawn()
        r.run(1f)
        assertTrue(r.remote.alive)
        assertTrue("guest sees itself alive again", r.guestWorld.player.alive)
        assertEquals(1, r.respawned)
        assertEquals("respawn is standard size", Cfg.START_MASS, r.remote.mass, 3f)
    }

    @Test
    fun guestLeavingFreesTheSlotAndTurnsBodyIntoFood() {
        val r = Room(bots = 0)
        r.run(1f)
        val slot = r.remote
        val foodBefore = r.hostWorld.aliveFood
        r.host.handle(JSONObject().put("t", "peer").put("id", 1).put("join", false))
        assertTrue(slot.gone)
        assertFalse(slot.alive)
        assertTrue(r.hostWorld.aliveFood > foodBefore)
        // A new guest reuses the slot instead of growing the list forever.
        val count = r.hostWorld.snakes.size
        r.host.handle(JSONObject().put("t", "peer").put("id", 2).put("name", "Cy").put("join", true))
        r.host.handle(
            JSONObject().put("t", "m").put("from", 2)
                .put("m", Protocol.hello("Cy", 1, 2, false, false, 0, IntArray(5))),
        )
        assertEquals(count, r.hostWorld.snakes.size)
    }

    @Test
    fun bodyPackingKeepsShapeWithinAFewUnits() {
        val s = Snake(0, true, "t")
        s.place(0f, 0f, 0f, 120f) // long body
        for (i in 0 until s.n) { // bend it
            s.xs[i] = i * 8f
            s.ys[i] = kotlin.math.sin(i * 0.15f) * 40f
        }
        val packed: JSONArray = Protocol.packBody(s)
        assertTrue("downsampled to <= ${Protocol.MAX_SAMPLES} points", packed.length() / 2 <= Protocol.MAX_SAMPLES + 1)
        val ox = FloatArray(Cfg.MAX_SEGS)
        val oy = FloatArray(Cfg.MAX_SEGS)
        Protocol.unpackBody(s.n, packed, ox, oy)
        var worst = 0f
        for (i in 0 until s.n) worst = maxOf(worst, hypot(ox[i] - s.xs[i], oy[i] - s.ys[i]))
        assertTrue("max reconstruction error $worst", worst < 4f)
        assertEquals(s.xs[s.n - 1], ox[s.n - 1], 1f) // tail is exact
    }

    @Test
    fun serverAddressesAreNormalized() {
        assertEquals("ws://192.168.1.2:8080", RelayClient.normalize("192.168.1.2:8080"))
        assertEquals("wss://my.app", RelayClient.normalize("https://my.app"))
        assertEquals("ws://x.y", RelayClient.normalize(" http://x.y "))
        assertEquals("wss://x.y/z", RelayClient.normalize("wss://x.y/z"))
    }
}

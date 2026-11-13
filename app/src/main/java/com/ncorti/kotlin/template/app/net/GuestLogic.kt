package com.ncorti.kotlin.template.app.net

import com.ncorti.kotlin.template.app.BoardEntry
import com.ncorti.kotlin.template.app.game.Cfg
import com.ncorti.kotlin.template.app.game.Powers
import com.ncorti.kotlin.template.app.game.Snake
import com.ncorti.kotlin.template.app.game.World
import kotlin.math.exp
import org.json.JSONObject

/**
 * Runs on a guest's phone. It owns no simulation: it mirrors what the host sends into a [World]
 * (so the normal renderer, camera and HUD work unchanged) and eases the drawn bodies toward the
 * latest snapshot so the 15 Hz updates look smooth.
 */
class GuestLogic(
    private val world: World,
    private val link: NetLink,
    private val hello: JSONObject,
    private val listener: Listener,
) {
    interface Listener {
        fun onKilled(victim: String)
        fun onOver(score: Int, rank: Int, length: Int, kills: Int)
        fun onRespawned()
    }

    private class Ghost(val snake: Snake) {
        val tx = FloatArray(Cfg.MAX_SEGS)
        val ty = FloatArray(Cfg.MAX_SEGS)
        var tn = 0
        var fresh = true
    }

    private val ghosts = HashMap<Int, Ghost>()
    private val seen = HashSet<Int>()
    private var meId = -1
    private var sendTimer = 0f
    private var lastAngle = 99f
    private var lastBoost = false
    private var helloSent = false
    private var wasAlive = true
    private var lastMass = 0f

    // HUD values mirrored from the latest snapshot.
    var score = 0
        private set
    var length = 0
        private set
    var rank = 1
        private set
    var alive = 1
        private set
    var energy = 1f
        private set
    var maxEnergy = Cfg.MAX_ENERGY
        private set
    var board: List<BoardEntry> = emptyList()
        private set

    /** Per power: (active seconds, cooldown seconds). */
    val powers: Array<Pair<Int, Int>> = Array(Powers.all.size) { 0 to 0 }
    var snapshots = 0
        private set

    /** Our own snake in the mirrored world. */
    private val me: Snake get() = world.player

    init {
        world.player.alive = false
    }

    /** Called once the relay confirmed we joined: introduces ourselves to the host. */
    fun sendHello() {
        if (helloSent) return
        helloSent = true
        link.send(Protocol.frameToHost(hello))
    }

    fun handle(frame: JSONObject) {
        if (frame.optString("t") != "m") return
        val m = frame.optJSONObject("m") ?: return
        when (m.optString("k")) {
            "you" -> meId = m.optInt("id", -1)
            "roster" -> applyRoster(m)
            "s" -> applySnapshot(m)
            "over" -> listener.onOver(m.optInt("score"), m.optInt("rank"), m.optInt("len"), m.optInt("kills"))
            "kill" -> listener.onKilled(m.optString("name"))
        }
    }

    private fun ghostFor(id: Int): Ghost {
        if (id == meId) {
            return ghosts.getOrPut(id) { Ghost(world.player) }
        }
        return ghosts.getOrPut(id) {
            val s = Snake(world.snakes.size, false, "Player")
            world.snakes.add(s)
            Ghost(s)
        }
    }

    private fun applyRoster(m: JSONObject) {
        val r = m.optJSONArray("r") ?: return
        for (i in 0 until r.length()) {
            val e = r.optJSONArray(i) ?: continue
            val id = e.optInt(0)
            if (id == meId) continue // our own look is already known locally
            val s = ghostFor(id).snake
            s.name = e.optString(1)
            s.headColor = e.optInt(2)
            s.tailColor = e.optInt(3)
            val f = e.optInt(4)
            s.glow = f and Protocol.F_GLOW != 0
            s.rainbow = f and Protocol.F_RAINBOW != 0
        }
    }

    private fun applySnapshot(m: JSONObject) {
        meId = m.optInt("me", meId)
        snapshots++
        score = m.optInt("sc")
        length = m.optInt("ln")
        rank = m.optInt("rk")
        alive = m.optInt("al")
        energy = m.optDouble("en").toFloat()
        maxEnergy = m.optDouble("mx", Cfg.MAX_ENERGY.toDouble()).toFloat()
        me.energy = energy
        me.maxEnergy = maxEnergy

        m.optJSONArray("pw")?.let { pw ->
            for (i in powers.indices) {
                val e = pw.optJSONArray(i) ?: continue
                powers[i] = e.optInt(0) to e.optInt(1)
            }
        }
        m.optJSONArray("bd")?.let { bd ->
            val list = ArrayList<BoardEntry>(bd.length())
            for (i in 0 until bd.length()) {
                val e = bd.optJSONArray(i) ?: continue
                list.add(BoardEntry(e.optString(0), e.optInt(1), e.optInt(2) == 1))
            }
            board = list
        }

        seen.clear()
        val sn = m.optJSONArray("sn")
        if (sn != null) {
            for (i in 0 until sn.length()) {
                val e = sn.optJSONArray(i) ?: continue
                val id = e.optInt(0)
                val g = ghostFor(id)
                val s = g.snake
                val flags = e.optInt(1)
                val isAlive = flags and Protocol.F_ALIVE != 0
                s.alive = isAlive
                s.mass = e.optDouble(2).toFloat()
                s.angle = e.optDouble(3).toFloat()
                s.boostActive = flags and Protocol.F_BOOST != 0
                s.grace = if (flags and Protocol.F_GRACE != 0) 1f else 0f
                s.shield = if (e.optBoolean(5)) 1f else 0f
                s.magnet = if (e.optBoolean(6)) 1f else 0f
                s.overdrive = if (e.optBoolean(7)) 1f else 0f
                s.freeze = if (e.optBoolean(8)) 1f else 0f
                s.slow = if (e.optBoolean(9)) 1f else 0f
                if (id == meId) {
                    s.glow = flags and Protocol.F_GLOW != 0
                    s.rainbow = flags and Protocol.F_RAINBOW != 0
                }
                val n = e.optInt(4).coerceIn(0, Cfg.MAX_SEGS)
                val pts = e.optJSONArray(10)
                if (isAlive && pts != null && n > 0) {
                    Protocol.unpackBody(n, pts, g.tx, g.ty)
                    if (g.fresh || g.tn == 0 || far(s, g)) {
                        System.arraycopy(g.tx, 0, s.xs, 0, n)
                        System.arraycopy(g.ty, 0, s.ys, 0, n)
                        s.n = n
                    } else if (n > s.n) {
                        for (k in s.n until n) {
                            s.xs[k] = g.tx[k]
                            s.ys[k] = g.ty[k]
                        }
                        s.n = n
                    } else {
                        s.n = n
                    }
                    g.tn = n
                    g.fresh = false
                }
                seen.add(id)
                if (id == meId) {
                    if (isAlive && !wasAlive) listener.onRespawned()
                    wasAlive = isAlive
                }
            }
        }
        // Snakes that went out of range or died are simply not drawn.
        for ((id, g) in ghosts) {
            if (id != meId && id !in seen) {
                g.snake.alive = false
                g.fresh = true
            }
        }

        m.optJSONArray("fd")?.let { fd ->
            world.clearFood()
            var i = 0
            while (i + 3 < fd.length()) {
                world.addFood(fd.getDouble(i).toFloat(), fd.getDouble(i + 1).toFloat(), fd.getDouble(i + 2).toFloat(), fd.getInt(i + 3))
                i += 4
            }
        }
    }

    private fun far(s: Snake, g: Ghost): Boolean {
        val dx = g.tx[0] - s.xs[0]
        val dy = g.ty[0] - s.ys[0]
        return dx * dx + dy * dy > 400f * 400f
    }

    /** Eases drawn bodies toward the latest snapshot. Returns true if our mass grew (ate something). */
    fun update(dt: Float): Boolean {
        val k = 1f - exp(-22f * dt)
        for (g in ghosts.values) {
            val s = g.snake
            if (!s.alive || g.tn == 0) continue
            val n = minOf(s.n, g.tn)
            for (i in 0 until n) {
                s.xs[i] += (g.tx[i] - s.xs[i]) * k
                s.ys[i] += (g.ty[i] - s.ys[i]) * k
            }
        }
        val grew = me.mass > lastMass + 0.01f && me.alive
        lastMass = me.mass
        return grew
    }

    /** Sends steering to the host at [Protocol.INPUT_HZ], or immediately when it changes a lot. */
    fun sendInput(angle: Float, boost: Boolean, dt: Float) {
        sendTimer -= dt
        if (sendTimer <= 0f || boost != lastBoost) {
            sendTimer = 1f / Protocol.INPUT_HZ
            lastAngle = angle
            lastBoost = boost
            link.send(Protocol.frameToHost(Protocol.input(angle, boost)))
        }
    }

    fun requestPower(index: Int) = link.send(Protocol.frameToHost(Protocol.power(index)))
    fun requestRespawn() = link.send(Protocol.frameToHost(Protocol.respawn()))
}

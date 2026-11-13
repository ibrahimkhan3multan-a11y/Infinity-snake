package com.ncorti.kotlin.template.app.net

import com.ncorti.kotlin.template.app.game.Cfg
import com.ncorti.kotlin.template.app.game.PowerState
import com.ncorti.kotlin.template.app.game.Powers
import com.ncorti.kotlin.template.app.game.Snake
import com.ncorti.kotlin.template.app.game.World
import org.json.JSONArray
import org.json.JSONObject

/**
 * Runs on the room host's phone. The host owns the one real simulation: guests only send their
 * steering and the host sends them back what is near them. Everything here is called from the
 * game loop (main thread).
 */
class HostLogic(private val world: World, private val link: NetLink) {
    private class Remote(val peer: Int, val name: String) {
        var snake: Snake? = null
        val kit = PowerState()
        var levels = IntArray(Powers.all.size)
        var angle = 0f
        var boost = false
        var snapshotsSent = 0
        var lastX = 0f
        var lastY = 0f
    }

    private val remotes = HashMap<Int, Remote>()
    private var snapTimer = 0f
    private var rosterTimer = 0f
    private var rosterDirty = true

    /** Number of guests currently connected. */
    val guestCount: Int get() = remotes.size

    /** Handles one relay frame (peer join/leave or a guest message). */
    fun handle(frame: JSONObject) {
        when (frame.optString("t")) {
            "peer" -> {
                val id = frame.optInt("id", -1)
                if (id < 0) return
                if (frame.optBoolean("join")) {
                    remotes[id] = Remote(id, frame.optString("name", "Player"))
                } else {
                    remotes.remove(id)?.snake?.let { world.removeRemote(it) }
                    rosterDirty = true
                }
            }
            "m" -> {
                val r = remotes[frame.optInt("from", -1)] ?: return
                val m = frame.optJSONObject("m") ?: return
                handleGuest(r, m)
            }
        }
    }

    private fun handleGuest(r: Remote, m: JSONObject) {
        when (m.optString("k")) {
            "hello" -> {
                if (r.snake != null) return
                val lv = m.optJSONArray("lv")
                for (i in r.levels.indices) r.levels[i] = Protocol.clampLevel(lv?.optInt(i, 0) ?: 0)
                val s = world.addRemote(
                    r.name, m.optInt("head"), m.optInt("tail"), m.optBoolean("glow"), m.optBoolean("rainbow"),
                )
                s.maxEnergy = Powers.tankCapacity(Protocol.clampTank(m.optInt("tank")))
                s.energy = s.maxEnergy
                r.snake = s
                r.angle = s.angle
                link.send(
                    Protocol.frameToGuest(r.peer, JSONObject().put("k", "you").put("id", s.id)),
                )
                rosterDirty = true
            }
            "in" -> {
                r.angle = m.optDouble("a", r.angle.toDouble()).toFloat()
                r.boost = m.optInt("b") == 1
            }
            "pw" -> {
                val s = r.snake ?: return
                val i = m.optInt("i", -1)
                if (i !in Powers.all.indices) return
                r.kit.use(s, Powers.all[i], r.levels[i])
            }
            "respawn" -> {
                val s = r.snake ?: return
                if (s.alive) return
                world.respawnRemote(s)
                s.energy = s.maxEnergy
                r.kit.reset()
            }
        }
    }

    /** Applies guests' steering to their snakes. Call right before world.step. */
    fun beforeStep() {
        for (r in remotes.values) {
            val s = r.snake ?: continue
            if (!s.alive) continue
            s.targetAngle = r.angle
            s.boosting = r.boost
            s.turnMul = 1f
        }
    }

    /** Ticks power cooldowns and sends snapshots at [Protocol.SNAPSHOT_HZ]. */
    fun afterFrame(dt: Float) {
        for (r in remotes.values) r.kit.tick(dt)
        rosterTimer -= dt
        if (rosterDirty || rosterTimer <= 0f) {
            rosterTimer = 2f
            rosterDirty = false
            sendRoster()
        }
        snapTimer -= dt
        if (snapTimer <= 0f) {
            snapTimer = 1f / Protocol.SNAPSHOT_HZ
            for (r in remotes.values) if (r.snake != null) sendSnapshot(r)
        }
    }

    /** World listener hook: tells guests about their own elimination or kill. */
    fun snakeDied(snake: Snake, killer: Snake?) {
        for (r in remotes.values) {
            if (r.snake === snake) {
                link.send(
                    Protocol.frameToGuest(
                        r.peer,
                        JSONObject().put("k", "over").put("score", snake.score).put("rank", snake.lastRank)
                            .put("len", snake.n).put("kills", snake.kills),
                    ),
                )
            }
            if (killer != null && r.snake === killer && snake !== killer) {
                link.send(Protocol.frameToGuest(r.peer, JSONObject().put("k", "kill").put("name", snake.name)))
            }
        }
    }

    private fun sendRoster() {
        val list = JSONArray()
        for (s in world.snakes) {
            if (s.gone) continue
            list.put(
                JSONArray().put(s.id).put(s.name).put(s.headColor).put(s.tailColor)
                    .put(Protocol.snakeFlags(s) and (Protocol.F_GLOW or Protocol.F_RAINBOW)),
            )
        }
        for (r in remotes.values) {
            if (r.snake == null) continue
            link.send(Protocol.frameToGuest(r.peer, JSONObject().put("k", "roster").put("r", list)))
        }
    }

    private fun sendSnapshot(r: Remote) {
        val me = r.snake ?: return
        if (me.alive) {
            r.lastX = me.xs[0]
            r.lastY = me.ys[0]
        }
        val cx = r.lastX
        val cy = r.lastY
        val sn = JSONArray()
        for (s in world.snakes) {
            if (s.gone || !s.alive && s !== me) continue
            if (s !== me && !near(s, cx, cy)) continue
            sn.put(
                JSONArray().put(s.id).put(Protocol.snakeFlags(s)).put(s.mass.toDouble())
                    .put(Math.round(s.angle * 100f) / 100.0).put(s.n)
                    .put(s.shield > 0f).put(s.magnet > 0f).put(s.overdrive > 0f).put(s.freeze > 0f).put(s.slow > 0f)
                    .put(if (s.alive) Protocol.packBody(s) else JSONArray()),
            )
        }
        val pw = JSONArray()
        for (p in Powers.all) pw.put(JSONArray().put(r.kit.activeSeconds(me, p)).put(r.kit.cooldownSeconds(me, p)))
        val bd = JSONArray()
        for (i in 0 until world.boardCount) {
            val b = world.board[i] ?: continue
            bd.put(JSONArray().put(b.name).put(b.score).put(if (b === me) 1 else 0))
        }
        val m = JSONObject()
            .put("k", "s").put("me", me.id).put("sc", me.score).put("ln", me.n)
            .put("rk", if (me.alive) world.rankOf(me) else me.lastRank).put("al", world.aliveCount())
            .put("en", Math.round(me.energy * 10f) / 10.0).put("mx", me.maxEnergy.toDouble())
            .put("pw", pw).put("bd", bd).put("sn", sn)
        if (r.snapshotsSent % Protocol.FOOD_EVERY == 0) {
            val fd = JSONArray()
            for (i in 0 until Cfg.FOOD_CAP) {
                if (!world.falive[i]) continue
                val dx = world.fx[i] - cx
                val dy = world.fy[i] - cy
                if (dx * dx + dy * dy > Protocol.FOOD_RANGE_SQ) continue
                fd.put(Math.round(world.fx[i])).put(Math.round(world.fy[i]))
                    .put(Math.round(world.fv[i] * 10f) / 10.0).put(world.fcol[i])
            }
            m.put("fd", fd)
        }
        r.snapshotsSent++
        link.send(Protocol.frameToGuest(r.peer, m))
    }

    private fun near(s: Snake, cx: Float, cy: Float): Boolean {
        var i = 0
        val step = Protocol.sampleStep(s.n) * 2
        while (i < s.n) {
            val dx = s.xs[i] - cx
            val dy = s.ys[i] - cy
            if (dx * dx + dy * dy <= Protocol.SNAKE_RANGE_SQ) return true
            i += step
        }
        return false
    }
}

package com.ncorti.kotlin.template.app.game

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

interface WorldListener {
    fun onFoodEaten(snake: Snake) {}
    fun onSnakeDied(snake: Snake, killer: Snake?) {}
}

/**
 * The whole arena simulation: snakes, food, bots, collisions. It has no Android dependencies and
 * all randomness comes from [rnd], so it can be unit tested deterministically. The player is
 * always snakes[0]; bots follow and are reused when they respawn (the list never changes size
 * while playing).
 */
class World(
    val rnd: Random,
    val botCount: Int,
    val deadlyWalls: Boolean,
    playerSkin: Skin,
    var listener: WorldListener? = null,
    val ambientFood: Int = Cfg.AMBIENT_FOOD,
    playerName: String = "You",
) {
    val snakes = ArrayList<Snake>(botCount + 1)
    val player = Snake(0, true, playerName)

    // Food in parallel arrays (no objects).
    val fx = FloatArray(Cfg.FOOD_CAP)
    val fy = FloatArray(Cfg.FOOD_CAP)
    val fv = FloatArray(Cfg.FOOD_CAP)
    val fcol = IntArray(Cfg.FOOD_CAP)
    val falive = BooleanArray(Cfg.FOOD_CAP)
    var aliveFood = 0
        private set
    private val freeStack = IntArray(Cfg.FOOD_CAP)
    private var freeTop = 0
    private var recycleCursor = 0

    val board = arrayOfNulls<Snake>(Cfg.BOARD_SIZE)
    var boardCount = 0
        private set
    private var boardTimer = 0f

    var time = 0f
        private set

    private var killer = IntArray(32)
    private val tmp = FloatArray(2)

    init {
        for (i in Cfg.FOOD_CAP - 1 downTo 0) freeStack[freeTop++] = i

        player.headColor = playerSkin.head
        player.tailColor = playerSkin.tail
        player.glow = playerSkin.glow
        player.rainbow = playerSkin.rainbow
        snakes.add(player)
        findSpawn(300f)
        player.place(tmp[0] * 0.4f, tmp[1] * 0.4f, rnd.nextFloat() * TWO_PI - PI_F, Cfg.START_MASS)
        player.grace = Cfg.SPAWN_GRACE

        for (i in 1..botCount) {
            findSpawn(500f)
            spawnBot(tmp[0], tmp[1], rnd.nextFloat() * TWO_PI - PI_F, Cfg.START_MASS, (i - 1) % 3)
        }

        var guard = 0
        while (aliveFood < ambientFood && guard < 5000) {
            spawnCluster()
            guard++
        }
        rebuildBoard()
    }

    fun spawnBot(x: Float, y: Float, ang: Float, mass: Float, level: Int): Snake {
        val id = snakes.size
        val bot = Snake(id, false, BOT_NAMES[(id - 1).coerceAtLeast(0) % BOT_NAMES.size])
        val hue = rnd.nextFloat() * 360f
        bot.headColor = hsvToArgb(hue, 0.65f, 1f)
        bot.tailColor = hsvToArgb(hue + 25f, 0.9f, 0.55f)
        bot.glow = level >= 2
        bot.level = level
        bot.place(x, y, ang, mass)
        bot.grace = Cfg.SPAWN_GRACE
        bot.thinkTimer = rnd.nextFloat() * 0.3f
        snakes.add(bot)
        if (snakes.size > killer.size) killer = killer.copyOf(snakes.size * 2)
        return bot
    }

    // ---------------------------------------------------------------- online players

    /** Adds (or reuses a free slot for) a remote human player and places them in the arena. */
    fun addRemote(name: String, head: Int, tail: Int, glow: Boolean, rainbow: Boolean): Snake {
        var s = snakes.firstOrNull { it.gone }
        if (s == null) {
            s = Snake(snakes.size, false, name)
            snakes.add(s)
            if (snakes.size > killer.size) killer = killer.copyOf(snakes.size * 2)
        }
        s.gone = false
        s.remote = true
        s.name = name
        s.headColor = head
        s.tailColor = tail
        s.glow = glow
        s.rainbow = rainbow
        s.kills = 0
        s.deaths = 0
        s.level = 1
        respawnRemote(s)
        return s
    }

    /** (Re)spawns a remote player with the standard start size and spawn protection. */
    fun respawnRemote(s: Snake) {
        findSpawn(600f)
        s.place(tmp[0], tmp[1], rnd.nextFloat() * TWO_PI - PI_F, Cfg.START_MASS)
        s.grace = Cfg.SPAWN_GRACE
        s.kills = 0
    }

    /** A remote player left: their body becomes food and their slot is free for reuse. */
    fun removeRemote(s: Snake) {
        if (s.alive) {
            s.alive = false
            dropBody(s)
        }
        s.remote = false
        s.gone = true
    }

    fun clearFood() {
        for (i in 0 until Cfg.FOOD_CAP) {
            if (falive[i]) removeFood(i)
        }
    }

    // ---------------------------------------------------------------- food

    fun addFood(x: Float, y: Float, v: Float, color: Int) {
        val idx = if (freeTop > 0) {
            freeStack[--freeTop]
        } else {
            val i = recycleCursor
            recycleCursor = (recycleCursor + 1) % Cfg.FOOD_CAP
            if (falive[i]) aliveFood--
            i
        }
        fx[idx] = x
        fy[idx] = y
        fv[idx] = v
        fcol[idx] = color
        falive[idx] = true
        aliveFood++
    }

    private fun removeFood(i: Int) {
        falive[i] = false
        aliveFood--
        freeStack[freeTop++] = i
    }

    private fun spawnCluster() {
        val a = rnd.nextFloat() * TWO_PI
        val d = sqrt(rnd.nextFloat()) * (Cfg.ARENA_RADIUS - 60f)
        val cx = cos(a) * d
        val cy = sin(a) * d
        val count = 1 + rnd.nextInt(4)
        val base = FOOD_COLORS[rnd.nextInt(FOOD_COLORS.size)]
        for (k in 0 until count) {
            addFood(cx + (rnd.nextFloat() - 0.5f) * 70f, cy + (rnd.nextFloat() - 0.5f) * 70f, 1f + rnd.nextInt(2), base)
        }
    }

    private fun dropBody(s: Snake) {
        val count = max(1, s.n / 2)
        val per = (s.mass * 0.75f / count).coerceIn(1f, 12f)
        val jitter = s.radius
        var i = 0
        while (i < s.n) {
            addFood(
                s.xs[i] + (rnd.nextFloat() - 0.5f) * jitter,
                s.ys[i] + (rnd.nextFloat() - 0.5f) * jitter,
                per,
                s.headColor,
            )
            i += 2
        }
    }

    private fun eatFood(s: Snake) {
        val hx = s.xs[0]
        val hy = s.ys[0]
        val reach = s.radius + 16f + (if (s.magnet > 0f) Cfg.MAGNET_REACH else 0f)
        for (i in 0 until Cfg.FOOD_CAP) {
            if (!falive[i]) continue
            val dx = fx[i] - hx
            val dy = fy[i] - hy
            val rr = reach + foodRadius(fv[i])
            if (dx * dx + dy * dy < rr * rr) {
                s.mass += fv[i]
                removeFood(i)
                listener?.onFoodEaten(s)
            }
        }
    }

    // ---------------------------------------------------------------- step

    fun step(dt: Float) {
        time += dt
        val n = snakes.size
        for (i in 0 until n) killer[i] = NONE

        // Freeze: slow every other snake close to a freezer.
        for (i in 0 until n) {
            val f = snakes[i]
            if (!f.alive || f.freeze <= 0f) continue
            val r2 = Cfg.FREEZE_RADIUS * Cfg.FREEZE_RADIUS
            for (j in 0 until n) {
                if (j == i) continue
                val o = snakes[j]
                if (!o.alive) continue
                val dx = o.xs[0] - f.xs[0]
                val dy = o.ys[0] - f.ys[0]
                if (dx * dx + dy * dy <= r2) o.slow = 0.25f
            }
        }

        for (i in 0 until n) {
            val s = snakes[i]
            if (!s.alive) {
                if (!s.human && !s.gone) {
                    s.respawnTimer -= dt
                    if (s.respawnTimer <= 0f) respawnBot(s)
                }
                continue
            }
            if (!s.human) {
                s.thinkTimer -= dt
                if (s.thinkTimer <= 0f) {
                    BotBrain.think(this, s)
                    s.thinkTimer = BotBrain.interval(s.level) + rnd.nextFloat() * 0.05f
                }
            }
            s.step(dt, this)

            val hx = s.xs[0]
            val hy = s.ys[0]
            val lim = Cfg.ARENA_RADIUS - s.radius
            val d2 = hx * hx + hy * hy
            if (d2 > lim * lim) {
                if (deadlyWalls && s.grace <= 0f && s.shield <= 0f) {
                    killer[i] = WALL
                } else {
                    val k = lim / sqrt(d2)
                    s.xs[0] = hx * k
                    s.ys[0] = hy * k
                }
            }
        }

        for (i in 0 until n) {
            val s = snakes[i]
            if (s.alive && killer[i] == NONE) eatFood(s)
        }

        collisions(n)

        for (i in 0 until n) {
            if (killer[i] != NONE) kill(i, killer[i])
        }

        var budget = 6
        while (aliveFood < ambientFood && budget > 0) {
            spawnCluster()
            budget--
        }

        boardTimer -= dt
        if (boardTimer <= 0f) {
            boardTimer = 0.25f
            rebuildBoard()
        }
    }

    private fun collisions(n: Int) {
        for (i in 0 until n) {
            val a = snakes[i]
            if (!a.alive || a.grace > 0f || a.shield > 0f || killer[i] != NONE) continue
            val ax = a.xs[0]
            val ay = a.ys[0]
            val ar = a.radius * 0.8f
            for (j in 0 until n) {
                if (j == i) continue
                val b = snakes[j]
                if (!b.alive || b.grace > 0f) continue
                val lim = ar + b.radius * 0.85f
                val lim2 = lim * lim

                // Head to head: the lighter snake loses; an exact tie eliminates both.
                val hdx = ax - b.xs[0]
                val hdy = ay - b.ys[0]
                if (hdx * hdx + hdy * hdy < lim2) {
                    if (b.shield > 0f || a.mass <= b.mass) killer[i] = j
                    continue
                }

                // Head to body (own body is never tested).
                for (k in 1 until b.n) {
                    val dx = ax - b.xs[k]
                    val dy = ay - b.ys[k]
                    if (dx * dx + dy * dy < lim2) {
                        killer[i] = j
                        break
                    }
                }
                if (killer[i] != NONE) break
            }
        }
    }

    private fun kill(i: Int, k: Int) {
        val s = snakes[i]
        if (!s.alive) return
        s.lastRank = rankOf(s)
        s.alive = false
        s.boostActive = false
        s.deaths++
        dropBody(s)
        val ks = if (k in 0 until snakes.size) snakes[k] else null
        if (ks != null && ks !== s) ks.kills++
        if (!s.human) s.respawnTimer = 3f + rnd.nextFloat() * 2f
        listener?.onSnakeDied(s, ks)
    }

    private fun respawnBot(s: Snake) {
        findSpawn(600f)
        s.place(tmp[0], tmp[1], rnd.nextFloat() * TWO_PI - PI_F, Cfg.START_MASS)
        s.grace = Cfg.SPAWN_GRACE
        s.thinkTimer = 0f
    }

    private fun findSpawn(minGap: Float) {
        var tries = 0
        while (true) {
            val a = rnd.nextFloat() * TWO_PI
            val d = sqrt(rnd.nextFloat()) * Cfg.ARENA_RADIUS * 0.8f
            val x = cos(a) * d
            val y = sin(a) * d
            var ok = true
            for (i in 0 until snakes.size) {
                val s = snakes[i]
                if (!s.alive) continue
                val dx = s.xs[0] - x
                val dy = s.ys[0] - y
                if (dx * dx + dy * dy < minGap * minGap) {
                    ok = false
                    break
                }
            }
            tries++
            if (ok || tries >= 15) {
                tmp[0] = x
                tmp[1] = y
                return
            }
        }
    }

    // ---------------------------------------------------------------- queries

    /** 1-based rank by mass among alive snakes. */
    fun rankOf(s: Snake): Int {
        var r = 1
        for (i in 0 until snakes.size) {
            val o = snakes[i]
            if (o !== s && o.alive && o.mass > s.mass) r++
        }
        return r
    }

    fun aliveCount(): Int {
        var c = 0
        for (i in 0 until snakes.size) if (snakes[i].alive) c++
        return c
    }

    private fun rebuildBoard() {
        boardCount = 0
        for (i in 0 until snakes.size) {
            val s = snakes[i]
            if (!s.alive) continue
            var pos = boardCount
            while (pos > 0 && board[pos - 1]!!.mass < s.mass) pos--
            if (pos >= Cfg.BOARD_SIZE) continue
            var k = if (boardCount < Cfg.BOARD_SIZE) boardCount else Cfg.BOARD_SIZE - 1
            while (k > pos) {
                board[k] = board[k - 1]
                k--
            }
            board[pos] = s
            if (boardCount < Cfg.BOARD_SIZE) boardCount++
        }
    }

    /**
     * How much free space lies ahead of [b] when heading along [angle]: the smallest gap between
     * three probe points and any other snake body (or the wall if it is deadly). Larger is safer.
     */
    fun clearance(b: Snake, angle: Float, look: Float): Float {
        val ca = cos(angle)
        val sa = sin(angle)
        val hx = b.xs[0]
        val hy = b.ys[0]
        val own = b.radius * 0.8f
        var minC = Float.MAX_VALUE
        for (step in 1..3) {
            val px = hx + ca * look * step / 3f
            val py = hy + sa * look * step / 3f
            if (deadlyWalls) {
                val dw = Cfg.ARENA_RADIUS - sqrt(px * px + py * py) - b.radius
                if (dw < minC) minC = dw
            }
            for (j in 0 until snakes.size) {
                val o = snakes[j]
                if (o === b || !o.alive || o.grace > 0f) continue
                var best = Float.MAX_VALUE
                var k = 0
                while (k < o.n) {
                    val dx = px - o.xs[k]
                    val dy = py - o.ys[k]
                    val d2 = dx * dx + dy * dy
                    if (d2 < best) best = d2
                    k += 2
                }
                val c = sqrt(best) - o.radius - own
                if (c < minC) minC = c
            }
        }
        return minC
    }

    companion object {
        const val NONE = -2
        const val WALL = -1

        private val BOT_NAMES = arrayOf(
            "Viper", "Cobra", "Mamba", "Python", "Anaconda", "Rattler", "Boa", "Taipan",
            "Adder", "Sidewinder", "Kraken", "Hydra", "Naga", "Ouroboros", "Basilisk", "Asp",
        )

        private val FOOD_COLORS = intArrayOf(
            0xFFFF5C8A.toInt(), 0xFF5CFFB1.toInt(), 0xFF5CC8FF.toInt(), 0xFFFFD45C.toInt(),
            0xFFB45CFF.toInt(), 0xFFFF8F5C.toInt(), 0xFF8CFF5C.toInt(), 0xFFFF5CF2.toInt(),
        )
    }
}

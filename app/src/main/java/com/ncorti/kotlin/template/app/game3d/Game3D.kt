package com.ncorti.kotlin.template.app.game3d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/**
 * The rules of the 3D snake game. It has no Android dependencies and all randomness comes from
 * [rnd], so it is unit tested without a phone. The snake moves one tile per step (faster as you
 * eat); [progress] is how far through the current step it is, and is used to draw smooth motion.
 */
class Game3D(val map: GameMap, val rnd: Random = Random(System.nanoTime())) {
    enum class Death { NONE, FELL, WALL_OR_OBSTACLE, SELF }

    private var cells = IntArray(64)
    /** Number of snake segments (cells holds one extra trailing cell used for smooth drawing). */
    var length = 0
        private set
    private var n = 0

    var heading = 1
        private set
    private var pending = -1

    var alive = true
        private set
    var death = Death.NONE
        private set
    var score = 0
        private set
    var foodEaten = 0
        private set
    var foodCell = -1
        private set
    var foodGolden = false
        private set
    private var growth = 0

    /** 0..1 progress through the current step. */
    var progress = 0f
        private set
    var time = 0f
        private set

    /** Bit mask of levels the head has stood on (tests and stats). */
    var levelsVisited = 0
        private set
    var steps = 0
        private set

    val crates = ArrayList<Int>()

    init {
        val start = map.id(map.def.startX, map.def.startZ, map.def.startLevel)
        // Face the direction that leaves room for a tail of three cells behind the head.
        cells[0] = start
        n = 1
        var chosen = -1
        for (h in intArrayOf(1, 2, 3, 0)) {
            var cur = start
            var ok = true
            for (k in 1..3) {
                val nb = map.next(cur, GameMap.opposite(h))
                if (nb < 0) { ok = false; break }
                cur = nb
            }
            if (ok) { chosen = h; break }
        }
        if (chosen < 0) chosen = 1
        heading = chosen
        var cur = start
        repeat(3) {
            val nb = map.next(cur, GameMap.opposite(heading))
            cells[n++] = if (nb >= 0) nb else cur
            if (nb >= 0) cur = nb
        }
        length = n - 1
        levelsVisited = 1 shl map.cl(start)
        spawnFood()
    }

    // ------------------------------------------------------------ access

    val headCell: Int get() = cells[0]
    fun cell(i: Int): Int = cells[i]
    val cellCount: Int get() = n

    val stepInterval: Float get() = max(MIN_STEP, START_STEP - STEP_GAIN * foodEaten)
    val speedLevel: Int get() = 1 + foodEaten / 4

    /** World position of the middle of [cell]'s surface. */
    fun center(cell: Int, out: FloatArray) {
        val l = map.cl(cell)
        out[0] = map.cx(cell) + 0.5f
        out[2] = map.cz(cell) + 0.5f
        out[1] = (if (map.isRamp(cell)) l + 0.5f else l.toFloat()) * GameMap.LEVEL_H
    }

    private val ta = FloatArray(3)
    private val tb = FloatArray(3)

    /** Smoothly interpolated world position of segment [i] (0 = head). */
    fun segmentPos(i: Int, out: FloatArray) {
        center(cells[i + 1], ta)
        center(cells[i], tb)
        val t = progress
        out[0] = ta[0] + (tb[0] - ta[0]) * t
        out[1] = ta[1] + (tb[1] - ta[1]) * t
        out[2] = ta[2] + (tb[2] - ta[2]) * t
    }

    fun foodPos(out: FloatArray) {
        if (foodCell >= 0) center(foodCell, out)
    }

    // ------------------------------------------------------------ input

    /** Turn to face [h] (0..3) at the next step. Turning straight back is ignored. */
    fun setHeading(h: Int) {
        if (!alive) return
        val current = if (pending >= 0) pending else heading
        if (h == current || h == GameMap.opposite(current)) return
        pending = h
    }

    /** Turn toward a world direction given as an angle (0 = +x east, PI/2 = +z south). */
    fun steerWorld(angle: Float) {
        val dx = cos(angle)
        val dz = sin(angle)
        var best = 0
        var bestDot = -2f
        for (d in 0 until 4) {
            val dot = dx * GameMap.DX[d] + dz * GameMap.DZ[d]
            if (dot > bestDot) { bestDot = dot; best = d }
        }
        setHeading(best)
    }

    fun turnLeft() = setHeading((currentHeading() + 3) % 4)
    fun turnRight() = setHeading((currentHeading() + 1) % 4)
    private fun currentHeading() = if (pending >= 0) pending else heading

    // ------------------------------------------------------------ simulation

    fun advance(dt: Float) {
        if (!alive) return
        time += dt
        progress += dt / stepInterval
        var guard = 0
        while (progress >= 1f && alive && guard < 6) {
            progress -= 1f
            step()
            guard++
        }
        if (!alive) progress = 0f
        if (guard == 6) progress = 0f
    }

    /** One tile move. Public so tests (and the autopilot) can drive the game deterministically. */
    fun step() {
        if (!alive) return
        if (pending >= 0) {
            heading = pending
            pending = -1
        }
        val head = cells[0]
        val target = map.next(head, heading)
        if (target < 0) {
            die(if (isDrop(head, heading)) Death.FELL else Death.WALL_OR_OBSTACLE)
            return
        }
        if (crates.contains(target)) {
            die(Death.WALL_OR_OBSTACLE)
            return
        }
        // The tail cell is free again when we are not growing.
        val solidSegments = if (growth > 0) length else length - 1
        for (i in 0 until solidSegments) {
            if (cells[i] == target) {
                die(Death.SELF)
                return
            }
        }
        if (n + 1 > cells.size) cells = cells.copyOf(cells.size * 2)
        System.arraycopy(cells, 0, cells, 1, n)
        cells[0] = target
        n++
        if (growth > 0) {
            growth--
            length++
        } else {
            n--
        }
        steps++
        levelsVisited = levelsVisited or (1 shl map.cl(target))
        if (target == foodCell) eat()
    }

    private fun isDrop(from: Int, d: Int): Boolean {
        val nx = map.cx(from) + GameMap.DX[d]
        val nz = map.cz(from) + GameMap.DZ[d]
        if (!map.inside(nx, nz)) return true
        if (map.wall(nx, nz)) return false
        for (l in 0 until map.levels) if (map.walkable(nx, nz, l)) return false
        return true
    }

    private fun die(why: Death) {
        alive = false
        death = why
    }

    private fun eat() {
        foodEaten++
        score += if (foodGolden) 30 else 10
        growth += if (foodGolden) 3 else 1
        spawnFood()
        if (foodEaten % CRATE_EVERY == 0 && crates.size < MAX_CRATES) spawnCrate()
    }

    private fun occupied(id: Int): Boolean {
        for (i in 0 until n) if (cells[i] == id) return true
        return id == foodCell || crates.contains(id)
    }

    private fun spawnFood() {
        val tiles = map.flatTiles
        if (tiles.isEmpty()) return
        var pick = -1
        for (attempt in 0 until 200) {
            val c = tiles[rnd.nextInt(tiles.size)]
            if (occupied(c)) continue
            // Not right in front of the head, so pickups need a little steering.
            if (manhattan(c, cells[0]) < 3 && attempt < 150) continue
            pick = c
            break
        }
        if (pick < 0) return
        foodCell = pick
        foodGolden = (foodEaten + 1) % GOLDEN_EVERY == 0
    }

    private fun spawnCrate() {
        val tiles = map.flatTiles
        for (attempt in 0 until 120) {
            val c = tiles[rnd.nextInt(tiles.size)]
            if (occupied(c) || manhattan(c, cells[0]) < 5) continue
            if (openNeighbours(c) < 3) continue // never plug a corridor
            crates.add(c)
            return
        }
    }

    private fun openNeighbours(c: Int): Int {
        var k = 0
        for (d in 0 until 4) if (map.next(c, d) >= 0) k++
        return k
    }

    private fun manhattan(a: Int, b: Int): Int {
        if (map.cl(a) != map.cl(b)) return abs(map.cx(a) - map.cx(b)) + abs(map.cz(a) - map.cz(b)) + 2
        return abs(map.cx(a) - map.cx(b)) + abs(map.cz(a) - map.cz(b))
    }

    // ------------------------------------------------------------ autopilot (tests and demos)

    private var distBuf = IntArray(0)
    private var firstBuf = IntArray(0)

    /**
     * Breadth-first search over (tile, heading) from the head to the food, avoiding the body and
     * crates. Returns the heading to take now, or -1 when no route exists.
     */
    fun routeHeading(): Int {
        if (foodCell < 0) return -1
        val total = map.cellCount * 4
        if (distBuf.size != total) {
            distBuf = IntArray(total)
            firstBuf = IntArray(total)
        }
        java.util.Arrays.fill(distBuf, -1)
        val blocked = HashSet<Int>()
        for (i in 0 until length - 1) blocked.add(cells[i])
        blocked.addAll(crates)
        val queue = IntArray(total)
        var qh = 0
        var qt = 0
        val h0 = currentHeading()
        val s0 = cells[0] * 4 + h0
        distBuf[s0] = 0
        firstBuf[s0] = -1
        queue[qt++] = s0
        while (qh < qt) {
            val s = queue[qh++]
            val c = s / 4
            val hd = s % 4
            for (d in 0 until 4) {
                if (d == GameMap.opposite(hd)) continue
                val nb = map.next(c, d)
                if (nb < 0 || nb in blocked) continue
                val ns = nb * 4 + d
                if (distBuf[ns] >= 0) continue
                distBuf[ns] = distBuf[s] + 1
                firstBuf[ns] = if (firstBuf[s] == -1) d else firstBuf[s]
                if (nb == foodCell) return firstBuf[ns]
                queue[qt++] = ns
            }
        }
        // No route: take any move that does not kill us right now.
        for (d in 0 until 4) {
            if (d == GameMap.opposite(h0)) continue
            val nb = map.next(cells[0], d)
            if (nb >= 0 && nb !in blocked) return d
        }
        return -1
    }

    /** Lets the autopilot choose the next heading. */
    fun autopilot() {
        val h = routeHeading()
        if (h >= 0) setHeading(h)
    }

    fun headingAngle(): Float = (heading - 1) * (PI.toFloat() / 2f)

    companion object {
        const val START_STEP = 0.26f
        const val MIN_STEP = 0.11f
        const val STEP_GAIN = 0.005f
        const val GOLDEN_EVERY = 6
        const val CRATE_EVERY = 3
        const val MAX_CRATES = 10
    }
}

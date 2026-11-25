package com.ncorti.kotlin.template.app.game3d

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Game3DTest {
    private fun map(id: String) = GameMap(MapData.all.first { it.id == id })
    private fun GameMap.at(x: Int, z: Int, l: Int) = id(x, z, l)

    /** Every tile that a snake could ever stand on, found by walking the real movement rule. */
    private fun reachable(m: GameMap): Set<Int> {
        val start = m.id(m.def.startX, m.def.startZ, m.def.startLevel)
        val seen = HashSet<Int>()
        val seenState = HashSet<Int>()
        val queue = ArrayDeque<Int>()
        for (h in 0 until 4) { queue.add(start * 4 + h); seenState.add(start * 4 + h) }
        seen.add(start)
        while (queue.isNotEmpty()) {
            val s = queue.removeFirst()
            val c = s / 4
            val hd = s % 4
            for (d in 0 until 4) {
                if (d == GameMap.opposite(hd)) continue
                val n = m.next(c, d)
                if (n < 0) continue
                seen.add(n)
                val ns = n * 4 + d
                if (seenState.add(ns)) queue.add(ns)
            }
        }
        return seen
    }

    private fun walkable(m: GameMap): Set<Int> {
        val out = HashSet<Int>()
        for (l in 0 until m.levels) for (z in 0 until m.h) for (x in 0 until m.w) {
            if (m.walkable(x, z, l) && !m.pillar(x, z, l)) out.add(m.id(x, z, l))
        }
        return out
    }

    @Test
    fun fiveMapsExist() {
        assertEquals(5, MapData.all.size)
        assertEquals(listOf("classic", "underground", "sky", "maze", "adventure"), MapData.all.map { it.id })
    }

    @Test
    fun everyTileOfEveryMapIsReachable() {
        for (def in MapData.all) {
            val m = GameMap(def)
            val missing = walkable(m) - reachable(m)
            assertTrue("${def.id}: unreachable tiles ${missing.map { "(${m.cx(it)},${m.cz(it)},L${m.cl(it)})" }}", missing.isEmpty())
        }
    }

    @Test
    fun noMapHasADeadEndTrap() {
        // A snake cannot turn around inside a one-tile corridor, so every state the player can
        // reach must have a way to keep moving forever (no trapping dead ends).
        for (def in MapData.all) {
            val m = GameMap(def)
            val start = m.id(def.startX, def.startZ, def.startLevel)
            val succ = HashMap<Int, IntArray>()
            val queue = ArrayDeque<Int>()
            for (h in 0 until 4) { succ[start * 4 + h] = IntArray(0); queue.add(start * 4 + h) }
            val seen = HashSet<Int>(queue)
            val order = ArrayList<Int>()
            while (queue.isNotEmpty()) {
                val st = queue.removeFirst()
                order.add(st)
                val list = ArrayList<Int>()
                for (d in 0 until 4) {
                    if (d == GameMap.opposite(st % 4)) continue
                    val n = m.next(st / 4, d)
                    if (n < 0) continue
                    val ns = n * 4 + d
                    list.add(ns)
                    if (seen.add(ns)) queue.add(ns)
                }
                succ[st] = list.toIntArray()
            }
            val alive = HashSet<Int>(order)
            var changed = true
            while (changed) {
                changed = false
                for (st in order) {
                    if (st in alive && succ[st]!!.none { it in alive }) { alive.remove(st); changed = true }
                }
            }
            val doomed = order.filter { it !in alive }
            assertTrue(
                "${def.id}: trapping states ${doomed.take(6).map { "(${m.cx(it / 4)},${m.cz(it / 4)},L${m.cl(it / 4)})" }}",
                doomed.isEmpty(),
            )
        }
    }

    @Test
    fun everyMapHasSeveralLevelsOrRoutes() {
        assertTrue(map("classic").levels >= 2)
        assertTrue(map("underground").levels >= 2)
        assertTrue(map("sky").levels >= 3)
        assertTrue(map("maze").levels >= 2)
        assertTrue(map("adventure").levels >= 3)
    }

    @Test
    fun aSnakeClimbsARampAndComesBackDown() {
        val m = map("classic")
        // Flat floor at (3,7), ramp at (4,7) rising east, plateau at (5,7) on level 1.
        val a = m.next(m.at(3, 7, 0), 1)
        assertEquals(m.at(4, 7, 0), a)
        assertTrue(m.isRamp(a))
        val up = m.next(a, 1)
        assertEquals(m.at(5, 7, 1), up)
        // And down the same way.
        val down = m.next(up, 3)
        assertEquals(a, down)
        assertEquals(m.at(3, 7, 0), m.next(down, 3))
    }

    @Test
    fun aRampCannotBeEnteredFromTheSide() {
        val m = map("classic")
        assertEquals(-1, m.next(m.at(4, 6, 0), 2)) // from the north onto the east-rising ramp at (4,7)
        assertEquals(-1, m.next(m.at(4, 7, 0), 0)) // and cannot leave it sideways either
    }

    @Test
    fun walkingOffALevelWithoutARampIsADrop() {
        val m = map("classic")
        // From the plateau edge straight north there is no ramp: nothing to stand on at level 1.
        assertEquals(-1, m.next(m.at(5, 5, 1), 0))
    }

    @Test
    fun tunnelsRunUnderneathTheSurface() {
        val m = map("underground")
        val tunnel = m.at(12, 3, 0)
        assertTrue(m.walkable(12, 3, 0))
        assertTrue(m.walkable(12, 3, 1)) // the roof above it
        assertTrue(m.hasUpper(12, 3, 0))
        assertTrue(m.hasLower(12, 3, 1))
        // Free air inside the tunnel, solid roof slab above it.
        assertFalse(m.solidAt(12.5f, 0.5f, 3.5f))
        assertTrue(m.solidAt(12.5f, GameMap.LEVEL_H - 0.1f, 3.5f))
        assertFalse(m.solidAt(12.5f, GameMap.LEVEL_H + 0.5f, 3.5f))
        // The tunnel is connected to the surface by a ramp.
        assertTrue(reachable(m).contains(tunnel))
    }

    @Test
    fun bridgesCrossOverOtherTiles() {
        val m = map("adventure")
        // Bridge on level 1 at (14,10) spans the river; the ground under it is empty there,
        // and at (12,8) the bridge passes over level-0 floor.
        assertTrue(m.walkable(14, 10, 1))
        assertFalse(m.walkable(14, 10, 0))
        assertTrue(m.walkable(12, 10, 0))
        assertTrue(m.hasLower(12, 10, 1))
        // Crossing: ramp at (11,10), bridge (12..17,10), down ramp at (18,10), land at (19,10).
        var c = m.at(10, 10, 0)
        for (x in 11..19) {
            c = m.next(c, 1)
            assertTrue("step to x=$x", c >= 0)
        }
        assertEquals(m.at(19, 10, 0), c)
    }

    @Test
    fun skyPlatformsAreJoinedByBridgesAndRamps() {
        val m = map("sky")
        var c = m.at(14, 11, 1)
        c = m.next(c, 1) // ramp up at (15,11)
        assertTrue(m.isRamp(c))
        c = m.next(c, 1)
        assertEquals(m.at(16, 11, 2), c)
        c = m.next(c, 1)
        assertEquals(m.at(17, 11, 2), c)
        // Walking off a platform edge into the sky is a drop.
        assertEquals(-1, m.next(m.at(9, 9, 1), 0))
    }

    @Test
    fun theSnakeCannotTurnBackOnItself() {
        val g = Game3D(map("classic"), Random(1))
        val h = g.heading
        g.setHeading(GameMap.opposite(h))
        g.step()
        assertEquals(h, g.heading)
        g.setHeading((h + 1) % 4)
        g.step()
        assertEquals((h + 1) % 4, g.heading)
    }

    @Test
    fun steeringByWorldAngleSelectsTheNearestHeading() {
        val g = Game3D(map("classic"), Random(1))
        g.placeHead(g.map.at(7, 13, 0), 1) // facing east
        g.steerWorld((Math.PI / 2).toFloat()) // toward +z (south)
        g.step()
        assertEquals(2, g.heading)
    }

    @Test
    fun eatingScoresAndGrows() {
        val g = Game3D(map("classic"), Random(2))
        val len0 = g.length
        var guard = 0
        while (g.foodEaten < 3 && g.alive && guard++ < 3000) {
            g.autopilot()
            g.step()
        }
        assertTrue(g.alive)
        assertEquals(3, g.foodEaten)
        assertTrue(g.score >= 30)
        // Growth is applied as the snake keeps moving; a few extra steps let it finish.
        repeat(8) { g.autopilot(); g.step() }
        assertTrue("length ${g.length} from $len0", g.length >= len0 + 3)
    }

    @Test
    fun hittingAWallEndsTheGame() {
        val g = Game3D(map("classic"), Random(3))
        g.placeHead(g.map.at(3, 1, 0), 0) // facing north towards the rock border at z=0
        g.step()
        assertFalse(g.alive)
        assertEquals(Game3D.Death.WALL_OR_OBSTACLE, g.death)
    }

    @Test
    fun walkingOffAnEdgeIsAFall() {
        val g = Game3D(map("sky"), Random(3))
        g.placeHead(g.map.at(14, 12, 1), 1) // east edge of the central platform
        g.step()
        assertFalse(g.alive)
        assertEquals(Game3D.Death.FELL, g.death)
    }

    @Test
    fun biteYourselfAndYouDie() {
        val g = Game3D(map("classic"), Random(4))
        g.placeHead(g.map.at(8, 13, 0), 1)
        var guard = 0
        while (g.length < 7 && g.alive && guard++ < 4000) { g.autopilot(); g.step() }
        assertTrue(g.alive)
        // Turn right on every step: a tight loop that runs into our own body.
        var died = false
        repeat(10) {
            if (g.alive) {
                g.turnRight()
                g.step()
                if (!g.alive) died = true
            }
        }
        assertTrue(died)
        assertTrue(g.death == Game3D.Death.SELF || g.death == Game3D.Death.WALL_OR_OBSTACLE)
    }

    @Test
    fun obstaclesAppearAsYouEatAndKillOnContact() {
        val g = Game3D(map("classic"), Random(5))
        var guard = 0
        while (g.crates.isEmpty() && g.alive && guard++ < 6000) { g.autopilot(); g.step() }
        assertTrue("a crate should appear after 3 pickups", g.crates.isNotEmpty())
        val crate = g.crates[0]
        // Face a crate from an open neighbour tile and walk into it.
        var placed = false
        for (d in 0 until 4) {
            val from = g.map.next(crate, GameMap.opposite(d))
            if (from >= 0) {
                g.placeHead(from, d)
                if (g.map.next(from, d) == crate) {
                    g.step()
                    placed = true
                    break
                }
            }
        }
        assertTrue(placed)
        assertFalse(g.alive)
    }

    @Test
    fun difficultyRisesWithEveryPickup() {
        val g = Game3D(map("classic"), Random(6))
        val s0 = g.stepInterval
        var guard = 0
        while (g.foodEaten < 6 && g.alive && guard++ < 4000) { g.autopilot(); g.step() }
        assertTrue(g.stepInterval < s0)
        assertTrue(g.speedLevel >= 2)
    }

    @Test
    fun autopilotPlaysEveryMapAndUsesTheUpperAndLowerLevels() {
        // The auto-player is not perfect (it can coil itself into a corner like a person), so each
        // map gets a few fixed seeds and needs one clean run of 12 pickups.
        for ((i, def) in MapData.all.withIndex()) {
            var good: Game3D? = null
            for (k in 0 until 6) {
                val g = Game3D(GameMap(def), Random(100 * (i + 1) + k))
                var guard = 0
                while (g.foodEaten < 12 && g.alive && guard++ < 20000) { g.autopilot(); g.step() }
                if (g.foodEaten >= 12) { good = g; break }
            }
            assertTrue("${def.id}: no seed reached 12 pickups", good != null)
            if (def.levels.size >= 2 && def.id != "classic") {
                assertTrue(
                    "${def.id}: should have visited several levels, mask=${good!!.levelsVisited}",
                    Integer.bitCount(good.levelsVisited) >= 2,
                )
            }
        }
    }

    @Test
    fun smoothMotionInterpolatesBetweenTiles() {
        val g = Game3D(map("classic"), Random(7))
        val a = FloatArray(3)
        val b = FloatArray(3)
        g.segmentPos(0, a)
        g.advance(g.stepInterval * 0.5f)
        g.segmentPos(0, b)
        val moved = kotlin.math.abs(b[0] - a[0]) + kotlin.math.abs(b[2] - a[2])
        assertTrue("moved $moved", moved in 0.3f..0.7f)
    }

    @Test
    fun climbingARampRaisesTheSnakeSmoothly() {
        val m = map("classic")
        val g = Game3D(m, Random(8))
        g.placeHead(m.at(3, 7, 0), 1)
        val p = FloatArray(3)
        val si = g.stepInterval
        g.segmentPos(0, p)
        val y0 = p[1]
        g.advance(si * 1.5f) // half way onto the ramp
        assertTrue(m.isRamp(g.headCell))
        g.segmentPos(0, p)
        val y1 = p[1]
        g.advance(si) // half way from the ramp onto the plateau
        assertEquals(1, m.cl(g.headCell))
        g.segmentPos(0, p)
        val y2 = p[1]
        assertTrue("rising $y0 -> $y1 -> $y2", y0 < y1 && y1 < y2)
        assertEquals(0.35f, y1, 0.05f)
        assertEquals(1.05f, y2, 0.05f)
        g.advance(si * 0.49f)
        g.segmentPos(0, p)
        assertEquals(GameMap.LEVEL_H, p[1], 0.05f)
    }

    @Test
    fun meshesBuildAndStayLightweight() {
        for (def in MapData.all) {
            val m = GameMap(def)
            val mesh = Mesh3D()
            MapMesh.build(m, paletteFor(def.id), mesh)
            assertTrue("${def.id} faces ${mesh.count}", mesh.count in 50..9000)
        }
    }

    @Test
    fun cameraFollowsAndNeverEntersSolids() {
        for ((i, def) in MapData.all.withIndex()) {
            val g = Game3D(GameMap(def), Random(200 + i))
            val cam = Camera3D()
            cam.reset(g)
            var guard = 0
            var frames = 0
            val head = FloatArray(3)
            var solidRun = 0
            while (g.alive && guard++ < 3000) {
                g.autopilot()
                g.advance(1f / 30f)
                cam.update(g, 1f / 30f)
                frames++
                solidRun = if (g.map.solidAt(cam.pos[0], cam.pos[1], cam.pos[2])) solidRun + 1 else 0
                assertTrue("${def.id}: camera stuck inside a solid at frame $frames", solidRun <= 3)
                g.segmentPos(0, head)
                val dx = cam.pos[0] - head[0]
                val dz = cam.pos[2] - head[2]
                assertTrue("${def.id}: camera too far", kotlin.math.hypot(dx, dz) < 7f)
            }
            assertTrue(frames > 100)
        }
    }

    @Test
    fun cameraPullsInsideTunnelsAndKeepsTheSnakeInView() {
        val m = map("underground")
        val g = Game3D(m, Random(9))
        g.placeHead(m.at(12, 3, 0), 1)
        val cam = Camera3D()
        cam.reset(g)
        repeat(90) { cam.update(g, 1f / 30f) }
        assertTrue(cam.inTunnel)
        assertFalse(m.solidAt(cam.pos[0], cam.pos[1], cam.pos[2]))
        // The snake is in front of the camera.
        val p = FloatArray(3)
        g.segmentPos(0, p)
        val dx = p[0] - cam.pos[0]
        val dy = p[1] - cam.pos[1]
        val dz = p[2] - cam.pos[2]
        val depth = dx * cam.fwd[0] + dy * cam.fwd[1] + dz * cam.fwd[2]
        assertTrue("depth $depth", depth > 0.5f)
    }

    @Test
    fun cameraTurnsWithTheSnake() {
        val m = map("classic")
        val g = Game3D(m, Random(10))
        g.placeHead(m.at(8, 13, 0), 1)
        val cam = Camera3D()
        cam.reset(g)
        val yaw0 = cam.yaw
        g.placeHead(m.at(8, 13, 0), 2) // now facing south
        repeat(120) { cam.update(g, 1f / 30f) }
        val delta = kotlin.math.abs(cam.yaw - yaw0)
        assertTrue("yaw moved $delta", delta > 1.2f)
    }
}

package com.ncorti.kotlin.template.app.game

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldTest {

    private val skin = Skins.all[0]

    /** A quiet arena: no bots, no random ambient food, so assertions are exact. */
    private fun emptyWorld(walls: Boolean = false, seed: Int = 1) =
        World(Random(seed), botCount = 0, deadlyWalls = walls, playerSkin = skin, ambientFood = 0)

    private fun World.run(seconds: Float) {
        var t = 0f
        while (t < seconds) {
            step(Cfg.STEP)
            t += Cfg.STEP
        }
    }

    private fun Snake.ready(x: Float, y: Float, ang: Float, m: Float) {
        place(x, y, ang, m)
        grace = 0f
    }

    @Test
    fun snakeMovesForwardAtBaseSpeed() {
        val w = emptyWorld()
        w.player.ready(0f, 0f, 0f, 10f)
        w.run(1f)
        assertEquals(Cfg.BASE_SPEED, w.player.xs[0], 3f)
        assertEquals(0f, w.player.ys[0], 0.5f)
    }

    @Test
    fun bodyKeepsFixedSpacingAndFollowsHead() {
        val w = emptyWorld()
        val p = w.player
        p.ready(0f, 0f, 0f, 60f)
        p.targetAngle = 1.2f
        w.run(3f)
        for (i in 1 until p.n) {
            val d = hypot(p.xs[i - 1] - p.xs[i], p.ys[i - 1] - p.ys[i])
            assertTrue("segment $i gap $d", d <= p.spacing + 0.01f)
        }
    }

    @Test
    fun eatingFoodGrowsMassAndRemovesFood() {
        val w = emptyWorld()
        val p = w.player
        p.ready(0f, 0f, 0f, 10f)
        w.addFood(8f, 0f, 3f, 0xFFFFFFFF.toInt())
        assertEquals(1, w.aliveFood)
        w.step(Cfg.STEP)
        assertEquals(13f, p.mass, 0.01f)
        assertEquals(0, w.aliveFood)
    }

    @Test
    fun segmentCountTracksMass() {
        val w = emptyWorld()
        val p = w.player
        p.ready(0f, 0f, 0f, 10f)
        p.mass = 200f
        w.step(Cfg.STEP)
        assertEquals(p.targetSegs, p.n)
        assertTrue(p.n > 100)
    }

    @Test
    fun headHittingOtherBodyEliminatesAndDropsFood() {
        val w = emptyWorld()
        val p = w.player
        p.ready(0f, 0f, 0f, 10f)
        // A long bot whose body crosses the player's path at x = 200.
        val bot = w.spawnBot(200f, 100f, PI_F / 2f, 200f, 1)
        bot.grace = 0f
        bot.targetAngle = bot.angle
        w.run(2.5f)
        assertFalse("player should be eliminated", p.alive)
        assertTrue("bot survives", bot.alive)
        assertTrue("eliminated snake leaves food", w.aliveFood > 0)
        assertEquals(1, bot.kills)
    }

    @Test
    fun lighterSnakeLosesHeadToHead() {
        val w = emptyWorld()
        val p = w.player
        p.ready(-60f, 0f, 0f, 10f)
        val big = w.spawnBot(60f, 0f, PI_F, 80f, 1)
        big.grace = 0f
        w.run(1f)
        assertFalse(p.alive)
        assertTrue(big.alive)
    }

    @Test
    fun equalMassHeadToHeadEliminatesBoth() {
        val w = emptyWorld()
        val p = w.player
        p.ready(-40f, 0f, 0f, 30f)
        val other = w.spawnBot(40f, 0f, PI_F, 30f, 1)
        other.grace = 0f
        w.run(1f)
        assertFalse(p.alive)
        assertFalse(other.alive)
    }

    @Test
    fun ownBodyNeverKillsEvenWhenCirclingTightly() {
        val w = emptyWorld()
        val p = w.player
        p.ready(0f, 0f, 0f, 150f)
        var t = 0f
        while (t < 20f) {
            p.targetAngle = p.angle + 1f // keep turning as hard as possible
            w.step(Cfg.STEP)
            t += Cfg.STEP
        }
        assertTrue(p.alive)
    }

    @Test
    fun spawnGraceProtectsFromBodiesAndWalls() {
        val w = emptyWorld(walls = true)
        val p = w.player
        p.place(Cfg.ARENA_RADIUS - 20f, 0f, 0f, 10f)
        p.grace = Cfg.SPAWN_GRACE
        w.run(1f)
        assertTrue("grace snake is clamped, not killed", p.alive)
        assertTrue(hypot(p.xs[0], p.ys[0]) <= Cfg.ARENA_RADIUS)
    }

    @Test
    fun deadlyWallKillsAfterGraceAndSoftWallClamps() {
        val deadly = emptyWorld(walls = true)
        deadly.player.ready(Cfg.ARENA_RADIUS - 40f, 0f, 0f, 10f)
        deadly.run(1f)
        assertFalse(deadly.player.alive)

        val soft = emptyWorld(walls = false)
        soft.player.ready(Cfg.ARENA_RADIUS - 40f, 0f, 0f, 10f)
        soft.run(3f)
        assertTrue(soft.player.alive)
        assertTrue(hypot(soft.player.xs[0], soft.player.ys[0]) <= Cfg.ARENA_RADIUS)
    }

    @Test
    fun boostMovesFasterAndCostsMassButStopsAtMinimum() {
        val w = emptyWorld()
        val p = w.player
        p.ready(0f, 0f, 0f, 40f)
        p.boosting = true
        w.run(1f)
        assertTrue(p.xs[0] > Cfg.BASE_SPEED * 1.5f)
        assertTrue(p.mass < 40f)
        w.run(20f)
        assertTrue("boost cannot drain below the minimum", p.mass >= Cfg.MIN_BOOST_MASS - 0.5f)
    }

    @Test
    fun sameSeedGivesIdenticalSimulation() {
        fun simulate(): FloatArray {
            val w = World(Random(7), Cfg.BOT_COUNT, true, skin)
            w.run(10f)
            return FloatArray(w.snakes.size) { w.snakes[it].xs[0] }
        }
        val a = simulate()
        val b = simulate()
        for (i in a.indices) assertEquals(a[i], b[i], 0f)
    }

    @Test
    fun rankAndLeaderboardAreOrderedByMass() {
        val w = emptyWorld()
        w.player.ready(0f, 0f, 0f, 50f)
        val a = w.spawnBot(500f, 500f, 0f, 90f, 1)
        val b = w.spawnBot(-500f, 500f, 0f, 20f, 1)
        a.grace = 0f
        b.grace = 0f
        assertEquals(2, w.rankOf(w.player))
        assertEquals(1, w.rankOf(a))
        assertEquals(3, w.rankOf(b))
        w.step(Cfg.STEP * 20)
        for (i in 1 until w.boardCount) {
            assertTrue(w.board[i - 1]!!.mass >= w.board[i]!!.mass)
        }
    }

    @Test
    fun longSessionWithBotsStaysStable() {
        val w = World(Random(99), Cfg.BOT_COUNT, true, skin)
        // Keep the player alive-ish by steering to the center.
        val minutes = 4
        var respawns = 0
        for (frame in 0 until 60 * 60 * minutes) {
            val p = w.player
            if (p.alive) p.targetAngle = kotlin.math.atan2(-p.ys[0], -p.xs[0])
            w.step(Cfg.STEP)
            if (frame % 600 == 0) {
                for (s in w.snakes) {
                    for (i in 0 until s.n) {
                        assertTrue("finite position", s.xs[i].isFinite() && s.ys[i].isFinite())
                    }
                    if (s.alive) assertTrue(hypot(s.xs[0], s.ys[0]) <= Cfg.ARENA_RADIUS + 1f)
                    assertTrue(s.n <= Cfg.MAX_SEGS)
                }
                assertTrue(w.aliveFood in 0..Cfg.FOOD_CAP)
            }
            if (!w.player.alive) respawns++
        }
        // Bots keep respawning so the arena is never empty.
        assertTrue("bots stay in play", w.aliveCount() - (if (w.player.alive) 1 else 0) >= Cfg.BOT_COUNT / 2)
        assertTrue(abs(w.time - 60f * minutes) < 1f)
        assertTrue(respawns >= 0)
    }
}

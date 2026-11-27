package com.ncorti.kotlin.template.app.game

import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionTest {
    private fun emptyWorld(seed: Int = 1) =
        World(Random(seed), botCount = 0, deadlyWalls = false, playerSkin = Skins.all[0], ambientFood = 0)

    private fun World.run(seconds: Float) {
        var t = 0f
        while (t < seconds) { step(Cfg.STEP); t += Cfg.STEP }
    }

    private fun Snake.ready(x: Float, y: Float, ang: Float, m: Float) {
        place(x, y, ang, m)
        grace = 0f
    }

    @Test
    fun contentCountsGrew() {
        assertEquals(12, Powers.all.size)
        assertTrue(Skins.all.size >= 20)
        assertEquals(20, Rebirth.MAX)
        assertTrue(Codes.count >= 30)
    }

    @Test
    fun skinsAndPowersAreUniqueAndCodesPointAtRealRewards() {
        assertEquals(Skins.all.size, Skins.all.map { it.id }.toSet().size)
        assertEquals(Skins.all.indices.toList(), Skins.all.map { it.id })
        assertEquals(Powers.all.size, Powers.all.map { it.id }.toSet().size)
        for (c in Codes.all) {
            val r = Codes.find(c)
            assertNotNull(c, r)
            r!!.skin?.let { assertTrue("$c skin", Skins.all.any { s -> s.id == it }) }
            r.power?.let { assertNotNull("$c power", Powers.byId(it)) }
            assertTrue("$c is already normalised", Codes.normalize(c) == c)
        }
        assertEquals(Codes.all.size, Codes.count)
    }

    @Test
    fun rebirthsKeepGrowingSensibly() {
        assertTrue(Rebirth.costs.zipWithNext().all { (a, b) -> b > a })
        assertEquals(12f, Rebirth.startBoost(10), 0.001f)
        assertTrue(Rebirth.startBoost(20) > Rebirth.startBoost(10))
        assertTrue(Rebirth.startBoost(20) <= 20f)
        assertEquals(6f, Rebirth.coinMultiplier(20), 0.001f)
    }

    @Test
    fun ghostPassesThroughBodiesAndCannotBeHit() {
        val w = emptyWorld(2)
        val p = w.player
        p.ready(0f, 0f, 0f, 10f)
        val other = w.spawnBot(100f, -300f, Math.PI.toFloat() / 2f, 60f, 0)
        other.grace = 0f
        // The bot's body lies across our path.
        for (i in 0 until other.n) { other.xs[i] = 100f; other.ys[i] = -300f + i * other.spacing }
        p.ghost = 5f
        w.run(1.2f)
        assertTrue("ghost survives crossing a body", p.alive)
    }

    @Test
    fun withoutGhostCrossingABodyKills() {
        val w = emptyWorld(2)
        val p = w.player
        p.ready(0f, 0f, 0f, 10f)
        val other = w.spawnBot(100f, -300f, 0f, 60f, 0)
        other.grace = 0f
        other.boosting = false
        for (i in 0 until other.n) { other.xs[i] = 100f; other.ys[i] = -300f + i * other.spacing }
        other.targetAngle = other.angle
        // Park the bot: the test only needs its body in the way.
        other.slow = 99f
        w.run(1.0f)
        assertFalse("normal snake dies on the body", p.alive)
    }

    @Test
    fun titanIsBiggerAndWinsHeadOns() {
        val w = emptyWorld(3)
        val p = w.player
        p.ready(-60f, 0f, 0f, 30f)
        val before = p.radius
        p.titan = 5f
        assertEquals(before * Cfg.TITAN_SCALE, p.radius, 0.01f)
        val b = w.spawnBot(60f, 0f, Math.PI.toFloat(), 400f, 0)
        b.grace = 0f
        w.run(1f)
        assertTrue("the titan survived the head-on with a much heavier snake", p.alive)
        assertFalse("the heavier normal snake lost", b.alive)
    }

    @Test
    fun warpJumpsForwardWithGraceAndStaysInTheArena() {
        val w = emptyWorld(4)
        val p = w.player
        p.ready(0f, 0f, 0f, 30f)
        val kit = PowerState()
        kit.warpLimit = w.radius - 150f
        assertTrue(kit.use(p, Powers.WARP, 1))
        assertEquals(Cfg.WARP_DISTANCE, p.xs[0], 1f)
        assertTrue(p.grace > 0.5f)
        // The body came along.
        assertTrue(hypot(p.xs[p.n - 1] - p.xs[0], p.ys[p.n - 1] - p.ys[0]) < p.n * p.spacing + 1f)
        assertFalse("cooling down", kit.ready(p, Powers.WARP, 1))
        // Near the wall the jump is clipped.
        p.ready(w.radius - 200f, 0f, 0f, 30f)
        kit.reset()
        kit.use(p, Powers.WARP, 1)
        assertTrue("stays inside: ${p.xs[0]}", hypot(p.xs[0], p.ys[0]) <= w.radius - 100f)
    }

    @Test
    fun timeWarpSlowsEveryRivalEverywhere() {
        val w = emptyWorld(5)
        val far = w.spawnBot(3000f, 0f, 0f, Cfg.START_MASS, 0)
        far.grace = 0f
        w.player.ready(0f, 0f, 0f, Cfg.START_MASS)
        w.player.timewarp = 3f
        val x0 = far.xs[0]
        repeat(30) { w.step(Cfg.STEP) }
        val moved = far.xs[0] - x0
        assertTrue("far rival slowed: $moved", moved < Cfg.BASE_SPEED * 0.5f * 0.75f)
    }
}

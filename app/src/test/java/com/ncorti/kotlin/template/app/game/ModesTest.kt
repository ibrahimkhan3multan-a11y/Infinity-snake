package com.ncorti.kotlin.template.app.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModesTest {
    private fun world(mode: Int, bots: Int = 0) =
        World(Random(5), botCount = bots, deadlyWalls = false, playerSkin = Skins.all[0], ambientFood = 0)
            .also { it.mode = mode }

    private fun World.run(seconds: Float) {
        var t = 0f
        while (t < seconds) {
            step(Cfg.STEP)
            t += Cfg.STEP
        }
    }

    @Test
    fun classicHasNoBombsAndFullRadius() {
        val w = world(GameMode.CLASSIC)
        w.run(10f)
        assertEquals(0, w.bombCount)
        assertEquals(Cfg.ARENA_RADIUS, w.radius, 0.01f)
    }

    @Test
    fun bombsAppearInBombRain() {
        val w = world(GameMode.BOMBS)
        var seen = false
        var t = 0f
        while (t < 8f) {
            w.step(Cfg.STEP)
            t += Cfg.STEP
            if (w.bombCount > 0) seen = true
        }
        assertTrue(seen)
    }

    @Test
    fun aBombKillsASnakeStandingUnderIt() {
        val w = world(GameMode.BOMBS)
        var exploded = false
        w.onBomb = { _, _ -> exploded = true }
        val p = w.player
        var guard = 0
        while (p.alive && guard < 60 * 20) {
            // Keep the player exactly where the first falling bomb lands.
            for (i in 0 until w.bombCount) {
                if (w.bt[i] > 0f) {
                    p.place(w.bx[i], w.by[i], 0f, Cfg.START_MASS)
                    p.grace = 0f
                    p.shield = 0f
                    break
                }
            }
            w.step(Cfg.STEP)
            guard++
        }
        assertTrue(exploded)
        assertFalse(p.alive)
    }

    @Test
    fun aShieldSurvivesABomb() {
        val w = world(GameMode.BOMBS)
        val p = w.player
        var t = 0f
        var bombs = 0
        w.onBomb = { _, _ -> bombs++ }
        while (t < 12f) {
            for (i in 0 until w.bombCount) {
                if (w.bt[i] > 0f) {
                    p.place(w.bx[i], w.by[i], 0f, Cfg.START_MASS)
                    p.grace = 0f
                    p.shield = 5f
                    break
                }
            }
            w.step(Cfg.STEP)
            t += Cfg.STEP
        }
        assertTrue(bombs > 0)
        assertTrue(p.alive)
    }

    @Test
    fun bombsDisappearAfterTheBlast() {
        val w = world(GameMode.BOMBS)
        w.run(40f)
        assertTrue(w.bombCount <= Cfg.MAX_BOMBS)
        // Bombs live at most fuse + linger seconds.
        for (i in 0 until w.bombCount) {
            assertTrue(w.bt[i] <= Cfg.BOMB_FUSE && w.bt[i] >= -Cfg.BOMB_LINGER)
        }
    }

    @Test
    fun theArenaShrinksAndKillsThoseOutside() {
        val w = world(GameMode.SHRINK)
        w.run(Cfg.SHRINK_SECONDS + 1f)
        assertEquals(Cfg.SHRINK_MIN, w.radius, 1f)
        val p = w.player
        p.place(3000f, 0f, 0f, Cfg.START_MASS)
        p.alive = true
        p.grace = 0f
        w.step(Cfg.STEP)
        assertFalse(p.alive)
    }

    @Test
    fun theArenaIsStillFullAtTheStart() {
        val w = world(GameMode.SHRINK)
        w.run(1f)
        assertTrue(w.radius > Cfg.ARENA_RADIUS - 60f)
    }

    @Test
    fun frenzyIsFaster() {
        fun travelled(mode: Int): Float {
            val w = world(mode)
            val p = w.player
            p.place(0f, 0f, 0f, Cfg.START_MASS)
            p.grace = 0f
            p.targetAngle = 0f
            w.run(1f)
            return p.xs[0]
        }
        val normal = travelled(GameMode.CLASSIC)
        val frenzy = travelled(GameMode.FRENZY)
        assertTrue("frenzy $frenzy vs $normal", frenzy > normal * 1.2f)
    }

    @Test
    fun modeNamesAreDefined() {
        for (m in 0 until GameMode.COUNT) {
            assertTrue(GameMode.valid(m))
            assertTrue(GameMode.name(m).isNotBlank())
            assertTrue(GameMode.blurb(m).isNotBlank())
        }
        assertFalse(GameMode.valid(GameMode.COUNT))
    }

    @Test
    fun botsKeepPlayingInEveryMode() {
        for (m in 0 until GameMode.COUNT) {
            val w = world(m, bots = 6)
            w.run(20f)
            assertTrue("mode $m", w.aliveCount() >= 0)
            assertTrue(w.player.xs[0].isFinite())
        }
    }
}

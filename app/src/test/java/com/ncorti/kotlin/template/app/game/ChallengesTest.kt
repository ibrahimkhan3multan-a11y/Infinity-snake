package com.ncorti.kotlin.template.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengesTest {
    @Test
    fun scoreChallengeTracksScore() {
        assertEquals(120, Challenges.progress(0, score = 120, kills = 9, rank = 3, solo = false))
        assertTrue(Challenges.progress(0, 250, 0, 9, true) >= Challenges.all[0].goal)
    }

    @Test
    fun hunterChallengeTracksKills() {
        assertEquals(4, Challenges.progress(1, score = 500, kills = 4, rank = 1, solo = false))
        assertTrue(Challenges.progress(1, 10, 5, 9, false) >= Challenges.all[1].goal)
    }

    @Test
    fun arenaKingNeedsArenaModeFirstPlaceAndEnoughPoints() {
        val goal = Challenges.all[2].goal
        assertTrue(Challenges.progress(2, 200, 0, 1, solo = false) >= goal)
        assertEquals(0, Challenges.progress(2, 200, 0, 1, solo = true))
        assertEquals(0, Challenges.progress(2, 200, 0, 2, solo = false))
        assertEquals(0, Challenges.progress(2, 100, 0, 1, solo = false))
    }

    @Test
    fun rewardsReferenceRealSkinsAndReadWell() {
        for (c in Challenges.all) {
            c.rewardSkin?.let { assertTrue(Skins.all.any { s -> s.id == it }) }
            assertTrue(c.rewardText.isNotEmpty())
        }
    }
}

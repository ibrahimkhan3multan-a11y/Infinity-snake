package com.ncorti.kotlin.template.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestsCodesTest {
    private fun run(score: Int = 0, kills: Int = 0, food: Int = 0, powers: Int = 0, seconds: Int = 0, online: Boolean = false) =
        RunStats(score, kills, food, powers, seconds, online)

    @Test
    fun sameDaySameQuestsAndDifferentDaysDiffer() {
        val a = Quests.forDay(20_000)
        assertEquals(Quests.PER_DAY, a.size)
        assertEquals(a.map { it.id }, Quests.forDay(20_000).map { it.id })
        assertEquals(Quests.PER_DAY, a.map { it.id }.toSet().size)
        val days = (20_000 until 20_030).map { d -> Quests.forDay(d).map { it.id } }.toSet()
        assertTrue("quests should vary from day to day", days.size > 5)
    }

    @Test
    fun totalQuestsAddUpAndBestQuestsTakeTheMax() {
        val kills = Quests.pool.first { it.kind == QuestKind.KILLS }
        assertEquals(4, kills.after(0, run(kills = 4)))
        assertEquals(kills.goal, kills.after(4, run(kills = 5)))
        val score = Quests.pool.first { it.kind == QuestKind.SCORE }
        assertEquals(200, score.after(0, run(score = 200)))
        assertEquals(200, score.after(200, run(score = 90)))
        assertEquals(score.goal, score.after(200, run(score = 5000)))
    }

    @Test
    fun everyQuestKindCanBeCompleted() {
        val big = run(score = 10_000, kills = 100, food = 10_000, powers = 100, seconds = 10_000, online = true)
        for (q in Quests.pool) {
            var p = 0
            repeat(q.goal) { p = q.after(p, big) }
            assertEquals("quest ${q.title}", q.goal, p)
        }
    }

    @Test
    fun codesAreForgivingAndStarterGivesHundred() {
        assertEquals(100, Codes.find(" starter ")?.coins)
        assertEquals(100, Codes.find("StArTeR")?.coins)
        assertNull(Codes.find("NOPE"))
        assertNotNull(Codes.find("FLARE")?.skin)
        assertEquals("shield", Codes.find("SHIELDUP")?.power)
        assertTrue(Codes.isSecret("ibrahim-test-7"))
        assertTrue(!Codes.isSecret("STARTER"))
        assertNull("the secret is not a normal code", Codes.find(Codes.SECRET))
        for (c in listOf("STARTER", "FLARE", "SHIELDUP", "DASHNOW")) assertTrue(Codes.find(c)!!.text.isNotEmpty())
    }
}

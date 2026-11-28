package com.ncorti.kotlin.template.app.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KillFxSkinLabTest {
    private fun red(c: Int) = (c shr 16) and 255
    private fun blue(c: Int) = c and 255

    @Test
    fun killAnimationsAreOrderedAndHavePerks() {
        assertEquals(KillFxs.all.indices.toList(), KillFxs.all.map { it.id })
        assertEquals(0, KillFxs.all[0].cost)
        assertTrue(KillFxs.all.zipWithNext().all { (a, b) -> b.cost > a.cost })
        assertEquals(KillFxs.all.size, KillFxs.all.map { it.name }.toSet().size)
        assertEquals("No perk: just looks good", KillFxs.all[0].perkText)
        for (fx in KillFxs.all.drop(1)) assertFalse("${fx.name} has a perk", fx.perkText.startsWith("No perk"))
        assertTrue(KillFxs.byId(99).id == 0)
        assertTrue(KillFxs.all.last().coinsPerKill > KillFxs.all[1].coinsPerKill)
    }

    @Test
    fun skinLabReadsKeywords() {
        val fire = SkinLab.generate("fire dragon", 0)
        assertTrue("warm head", red(fire.head) > 200)
        assertTrue("red tail", red(fire.tail) > blue(fire.tail))
        val ice = SkinLab.generate("ice", 0)
        assertTrue("cold: more blue than red", blue(ice.tail) > red(ice.tail))
        assertTrue(SkinLab.generate("rainbow", 0).rainbow)
        assertEquals("Fire Dragon", fire.name)
    }

    @Test
    fun variationsAndUnknownWordsStillGiveDistinctValidSkins() {
        val looks = (0..3).map { SkinLab.generate("ocean shark", it) }
        assertEquals("variations differ", 4, looks.map { it.head to it.tail }.toSet().size)
        val a = SkinLab.generate("zzqx flurb", 0)
        val b = SkinLab.generate("wibble wobble", 0)
        assertNotEquals(a.head to a.tail, b.head to b.tail)
        assertEquals("deterministic", a.head, SkinLab.generate("zzqx flurb", 0).head)
        for (l in looks + a + b) {
            assertEquals("opaque head", 255, (l.head shr 24) and 255)
            assertEquals("opaque tail", 255, (l.tail shr 24) and 255)
            assertTrue(l.name.isNotBlank())
        }
        assertEquals("Custom", SkinLab.generate("  ", 0).name)
        assertEquals(1000, SkinLab.COST)
    }

    @Test
    fun customSkinsResolveById() {
        val look = SkinLab.generate("gold king", 0)
        Skins.custom = listOf(Skin(100, look.name, 0, look.head, look.tail, look.glow, false))
        try {
            assertEquals("Gold King", Skins.byId(100).name)
            assertEquals("unknown ids fall back to the first skin", Skins.all[0].id, Skins.byId(555).id)
        } finally {
            Skins.custom = emptyList()
        }
    }
}

package com.ncorti.kotlin.template.app.game

/**
 * An unlockable animation that plays where a snake you eliminated dies. Each one also carries a perk
 * that triggers on every kill (extra coins, boost energy, a speed burst, extra length) and/or a bonus
 * on the coins you earn at the end of a run.
 */
class KillFx(
    val id: Int,
    val name: String,
    val desc: String,
    val cost: Int,
    val color: Int,
    val color2: Int,
    /** Visual style, see GameSession.playKillFx. */
    val kind: Int,
    val coinsPerKill: Int = 0,
    val energyPerKill: Float = 0f,
    val burstPerKill: Float = 0f,
    val massPerKill: Float = 0f,
    val coinPercent: Int = 0,
) {
    /** The perk written out for the shop. */
    val perkText: String
        get() {
            val parts = ArrayList<String>()
            if (coinsPerKill > 0) parts.add("+$coinsPerKill coins per kill")
            if (energyPerKill > 0f) parts.add("+${energyPerKill.toInt()} boost energy per kill")
            if (burstPerKill > 0f) parts.add("${"%.1f".format(burstPerKill)} s speed burst per kill")
            if (massPerKill > 0f) parts.add("+${massPerKill.toInt()} length points per kill")
            if (coinPercent > 0) parts.add("+$coinPercent% coins every run")
            return if (parts.isEmpty()) "No perk: just looks good" else parts.joinToString("  ·  ")
        }
}

object KillFxs {
    val all: List<KillFx> = listOf(
        KillFx(0, "Pop", "The classic puff of colour.", 0, 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0),
        KillFx(1, "Confetti", "A party burst of every colour.", 150, 0xFFFF6EC7.toInt(), 0xFF2DE2E6.toInt(), 1, coinsPerKill = 1),
        KillFx(2, "Shockwave", "Rings of force blast outward.", 300, 0xFF4DD0FF.toInt(), 0xFFFFFFFF.toInt(), 2, energyPerKill = 12f),
        KillFx(3, "Lightning", "A bolt strikes from the victim to you.", 500, 0xFFFFF176.toInt(), 0xFFB3E5FC.toInt(), 3, burstPerKill = 0.8f),
        KillFx(4, "Gold Rain", "A shower of golden coins.", 750, 0xFFFFD54F.toInt(), 0xFFFFA000.toInt(), 4, coinsPerKill = 3),
        KillFx(5, "Supernova", "A star dies in a huge white-hot blast.", 1100, 0xFFFFFFFF.toInt(), 0xFFFFC107.toInt(), 5, coinPercent = 10),
        KillFx(6, "Black Hole", "Everything is sucked back to you.", 1500, 0xFFB06BFF.toInt(), 0xFF1A0033.toInt(), 6, massPerKill = 4f),
        KillFx(7, "Phoenix", "Fire rises from the ashes.", 2000, 0xFFFF7043.toInt(), 0xFFFFC107.toInt(), 7, coinsPerKill = 5, burstPerKill = 1f),
        KillFx(8, "Galaxy", "A spiral of stars.", 2800, 0xFF7C4DFF.toInt(), 0xFF18FFFF.toInt(), 8, energyPerKill = 20f, coinPercent = 10),
        KillFx(9, "Infinity", "Every effect at once, in rainbow.", 4500, 0xFFFFFFFF.toInt(), 0xFFFF4081.toInt(), 9,
            coinsPerKill = 8, energyPerKill = 25f, burstPerKill = 2f, coinPercent = 15),
    )

    fun byId(id: Int): KillFx = all.firstOrNull { it.id == id } ?: all[0]
}

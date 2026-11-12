package com.ncorti.kotlin.template.app.game

/** A consumable power sold in the shop. Bought with coins, used once. */
class Power(val id: String, val name: String, val desc: String, val cost: Int)

object Powers {
    val SHIELD = Power(
        "shield", "Shield",
        "6 seconds of invincibility. Crashes, walls and head-ons can't eliminate you, and rivals who hit you die.",
        40,
    )
    val MAGNET = Power(
        "magnet", "Magnet",
        "12 seconds of a much wider pickup range: pellets fly into you from far away.",
        30,
    )
    val OVERDRIVE = Power(
        "over", "Overdrive",
        "6 seconds of free, unlimited boost. Your boost energy isn't used.",
        50,
    )
    val HEADSTART = Power(
        "start", "Head Start",
        "Your next run begins with a score of 60 instead of 10.",
        35,
    )

    /** Powers you trigger during a run (Head Start is applied automatically at the start). */
    val inGame: List<Power> = listOf(SHIELD, MAGNET, OVERDRIVE)
    val all: List<Power> = listOf(SHIELD, MAGNET, OVERDRIVE, HEADSTART)

    const val TANK_MAX = 5
    const val TANK_STEP = 25f
    const val MAX_STOCK = 99

    fun tankCost(level: Int): Int = 80 * (level + 1)
    fun tankCapacity(level: Int): Float = Cfg.MAX_ENERGY + TANK_STEP * level
}

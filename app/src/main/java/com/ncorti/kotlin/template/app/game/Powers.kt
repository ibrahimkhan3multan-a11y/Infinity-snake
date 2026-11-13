package com.ncorti.kotlin.template.app.game

import kotlin.math.roundToInt

/**
 * A permanent power. You unlock it once (level 1) and can upgrade it to [MAX_LEVEL]. Every use
 * lasts [duration] seconds and is followed by a [cooldown]; both improve linearly with level.
 * A duration of 0 means the power is instant (it fires once and only the cooldown matters).
 */
class Power(
    val id: String,
    val name: String,
    val desc: String,
    val unlockCost: Int,
    private val duration1: Float,
    private val duration5: Float,
    private val cooldown1: Float,
    private val cooldown5: Float,
) {
    private fun lerp(a: Float, b: Float, level: Int): Float =
        a + (b - a) * (level.coerceIn(1, Powers.MAX_LEVEL) - 1) / (Powers.MAX_LEVEL - 1)

    /** Seconds the effect lasts at [level]. */
    fun duration(level: Int): Float = lerp(duration1, duration5, level)

    /** Seconds you wait after the effect ends before it can be used again. */
    fun cooldown(level: Int): Float = lerp(cooldown1, cooldown5, level)

    /** Coins to go from [level] to level + 1 (level 0 means unlock). */
    fun costFor(level: Int): Int = if (level <= 0) unlockCost else (unlockCost * 0.8f * level).roundToInt()
}

object Powers {
    const val MAX_LEVEL = 5

    val SHIELD = Power(
        "shield", "Shield",
        "Invincible while it lasts: crashes, walls and head-ons can't eliminate you, and rivals who hit you die.",
        60, 6f, 15f, 30f, 15f,
    )
    val MAGNET = Power(
        "magnet", "Magnet",
        "A much wider pickup range: pellets fly into you from far away.",
        50, 8f, 20f, 40f, 20f,
    )
    val OVERDRIVE = Power(
        "over", "Overdrive",
        "Free, unlimited boost. Your boost energy isn't used.",
        90, 4f, 10f, 45f, 25f,
    )
    val FREEZE = Power(
        "freeze", "Freeze",
        "Rivals close to you are slowed right down, so you can cut them off or escape.",
        80, 5f, 12f, 40f, 20f,
    )
    val RECHARGE = Power(
        "recharge", "Recharge",
        "Instantly refills your boost energy. Instant: only the cooldown matters.",
        70, 0f, 0f, 60f, 25f,
    )

    val all: List<Power> = listOf(SHIELD, MAGNET, OVERDRIVE, FREEZE, RECHARGE)
    val inGame: List<Power> = all

    fun byId(id: String): Power? = all.firstOrNull { it.id == id }

    const val TANK_MAX = 5
    const val TANK_STEP = 25f

    fun tankCost(level: Int): Int = 80 * (level + 1)
    fun tankCapacity(level: Int): Float = Cfg.MAX_ENERGY + TANK_STEP * level
}

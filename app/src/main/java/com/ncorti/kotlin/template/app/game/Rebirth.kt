package com.ncorti.kotlin.template.app.game

/**
 * Rebirth: spend everything you have collected (coins and skins) to start over stronger.
 * Powers, tank upgrades, scores and quests are kept. Each rebirth gives a bigger coin bonus on
 * every run and a longer speed burst at the start of every life.
 */
object Rebirth {
    /** What the next rebirth costs, in coins you must have. Index = rebirths already done. */
    val costs = listOf(200, 500, 700, 1000, 1400, 1900, 2500, 3200, 4000, 5000)
    val MAX: Int = costs.size

    /** Coins needed for the next rebirth, or null once all are done. */
    fun cost(done: Int): Int? = costs.getOrNull(done)

    /** Coin bonus on run earnings: +25% per rebirth. */
    fun coinMultiplier(done: Int): Float = 1f + 0.25f * done.coerceIn(0, MAX)

    /** Seconds of free speed burst when a life starts: 0 before the first rebirth, then 3, 4, 5, ... */
    fun startBoost(done: Int): Float = if (done <= 0) 0f else 2f + done.coerceAtMost(MAX)
}

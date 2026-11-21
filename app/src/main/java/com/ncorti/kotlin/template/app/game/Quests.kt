package com.ncorti.kotlin.template.app.game

import kotlin.random.Random

/** What a finished run did, as far as quests are concerned. */
class RunStats(
    val score: Int,
    val kills: Int,
    val food: Int,
    val powers: Int,
    val seconds: Int,
    val online: Boolean,
)

enum class QuestKind { PLAY, KILLS, SCORE, FOOD, POWERS, SURVIVE, ONLINE }

/**
 * A goal for today. Quests with [total] = true add up across games; the others take your best
 * single game.
 */
class Quest(
    val id: Int,
    val title: String,
    val desc: String,
    val kind: QuestKind,
    val goal: Int,
    val reward: Int,
    val total: Boolean,
) {
    /** The new progress after [run], given the [current] progress. */
    fun after(current: Int, run: RunStats): Int {
        val gain = when (kind) {
            QuestKind.PLAY -> 1
            QuestKind.KILLS -> run.kills
            QuestKind.SCORE -> run.score
            QuestKind.FOOD -> run.food
            QuestKind.POWERS -> run.powers
            QuestKind.SURVIVE -> run.seconds
            QuestKind.ONLINE -> if (run.online) 1 else 0
        }
        val next = if (total) current + gain else maxOf(current, gain)
        return minOf(next, goal)
    }
}

object Quests {
    const val PER_DAY = 3
    const val ALL_DONE_BONUS = 60

    val pool: List<Quest> = listOf(
        Quest(0, "Warm-up", "Play 3 games", QuestKind.PLAY, 3, 40, true),
        Quest(1, "Hunter", "Eliminate 6 snakes", QuestKind.KILLS, 6, 60, true),
        Quest(2, "High roller", "Score 300 in one game", QuestKind.SCORE, 300, 70, false),
        Quest(3, "Foodie", "Eat 150 pellets", QuestKind.FOOD, 150, 50, true),
        Quest(4, "Power user", "Use powers 5 times", QuestKind.POWERS, 5, 50, true),
        Quest(5, "Survivor", "Stay alive for 2 minutes in one game", QuestKind.SURVIVE, 120, 60, false),
        Quest(6, "Social", "Finish an online game", QuestKind.ONLINE, 1, 80, true),
        Quest(7, "Big eater", "Eat 400 pellets", QuestKind.FOOD, 400, 90, true),
    )

    fun byId(id: Int): Quest? = pool.firstOrNull { it.id == id }

    /** Today's quests: the same three for everyone on the same day. */
    fun forDay(day: Int): List<Quest> = pool.shuffled(Random(day.toLong() * 31L + 7L)).take(PER_DAY)
}

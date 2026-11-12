package com.ncorti.kotlin.template.app.game

/** An offline goal with a reward, shown as a card on the main menu. */
class Challenge(
    val id: Int,
    val title: String,
    val desc: String,
    val goal: Int,
    val rewardCoins: Int,
    val rewardSkin: Int?,
) {
    val rewardText: String
        get() = buildString {
            if (rewardSkin != null) append("Unlocks ${Skins.byId(rewardSkin).name}")
            if (rewardSkin != null && rewardCoins > 0) append(" + ")
            if (rewardCoins > 0) append("$rewardCoins coins")
        }
}

object Challenges {
    val all: List<Challenge> = listOf(
        Challenge(0, "Snake Challenge", "Score 250 in one game", 250, 0, 3),
        Challenge(1, "Hunter", "Eliminate 5 snakes in one game", 5, 120, null),
        Challenge(2, "Arena King", "Finish an Arena game ranked #1 with 150+ points", 1, 150, 6),
    )

    /** How far a single finished run gets toward challenge [id]. */
    fun progress(id: Int, score: Int, kills: Int, rank: Int, solo: Boolean): Int = when (id) {
        0 -> score
        1 -> kills
        2 -> if (!solo && rank == 1 && score >= 150) 1 else 0
        else -> 0
    }
}

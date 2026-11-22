package com.ncorti.kotlin.template.app.game

/** What a redeem code gives. Each code works once per install. */
class CodeReward(
    val coins: Int = 0,
    val skin: Int? = null,
    /** A power to unlock at level 1 (does nothing if you already own it). */
    val power: String? = null,
) {
    val text: String
        get() = buildString {
            if (coins > 0) append("$coins coins")
            if (skin != null) {
                if (isNotEmpty()) append(" + ")
                append("${Skins.byId(skin).name} skin")
            }
            if (power != null) {
                if (isNotEmpty()) append(" + ")
                append("${Powers.byId(power)?.name ?: power} power")
            }
        }
}

object Codes {
    /** Typing this on the Codes screen opens the tester menu instead of redeeming anything. */
    const val SECRET = "TEST"

    private val rewards: Map<String, CodeReward> = mapOf(
        "STARTER" to CodeReward(coins = 100),
        "WELCOME" to CodeReward(coins = 150),
        "BROTHER" to CodeReward(coins = 200),
        "SNAKELIFE" to CodeReward(coins = 250),
        "INFINITY" to CodeReward(coins = 500),
        "FLARE" to CodeReward(skin = 2),
        "SHIELDUP" to CodeReward(power = "shield"),
        "DASHNOW" to CodeReward(coins = 50, power = "dash"),
    )

    /** Upper-cases and strips spaces, so " starter " works. */
    fun normalize(raw: String): String = raw.trim().uppercase().filter { !it.isWhitespace() }

    fun find(raw: String): CodeReward? = rewards[normalize(raw)]

    fun isSecret(raw: String): Boolean = normalize(raw) == SECRET

    val count: Int get() = rewards.size
}

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
        "GHOSTLY" to CodeReward(power = "ghost"),
        "TITANRISE" to CodeReward(coins = 100, power = "titan"),
        "WARPSPEED" to CodeReward(power = "warp"),
        "SLOWMO" to CodeReward(coins = 100, power = "timewarp"),
        "FANG" to CodeReward(skin = 8),
        "DEEPSEA" to CodeReward(skin = 9),
        "DRAGON" to CodeReward(coins = 300, skin = 10),
        "VIPER" to CodeReward(skin = 11),
        "SAKURA" to CodeReward(skin = 12),
        "EMERALD" to CodeReward(skin = 13),
        "STORM" to CodeReward(coins = 200, skin = 14),
        "LAVALORD" to CodeReward(skin = 15),
        "AURORA" to CodeReward(coins = 400, skin = 16),
        "VOIDWALKER" to CodeReward(skin = 17),
        "CANDY" to CodeReward(skin = 18),
        "PRISM" to CodeReward(coins = 600, skin = 19),
        "HUNDRED" to CodeReward(coins = 100),
        "COINRAIN" to CodeReward(coins = 300),
        "JACKPOT" to CodeReward(coins = 750),
        "MEGACOINS" to CodeReward(coins = 1000),
        "ULTRA" to CodeReward(coins = 1500),
        "REBIRTH" to CodeReward(coins = 400),
        "NEWSNAKE" to CodeReward(coins = 120),
        "GAMER" to CodeReward(coins = 220),
        "NEON" to CodeReward(coins = 180),
        "LEGEND" to CodeReward(coins = 800, power = "shield"),
    )

    /** Upper-cases and strips spaces, so " starter " works. */
    fun normalize(raw: String): String = raw.trim().uppercase().filter { !it.isWhitespace() }

    fun find(raw: String): CodeReward? = rewards[normalize(raw)]

    fun isSecret(raw: String): Boolean = normalize(raw) == SECRET

    val count: Int get() = rewards.size

    /** Every valid code (for tests). */
    val all: Set<String> get() = rewards.keys
}

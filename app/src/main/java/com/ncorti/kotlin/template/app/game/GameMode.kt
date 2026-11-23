package com.ncorti.kotlin.template.app.game

/** The rule sets a match can use. The host picks one and every guest plays the same one. */
object GameMode {
    const val CLASSIC = 0
    const val BOMBS = 1
    const val SHRINK = 2
    const val FRENZY = 3
    const val COUNT = 4

    fun valid(m: Int) = m in 0 until COUNT

    fun name(m: Int) = when (m) {
        BOMBS -> "Bomb Rain"
        SHRINK -> "Shrinking Arena"
        FRENZY -> "Food Frenzy"
        else -> "Classic"
    }

    fun blurb(m: Int) = when (m) {
        BOMBS -> "Bombs fall from the sky. Watch the red circles and run!"
        SHRINK -> "The arena keeps shrinking. Stay inside the ring."
        FRENZY -> "Everyone is faster and food is everywhere."
        else -> "The normal arena. Eat, grow, outsmart the others."
    }

    fun short(m: Int) = when (m) {
        BOMBS -> "BOMBS"
        SHRINK -> "SHRINK"
        FRENZY -> "FRENZY"
        else -> "CLASSIC"
    }
}

package com.ncorti.kotlin.template.app.game

import kotlin.math.ceil

/**
 * Cooldown bookkeeping for one snake's powers. The cooldown starts when the effect ends:
 * [wait] holds effect time + cooldown, so "ready" means wait has run out.
 */
class PowerState {
    val wait = FloatArray(Powers.all.size)

    fun effectLeft(s: Snake, p: Power): Float = when (p.id) {
        Powers.SHIELD.id -> s.shield
        Powers.MAGNET.id -> s.magnet
        Powers.OVERDRIVE.id -> s.overdrive
        Powers.FREEZE.id -> s.freeze
        else -> 0f
    }

    fun isActive(s: Snake, p: Power): Boolean = effectLeft(s, p) > 0f

    fun activeSeconds(s: Snake, p: Power): Int = ceil(effectLeft(s, p)).toInt()

    /** Whole seconds until the power can be used again once its effect ended (0 = ready). */
    fun cooldownSeconds(s: Snake, p: Power): Int =
        ceil((wait[Powers.all.indexOf(p)] - effectLeft(s, p)).coerceAtLeast(0f)).toInt()

    fun ready(s: Snake, p: Power, level: Int): Boolean =
        level > 0 && s.alive && !isActive(s, p) && wait[Powers.all.indexOf(p)] <= 0f

    /** Fires the power if it is ready. Returns true if it was used. */
    fun use(s: Snake, p: Power, level: Int): Boolean {
        if (!ready(s, p, level)) return false
        val dur = p.duration(level)
        when (p.id) {
            Powers.SHIELD.id -> s.shield = dur
            Powers.MAGNET.id -> s.magnet = dur
            Powers.OVERDRIVE.id -> s.overdrive = dur
            Powers.FREEZE.id -> s.freeze = dur
            else -> s.energy = s.maxEnergy
        }
        wait[Powers.all.indexOf(p)] = dur + p.cooldown(level)
        return true
    }

    fun tick(dt: Float) {
        for (i in wait.indices) if (wait[i] > 0f) wait[i] = (wait[i] - dt).coerceAtLeast(0f)
    }

    fun reset() = wait.fill(0f)
}

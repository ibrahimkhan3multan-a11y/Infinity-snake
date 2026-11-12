package com.ncorti.kotlin.template.app.game

import kotlin.math.min

const val PI_F = 3.1415927f
const val TWO_PI = 6.2831855f

/** Tunable constants for the arena simulation. World units are abstract "pixels". */
object Cfg {
    const val ARENA_RADIUS = 2600f
    const val STEP = 1f / 60f
    const val BASE_SPEED = 170f
    const val BOOST_SPEED = 300f
    const val BOOST_DRAIN = 38f      // energy per second while boosting
    const val ENERGY_REGEN = 20f     // energy per second while not boosting
    const val BOOST_MIN_START = 15f  // energy needed to (re)start a boost
    const val MAX_ENERGY = 100f
    const val SHIELD_TIME = 6f
    const val MAGNET_TIME = 12f
    const val OVERDRIVE_TIME = 6f
    const val MAGNET_REACH = 140f
    const val HEADSTART_MASS = 60f
    const val TURN_RATE = 6f
    const val BASE_RADIUS = 6f
    const val MAX_RADIUS = 26f
    const val START_SEGS = 8
    const val SEGS_PER_MASS = 0.55f
    const val MAX_SEGS = 600
    const val START_MASS = 10f
    const val FOOD_CAP = 2000
    const val AMBIENT_FOOD = 1100
    const val SPAWN_GRACE = 2.5f
    const val BOARD_SIZE = 6
    const val BOT_COUNT = 14
}

/** Visual and pickup radius of a food item with the given value. */
fun foodRadius(value: Float): Float = 3.5f + min(value, 10f)

/** Wraps an angle difference into (-PI, PI]. */
fun angleDiff(target: Float, from: Float): Float {
    var d = (target - from) % TWO_PI
    if (d > PI_F) d -= TWO_PI
    if (d < -PI_F) d += TWO_PI
    return d
}

/** HSV (h in degrees, s and v in 0..1) to opaque ARGB. */
fun hsvToArgb(h: Float, s: Float, v: Float): Int {
    val hh = (((h % 360f) + 360f) % 360f) / 60f
    val i = hh.toInt()
    val f = hh - i
    val p = v * (1f - s)
    val q = v * (1f - s * f)
    val t = v * (1f - s * (1f - f))
    val r: Float
    val g: Float
    val b: Float
    when (i) {
        0 -> { r = v; g = t; b = p }
        1 -> { r = q; g = v; b = p }
        2 -> { r = p; g = v; b = t }
        3 -> { r = p; g = q; b = v }
        4 -> { r = t; g = p; b = v }
        else -> { r = v; g = p; b = q }
    }
    return (0xFF shl 24) or
        ((r * 255f + 0.5f).toInt() shl 16) or
        ((g * 255f + 0.5f).toInt() shl 8) or
        (b * 255f + 0.5f).toInt()
}

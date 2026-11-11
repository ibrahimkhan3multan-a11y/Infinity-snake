package com.ncorti.kotlin.template.app.game

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Offline bot AI. Level 0 (easy) reacts slowly, wanders and only sees close food; level 1 weighs
 * food by value; level 2 (hard) reacts fastest, hunts smaller snakes and boosts to chase.
 * Every decision first picks a goal (wall avoidance, hunt, or food) and then steers around
 * obstacles by probing a few headings for free space.
 */
object BotBrain {
    private val OFFSETS = floatArrayOf(0f, 0.4f, -0.4f, 0.8f, -0.8f, 1.3f, -1.3f, 2.0f)

    fun interval(level: Int): Float = when (level) {
        0 -> 0.35f
        1 -> 0.2f
        else -> 0.1f
    }

    fun think(w: World, b: Snake) {
        val hx = b.xs[0]
        val hy = b.ys[0]
        val level = b.level
        var desired = b.angle
        var boost = false

        // 1. Stay away from the arena edge.
        val distC = sqrt(hx * hx + hy * hy)
        val margin = 420f + b.radius * 3f
        val wallMode = distC > Cfg.ARENA_RADIUS - margin
        if (wallMode) desired = atan2(-hy, -hx)

        // 2. Hard bots hunt smaller snakes nearby.
        var hunted = false
        if (!wallMode && level >= 2 && w.rnd.nextFloat() < 0.35f) {
            var bestD2 = 450f * 450f
            var target: Snake? = null
            for (i in 0 until w.snakes.size) {
                val o = w.snakes[i]
                if (o === b || !o.alive || o.grace > 0f || o.mass > b.mass * 0.85f) continue
                val dx = o.xs[0] - hx
                val dy = o.ys[0] - hy
                val d2 = dx * dx + dy * dy
                if (d2 < bestD2) {
                    bestD2 = d2
                    target = o
                }
            }
            if (target != null) {
                val tx = target.xs[0] + cos(target.angle) * 140f
                val ty = target.ys[0] + sin(target.angle) * 140f
                desired = atan2(ty - hy, tx - hx)
                boost = bestD2 < 350f * 350f
                hunted = true
            }
        }

        // 3. Otherwise go for food.
        if (!wallMode && !hunted) {
            val ca = cos(b.angle)
            val sa = sin(b.angle)
            val range = 600f + level * 150f
            val range2 = range * range
            var bestScore = -1f
            var bx = 0f
            var by = 0f
            var bd = 0f
            for (i in 0 until Cfg.FOOD_CAP) {
                if (!w.falive[i]) continue
                val dx = w.fx[i] - hx
                val dy = w.fy[i] - hy
                val d2 = dx * dx + dy * dy
                if (d2 > range2) continue
                val d = sqrt(d2)
                val ahead = ((dx * ca + dy * sa) / (d + 0.001f)).coerceIn(-1f, 1f)
                val base = if (level == 0) 1f / (d + 20f) else (w.fv[i] + 0.5f) / (d + 70f)
                val sc = base * (0.55f + 0.45f * ahead)
                if (sc > bestScore) {
                    bestScore = sc
                    bx = w.fx[i]
                    by = w.fy[i]
                    bd = d
                }
            }
            if (bestScore >= 0f) {
                desired = atan2(by - hy, bx - hx)
                boost = level >= 1 && bd > 280f && b.mass > 45f && w.rnd.nextFloat() < 0.4f
            } else {
                desired = b.angle + (w.rnd.nextFloat() - 0.5f) * 1.2f
            }
        }

        // 4. Steer around other snakes (and the wall if it is deadly).
        val look = (if (level == 0) 90f else 120f) + b.radius * 5f + (if (b.boostActive) 60f else 0f)
        val safe = b.radius * 1.8f + 14f
        val tries = if (level == 0) 5 else OFFSETS.size
        var chosen = desired
        var bestClear = -Float.MAX_VALUE
        var found = false
        for (k in 0 until tries) {
            val a = desired + OFFSETS[k]
            val c = w.clearance(b, a, look)
            if (c >= safe) {
                chosen = a
                found = true
                break
            }
            if (c > bestClear) {
                bestClear = c
                chosen = a
            }
        }
        if (!found || chosen != desired) boost = false

        // Easy bots are sloppy.
        val noise = if (level == 0) (w.rnd.nextFloat() - 0.5f) * 0.5f else 0f
        b.targetAngle = chosen + noise
        b.boosting = boost
    }
}

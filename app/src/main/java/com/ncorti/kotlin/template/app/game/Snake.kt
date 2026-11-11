package com.ncorti.kotlin.template.app.game

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One snake. The body is a chain of points in preallocated arrays: index 0 is the head and each
 * following point is pulled toward the previous one to keep a fixed spacing, so the body follows
 * the head naturally without any per-frame allocation.
 */
class Snake(val id: Int, val isPlayer: Boolean, val name: String) {
    val xs = FloatArray(Cfg.MAX_SEGS)
    val ys = FloatArray(Cfg.MAX_SEGS)
    var n = 0
    var angle = 0f
    var targetAngle = 0f
    var mass = Cfg.START_MASS
    var boosting = false
    var boostActive = false
    var alive = false
    var grace = 0f
    var turnMul = 1f
    var headColor = 0
    var tailColor = 0
    var glow = false
    var rainbow = false
    var level = 1
    var kills = 0
    var deaths = 0
    var lastRank = 1
    var respawnTimer = 0f
    var thinkTimer = 0f
    private var dropAcc = 0f

    val radius: Float
        get() = minOf(Cfg.MAX_RADIUS, Cfg.BASE_RADIUS + sqrt(mass) * 0.6f)

    val spacing: Float
        get() = max(7f, radius * 0.9f)

    val targetSegs: Int
        get() = (Cfg.START_SEGS + mass * Cfg.SEGS_PER_MASS).toInt().coerceIn(Cfg.START_SEGS, Cfg.MAX_SEGS)

    val score: Int
        get() = mass.toInt()

    /** Puts the snake alive at (x, y) heading [ang], body trailing straight behind the head. */
    fun place(x: Float, y: Float, ang: Float, m: Float) {
        mass = m
        angle = ang
        targetAngle = ang
        xs[0] = x
        ys[0] = y
        n = 1
        val want = targetSegs
        val sp = spacing
        val cx = cos(ang)
        val sy = sin(ang)
        while (n < want) {
            xs[n] = x - cx * sp * n
            ys[n] = y - sy * sp * n
            n++
        }
        alive = true
        boosting = false
        boostActive = false
        dropAcc = 0f
    }

    fun step(dt: Float, world: World) {
        if (grace > 0f) grace = max(0f, grace - dt)

        val r = radius
        val maxTurn = Cfg.TURN_RATE * turnMul / (1f + (r - Cfg.BASE_RADIUS) * 0.05f) * dt
        angle += angleDiff(targetAngle, angle).coerceIn(-maxTurn, maxTurn)
        if (angle > PI_F) angle -= TWO_PI else if (angle < -PI_F) angle += TWO_PI

        boostActive = boosting && mass > Cfg.MIN_BOOST_MASS
        val speed = if (boostActive) Cfg.BOOST_SPEED else Cfg.BASE_SPEED
        if (boostActive) {
            val cost = Cfg.BOOST_COST * dt
            mass -= cost
            dropAcc += cost
            if (dropAcc >= 1.5f) {
                dropAcc -= 1.5f
                world.dropTrailFood(this, 1.5f)
            }
        }

        xs[0] += cos(angle) * speed * dt
        ys[0] += sin(angle) * speed * dt

        val want = targetSegs
        while (n < want) {
            xs[n] = xs[n - 1]
            ys[n] = ys[n - 1]
            n++
        }
        if (n > want) n = want

        val sp = spacing
        for (i in 1 until n) {
            val dx = xs[i - 1] - xs[i]
            val dy = ys[i - 1] - ys[i]
            val d = sqrt(dx * dx + dy * dy)
            if (d > sp) {
                val f = (d - sp) / d
                xs[i] += dx * f
                ys[i] += dy * f
            }
        }
    }
}

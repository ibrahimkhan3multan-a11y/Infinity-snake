package com.ncorti.kotlin.template.app.game

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Fixed-size particle pool; nothing is allocated while the game runs. */
class Particles(val capacity: Int = 300) {
    val x = FloatArray(capacity)
    val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    val life = FloatArray(capacity)
    val maxLife = FloatArray(capacity)
    val size = FloatArray(capacity)
    val color = IntArray(capacity)
    private var cursor = 0

    fun spawn(px: Float, py: Float, pvx: Float, pvy: Float, lifeSec: Float, argb: Int, sz: Float) {
        val i = cursor
        cursor = (cursor + 1) % capacity
        x[i] = px
        y[i] = py
        vx[i] = pvx
        vy[i] = pvy
        life[i] = lifeSec
        maxLife[i] = lifeSec
        color[i] = argb
        size[i] = sz
    }

    fun burst(px: Float, py: Float, argb: Int, count: Int, speed: Float, sz: Float, rnd: Random) {
        for (k in 0 until count) {
            val a = rnd.nextFloat() * TWO_PI
            val s = speed * (0.3f + rnd.nextFloat() * 0.7f)
            spawn(px, py, cos(a) * s, sin(a) * s, 0.4f + rnd.nextFloat() * 0.5f, argb, sz * (0.6f + rnd.nextFloat() * 0.8f))
        }
    }

    fun update(dt: Float) {
        val drag = 1f - 2.5f * dt
        for (i in 0 until capacity) {
            if (life[i] <= 0f) continue
            life[i] -= dt
            x[i] += vx[i] * dt
            y[i] += vy[i] * dt
            vx[i] *= drag
            vy[i] *= drag
        }
    }

    fun clear() {
        for (i in 0 until capacity) life[i] = 0f
    }
}

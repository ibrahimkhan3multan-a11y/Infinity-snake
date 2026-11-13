package com.ncorti.kotlin.template.app.net

import com.ncorti.kotlin.template.app.game.Powers
import com.ncorti.kotlin.template.app.game.Snake
import org.json.JSONArray
import org.json.JSONObject

/** Wire format helpers shared by host and guest. Payloads travel inside relay `{t:"m"}` frames. */
object Protocol {
    const val SNAPSHOT_HZ = 15f
    const val INPUT_HZ = 20f
    /** Snakes and food farther than this from the guest's head are not sent. */
    const val SNAKE_RANGE = 1500f
    const val FOOD_RANGE = 950f
    /** Food is resent every Nth snapshot. */
    const val FOOD_EVERY = 3
    const val MAX_SAMPLES = 48

    const val F_ALIVE = 1
    const val F_BOOST = 2
    const val F_GRACE = 4
    const val F_GLOW = 8
    const val F_RAINBOW = 16

    fun frameToHost(m: JSONObject): JSONObject = JSONObject().put("t", "m").put("m", m)
    fun frameToGuest(to: Int, m: JSONObject): JSONObject = JSONObject().put("t", "m").put("to", to).put("m", m)

    fun hello(name: String, head: Int, tail: Int, glow: Boolean, rainbow: Boolean, tank: Int, levels: IntArray) =
        JSONObject()
            .put("k", "hello").put("name", name).put("head", head).put("tail", tail)
            .put("glow", glow).put("rainbow", rainbow).put("tank", tank)
            .put("lv", JSONArray(levels.toList()))

    fun input(angle: Float, boost: Boolean) =
        JSONObject().put("k", "in").put("a", angle.toDouble()).put("b", if (boost) 1 else 0)

    fun power(index: Int) = JSONObject().put("k", "pw").put("i", index)
    fun respawn() = JSONObject().put("k", "respawn")

    /** Indices of the body points that are sent for a snake with [n] points. */
    fun sampleStep(n: Int): Int = ((n + MAX_SAMPLES - 1) / MAX_SAMPLES).coerceAtLeast(1)

    fun snakeFlags(s: Snake): Int =
        (if (s.alive) F_ALIVE else 0) or (if (s.boostActive) F_BOOST else 0) or
            (if (s.grace > 0f) F_GRACE else 0) or (if (s.glow) F_GLOW else 0) or (if (s.rainbow) F_RAINBOW else 0)

    /** Packs the (down-sampled) body of [s] as a flat int array: x0,y0,x1,y1... always ending on the tail. */
    fun packBody(s: Snake): JSONArray {
        val out = JSONArray()
        val step = sampleStep(s.n)
        var i = 0
        while (i < s.n) {
            out.put(Math.round(s.xs[i])).put(Math.round(s.ys[i]))
            if (i == s.n - 1) break
            i = minOf(i + step, s.n - 1)
        }
        return out
    }

    /** Expands a packed body back to [n] points (linear interpolation between samples). */
    fun unpackBody(n: Int, pts: JSONArray, outX: FloatArray, outY: FloatArray) {
        val samples = pts.length() / 2
        if (samples == 0 || n <= 0) return
        val step = sampleStep(n)
        for (k in 0 until samples - 1) {
            val i0 = minOf(k * step, n - 1)
            val i1 = minOf((k + 1) * step, n - 1)
            val ax = pts.getDouble(2 * k).toFloat()
            val ay = pts.getDouble(2 * k + 1).toFloat()
            val bx = pts.getDouble(2 * k + 2).toFloat()
            val by = pts.getDouble(2 * k + 3).toFloat()
            val span = (i1 - i0).coerceAtLeast(1)
            for (i in i0 until i1) {
                val t = (i - i0).toFloat() / span
                outX[i] = ax + (bx - ax) * t
                outY[i] = ay + (by - ay) * t
            }
        }
        val last = minOf((samples - 1) * step, n - 1)
        outX[last] = pts.getDouble(2 * (samples - 1)).toFloat()
        outY[last] = pts.getDouble(2 * (samples - 1) + 1).toFloat()
    }

    fun clampLevel(v: Int) = v.coerceIn(0, Powers.MAX_LEVEL)
    fun clampTank(v: Int) = v.coerceIn(0, Powers.TANK_MAX)

    val FOOD_RANGE_SQ = FOOD_RANGE * FOOD_RANGE
    val SNAKE_RANGE_SQ = SNAKE_RANGE * SNAKE_RANGE
}

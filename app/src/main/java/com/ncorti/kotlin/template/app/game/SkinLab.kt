package com.ncorti.kotlin.template.app.game

import kotlin.math.abs

/**
 * The Skin Lab turns a few words into a snake skin. It runs entirely on the phone (no internet, no
 * account): known words pick colours and effects, and the rest of the text plus a "variation" number
 * shifts the shades, so the same words with another variation give another look.
 */
object SkinLab {
    const val COST = 1000
    const val MAX_CUSTOM = 6
    const val FIRST_ID = 100

    private class Theme(val words: List<String>, val head: Int, val tail: Int, val glow: Boolean = true, val rainbow: Boolean = false)

    private fun c(v: Long) = v.toInt()

    private val themes = listOf(
        Theme(listOf("fire", "flame", "lava", "dragon", "hot", "burn", "magma", "red"), c(0xFFFFD54F), c(0xFFD50000)),
        Theme(listOf("ice", "frost", "snow", "cold", "winter", "crystal", "freeze"), c(0xFFE8FAFF), c(0xFF2196F3)),
        Theme(listOf("ocean", "sea", "water", "wave", "blue", "aqua", "shark", "fish"), c(0xFF4DD0FF), c(0xFF0D47A1)),
        Theme(listOf("forest", "leaf", "jungle", "green", "nature", "tree", "frog", "toxic", "acid", "slime"), c(0xFF9CFF57), c(0xFF1B5E20)),
        Theme(listOf("gold", "golden", "king", "royal", "rich", "sun", "yellow", "treasure"), c(0xFFFFF176), c(0xFFB8860B)),
        Theme(listOf("shadow", "dark", "night", "black", "ninja", "ghost", "void", "death", "vampire"), c(0xFF9AA0B5), c(0xFF0A0A14)),
        Theme(listOf("pink", "love", "candy", "sweet", "cute", "flower", "sakura", "heart", "unicorn"), c(0xFFFFB3E6), c(0xFFE91E8C)),
        Theme(listOf("galaxy", "space", "star", "cosmic", "universe", "alien", "purple", "magic"), c(0xFFD1A3FF), c(0xFF311B92)),
        Theme(listOf("electric", "thunder", "storm", "lightning", "energy", "neon", "cyber", "robot"), c(0xFFFFFF7A), c(0xFF2D5BFF)),
        Theme(listOf("rainbow", "unicorn", "party", "colour", "color", "pride", "prism"), c(0xFFFFFFFF), c(0xFFFFFFFF), rainbow = true),
        Theme(listOf("silver", "steel", "metal", "chrome", "iron", "knight", "stone", "grey", "gray"), c(0xFFF5F5F5), c(0xFF546E7A)),
        Theme(listOf("orange", "tiger", "autumn", "pumpkin", "fox", "desert", "sand"), c(0xFFFFB74D), c(0xFFBF360C)),
        Theme(listOf("mint", "emerald", "jade", "spring", "tea"), c(0xFF7DFFB3), c(0xFF00695C)),
        Theme(listOf("chocolate", "coffee", "brown", "wood", "earth", "mud"), c(0xFFD7A86E), c(0xFF3E2723)),
    )

    class Look(val name: String, val head: Int, val tail: Int, val glow: Boolean, val rainbow: Boolean)

    private fun hash(s: String): Int {
        var h = 7
        for (ch in s) h = h * 31 + ch.code
        return abs(h)
    }

    private fun mixC(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 255) * (1 - t) + ((b shr shift) and 255) * t).toInt().coerceIn(0, 255)
        return (255 shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun hsv(h: Float, s: Float, v: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val i = hh.toInt()
        val f = hh - i
        val p = v * (1 - s)
        val q = v * (1 - s * f)
        val t = v * (1 - s * (1 - f))
        val (r, g, b) = when (i) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        return (255 shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
    }

    /** A skin look for [prompt]. [variation] 0, 1, 2... gives different takes on the same words. */
    fun generate(prompt: String, variation: Int): Look {
        val text = prompt.trim().lowercase().take(40)
        val words = text.split(Regex("[^a-z]+")).filter { it.length >= 2 }
        val hits = ArrayList<Theme>()
        for (w in words) {
            val t = themes.firstOrNull { th -> th.words.any { k -> k == w || (w.length >= 4 && (w.startsWith(k) || k.startsWith(w))) } }
            if (t != null && t !in hits) hits.add(t)
        }
        val seed = hash(text) + variation * 7919
        val name = niceName(text, variation)
        if (hits.isEmpty()) {
            // Unknown words: a colour scheme made from the text itself.
            val hue = (seed % 360).toFloat()
            val head = hsv(hue, 0.35f + (seed / 7 % 30) / 100f, 1f)
            val tail = hsv(hue + 40f + (seed / 11 % 80), 0.9f, 0.45f + (seed / 13 % 25) / 100f)
            return Look(name, head, tail, true, false)
        }
        val a = hits[0]
        val b = hits.getOrNull(1)
        if (a.rainbow || b?.rainbow == true) return Look(name, c(0xFFFFFFFF), c(0xFFFFFFFF), true, true)
        // Variation shifts the blend between the two themes (or the shade of one theme).
        val k = (variation % 4) / 4f
        var head = if (b != null) mixC(a.head, b.head, 0.25f + 0.5f * k) else mixC(a.head, 0xFFFFFFFF.toInt(), 0.12f * (variation % 3))
        var tail = if (b != null) mixC(a.tail, b.tail, 0.35f + 0.4f * k) else mixC(a.tail, c(0xFF000000), 0.12f * ((variation + 1) % 3))
        if (variation % 2 == 1 && b == null) {
            val t = head; head = tail; tail = t
            head = mixC(head, 0xFFFFFFFF.toInt(), 0.45f)
        }
        return Look(name, head, tail, a.glow, false)
    }

    private fun niceName(text: String, variation: Int): String {
        val cleaned = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.take(3)
            .joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }
        val base = cleaned.ifEmpty { "Custom" }.take(18)
        return if (variation == 0) base else "$base ${variation + 1}"
    }
}

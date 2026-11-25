package com.ncorti.kotlin.template.app.game3d

import kotlin.math.floor

/**
 * The 3D world: a grid of tiles stacked in levels (floors, ramps, bridges, tunnels).
 *
 * Directions are 0 = North (-z), 1 = East (+x), 2 = South (+z), 3 = West (-x). A ramp tile at level
 * L rises in one direction: its low end is at height L and its high end at height L + 1, so a snake
 * can walk between levels only along ramps. [next] is the single rule for where a snake can go;
 * everything else (camera, tests, bots) builds on it.
 */
class GameMap(val def: MapData.Def) {
    val w: Int = def.levels.maxOf { lvl -> lvl.maxOfOrNull { it.length } ?: 0 }
    val h: Int = def.levels.maxOf { it.size }
    val levels: Int = def.levels.size

    /** Floating maps (sky) have thin slabs; the others stand on solid ground. */
    val floating: Boolean = def.id == "sky"

    // kinds: NONE, FLAT, or ramp rising in direction 0..3 (stored as 2 + dir)
    private val kinds = Array(levels) { ByteArray(w * h) }
    private val pillars = Array(levels) { BooleanArray(w * h) }
    private val walls = BooleanArray(w * h)

    /** Cells where the snake may be placed, one id per (x, z, level) that is a free flat tile. */
    val flatTiles: IntArray

    init {
        for (l in 0 until levels) {
            for (z in 0 until h) {
                val row = def.levels[l].getOrNull(z) ?: continue
                for (x in 0 until w) {
                    val c = row.getOrElse(x) { ' ' }
                    val i = z * w + x
                    when (c) {
                        '.' -> kinds[l][i] = FLAT
                        'x' -> { kinds[l][i] = FLAT; pillars[l][i] = true }
                        '^' -> kinds[l][i] = (RAMP + 0).toByte()
                        '>' -> kinds[l][i] = (RAMP + 1).toByte()
                        'v' -> kinds[l][i] = (RAMP + 2).toByte()
                        '<' -> kinds[l][i] = (RAMP + 3).toByte()
                        '#' -> if (l == 0) walls[i] = true
                    }
                }
            }
        }
        val list = ArrayList<Int>()
        for (l in 0 until levels) for (z in 0 until h) for (x in 0 until w) {
            if (kind(x, z, l) == FLAT.toInt() && !pillars[l][z * w + x]) list.add(id(x, z, l))
        }
        flatTiles = list.toIntArray()
    }

    // ------------------------------------------------------------ cells

    fun id(x: Int, z: Int, l: Int): Int = (l * h + z) * w + x
    fun cx(id: Int): Int = id % w
    fun cz(id: Int): Int = (id / w) % h
    fun cl(id: Int): Int = id / (w * h)
    val cellCount: Int get() = levels * w * h

    fun inside(x: Int, z: Int) = x in 0 until w && z in 0 until h

    /** NONE (0), FLAT (1) or 2..5 for a ramp rising N/E/S/W. */
    fun kind(x: Int, z: Int, l: Int): Int =
        if (l < 0 || l >= levels || !inside(x, z)) NONE.toInt() else kinds[l][z * w + x].toInt()

    fun kind(id: Int): Int = kind(cx(id), cz(id), cl(id))
    fun isRamp(id: Int) = kind(id) >= RAMP
    fun rampDir(id: Int) = kind(id) - RAMP
    fun walkable(x: Int, z: Int, l: Int) = kind(x, z, l) != NONE.toInt()
    fun pillar(x: Int, z: Int, l: Int) = l in 0 until levels && inside(x, z) && pillars[l][z * w + x]
    fun wall(x: Int, z: Int) = inside(x, z) && walls[z * w + x]

    /** A tile some level below this one is walkable: this tile is then a bridge or tunnel roof. */
    fun hasLower(x: Int, z: Int, l: Int): Boolean {
        for (k in 0 until l) if (walkable(x, z, k)) return true
        return false
    }

    fun hasUpper(x: Int, z: Int, l: Int): Boolean {
        for (k in l + 1 until levels) if (walkable(x, z, k)) return true
        return false
    }

    // ------------------------------------------------------------ the movement rule

    /** Height (in levels) where a snake leaves tile [k]/[l] heading [d], or -1 if it can't. */
    private fun exitH(k: Int, l: Int, d: Int): Int {
        if (k == FLAT.toInt()) return l
        val r = k - RAMP
        return when (d) {
            r -> l + 1
            (r + 2) % 4 -> l
            else -> -1
        }
    }

    private fun entryH(k: Int, l: Int, d: Int): Int {
        if (k == FLAT.toInt()) return l
        val r = k - RAMP
        return when (d) {
            r -> l
            (r + 2) % 4 -> l + 1
            else -> -1
        }
    }

    /**
     * The tile reached by moving one cell from [cell] in direction [d], or -1 when there is nothing
     * to stand on there (a wall, an edge, a drop, or a ramp approached from its side).
     */
    fun next(cell: Int, d: Int): Int {
        val x = cx(cell)
        val z = cz(cell)
        val l = cl(cell)
        val e = exitH(kind(x, z, l), l, d)
        if (e < 0) return -1
        val nx = x + DX[d]
        val nz = z + DZ[d]
        if (!inside(nx, nz)) return -1
        for (l2 in 0 until levels) {
            val k2 = kind(nx, nz, l2)
            if (k2 == NONE.toInt() || pillars[l2][nz * w + nx]) continue
            if (entryH(k2, l2, d) == e) return id(nx, nz, l2)
        }
        return -1
    }

    // ------------------------------------------------------------ geometry queries

    /** Surface height of tile (x, z, l) at local position (fx, fz) in 0..1. */
    fun surfaceY(x: Int, z: Int, l: Int, fx: Float, fz: Float): Float {
        val k = kind(x, z, l)
        val base = l * LEVEL_H
        if (k < RAMP) return base
        val t = when (k - RAMP) {
            0 -> 1f - fz
            1 -> fx
            2 -> fz
            else -> 1f - fx
        }
        return base + t.coerceIn(0f, 1f) * LEVEL_H
    }

    /** Lowest point of the solid under tile (x, z, l). */
    fun bottomY(x: Int, z: Int, l: Int): Float {
        val base = l * LEVEL_H
        return if (floating || hasLower(x, z, l)) base - if (hasLower(x, z, l)) SLAB else FLOAT_SLAB else -GROUND
    }

    /** True when the point is inside something solid (used to keep the camera out of walls). */
    fun solidAt(px: Float, py: Float, pz: Float): Boolean {
        val x = floor(px).toInt()
        val z = floor(pz).toInt()
        if (!inside(x, z)) return false
        if (walls[z * w + x] && py < WALL_H && py > -GROUND) return true
        val fx = px - x
        val fz = pz - z
        for (l in 0 until levels) {
            if (!walkable(x, z, l)) continue
            val top = surfaceY(x, z, l, fx, fz)
            val extra = if (pillars[l][z * w + x]) PILLAR_H else 0f
            if (py < top + extra && py > bottomY(x, z, l)) return true
        }
        return false
    }

    companion object {
        const val NONE: Byte = 0
        const val FLAT: Byte = 1
        const val RAMP = 2
        val DX = intArrayOf(0, 1, 0, -1)
        val DZ = intArrayOf(-1, 0, 1, 0)
        const val LEVEL_H = 1.4f
        const val SLAB = 0.3f
        const val FLOAT_SLAB = 0.55f
        const val GROUND = 0.6f
        const val WALL_H = LEVEL_H
        const val PILLAR_H = 1.15f
        fun opposite(d: Int) = (d + 2) % 4
    }
}

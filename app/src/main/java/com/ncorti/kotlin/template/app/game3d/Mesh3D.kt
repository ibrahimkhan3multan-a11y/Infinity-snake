package com.ncorti.kotlin.template.app.game3d

import kotlin.math.sqrt

/** Colours for one map. */
class Palette(
    val top1: Int, val top2: Int, val ramp: Int, val side: Int, val wall: Int, val pillar: Int,
    val roof: Int, val skyTop: Int, val fog: Int,
)

fun paletteFor(id: String): Palette = when (id) {
    "underground" -> Palette(0xFF6D5A4A.toInt(), 0xFF5F4E40.toInt(), 0xFFB08A5A.toInt(), 0xFF3D3128.toInt(), 0xFF2A2420.toInt(), 0xFF8A7A9A.toInt(), 0xFF7A6A5A.toInt(), 0xFF0B0714.toInt(), 0xFF150E1E.toInt())
    "sky" -> Palette(0xFFEAF4FF.toInt(), 0xFFCFE4FA.toInt(), 0xFFFFC857.toInt(), 0xFF8FB4DE.toInt(), 0xFF6A8EC0.toInt(), 0xFFFF7AC8.toInt(), 0xFFDCEBFA.toInt(), 0xFF2B64C8.toInt(), 0xFF9CCBF5.toInt())
    "maze" -> Palette(0xFF3F9E7D.toInt(), 0xFF36896C.toInt(), 0xFFFFB347.toInt(), 0xFF24604D.toInt(), 0xFF5E4B9A.toInt(), 0xFFE8D25C.toInt(), 0xFF4DB894.toInt(), 0xFF140C2C.toInt(), 0xFF2A1E4F.toInt())
    "adventure" -> Palette(0xFF6FBF5B.toInt(), 0xFF63AF50.toInt(), 0xFFE6B655.toInt(), 0xFF7A6A52.toInt(), 0xFF6B6F7A.toInt(), 0xFFB5E0FF.toInt(), 0xFF7BC96A.toInt(), 0xFF3C8CE0.toInt(), 0xFFBFE3F7.toInt())
    else -> Palette(0xFF8E5CF2.toInt(), 0xFF7E4DE0.toInt(), 0xFFFFC857.toInt(), 0xFF4B2E94.toInt(), 0xFF2D2060.toInt(), 0xFF5CE1E6.toInt(), 0xFF9A6CFF.toInt(), 0xFF0E0824.toInt(), 0xFF241552.toInt())
}

/**
 * A growing list of lit quads. The first [staticCount] are the map (built once); the rest are
 * rebuilt every frame (food, obstacles). Triangles repeat their last vertex.
 */
class Mesh3D(capacity: Int = 16384) {
    var v = FloatArray(capacity * 12)
    var col = IntArray(capacity)
    var nrm = FloatArray(capacity * 3)
    var cen = FloatArray(capacity * 3)
    /** Column index (z * width + x) of a roof/bridge tile this face belongs to, or -1. */
    var fadeCol = IntArray(capacity)
    var fadeLvl = IntArray(capacity)
    var count = 0
    var staticCount = 0

    private fun grow() {
        val cap = col.size * 2
        v = v.copyOf(cap * 12)
        col = col.copyOf(cap)
        nrm = nrm.copyOf(cap * 3)
        cen = cen.copyOf(cap * 3)
        fadeCol = fadeCol.copyOf(cap)
        fadeLvl = fadeLvl.copyOf(cap)
    }

    fun resetDynamic() {
        count = staticCount
    }

    fun freeze() {
        staticCount = count
    }

    /**
     * Adds a quad (a, b, c, d in order). [hx],[hy],[hz] is the direction the face should look
     * toward; the winding is corrected to match it. Lighting is baked into the colour.
     */
    fun quad(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float,
        hx: Float, hy: Float, hz: Float, color: Int, fadeColumn: Int = -1, fadeLevel: Int = 0,
    ) {
        if (count >= col.size) grow()
        val i = count++
        // normal = (b - a) x (c - a), or from (c - a) x (d - b) for robustness with repeated vertices
        val ux = cx - ax; val uy = cy - ay; val uz = cz - az
        val wx = dx - bx; val wy = dy - by; val wz = dz - bz
        var nx = uy * wz - uz * wy
        var ny = uz * wx - ux * wz
        var nz = ux * wy - uy * wx
        val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        nx /= l; ny /= l; nz /= l
        if (nx * hx + ny * hy + nz * hz < 0f) { nx = -nx; ny = -ny; nz = -nz }
        val o = i * 12
        v[o] = ax; v[o + 1] = ay; v[o + 2] = az
        v[o + 3] = bx; v[o + 4] = by; v[o + 5] = bz
        v[o + 6] = cx; v[o + 7] = cy; v[o + 8] = cz
        v[o + 9] = dx; v[o + 10] = dy; v[o + 11] = dz
        nrm[i * 3] = nx; nrm[i * 3 + 1] = ny; nrm[i * 3 + 2] = nz
        cen[i * 3] = (ax + bx + cx + dx) / 4f
        cen[i * 3 + 1] = (ay + by + cy + dy) / 4f
        cen[i * 3 + 2] = (az + bz + cz + dz) / 4f
        col[i] = shade(color, nx, ny, nz)
        fadeCol[i] = fadeColumn
        fadeLvl[i] = fadeLevel
    }

    private fun shade(c: Int, nx: Float, ny: Float, nz: Float): Int {
        // One fixed sun, high and a little to the front-left, plus a soft ambient floor.
        val d = (nx * LX + ny * LY + nz * LZ).coerceAtLeast(0f)
        val k = 0.50f + 0.50f * d
        val r = (((c shr 16) and 255) * k).toInt().coerceIn(0, 255)
        val g = (((c shr 8) and 255) * k).toInt().coerceIn(0, 255)
        val b = ((c and 255) * k).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    companion object {
        private const val LX = -0.32f
        private const val LY = 0.88f
        private const val LZ = -0.35f
    }
}

/** Builds the static geometry of a map into [Mesh3D]. */
object MapMesh {
    private fun edgeH(kind: Int, l: Int, d: Int): Int {
        if (kind == GameMap.FLAT.toInt()) return l
        val r = kind - GameMap.RAMP
        return when (d) {
            r -> l + 1
            GameMap.opposite(r) -> l
            else -> -1
        }
    }

    private fun covered(map: GameMap, x: Int, z: Int, l: Int, d: Int): Boolean {
        val nx = x + GameMap.DX[d]
        val nz = z + GameMap.DZ[d]
        if (!map.inside(nx, nz)) return false
        if (map.wall(nx, nz)) return true
        val eh = edgeH(map.kind(x, z, l), l, d)
        if (eh < 0) return false
        for (l2 in 0 until map.levels) {
            val k2 = map.kind(nx, nz, l2)
            if (k2 == 0) continue
            if (edgeH(k2, l2, GameMap.opposite(d)) == eh) return true
        }
        return false
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 255) * (1 - t) + ((b shr s) and 255) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun build(map: GameMap, pal: Palette, mesh: Mesh3D) {
        mesh.count = 0
        val lh = GameMap.LEVEL_H
        // Corner order: NW, NE, SE, SW.
        val px = FloatArray(4)
        val pz = FloatArray(4)
        val hs = FloatArray(4)
        val sideA = intArrayOf(0, 1, 2, 3)
        val sideB = intArrayOf(1, 2, 3, 0)
        for (l in 0 until map.levels) {
            for (z in 0 until map.h) {
                for (x in 0 until map.w) {
                    val k = map.kind(x, z, l)
                    if (k == 0) continue
                    val base = l * lh
                    val low = map.hasLower(x, z, l)
                    val bottom = map.bottomY(x, z, l)
                    val fc = if (low) z * map.w + x else -1
                    px[0] = x.toFloat(); pz[0] = z.toFloat()
                    px[1] = x + 1f; pz[1] = z.toFloat()
                    px[2] = x + 1f; pz[2] = z + 1f
                    px[3] = x.toFloat(); pz[3] = z + 1f
                    for (c in 0 until 4) hs[c] = base
                    if (k >= GameMap.RAMP) {
                        when (k - GameMap.RAMP) {
                            0 -> { hs[0] = base + lh; hs[1] = base + lh }
                            1 -> { hs[1] = base + lh; hs[2] = base + lh }
                            2 -> { hs[2] = base + lh; hs[3] = base + lh }
                            else -> { hs[3] = base + lh; hs[0] = base + lh }
                        }
                    }
                    var top = if (k >= GameMap.RAMP) pal.ramp else if ((x + z) and 1 == 0) pal.top1 else pal.top2
                    if (low) top = mix(top, pal.roof, 0.35f)
                    mesh.quad(
                        px[0], hs[0], pz[0], px[1], hs[1], pz[1], px[2], hs[2], pz[2], px[3], hs[3], pz[3],
                        0f, 1f, 0f, top, fc, l,
                    )
                    for (d in 0 until 4) {
                        if (covered(map, x, z, l, d)) continue
                        val a = sideA[d]
                        val b = sideB[d]
                        val ha = hs[a]
                        val hb = hs[b]
                        val span = maxOf(ha, hb) - bottom
                        val segs = kotlin.math.ceil(span / 1.25f).toInt().coerceIn(1, 4)
                        val dxn = GameMap.DX[d].toFloat()
                        val dzn = GameMap.DZ[d].toFloat()
                        for (s in 0 until segs) {
                            val t0 = s / segs.toFloat()
                            val t1 = (s + 1) / segs.toFloat()
                            val ya0 = ha + (bottom - ha) * t0
                            val yb0 = hb + (bottom - hb) * t0
                            val ya1 = ha + (bottom - ha) * t1
                            val yb1 = hb + (bottom - hb) * t1
                            mesh.quad(
                                px[a], ya0, pz[a], px[b], yb0, pz[b], px[b], yb1, pz[b], px[a], ya1, pz[a],
                                dxn, 0f, dzn, pal.side, fc, l,
                            )
                        }
                    }
                    if (low) {
                        mesh.quad(
                            x.toFloat(), bottom, z.toFloat(), x + 1f, bottom, z.toFloat(), x + 1f, bottom, z + 1f, x.toFloat(), bottom, z + 1f,
                            0f, -1f, 0f, pal.side, fc, l,
                        )
                    }
                    if (map.pillar(x, z, l)) pillar(mesh, x + 0.5f, base, z + 0.5f, pal.pillar)
                }
            }
        }
        // Rock walls: solid columns beside the floor.
        val wh = GameMap.WALL_H
        val g = GameMap.GROUND
        for (z in 0 until map.h) {
            for (x in 0 until map.w) {
                if (!map.wall(x, z)) continue
                if (!map.hasUpper(x, z, 0)) {
                    mesh.quad(
                        x.toFloat(), wh, z.toFloat(), x + 1f, wh, z.toFloat(), x + 1f, wh, z + 1f, x.toFloat(), wh, z + 1f,
                        0f, 1f, 0f, mix(pal.wall, 0xFFFFFFFF.toInt(), 0.16f),
                    )
                }
                for (d in 0 until 4) {
                    val nx = x + GameMap.DX[d]
                    val nz = z + GameMap.DZ[d]
                    if (!map.inside(nx, nz) || map.wall(nx, nz)) continue
                    val a = sideA[d]
                    val b = sideB[d]
                    val ax = if (a == 0 || a == 3) x.toFloat() else x + 1f
                    val az = if (a == 0 || a == 1) z.toFloat() else z + 1f
                    val bx = if (b == 0 || b == 3) x.toFloat() else x + 1f
                    val bz = if (b == 0 || b == 1) z.toFloat() else z + 1f
                    val dxn = GameMap.DX[d].toFloat()
                    val dzn = GameMap.DZ[d].toFloat()
                    mesh.quad(ax, wh, az, bx, wh, bz, bx, wh * 0.5f, bz, ax, wh * 0.5f, az, dxn, 0f, dzn, pal.wall)
                    mesh.quad(ax, wh * 0.5f, az, bx, wh * 0.5f, bz, bx, 0f, bz, ax, 0f, az, dxn, 0f, dzn, mix(pal.wall, 0xFF000000.toInt(), 0.12f))
                    mesh.quad(ax, 0f, az, bx, 0f, bz, bx, -g, bz, ax, -g, az, dxn, 0f, dzn, mix(pal.wall, 0xFF000000.toInt(), 0.3f))
                }
            }
        }
        mesh.freeze()
    }

    fun pillar(mesh: Mesh3D, cx: Float, y0: Float, cz: Float, color: Int) {
        val r = 0.34f
        val y1 = y0 + GameMap.PILLAR_H
        box(mesh, cx - r, y0, cz - r, cx + r, y1, cz + r, color)
    }

    /** Axis-aligned box (no bottom face). */
    fun box(mesh: Mesh3D, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, color: Int) {
        mesh.quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0f, 1f, 0f, color)
        mesh.quad(x0, y1, z0, x1, y1, z0, x1, y0, z0, x0, y0, z0, 0f, 0f, -1f, color)
        mesh.quad(x1, y1, z0, x1, y1, z1, x1, y0, z1, x1, y0, z0, 1f, 0f, 0f, color)
        mesh.quad(x1, y1, z1, x0, y1, z1, x0, y0, z1, x1, y0, z1, 0f, 0f, 1f, color)
        mesh.quad(x0, y1, z1, x0, y1, z0, x0, y0, z0, x0, y0, z1, -1f, 0f, 0f, color)
    }
}

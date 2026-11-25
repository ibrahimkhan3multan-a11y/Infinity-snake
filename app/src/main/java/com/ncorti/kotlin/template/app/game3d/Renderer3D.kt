package com.ncorti.kotlin.template.app.game3d

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Software 3D renderer: real perspective projection of a real 3D scene, drawn back to front
 * (painter's algorithm) with per-face lighting, distance fog, blob shadows and spherical snake
 * segments. Geometry is small and low-poly on purpose so it runs smoothly on mid-range phones.
 */
class Renderer3D(private val map: GameMap) {
    private val pal = paletteFor(map.def.id)
    private val mesh = Mesh3D()

    init {
        MapMesh.build(map, pal, mesh)
    }

    // ---- projection state (per frame)
    private var cpx = 0f; private var cpy = 0f; private var cpz = 0f
    private var fx = 0f; private var fy = 0f; private var fz = 0f
    private var rx = 0f; private var ry = 0f; private var rz = 0f
    private var ux = 0f; private var uy = 0f; private var uz = 0f
    private var focal = 1f
    private var halfW = 0f
    private var halfH = 0f
    private var psx = 0f
    private var psy = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var bgShader: LinearGradient? = null
    private var bgH = 0
    private val bgPaint = Paint()

    // ---- sprites (spheres, shadows, eyes, halos)
    private val maxSprites = 2600
    private val sKind = IntArray(maxSprites)
    private val sX = FloatArray(maxSprites)
    private val sY = FloatArray(maxSprites)
    private val sR = FloatArray(maxSprites)
    private val sR2 = FloatArray(maxSprites)
    private val sCol = IntArray(maxSprites)
    private val sDepth = FloatArray(maxSprites)
    private var sprites = 0

    private var keys = LongArray(8192)
    private val tmp = FloatArray(3)
    private val tmp2 = FloatArray(3)

    /** Faces and sprites actually drawn last frame (for stats and tests). */
    var lastDrawn = 0
        private set

    private fun project(x: Float, y: Float, z: Float): Float {
        val dx = x - cpx
        val dy = y - cpy
        val dz = z - cpz
        val zc = dx * fx + dy * fy + dz * fz
        if (zc < 0.05f) return zc
        psx = halfW + (dx * rx + dy * ry + dz * rz) / zc * focal
        psy = halfH - (dx * ux + dy * uy + dz * uz) / zc * focal
        return zc
    }

    private fun fogMix(c: Int, depth: Float, alpha: Float): Int {
        val t = ((depth - FOG_START) / (FOG_END - FOG_START)).coerceIn(0f, 1f) * 0.9f
        val f = pal.fog
        val r = (((c shr 16) and 255) * (1 - t) + ((f shr 16) and 255) * t).toInt()
        val g = (((c shr 8) and 255) * (1 - t) + ((f shr 8) and 255) * t).toInt()
        val b = ((c and 255) * (1 - t) + (f and 255) * t).toInt()
        return ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun shadeColor(c: Int, k: Float): Int {
        val r = (((c shr 16) and 255) * k).toInt().coerceIn(0, 255)
        val g = (((c shr 8) and 255) * k).toInt().coerceIn(0, 255)
        val b = ((c and 255) * k).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun addSprite(kind: Int, x: Float, y: Float, r: Float, r2: Float, color: Int, depth: Float) {
        if (sprites >= maxSprites) return
        val i = sprites++
        sKind[i] = kind; sX[i] = x; sY[i] = y; sR[i] = r; sR2[i] = r2; sCol[i] = color; sDepth[i] = depth
    }

    /**
     * Draws one frame. [headColor]/[tailColor] are the snake skin; [rainbow] cycles hue.
     * [quality]: 0 = no shadows or highlights, 1 and 2 = full.
     */
    fun draw(
        canvas: Canvas, w: Int, h: Int, g: Game3D, cam: Camera3D, quality: Int,
        headColor: Int, tailColor: Int, rainbow: Boolean,
    ) {
        halfW = w / 2f
        halfH = h / 2f
        focal = halfH / tan(Math.toRadians(30.0)).toFloat() // 60 degree vertical field of view
        cpx = cam.pos[0]; cpy = cam.pos[1]; cpz = cam.pos[2]
        fx = cam.fwd[0]; fy = cam.fwd[1]; fz = cam.fwd[2]
        rx = cam.right[0]; ry = cam.right[1]; rz = cam.right[2]
        ux = cam.up[0]; uy = cam.up[1]; uz = cam.up[2]

        // Sky / backdrop.
        if (bgShader == null || bgH != h) {
            bgShader = LinearGradient(0f, 0f, 0f, h.toFloat(), pal.skyTop, pal.fog, Shader.TileMode.CLAMP)
            bgPaint.shader = bgShader
            bgH = h
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Dynamic geometry: food gem and obstacle crates.
        mesh.resetDynamic()
        buildDynamic(g)

        // Where is the snake's head (for fading roofs and bridges that would hide it)?
        g.segmentPos(0, tmp)
        val headLevel = map.cl(g.headCell)
        val hx = tmp[0]
        val hz = tmp[2]

        sprites = 0
        val faces = mesh.count
        var items = 0
        if (keys.size < faces + maxSprites) keys = LongArray((faces + maxSprites) * 2)
        val v = mesh.v
        for (i in 0 until faces) {
            // Distance and facing first (cheap), then the full projection.
            val cx = mesh.cen[i * 3]
            val cy = mesh.cen[i * 3 + 1]
            val cz = mesh.cen[i * 3 + 2]
            val ddx = cx - cpx
            val ddy = cy - cpy
            val ddz = cz - cpz
            val depth = ddx * fx + ddy * fy + ddz * fz
            if (depth > FAR || depth < -2f) continue
            if (mesh.nrm[i * 3] * -ddx + mesh.nrm[i * 3 + 1] * -ddy + mesh.nrm[i * 3 + 2] * -ddz <= 0f) continue
            keys[items++] = (java.lang.Float.floatToIntBits(max(depth, 0.001f)).toLong() shl 32) or i.toLong()
        }

        // Snake and food sprites.
        buildSprites(g, quality, headColor, tailColor, rainbow)
        for (s in 0 until sprites) {
            keys[items++] = (java.lang.Float.floatToIntBits(max(sDepth[s], 0.001f)).toLong() shl 32) or (SPRITE_BASE + s).toLong()
        }

        java.util.Arrays.sort(keys, 0, items)

        paint.strokeJoin = Paint.Join.ROUND
        var drawn = 0
        var k = items - 1
        while (k >= 0) {
            val id = (keys[k] and 0xFFFFFFFFL).toInt()
            k--
            if (id >= SPRITE_BASE) {
                drawSprite(canvas, id - SPRITE_BASE, quality)
                drawn++
                continue
            }
            val o = id * 12
            val depth = java.lang.Float.intBitsToFloat((keys[k + 1] shr 32).toInt())
            var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
            var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
            var ok = true
            path.rewind()
            for (c in 0 until 4) {
                val z = project(v[o + c * 3], v[o + c * 3 + 1], v[o + c * 3 + 2])
                if (z < NEAR) { ok = false; break }
                if (c == 0) path.moveTo(psx, psy) else path.lineTo(psx, psy)
                if (psx < minX) minX = psx
                if (psx > maxX) maxX = psx
                if (psy < minY) minY = psy
                if (psy > maxY) maxY = psy
            }
            if (!ok || maxX < 0 || minX > w || maxY < 0 || minY > h) continue
            path.close()
            var alpha = 1f
            val fc = mesh.fadeCol[id]
            if (fc >= 0 && headLevel < mesh.fadeLvl[id]) {
                val tx = fc % map.w + 0.5f
                val tz = fc / map.w + 0.5f
                if (abs(tx - hx) < FADE_R && abs(tz - hz) < FADE_R) alpha = 0.18f
            }
            val color = fogMix(mesh.col[id], depth, alpha)
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawPath(path, paint)
            if (alpha >= 1f) {
                // A hairline of the same colour hides the seams between neighbouring faces.
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.2f
                canvas.drawPath(path, paint)
            }
            drawn++
        }
        lastDrawn = drawn
    }

    // ------------------------------------------------------------ dynamic faces

    private fun buildDynamic(g: Game3D) {
        // Obstacle crates.
        for (c in g.crates) {
            val x = map.cx(c).toFloat()
            val z = map.cz(c).toFloat()
            val y = map.cl(c) * GameMap.LEVEL_H
            MapMesh.box(mesh, x + 0.12f, y, z + 0.12f, x + 0.88f, y + 0.78f, z + 0.88f, 0xFFE0573B.toInt())
            MapMesh.box(mesh, x + 0.07f, y + 0.66f, z + 0.07f, x + 0.93f, y + 0.82f, z + 0.93f, 0xFFB8402A.toInt())
        }
        // Food: a spinning, bobbing gem.
        if (g.foodCell >= 0) {
            g.foodPos(tmp)
            val gold = g.foodGolden
            val s = if (gold) 0.40f else 0.32f
            val cy = tmp[1] + 0.55f + 0.1f * sin(g.time * 3f)
            val a0 = g.time * 2.4f
            val col = if (gold) 0xFFFFD23C.toInt() else FOOD_COLORS[(g.foodEaten) % FOOD_COLORS.size]
            val px = FloatArray(4)
            val pz = FloatArray(4)
            for (i in 0 until 4) {
                val a = a0 + i * (Math.PI.toFloat() / 2f)
                px[i] = tmp[0] + cos(a) * s
                pz[i] = tmp[2] + sin(a) * s
            }
            val top = cy + s * 1.3f
            val bot = cy - s * 1.3f
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                val mx = (px[i] + px[j]) / 2f - tmp[0]
                val mz = (pz[i] + pz[j]) / 2f - tmp[2]
                mesh.quad(tmp[0], top, tmp[2], px[i], cy, pz[i], px[j], cy, pz[j], px[j], cy, pz[j], mx, 0.6f, mz, col)
                mesh.quad(tmp[0], bot, tmp[2], px[j], cy, pz[j], px[i], cy, pz[i], px[i], cy, pz[i], mx, -0.6f, mz, col)
            }
        }
    }

    // ------------------------------------------------------------ sprites

    private fun buildSprites(g: Game3D, quality: Int, head: Int, tail: Int, rainbow: Boolean) {
        val len = min(g.length, 700)
        val camR = rx
        for (i in len - 1 downTo 0) {
            g.segmentPos(i, tmp)
            val t = if (len > 1) i / (len - 1).toFloat() else 0f
            val radius = 0.43f - 0.17f * t
            val cy = tmp[1] + radius
            val z = project(tmp[0], cy, tmp[2])
            if (z < NEAR || z > FAR) continue
            val sx = psx
            val sy = psy
            val pr = radius / z * focal
            if (sx < -pr || sx > halfW * 2 + pr || sy < -pr || sy > halfH * 2 + pr) continue
            val col = if (rainbow) hsv((i * 14f + g.time * 120f) % 360f) else mixColor(head, tail, t)
            if (quality >= 1) {
                val gz = project(tmp[0], tmp[1] + 0.02f, tmp[2])
                if (gz >= NEAR) addSprite(KIND_SHADOW, psx, psy, radius * 1.05f / gz * focal, 0f, 0, z + 0.35f)
            }
            addSprite(KIND_SPHERE, sx, sy, pr, 0f, col, z)
        }
        // Eyes on the head.
        g.segmentPos(0, tmp)
        val hr = 0.43f
        val ang = g.headingAngle()
        val hfx = cos(ang)
        val hfz = sin(ang)
        for (side in intArrayOf(-1, 1)) {
            val ex = tmp[0] + hfx * 0.24f - hfz * 0.2f * side
            val ez = tmp[2] + hfz * 0.24f + hfx * 0.2f * side
            val ey = tmp[1] + hr + 0.14f
            val z = project(ex, ey, ez)
            if (z < NEAR) continue
            addSprite(KIND_EYE, psx, psy, 0.13f / z * focal, 0f, 0, z - 0.45f)
        }
        // Food shadow and glow.
        if (g.foodCell >= 0) {
            g.foodPos(tmp)
            val z0 = project(tmp[0], tmp[1] + 0.02f, tmp[2])
            if (quality >= 1 && z0 >= NEAR) addSprite(KIND_SHADOW, psx, psy, 0.34f / z0 * focal, 0f, 0, z0 + 0.3f)
            val z1 = project(tmp[0], tmp[1] + 0.55f, tmp[2])
            if (quality >= 1 && z1 >= NEAR) {
                val gold = g.foodGolden
                addSprite(KIND_HALO, psx, psy, (if (gold) 0.85f else 0.65f) / z1 * focal, 0f, if (gold) 0xFFFFD23C.toInt() else 0xFFFFFFFF.toInt(), z1 + 0.6f)
            }
        }
        for (c in g.crates) {
            val z = project(map.cx(c) + 0.5f, map.cl(c) * GameMap.LEVEL_H + 0.01f, map.cz(c) + 0.5f)
            if (quality >= 1 && z >= NEAR) addSprite(KIND_SHADOW, psx, psy, 0.7f / z * focal, 0f, 0, z + 0.8f)
        }
        @Suppress("UNUSED_VARIABLE") val unused = camR
    }

    private fun drawSprite(canvas: Canvas, s: Int, quality: Int) {
        val x = sX[s]
        val y = sY[s]
        val r = sR[s]
        paint.style = Paint.Style.FILL
        when (sKind[s]) {
            KIND_SHADOW -> {
                paint.color = 0x55000000
                // Ground circles look like ellipses from a raised camera.
                canvas.save()
                canvas.scale(1f, 0.55f, x, y)
                canvas.drawCircle(x, y, r, paint)
                canvas.restore()
            }
            KIND_SPHERE -> {
                val c = sCol[s]
                paint.color = shadeColor(c, 0.55f)
                canvas.drawCircle(x, y, r, paint)
                paint.color = c
                canvas.drawCircle(x - r * 0.1f, y - r * 0.12f, r * 0.86f, paint)
                if (quality >= 1) {
                    paint.color = shadeColor(c, 1.25f)
                    canvas.drawCircle(x - r * 0.28f, y - r * 0.32f, r * 0.42f, paint)
                    paint.color = 0x66FFFFFF
                    canvas.drawCircle(x - r * 0.34f, y - r * 0.4f, r * 0.17f, paint)
                }
            }
            KIND_EYE -> {
                paint.color = 0xFFFFFFFF.toInt()
                canvas.drawCircle(x, y, r, paint)
                paint.color = 0xFF14142A.toInt()
                canvas.drawCircle(x, y, r * 0.55f, paint)
            }
            KIND_HALO -> {
                paint.color = (sCol[s] and 0x00FFFFFF) or 0x2A000000
                canvas.drawCircle(x, y, r, paint)
                paint.color = (sCol[s] and 0x00FFFFFF) or 0x33000000
                canvas.drawCircle(x, y, r * 0.55f, paint)
            }
        }
    }

    private fun mixColor(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 255) * (1 - t) + ((b shr s) and 255) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun hsv(h: Float): Int = android.graphics.Color.HSVToColor(floatArrayOf(h, 0.7f, 1f))

    companion object {
        private const val NEAR = 0.12f
        private const val FAR = 26f
        private const val FOG_START = 9f
        private const val FOG_END = 24f
        private const val FADE_R = 3.6f
        private const val SPRITE_BASE = 1 shl 28
        private const val KIND_SHADOW = 0
        private const val KIND_SPHERE = 1
        private const val KIND_EYE = 2
        private const val KIND_HALO = 3
        private val FOOD_COLORS = intArrayOf(
            0xFFFF4F8B.toInt(), 0xFF4FE0FF.toInt(), 0xFF7CFF5B.toInt(), 0xFFFF9A3C.toInt(), 0xFFB07CFF.toInt(),
        )
    }
}

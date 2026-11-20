package com.ncorti.kotlin.template.app.ui

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.ncorti.kotlin.template.app.GameSession
import com.ncorti.kotlin.template.app.game.Cfg
import com.ncorti.kotlin.template.app.game.foodRadius
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the arena with plain Canvas calls. Positions are converted to screen space by hand and
 * off-screen things are skipped, so there is no per-frame allocation beyond tiny value classes.
 * [quality]: 0 = low (no glow, no names), 1 = medium, 2 = high (glow everywhere, pulsing food).
 */
class GameRenderer {
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.argb(200, 255, 255, 255)
        textAlign = Paint.Align.CENTER
    }

    private var vignette: Brush? = null
    private var vigW = 0f
    private var vigH = 0f

    private val arenaColor = Color(0xFF110B22)
    private val outsideColor = Color(0xFF05030B)
    private val gridColor = Color(0xFF241A3F)
    private val borderColor = Color(0xFFFF3DAA)
    private val dotColor = Color(0xFF3A2B66)

    fun draw(ds: DrawScope, s: GameSession, quality: Int) {
        with(ds) {
            val w = size.width
            val h = size.height
            s.viewW = w
            s.viewH = h
            val zoom = s.zoom
            val ox = w / 2f - (s.camX + s.shakeX) * zoom
            val oy = h / 2f - (s.camY + s.shakeY) * zoom
            val world = s.world
            val time = world.time

            val arenaR = Cfg.ARENA_RADIUS * zoom
            val centre = Offset(ox, oy)

            // Arena floor: flat colour with a soft lighter glow in the middle.
            drawRect(arenaColor)
            drawCircle(
                Brush.radialGradient(
                    colorStops = arrayOf(0f to Color(0xFF241647), 0.7f to Color(0x00110B22), 1f to Color(0x00110B22)),
                    center = centre,
                    radius = arenaR,
                ),
                arenaR,
                centre,
            )

            // Grid (the space ring below hides whatever spills past the wall).
            val grid = 140f
            val left = s.camX - w / 2f / zoom
            val right = s.camX + w / 2f / zoom
            val top = s.camY - h / 2f / zoom
            val bottom = s.camY + h / 2f / zoom
            var gx = kotlin.math.floor(left / grid) * grid
            while (gx <= right) {
                val sx = ox + gx * zoom
                drawLine(gridColor, Offset(sx, 0f), Offset(sx, h), 1f)
                gx += grid
            }
            var gy = kotlin.math.floor(top / grid) * grid
            while (gy <= bottom) {
                val sy = oy + gy * zoom
                drawLine(gridColor, Offset(0f, sy), Offset(w, sy), 1f)
                gy += grid
            }
            if (quality >= 2) {
                gx = kotlin.math.floor(left / grid) * grid
                while (gx <= right) {
                    gy = kotlin.math.floor(top / grid) * grid
                    while (gy <= bottom) {
                        drawCircle(dotColor, 2.2f * zoom + 0.8f, Offset(ox + gx * zoom, oy + gy * zoom))
                        gy += grid
                    }
                    gx += grid
                }
            }

            // Danger zone: the last stretch before the wall glows red (bounded by the circle itself).
            drawCircle(
                Brush.radialGradient(
                    colorStops = arrayOf(0f to Color.Transparent, 0.92f to Color.Transparent, 1f to Color(0x66FF2D6F)),
                    center = centre,
                    radius = arenaR,
                ),
                arenaR,
                centre,
            )

            // Everything beyond the wall: deep space, covered by one huge ring, with drifting stars on top.
            val ringWidth = 9000f * zoom
            drawCircle(
                outsideColor,
                radius = arenaR + ringWidth / 2f,
                center = centre,
                style = Stroke(width = ringWidth),
            )
            if (quality >= 1) drawStars(w, h, s.camX, s.camY, time, if (quality >= 2) 70 else 36, centre, arenaR)

            // Wall: wide soft glow plus a bright pulsing line.
            if (quality >= 1) {
                drawCircle(borderColor.copy(alpha = 0.16f), arenaR, centre, style = Stroke(width = maxOf(14f, 46f * zoom)))
                drawCircle(borderColor.copy(alpha = 0.28f), arenaR, centre, style = Stroke(width = maxOf(8f, 22f * zoom)))
            }
            val pulse = 0.8f + 0.2f * sin(time * 3f)
            drawCircle(borderColor.copy(alpha = pulse), arenaR, centre, style = Stroke(width = maxOf(2f, 7f * zoom)))

            // Food.
            val m = 40f
            for (i in 0 until Cfg.FOOD_CAP) {
                if (!world.falive[i]) continue
                val x = ox + world.fx[i] * zoom
                val y = oy + world.fy[i] * zoom
                if (x < -m || x > w + m || y < -m || y > h + m) continue
                val v = world.fv[i]
                var rad = foodRadius(v) * zoom
                if (quality >= 1) rad *= 1f + 0.12f * sin(time * 4f + i)
                val col = Color(world.fcol[i])
                if (quality >= 1) drawCircle(col.copy(alpha = 0.22f), rad * 2.3f, Offset(x, y))
                drawCircle(col, rad, Offset(x, y))
                if (quality >= 2) drawCircle(Color.White.copy(alpha = 0.55f), rad * 0.32f, Offset(x - rad * 0.28f, y - rad * 0.28f))
            }

            // Snakes (tail first so heads end up on top).
            val dens = density
            namePaint.textSize = 11f * dens
            for (si in 0 until world.snakes.size) {
                val sn = world.snakes[si]
                if (!sn.alive) continue
                val head = Color(sn.headColor)
                val tail = Color(sn.tailColor)
                val blink = sn.grace > 0f && ((time * 8f).toInt() and 1) == 0
                val a = if (blink) 0.4f else 1f
                val r = sn.radius * zoom
                val n = sn.n
                val glowOn = quality >= 1 && sn.glow
                val margin = r * 2.5f + 4f
                var i = n - 1
                while (i >= 0) {
                    val x = ox + sn.xs[i] * zoom
                    val y = oy + sn.ys[i] * zoom
                    if (x < -margin || x > w + margin || y < -margin || y > h + margin) {
                        i--
                        continue
                    }
                    val t = i.toFloat() / (n - 1).coerceAtLeast(1)
                    val col = if (sn.rainbow) Color.hsv((i * 7f + time * 90f) % 360f, 0.75f, 1f) else mix(head, tail, t)
                    val taper = if (t > 0.85f) 1f - (t - 0.85f) * 2f else 1f
                    if (glowOn && (i and 1) == 0) drawCircle(col.copy(alpha = 0.16f * a), r * taper * 1.9f, Offset(x, y))
                    val seg = if (quality >= 1 && (i / 3) % 2 == 1) shade(col, 0.88f) else col
                    drawCircle(if (a < 1f) seg.copy(alpha = a) else seg, r * taper, Offset(x, y))
                    if (quality >= 2 && (i and 1) == 0) {
                        drawCircle(Color.White.copy(alpha = 0.16f * a), r * taper * 0.45f, Offset(x - r * 0.25f, y - r * 0.3f))
                    }
                    i--
                }

                // Head: boost aura, eyes, name.
                val hx = ox + sn.xs[0] * zoom
                val hy = oy + sn.ys[0] * zoom
                if (hx < -margin || hx > w + margin || hy < -margin || hy > h + margin) continue
                if (sn.boostActive && quality >= 1) drawCircle(head.copy(alpha = 0.25f), r * 2.6f, Offset(hx, hy))
                if (sn.magnet > 0f) {
                    val mr = (sn.radius + 16f + Cfg.MAGNET_REACH) * zoom
                    drawCircle(Color(0xFFFF3DAA).copy(alpha = 0.07f), mr, Offset(hx, hy))
                    drawCircle(Color(0xFFFF3DAA).copy(alpha = 0.5f), mr, Offset(hx, hy), style = Stroke(2f))
                }
                if (sn.freeze > 0f) {
                    val fr = Cfg.FREEZE_RADIUS * zoom
                    drawCircle(Color(0xFF9BE7FF).copy(alpha = 0.06f), fr, Offset(hx, hy))
                    drawCircle(Color(0xFF9BE7FF).copy(alpha = 0.5f), fr, Offset(hx, hy), style = Stroke(2f))
                }
                if (sn.slow > 0f) drawCircle(Color(0xFF9BE7FF).copy(alpha = 0.35f), r * 1.7f, Offset(hx, hy))
                if (sn.overdrive > 0f) drawCircle(Color(0xFFFFD45C).copy(alpha = 0.3f), r * 3.2f, Offset(hx, hy))
                if (sn.shield > 0f) {
                    val flicker = if (sn.shield < 1.5f && ((time * 10f).toInt() and 1) == 0) 0.35f else 1f
                    drawCircle(Color(0xFF2DE2E6).copy(alpha = 0.16f * flicker), r * 2.4f, Offset(hx, hy))
                    drawCircle(Color(0xFF2DE2E6).copy(alpha = 0.9f * flicker), r * 2.4f, Offset(hx, hy), style = Stroke(3f))
                }
                val ca = cos(sn.angle)
                val sa = sin(sn.angle)
                val eyeR = r * 0.34f
                val fwd = r * 0.35f
                val side = r * 0.5f
                var sign = -1f
                while (sign <= 1f) {
                    val ex = hx + ca * fwd - sa * side * sign
                    val ey = hy + sa * fwd + ca * side * sign
                    drawCircle(Color.White, eyeR, Offset(ex, ey))
                    drawCircle(Color(0xFF111122), eyeR * 0.58f, Offset(ex + ca * eyeR * 0.35f, ey + sa * eyeR * 0.35f))
                    if (quality >= 1) drawCircle(Color.White, eyeR * 0.18f, Offset(ex + ca * eyeR * 0.5f - sa * eyeR * 0.2f, ey + sa * eyeR * 0.5f + ca * eyeR * 0.2f))
                    sign += 2f
                }
                if (!sn.isPlayer && quality >= 1) {
                    drawContext.canvas.nativeCanvas.drawText(sn.name, hx, hy - r - 8f * dens, namePaint)
                }
            }

            // Particles.
            val p = s.particles
            for (i in 0 until p.capacity) {
                val life = p.life[i]
                if (life <= 0f) continue
                val x = ox + p.x[i] * zoom
                val y = oy + p.y[i] * zoom
                if (x < -m || x > w + m || y < -m || y > h + m) continue
                val f = (life / p.maxLife[i]).coerceIn(0f, 1f)
                drawCircle(Color(p.color[i]).copy(alpha = f), p.size[i] * zoom * (0.4f + 0.6f * f), Offset(x, y))
            }

            // Soft vignette pulls the eye to the middle of the screen.
            if (quality >= 1) {
                if (vignette == null || vigW != w || vigH != h) {
                    vignette = Brush.radialGradient(
                        colorStops = arrayOf(0.5f to Color.Transparent, 1f to Color(0xAA000000)),
                        center = Offset(w / 2f, h / 2f),
                        radius = kotlin.math.hypot(w, h) / 2f,
                    )
                    vigW = w
                    vigH = h
                }
                drawRect(vignette!!)
            }
        }
    }

    private fun shade(c: Color, k: Float) = Color(c.red * k, c.green * k, c.blue * k, c.alpha)

    /** Cheap deterministic star field in screen space; two layers drift at different speeds. */
    private fun DrawScope.drawStars(w: Float, h: Float, camX: Float, camY: Float, time: Float, count: Int, centre: Offset, arenaR: Float) {
        for (i in 0 until count) {
            val layer = if (i % 3 == 0) 0.12f else 0.05f
            val bx = (i * 7919 % 1000) / 1000f * w * 1.4f
            val by = (i * 6271 % 1000) / 1000f * h * 1.4f
            var x = (bx - camX * layer) % (w * 1.4f)
            var y = (by - camY * layer) % (h * 1.4f)
            if (x < 0) x += w * 1.4f
            if (y < 0) y += h * 1.4f
            val px = x - w * 0.2f
            val py = y - h * 0.2f
            if (kotlin.math.hypot(px - centre.x, py - centre.y) < arenaR) continue
            val tw = 0.5f + 0.5f * sin(time * 1.5f + i)
            drawCircle(Color.White.copy(alpha = 0.18f + 0.35f * tw), if (i % 3 == 0) 2.2f else 1.3f, Offset(px, py))
        }
    }
}

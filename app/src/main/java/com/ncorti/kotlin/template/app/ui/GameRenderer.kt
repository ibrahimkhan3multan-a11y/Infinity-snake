package com.ncorti.kotlin.template.app.ui

import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
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

    private val arenaColor = Color(0xFF110B22)
    private val outsideColor = Color(0xFF05030B)
    private val gridColor = Color(0xFF241A3F)
    private val borderColor = Color(0xFFFF3DAA)

    fun draw(ds: DrawScope, s: GameSession, quality: Int) {
        with(ds) {
            val w = size.width
            val h = size.height
            s.viewW = w
            s.viewH = h
            val zoom = s.zoom
            val ox = w / 2f - s.camX * zoom
            val oy = h / 2f - s.camY * zoom
            val world = s.world
            val time = world.time

            // Arena floor.
            drawRect(arenaColor)

            // Grid.
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

            // Everything beyond the arena edge is covered by a huge ring stroke.
            val ringWidth = 6000f * zoom
            drawCircle(
                outsideColor,
                radius = Cfg.ARENA_RADIUS * zoom + ringWidth / 2f,
                center = Offset(ox, oy),
                style = Stroke(width = ringWidth),
            )
            if (quality >= 1) {
                drawCircle(
                    borderColor.copy(alpha = 0.22f),
                    radius = Cfg.ARENA_RADIUS * zoom,
                    center = Offset(ox, oy),
                    style = Stroke(width = maxOf(10f, 30f * zoom)),
                )
            }
            drawCircle(
                borderColor,
                radius = Cfg.ARENA_RADIUS * zoom,
                center = Offset(ox, oy),
                style = Stroke(width = maxOf(2f, 7f * zoom)),
            )

            // Food.
            val m = 40f
            for (i in 0 until Cfg.FOOD_CAP) {
                if (!world.falive[i]) continue
                val x = ox + world.fx[i] * zoom
                val y = oy + world.fy[i] * zoom
                if (x < -m || x > w + m || y < -m || y > h + m) continue
                val v = world.fv[i]
                var rad = foodRadius(v) * zoom
                if (quality >= 2) rad *= 1f + 0.12f * sin(time * 4f + i)
                val col = Color(world.fcol[i])
                if (quality >= 1) drawCircle(col.copy(alpha = 0.22f), rad * 2.3f, Offset(x, y))
                drawCircle(col, rad, Offset(x, y))
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
                    drawCircle(if (a < 1f) col.copy(alpha = a) else col, r * taper, Offset(x, y))
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
                    drawCircle(Color.Black, eyeR * 0.5f, Offset(ex + ca * eyeR * 0.35f, ey + sa * eyeR * 0.35f))
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
        }
    }
}

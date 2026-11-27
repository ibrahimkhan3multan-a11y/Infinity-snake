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

    private val src3d = FloatArray(8)
    private val dst3d = FloatArray(8)
    private val proj = android.graphics.Matrix()

    fun draw(ds: DrawScope, s: GameSession, quality: Int) {
        val w = ds.size.width
        val h = ds.size.height
        s.viewW = w
        s.viewH = h
        if (!s.view3d) {
            s.view3dActive = false
            drawScene(ds, s, quality, w, h, 0.5f)
            if (quality >= 1) drawVignette(ds, w, h)
            if (quality >= 3) drawGrade(ds, w, h)
            return
        }

        // 3D view: draw the normal flat scene on a bigger virtual sheet, then bend that sheet into a
        // trapezoid so the far side shrinks toward a horizon (tilted, GTA-style camera).
        val w2 = w * 1.6f
        val h2 = h * 1.3f
        val pf = 0.72f
        val yTop = h * 0.10f
        val yBot = h * 1.10f
        val farHalf = w * 1.05f / 2f
        val nearHalf = w2 / 2f
        src3d[0] = 0f; src3d[1] = 0f; src3d[2] = w2; src3d[3] = 0f
        src3d[4] = w2; src3d[5] = h2; src3d[6] = 0f; src3d[7] = h2
        dst3d[0] = w / 2f - farHalf; dst3d[1] = yTop; dst3d[2] = w / 2f + farHalf; dst3d[3] = yTop
        dst3d[4] = w / 2f + nearHalf; dst3d[5] = yBot; dst3d[6] = w / 2f - nearHalf; dst3d[7] = yBot
        proj.setPolyToPoly(src3d, 0, dst3d, 0, 4)
        proj.invert(s.invProj)
        s.view3dActive = true
        s.virtPlayerX = w2 / 2f
        s.virtPlayerY = h2 * pf

        with(ds) {
            // Sky above the horizon.
            drawRect(outsideColor)
            if (quality >= 1) {
                drawRect(
                    Brush.verticalGradient(0f to Color(0xFF0B0620), 1f to Color(0xFF3A1A66), startY = 0f, endY = yTop + h * 0.12f),
                    size = androidx.compose.ui.geometry.Size(w, yTop + h * 0.12f),
                )
            }
            val nc = drawContext.canvas.nativeCanvas
            nc.save()
            nc.concat(proj)
            drawScene(ds, s, quality, w2, h2, pf)
            nc.restore()
            if (quality >= 1) {
                drawRect(
                    Brush.verticalGradient(0f to Color(0xCC05030B), 1f to Color.Transparent, startY = yTop, endY = yTop + h * 0.22f),
                    topLeft = Offset(0f, yTop),
                    size = androidx.compose.ui.geometry.Size(w, h * 0.22f),
                )
            }
        }
        if (quality >= 1) drawVignette(ds, w, h)
    }

    private fun drawVignette(ds: DrawScope, w: Float, h: Float) {
        if (vignette == null || vigW != w || vigH != h) {
            vignette = Brush.radialGradient(
                colorStops = arrayOf(0.5f to Color.Transparent, 1f to Color(0xAA000000)),
                center = Offset(w / 2f, h / 2f),
                radius = kotlin.math.hypot(w, h) / 2f,
            )
            vigW = w
            vigH = h
        }
        ds.drawRect(vignette!!)
    }

    /** Ultra colour grade: a soft cool light from the top and a warm glow from the bottom. */
    private fun drawGrade(ds: DrawScope, w: Float, h: Float) {
        ds.drawRect(
            Brush.verticalGradient(
                colorStops = arrayOf(0f to Color(0x1A9BE7FF), 0.45f to Color.Transparent, 1f to Color(0x1AFF7A3D)),
                startY = 0f,
                endY = h,
            ),
        )
    }

    private fun drawScene(ds: DrawScope, s: GameSession, quality: Int, w: Float, h: Float, pf: Float) {
        with(ds) {
            val zoom = s.zoom
            val ox = w / 2f - (s.camX + s.shakeX) * zoom
            val oy = h * pf - (s.camY + s.shakeY) * zoom
            val world = s.world
            val time = world.time

            val arenaR = world.radius * zoom
            val centre = Offset(ox, oy)

            // Arena floor: flat colour with a soft lighter glow in the middle.
            drawRect(arenaColor, Offset(-w, -h), androidx.compose.ui.geometry.Size(3f * w, 3f * h))
            if (quality >= 2) {
                drawCircle(
                    Brush.radialGradient(
                        colorStops = arrayOf(0f to Color(0xFF241647), 0.7f to Color(0x00110B22), 1f to Color(0x00110B22)),
                        center = centre,
                        radius = arenaR,
                    ),
                    arenaR,
                    centre,
                )
            }

            // Ultra: slow drifting nebula clouds that move at a different speed than the floor (depth).
            if (quality >= 3) {
                for (k in 0 until 3) {
                    val par = 0.55f + 0.12f * k
                    val nx = w * (0.2f + 0.3f * k) + sin(time * (0.07f + 0.03f * k) + k) * w * 0.18f - s.camX * zoom * (1f - par) * 0.25f
                    val ny = h * (0.3f + 0.2f * k) + cos(time * (0.05f + 0.04f * k) + k * 2f) * h * 0.2f - s.camY * zoom * (1f - par) * 0.25f
                    val nr = maxOf(w, h) * (0.55f + 0.12f * k)
                    val c = when (k) {
                        0 -> Color(0xFF7B2FF7)
                        1 -> Color(0xFF00B8D4)
                        else -> Color(0xFFFF3D81)
                    }
                    drawCircle(
                        Brush.radialGradient(
                            colorStops = arrayOf(0f to c.copy(alpha = 0.16f), 1f to c.copy(alpha = 0f)),
                            center = Offset(nx, ny),
                            radius = nr,
                        ),
                        nr,
                        Offset(nx, ny),
                    )
                }
            }

            // Grid (the space ring below hides whatever spills past the wall).
            val grid = 140f
            val left = s.camX - w / 2f / zoom
            val right = s.camX + w / 2f / zoom
            val top = s.camY - h * pf / zoom
            val bottom = s.camY + h * (1f - pf) / zoom
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

            // Danger zone: the last stretch before the wall glows red.
            if (quality >= 2) {
                drawCircle(
                    Brush.radialGradient(
                        colorStops = arrayOf(0f to Color.Transparent, 0.92f to Color.Transparent, 1f to Color(0x66FF2D6F)),
                        center = centre,
                        radius = arenaR,
                    ),
                    arenaR,
                    centre,
                )
            } else if (quality == 1) {
                drawCircle(Color(0x22FF2D6F), arenaR - 120f * zoom, centre, style = Stroke(width = 240f * zoom))
            }

            // Everything beyond the wall: deep space, covered by one huge ring, with drifting stars on top.
            val ringWidth = 9000f * zoom
            drawCircle(
                outsideColor,
                radius = arenaR + ringWidth / 2f,
                center = centre,
                style = Stroke(width = ringWidth),
            )
            if (quality >= 2) drawStars(w, h, s.camX, s.camY, time, if (quality >= 3) 170 else 70, centre, arenaR)

            // Wall: wide soft glow plus a bright pulsing line.
            if (quality >= 1) drawCircle(borderColor.copy(alpha = 0.22f), arenaR, centre, style = Stroke(width = maxOf(10f, 30f * zoom)))
            if (quality >= 2) drawCircle(borderColor.copy(alpha = 0.14f), arenaR, centre, style = Stroke(width = maxOf(14f, 56f * zoom)))
            if (quality >= 3) {
                drawCircle(borderColor.copy(alpha = 0.08f), arenaR, centre, style = Stroke(width = maxOf(30f, 130f * zoom)))
                drawCircle(Color.White.copy(alpha = 0.35f), arenaR, centre, style = Stroke(width = maxOf(1f, 2.2f * zoom)))
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
                if (quality >= 3) drawCircle(col.copy(alpha = 0.10f), rad * 4.2f, Offset(x, y))
                if (quality >= 1) drawCircle(col.copy(alpha = if (quality >= 3) 0.3f else 0.22f), rad * 2.3f, Offset(x, y))
                drawCircle(col, rad, Offset(x, y))
                if (quality >= 3 && v >= 2f) {
                    val ray = rad * (2.4f + 0.6f * sin(time * 6f + i))
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(x - ray, y), Offset(x + ray, y), 1.3f)
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(x, y - ray), Offset(x, y + ray), 1.3f)
                }
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
                val a = if (blink || sn.ghost > 0f) 0.4f else 1f
                val r = sn.radius * zoom
                val n = sn.n
                val glowOn = quality >= 1 && (sn.glow || quality >= 3)
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
                    if (glowOn && (i and 1) == 0) drawCircle(col.copy(alpha = (if (quality >= 3) 0.2f else 0.16f) * a), r * taper * (if (quality >= 3) 2.4f else 1.9f), Offset(x, y))
                    if (quality >= 3 && (i and 1) == 0) drawCircle(shade(col, 0.45f).copy(alpha = a), r * taper * 1.14f, Offset(x, y))
                    val seg = if (quality >= 1 && (i / 3) % 2 == 1) shade(col, 0.88f) else col
                    drawCircle(if (a < 1f) seg.copy(alpha = a) else seg, r * taper, Offset(x, y))
                    if (quality >= 2 && (i and 1) == 0 || quality >= 3) {
                        drawCircle(Color.White.copy(alpha = 0.16f * a), r * taper * 0.45f, Offset(x - r * 0.25f, y - r * 0.3f))
                    }
                    i--
                }

                // Head: boost aura, eyes, name.
                val hx = ox + sn.xs[0] * zoom
                val hy = oy + sn.ys[0] * zoom
                if (hx < -margin || hx > w + margin || hy < -margin || hy > h + margin) continue
if (sn.boostActive && quality >= 1) drawCircle(head.copy(alpha = 0.25f), r * 2.6f, Offset(hx, hy))
                if (quality >= 3) {
                    drawCircle(head.copy(alpha = 0.12f), r * 3.4f, Offset(hx, hy))
                    drawCircle(Color.White.copy(alpha = 0.28f), r * 1.18f, Offset(hx, hy), style = Stroke(1.6f))
                }
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
                if (sn.dash > 0f || sn.rush > 0f) drawCircle(Color(0xFFFF7A3D).copy(alpha = 0.35f), r * 3.4f, Offset(hx, hy))
                if (sn.feast > 0f) {
                    val gold = Color(0xFFFFC93D)
                    drawCircle(gold.copy(alpha = 0.12f), r * 2.2f, Offset(hx, hy))
                    drawCircle(gold.copy(alpha = 0.8f), r * 2.2f, Offset(hx, hy), style = Stroke(2.5f))
                }
                if (sn.vacuum > 0f) {
                    val vr = (sn.radius + 16f + Cfg.VACUUM_REACH) * zoom
                    drawCircle(Color(0xFFB06BFF).copy(alpha = 0.08f), vr, Offset(hx, hy))
                    drawCircle(Color(0xFFB06BFF).copy(alpha = 0.7f), vr, Offset(hx, hy), style = Stroke(3f))
                }
                if (sn.titan > 0f) {
                    drawCircle(Color(0xFFFF9A3D).copy(alpha = 0.10f), r * 1.9f, Offset(hx, hy))
                    drawCircle(Color(0xFFFF9A3D).copy(alpha = 0.55f), r * 1.5f, Offset(hx, hy), style = Stroke(2.5f))
                }
                if (sn.timewarp > 0f) {
                    val pr = (60f + 30f * sin(time * 5f)) * zoom + r * 2f
                    drawCircle(Color(0xFF7AA8FF).copy(alpha = 0.5f), pr, Offset(hx, hy), style = Stroke(2.5f))
                    drawCircle(Color(0xFF7AA8FF).copy(alpha = 0.18f), pr * 1.5f, Offset(hx, hy), style = Stroke(2f))
                }
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

            // Bombs: a pulsing red warning circle that fills in as the bomb falls, then a blast.
            for (bi in 0 until world.bombCount) {
                val bxs = ox + world.bx[bi] * zoom
                val bys = oy + world.by[bi] * zoom
                val br = Cfg.BOMB_RADIUS * zoom
                if (bxs < -br || bxs > w + br || bys < -br || bys > h + br) continue
                val t = world.bt[bi]
                val bc = Offset(bxs, bys)
                if (t > 0f) {
                    val k = (t / Cfg.BOMB_FUSE).coerceIn(0f, 1f)
                    val blink = 0.55f + 0.45f * sin(time * 14f)
                    drawCircle(Color(0xFFFF2D2D).copy(alpha = 0.10f + 0.10f * blink), br, bc)
                    drawCircle(Color(0xFFFF4545).copy(alpha = 0.30f * (1f - k) + 0.1f), br * (1f - k), bc)
                    drawCircle(Color(0xFFFF4545).copy(alpha = 0.55f + 0.35f * blink), br, bc, style = Stroke(width = maxOf(2f, 3f * zoom)))
                    // The bomb itself, dropping from above.
                    val drop = k * k * 520f * zoom
                    val body = Offset(bxs, bys - drop)
                    drawCircle(Color(0xFF1C1C28), 17f * zoom + 3f, body)
                    drawCircle(Color(0xFF8A8AA8), 17f * zoom + 3f, body, style = Stroke(width = 2f))
                    drawCircle(Color(0xFFFFC857), 5f * zoom + 1.5f, Offset(body.x + 8f * zoom, body.y - 17f * zoom - 3f))
                } else {
                    val f = ((-t) / Cfg.BOMB_LINGER).coerceIn(0f, 1f)
                    drawCircle(Color(0xFFFFA040).copy(alpha = 0.6f * (1f - f)), br * (0.55f + 0.45f * f), bc)
                    drawCircle(Color(0xFFFFE9A8).copy(alpha = 0.9f * (1f - f)), br * 0.5f * (1f - f * 0.5f), bc)
                    drawCircle(Color(0xFFFFD060).copy(alpha = 0.8f * (1f - f)), br * (0.6f + 0.5f * f), bc, style = Stroke(width = maxOf(2f, 6f * zoom)))
                }
            }

            // Route to a tapped spot or player: a moving dashed line and a pulsing marker.
            if (s.routeActive && s.world.player.alive) {
                val me = s.world.player
                val from = Offset(ox + me.xs[0] * zoom, oy + me.ys[0] * zoom)
                val to = Offset(ox + s.routeX * zoom, oy + s.routeY * zoom)
                val cyan = Color(0xFF2DE2E6)
                drawLine(
                    cyan.copy(alpha = 0.75f), from, to, 3f * density,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(18f * density, 12f * density), (time * 60f) % (30f * density)),
                )
                val pulse = 1f + 0.18f * sin(time * 6f)
                drawCircle(cyan.copy(alpha = 0.22f), 20f * density * pulse, to)
                drawCircle(cyan, 20f * density * pulse, to, style = Stroke(2.5f * density))
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

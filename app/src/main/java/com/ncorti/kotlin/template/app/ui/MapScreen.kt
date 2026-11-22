package com.ncorti.kotlin.template.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncorti.kotlin.template.app.GameSession
import com.ncorti.kotlin.template.app.game.Cfg
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlinx.coroutines.delay

private class Person(val id: Int, val name: String, val score: Int, val color: Int)

/**
 * Full-screen map. Tap an area or a player to pick it (a dashed line shows the way), then GO THERE:
 * your snake steers itself along that line until you steer by hand, arrive, or stop the route.
 */
@Composable
fun MapOverlay(session: GameSession, onClose: () -> Unit) {
    var hasSel by remember { mutableStateOf(false) }
    var selX by remember { mutableFloatStateOf(0f) }
    var selY by remember { mutableFloatStateOf(0f) }
    var selId by remember { mutableIntStateOf(-1) }
    var selName by remember { mutableStateOf("") }
    var people by remember { mutableStateOf(emptyList<Person>()) }

    LaunchedEffect(session) {
        while (true) {
            people = session.world.snakes
                .filter { it.alive && !it.isPlayer && !it.gone }
                .sortedByDescending { it.score }
                .take(6)
                .map { Person(it.id, it.name, it.score, it.headColor) }
            delay(500)
        }
    }

    fun pickArea(x: Float, y: Float, name: String) {
        selId = -1
        selX = x
        selY = y
        selName = name
        hasSel = true
    }

    fun pickPerson(p: Person) {
        selId = p.id
        selName = p.name
        hasSel = true
    }

    /** The place the selection points at right now (a picked player moves). */
    fun selPos(): Offset {
        if (selId >= 0) {
            val t = session.world.snakes.firstOrNull { it.id == selId && it.alive }
            if (t != null) return Offset(t.xs[0], t.ys[0])
        }
        return Offset(selX, selY)
    }

    val me = session.world.player
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF007030F))
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(12.dp),
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val side = minOf(maxWidth, maxHeight)
                Canvas(
                    Modifier
                        .size(side)
                        .semantics { contentDescription = "Arena map" }
                        .pointerInput(session) {
                            detectTapGestures { pos ->
                                val c = Offset(size.width / 2f, size.height / 2f)
                                val rad = size.width / 2f - 6.dp.toPx()
                                val k = rad / Cfg.ARENA_RADIUS
                                // A tap near a player picks that player; anywhere else picks the area.
                                var best: com.ncorti.kotlin.template.app.game.Snake? = null
                                var bestD = 26.dp.toPx()
                                for (s in session.world.snakes) {
                                    if (!s.alive || s.isPlayer || s.gone) continue
                                    val d = hypot(c.x + s.xs[0] * k - pos.x, c.y + s.ys[0] * k - pos.y)
                                    if (d < bestD) {
                                        bestD = d
                                        best = s
                                    }
                                }
                                val b = best
                                if (b != null) {
                                    pickPerson(Person(b.id, b.name, b.score, b.headColor))
                                } else {
                                    val wx = (pos.x - c.x) / k
                                    val wy = (pos.y - c.y) / k
                                    if (hypot(wx, wy) <= Cfg.ARENA_RADIUS) pickArea(wx, wy, "that spot")
                                }
                            }
                        },
                ) {
                    @Suppress("UNUSED_VARIABLE")
                    val tick = session.frame
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val rad = size.width / 2f - 6.dp.toPx()
                    val k = rad / Cfg.ARENA_RADIUS
                    drawCircle(Color(0xFF110B22), rad, c)
                    for (f in listOf(1f / 3f, 2f / 3f)) {
                        drawCircle(Color(0x33FFFFFF), rad * f, c, style = Stroke(1.dp.toPx()))
                    }
                    drawLine(Color(0x22FFFFFF), Offset(c.x - rad, c.y), Offset(c.x + rad, c.y), 1.dp.toPx())
                    drawLine(Color(0x22FFFFFF), Offset(c.x, c.y - rad), Offset(c.x, c.y + rad), 1.dp.toPx())
                    drawCircle(Neon.Pink, rad, c, style = Stroke(2.dp.toPx()))
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb(150, 255, 255, 255)
                        textSize = 12.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val nc = drawContext.canvas.nativeCanvas
                    nc.drawText("N", c.x, c.y - rad + 16.dp.toPx(), paint)
                    nc.drawText("S", c.x, c.y + rad - 6.dp.toPx(), paint)
                    nc.drawText("W", c.x - rad + 12.dp.toPx(), c.y + 4.dp.toPx(), paint)
                    nc.drawText("E", c.x + rad - 12.dp.toPx(), c.y + 4.dp.toPx(), paint)

                    // What you can see on the real screen.
                    val vw = session.viewW / session.zoom * k
                    val vh = session.viewH / session.zoom * k
                    drawRect(
                        Color.White.copy(alpha = 0.3f),
                        Offset(c.x + session.camX * k - vw / 2f, c.y + session.camY * k - vh / 2f),
                        androidx.compose.ui.geometry.Size(vw, vh),
                        style = Stroke(1.dp.toPx()),
                    )

                    for (s in session.world.snakes) {
                        if (!s.alive || s.isPlayer || s.gone) continue
                        val r = (2.5f + sqrt(s.mass) / 7f).coerceAtMost(7f).dp.toPx()
                        val p = Offset(c.x + s.xs[0] * k, c.y + s.ys[0] * k)
                        drawCircle(Color(s.headColor).copy(alpha = 0.95f), r, p)
                        if (s.id == selId) drawCircle(Neon.Lime, r + 5.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                    }

                    val mePos = Offset(c.x + me.xs[0] * k, c.y + me.ys[0] * k)
                    val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                    if (session.routeActive) {
                        drawLine(
                            Neon.Lime, mePos, Offset(c.x + session.routeX * k, c.y + session.routeY * k),
                            2.5.dp.toPx(), pathEffect = dash,
                        )
                    }
                    if (hasSel) {
                        val sp = selPos()
                        val tp = Offset(c.x + sp.x * k, c.y + sp.y * k)
                        drawLine(Neon.Cyan, mePos, tp, 2.dp.toPx(), pathEffect = dash)
                        drawCircle(Neon.Cyan, 8.dp.toPx(), tp, style = Stroke(2.dp.toPx()))
                        drawCircle(Neon.Cyan.copy(alpha = 0.3f), 8.dp.toPx(), tp)
                    }
                    if (me.alive) {
                        drawCircle(Neon.Cyan, 5.dp.toPx(), mePos)
                        drawLine(
                            Neon.Cyan, mePos,
                            Offset(mePos.x + kotlin.math.cos(me.angle) * 13.dp.toPx(), mePos.y + kotlin.math.sin(me.angle) * 13.dp.toPx()),
                            2.dp.toPx(),
                        )
                    }
                }
            }

            Column(
                Modifier.width(236.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("MAP", color = Neon.Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                val sel = if (hasSel) selPos() else null
                val dist = if (sel != null) hypot(sel.x - me.xs[0], sel.y - me.ys[0]).toInt() / 10 else 0
                Text(
                    if (sel != null) "Go to ${selName}  ·  $dist m" else "Tap the map to pick an area, or tap a player.",
                    color = if (sel != null) Neon.Lime else Neon.Dim,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                NeonButton(
                    "GO THERE",
                    {
                        if (selId >= 0) session.routeToSnake(selId) else session.routeToPoint(selX, selY, selName)
                        onClose()
                    },
                    Modifier.fillMaxWidth().height(46.dp),
                    primary = hasSel && me.alive,
                    accent = Neon.Lime,
                )
                if (session.routeActive) {
                    NeonButton("STOP ROUTE", { session.cancelRoute() }, Modifier.fillMaxWidth().height(40.dp), primary = false, accent = Neon.Pink)
                }
                Text("QUICK AREAS", color = Neon.Dim, fontSize = 10.sp, letterSpacing = 1.sp)
                val far = Cfg.ARENA_RADIUS * 0.6f
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    AreaChip("CENTER") { pickArea(0f, 0f, "the center") }
                    AreaChip("NORTH") { pickArea(0f, -far, "the north") }
                    AreaChip("SOUTH") { pickArea(0f, far, "the south") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    AreaChip("WEST") { pickArea(-far, 0f, "the west") }
                    AreaChip("EAST") { pickArea(far, 0f, "the east") }
                }
                Text("PLAYERS  ·  tap one to travel to them", color = Neon.Dim, fontSize = 10.sp, letterSpacing = 1.sp)
                if (people.isEmpty()) Text("Nobody in sight yet.", color = Neon.Dim, fontSize = 12.sp)
                people.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selId == p.id) Color(0x3329E5A0) else Neon.Surface)
                            .clickable { pickPerson(p) }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(p.color)))
                        Text(p.name, color = Neon.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${p.score}", color = Neon.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                NeonButton("CLOSE", onClose, Modifier.fillMaxWidth().height(40.dp), primary = false)
            }
        }
    }
}

@Composable
private fun AreaChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Neon.Text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Neon.Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp),
    )
}

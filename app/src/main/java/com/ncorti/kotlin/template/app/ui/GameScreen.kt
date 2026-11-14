package com.ncorti.kotlin.template.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncorti.kotlin.template.app.AppState
import com.ncorti.kotlin.template.app.GameSession
import com.ncorti.kotlin.template.app.game.Cfg
import com.ncorti.kotlin.template.app.game.Power
import com.ncorti.kotlin.template.app.game.Powers
import kotlinx.coroutines.delay
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun GameScreen(app: AppState, session: GameSession) {
    val settings = app.settings
    val renderer = remember { GameRenderer() }
    val focusRequester = remember { FocusRequester() }

    BackHandler { if (session.over) app.toMenu() else session.paused = !session.paused }

    // One tick per display frame while this screen is composed (frames stop in the background,
    // and AppState also pauses the run then).
    LaunchedEffect(session) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000_000f
                last = now
                session.tick(dt)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Neon.Bg)
            .onPreviewKeyEvent { handleKey(session, it) }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        // Fixed joystick position: bottom-left, clear of notches and the nav bar.
        val density = LocalDensity.current
        val insets = WindowInsets.safeDrawing
        val joyR = with(density) { 56.dp.toPx() }
        val homeX = insets.getLeft(density, LayoutDirection.Ltr) + with(density) { 30.dp.toPx() } + joyR
        val homeY = constraints.maxHeight - insets.getBottom(density) - with(density) { 24.dp.toPx() } - joyR
        SideEffect {
            session.joystick.home = Offset(homeX, homeY)
            session.joystick.radius = joyR
            if (!session.joystick.active) session.joystick.knob = session.joystick.home
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .systemGestureExclusion()
                .pointerInput(session, settings.controlMode) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val joystickMode = settings.controlMode == 0
                        val j = session.joystick
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        var activeId = down.id

                        if (joystickMode) {
                            // The fixed joystick only listens to touches that start near its ring.
                            if (hypot(down.position.x - j.home.x, down.position.y - j.home.y) > j.radius * 1.9f) {
                                return@awaitEachGesture
                            }
                            j.active = true
                        } else {
                            steerDrag(session, down.position, w, h, 30.dp.toPx())
                        }
                        try {
                            var first = true
                            while (true) {
                                val event = if (first) null else awaitPointerEvent()
                                val ch = if (event == null) down else (
                                    event.changes.firstOrNull { it.id == activeId && it.pressed }
                                        ?: event.changes.firstOrNull { it.pressed }
                                        ?: break
                                    )
                                first = false
                                activeId = ch.id
                                if (joystickMode) {
                                    val dx = ch.position.x - j.home.x
                                    val dy = ch.position.y - j.home.y
                                    val dist = hypot(dx, dy)
                                    if (dist > 10.dp.toPx()) session.input.angle = atan2(dy, dx)
                                    val reach = j.radius * 0.8f
                                    val k = if (dist > reach) reach / dist else 1f
                                    j.knob = Offset(j.home.x + dx * k, j.home.y + dy * k)
                                } else {
                                    steerDrag(session, ch.position, w, h, 30.dp.toPx())
                                }
                                ch.consume()
                            }
                        } finally {
                            j.active = false
                            j.knob = j.home
                        }
                    }
                }
                .pointerInput(session) {
                    // Desktop: a hovering mouse steers without any button pressed (touch & drag mode).
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (settings.controlMode != 1) continue
                            val c = e.changes.firstOrNull() ?: continue
                            if (c.type == PointerType.Mouse) {
                                steerDrag(session, c.position, size.width.toFloat(), size.height.toFloat(), 6f)
                            }
                        }
                    }
                },
        ) {
            @Suppress("UNUSED_VARIABLE")
            val tick = session.frame // subscribes the draw phase to the per-frame counter
            renderer.draw(this, session, settings.quality)
        }

        if (settings.controlMode == 0) {
            Canvas(Modifier.fillMaxSize()) { drawJoystick(session) }
        }

        // ---------------------------------------------------------------- HUD
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(10.dp),
        ) {
            Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudIconButton({ session.paused = true }, "Pause") { drawPauseBars() }
                    HudIconButton({ app.setSound(!settings.sound) }, "Sound") { drawSpeaker(settings.sound) }
                }
                StatsPanel(session)
                Minimap(session)
            }

            session.room?.let { room ->
                val peers = if (room.isHost) session.peerCount + 1 else null
                Text(
                    "ROOM ${room.code}" + (if (peers != null) "  ·  $peers online" else "") +
                        (if (room.address != null) "  ·  ${room.address}" else ""),
                    color = Neon.Cyan,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x66000000))
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
            if (session.banner.isNotEmpty()) {
                Text(
                    session.banner,
                    color = Neon.Lime,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 26.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }

            Leaderboard(session, settings.playerName, Modifier.align(Alignment.TopEnd))

            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 124.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Powers.inGame.forEach { power ->
                    if (settings.powerLevel(power.id) > 0) PowerButton(session, power)
                }
            }
            BoostButton(session, Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 4.dp))
        }

        // ---------------------------------------------------------------- overlays
        val result = session.result
        if (session.disconnected == null && session.over && result != null) {
            Scrim {
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { shown = true }
                val enter by animateFloatAsState(
                    if (shown) 1f else 0.7f,
                    spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
                    label = "enter",
                )
                val scoreShown by animateIntAsState(if (shown) result.score else 0, tween(900), label = "score")
                val lengthShown by animateIntAsState(if (shown) result.length else 0, tween(900), label = "length")
                val coinsShown by animateIntAsState(if (shown) result.earnedCoins else 0, tween(900), label = "coins")
                Row(
                    Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .graphicsLayer {
                            scaleX = enter
                            scaleY = enter
                            alpha = ((enter - 0.7f) / 0.3f).coerceIn(0f, 1f)
                        },
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (result.newHighScore) "NEW HIGH SCORE!" else "ELIMINATED",
                            color = if (result.newHighScore) Neon.Lime else Neon.Pink,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                        )
                        ResultRow("Final score", scoreShown.toString())
                        ResultRow("Best score", result.best.toString())
                        if (!result.solo) ResultRow("Final rank", "#${result.rank}")
                        ResultRow("Snake length", lengthShown.toString())
                        ResultRow("Coins earned", "+$coinsShown  (${result.kills} eliminations)")
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        result.challenges.forEach {
                            Text("Challenge complete! $it", color = Neon.Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        NeonButton(
                            "PLAY AGAIN",
                            { if (session.isOnline) session.respawnInRoom() else app.startGame(session.solo) },
                            Modifier.fillMaxWidth(),
                        )
                        NeonButton("MAIN MENU", { app.toMenu() }, Modifier.fillMaxWidth(), primary = false)
                    }
                }
            }
        } else if (session.disconnected != null) {
            Scrim {
                Column(
                    Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("DISCONNECTED", color = Neon.Pink, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text(session.disconnected ?: "", color = Neon.Text, fontSize = 14.sp, textAlign = TextAlign.Center)
                    NeonButton("MAIN MENU", { app.toMenu() }, Modifier.fillMaxWidth())
                }
            }
        } else if (session.paused) {
            Scrim {
                Column(
                    Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("PAUSED", color = Neon.Text, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                    NeonButton("RESUME", { session.paused = false }, Modifier.fillMaxWidth())
                    if (session.isOnline) {
                        Text(
                            "The arena keeps running while you're in this menu. Leaving closes the room if you are the host.",
                            color = Neon.Dim,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        )
                        NeonButton("LEAVE ROOM", { app.toMenu() }, Modifier.fillMaxWidth(), primary = false)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NeonButton("RESTART", { app.startGame(session.solo) }, Modifier.weight(1f), primary = false)
                            NeonButton("MAIN MENU", { app.toMenu() }, Modifier.weight(1f), primary = false)
                        }
                    }
                }
            }
        }
    }
}

private fun steerDrag(s: GameSession, pos: Offset, w: Float, h: Float, minDist: Float) {
    val p = s.world.player
    val hx = w / 2f + (p.xs[0] - s.camX) * s.zoom
    val hy = h / 2f + (p.ys[0] - s.camY) * s.zoom
    val dx = pos.x - hx
    val dy = pos.y - hy
    if (dx * dx + dy * dy > minDist * minDist) s.input.angle = atan2(dy, dx)
}

private fun handleKey(s: GameSession, e: KeyEvent): Boolean {
    val down = e.type == KeyEventType.KeyDown
    if (!down && e.type != KeyEventType.KeyUp) return false
    return when (e.key) {
        Key.DirectionLeft, Key.A -> { s.input.left = down; true }
        Key.DirectionRight, Key.D -> { s.input.right = down; true }
        Key.Spacebar, Key.ShiftLeft, Key.ShiftRight -> { s.input.keyBoost = down; true }
        Key.P -> { if (down && !s.over) s.paused = !s.paused; true }
        else -> false
    }
}

// ------------------------------------------------------------------ joystick

private fun DrawScope.drawJoystick(session: GameSession) {
    val j = session.joystick
    val c = j.home
    val r = j.radius
    val active = j.active
    drawCircle(Color.White.copy(alpha = if (active) 0.16f else 0.10f), r, c)
    drawCircle(Color.White.copy(alpha = if (active) 0.65f else 0.40f), r, c, style = Stroke(3.dp.toPx()))
    // Faint direction ticks around the ring.
    for (i in 0 until 4) {
        val a = i * 1.5707964f
        drawLine(
            Color.White.copy(alpha = 0.35f),
            Offset(c.x + cos(a) * r * 0.86f, c.y + sin(a) * r * 0.86f),
            Offset(c.x + cos(a) * r * 0.97f, c.y + sin(a) * r * 0.97f),
            2.dp.toPx(),
            StrokeCap.Round,
        )
    }
    val k = j.knob
    drawCircle(Color.Black.copy(alpha = 0.25f), r * 0.44f, Offset(k.x, k.y + 3.dp.toPx()))
    drawCircle(Color.White.copy(alpha = if (active) 1f else 0.85f), r * 0.44f, k)
    drawCircle(Neon.Violet.copy(alpha = 0.55f), r * 0.2f, k)
}

// ------------------------------------------------------------------ HUD pieces

@Composable
private fun StatsPanel(session: GameSession) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x66000000))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val score = session.hudScore
            var pop by remember { mutableStateOf(false) }
            LaunchedEffect(score) {
                pop = true
                delay(140)
                pop = false
            }
            val popScale by animateFloatAsState(
                if (pop) 1.15f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "pop",
            )
            Text(
                score.toString(),
                color = Neon.Text,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(end = 6.dp).graphicsLayer {
                    scaleX = popScale
                    scaleY = popScale
                    transformOrigin = TransformOrigin(0f, 1f)
                },
            )
            Text("SCORE", color = Neon.Dim, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStat("LENGTH", session.hudLength.toString())
            if (!session.solo) MiniStat("RANK", "#${session.hudRank}/${session.hudAlive}")
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Neon.Dim, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = Neon.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Minimap(session: GameSession) {
    Canvas(
        Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(Color(0x66000000)),
    ) {
        @Suppress("UNUSED_VARIABLE")
        val tick = session.frame
        val c = Offset(size.width / 2f, size.height / 2f)
        val rad = size.width / 2f - 3.dp.toPx()
        val k = rad / Cfg.ARENA_RADIUS
        drawCircle(Neon.Pink.copy(alpha = 0.8f), rad, c, style = Stroke(2.dp.toPx()))
        val world = session.world
        for (i in 0 until world.snakes.size) {
            val s = world.snakes[i]
            if (!s.alive || s.isPlayer) continue
            drawCircle(Color(0xFFFF8A9B).copy(alpha = 0.85f), 2.2.dp.toPx(), Offset(c.x + s.xs[0] * k, c.y + s.ys[0] * k))
        }
        val p = world.player
        if (p.alive) {
            val pp = Offset(c.x + p.xs[0] * k, c.y + p.ys[0] * k)
            drawCircle(Neon.Cyan, 3.6.dp.toPx(), pp)
            drawLine(Neon.Cyan, pp, Offset(pp.x + cos(p.angle) * 9.dp.toPx(), pp.y + sin(p.angle) * 9.dp.toPx()), 2.dp.toPx())
        }
    }
}

@Composable
private fun Leaderboard(session: GameSession, myName: String, modifier: Modifier) {
    val board = session.hudBoard
    if (board.isEmpty()) return
    Column(
        modifier
            .widthIn(min = 150.dp, max = 180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x66000000))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text("LEADERBOARD", color = Neon.Dim, fontSize = 9.sp, letterSpacing = 1.sp)
        var meShown = false
        board.take(6).forEachIndexed { i, e ->
            if (e.isYou) meShown = true
            BoardRow("${i + 1}. ${e.name}", e.score.toString(), e.isYou, crown = i == 0)
        }
        if (!meShown && !session.solo && session.world.player.alive) {
            BoardRow("${session.hudRank}. $myName", session.hudScore.toString(), true)
        }
    }
}

@Composable
private fun BoardRow(name: String, score: String, you: Boolean, crown: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (crown) {
            Ico(Ico.CROWN, 12.dp, Color(0xFFFFD45C))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            name,
            color = if (you) Neon.Cyan else Neon.Text,
            fontSize = 12.sp,
            fontWeight = if (you) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(score, color = if (you) Neon.Cyan else Neon.Dim, fontSize = 12.sp)
    }
}

@Composable
private fun HudIconButton(onClick: () -> Unit, description: String, draw: DrawScope.() -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .bounceClick(onClick = onClick)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp), onDraw = draw)
    }
}

private fun DrawScope.drawPauseBars() {
    drawRect(Color.White, Offset(size.width * 0.12f, 0f), Size(size.width * 0.28f, size.height))
    drawRect(Color.White, Offset(size.width * 0.60f, 0f), Size(size.width * 0.28f, size.height))
}

private fun DrawScope.drawSpeaker(on: Boolean) {
    val w = size.width
    val h = size.height
    val body = Path().apply {
        moveTo(0f, h * 0.35f)
        lineTo(w * 0.28f, h * 0.35f)
        lineTo(w * 0.58f, h * 0.08f)
        lineTo(w * 0.58f, h * 0.92f)
        lineTo(w * 0.28f, h * 0.65f)
        lineTo(0f, h * 0.65f)
        close()
    }
    drawPath(body, Color.White)
    if (on) {
        drawArc(
            Color.White, -45f, 90f, false,
            topLeft = Offset(w * 0.46f, h * 0.22f), size = Size(w * 0.5f, h * 0.56f),
            style = Stroke(w * 0.1f),
        )
    } else {
        drawLine(Neon.Pink, Offset(w * 0.7f, h * 0.3f), Offset(w, h * 0.7f), w * 0.12f)
        drawLine(Neon.Pink, Offset(w, h * 0.3f), Offset(w * 0.7f, h * 0.7f), w * 0.12f)
    }
}

// ------------------------------------------------------------------ boost + powers

private fun hexPath(c: Offset, r: Float): Path {
    val p = Path()
    for (i in 0 until 6) {
        val a = (-90f + 60f * i) * 0.017453292f
        val x = c.x + cos(a) * r
        val y = c.y + sin(a) * r
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    return p
}

@Composable
private fun BoostButton(session: GameSession, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val energy = session.hudEnergy
    val low = energy < 0.15f
    val squish by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "boost",
    )
    Box(
        modifier
            .size(100.dp)
            .graphicsLayer {
                scaleX = squish
                scaleY = squish
            }
            .pointerInput(session) {
                // Hold-to-boost that survives the thumb sliding off the button: it only ends when
                // the finger actually lifts (or the gesture is cancelled).
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    pressed = true
                    session.input.touchBoost = true
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            ch.consume()
                        }
                    } finally {
                        pressed = false
                        session.input.touchBoost = false
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f - 3.dp.toPx()
            val hex = hexPath(c, r)
            drawPath(hex, Color(0xCC120A24))
            // Energy rises from the bottom like liquid in a tank.
            clipPath(hex) {
                val top = c.y + r - (2f * r) * energy
                drawRect(
                    if (low) Neon.Pink.copy(alpha = 0.85f) else Neon.Lime.copy(alpha = if (pressed) 0.95f else 0.7f),
                    Offset(c.x - r, top),
                    Size(2f * r, c.y + r - top),
                )
            }
            drawPath(hex, if (pressed) Neon.Cyan else Color.White.copy(alpha = 0.85f), style = Stroke(4.dp.toPx()))
            // Lightning bolt.
            val s = r * 0.42f
            val bolt = Path().apply {
                moveTo(c.x + 0.12f * s, c.y - 1.0f * s)
                lineTo(c.x - 0.60f * s, c.y + 0.10f * s)
                lineTo(c.x - 0.05f * s, c.y + 0.10f * s)
                lineTo(c.x - 0.18f * s, c.y + 1.0f * s)
                lineTo(c.x + 0.60f * s, c.y - 0.15f * s)
                lineTo(c.x + 0.05f * s, c.y - 0.15f * s)
                close()
            }
            drawPath(bolt, Color.White)
        }
        Text(
            "BOOST",
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 9.sp,
            letterSpacing = 1.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
        )
    }
}

@Composable
private fun PowerButton(session: GameSession, power: Power) {
    val secs = session.activeSeconds(power)
    val cd = session.cooldownSeconds(power)
    val frac = session.cooldownFraction(power)
    val active = secs > 0
    val ready = !active && cd <= 0
    val pulse = rememberInfiniteTransition(label = "power")
    val glow by pulse.animateFloat(
        0.35f, 1f,
        infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "glow",
    )
    val tint = if (active) Neon.Cyan else if (ready) Color.White else Color(0xFF7C7396)
    val ring = if (active) Neon.Cyan.copy(alpha = glow) else if (ready) Neon.Lime else Neon.Violet.copy(alpha = 0.5f)
    Box(
        Modifier
            .size(56.dp)
            .bounceClick(sound = null) { session.usePower(power) }
            .clip(CircleShape)
            .background(Color(0xAA000000))
            .border(2.dp, ring, CircleShape)
            .semantics { contentDescription = power.name.uppercase() },
        contentAlignment = Alignment.Center,
    ) {
        if (!active && cd > 0) {
            // Cooldown sweep: the dark wedge shrinks as the power comes back.
            Canvas(Modifier.matchParentSize()) {
                drawArc(
                    Color(0x99000000), -90f, 360f * frac, true,
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Ico(powerIcon(power.id), 22.dp, tint)
            Text(
                if (active) "${secs}s" else if (cd > 0) "${cd}s" else "READY",
                color = if (active) Neon.Cyan else if (ready) Neon.Lime else Neon.Dim,
                fontSize = if (ready) 9.sp else 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ------------------------------------------------------------------ overlays

@Composable
private fun Scrim(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC07030F))
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Neon.Surface)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(label, color = Neon.Dim, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = Neon.Text, fontWeight = FontWeight.Black, fontSize = 13.sp, textAlign = TextAlign.End)
    }
}

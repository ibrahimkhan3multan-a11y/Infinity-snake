package com.ncorti.kotlin.template.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncorti.kotlin.template.app.AppState
import com.ncorti.kotlin.template.app.GameSession
import com.ncorti.kotlin.template.app.game.Power
import com.ncorti.kotlin.template.app.game.Powers
import kotlin.math.atan2
import kotlin.math.hypot

@Composable
fun GameScreen(app: AppState, session: GameSession) {
    val settings = app.settings
    val renderer = remember { GameRenderer() }
    val focusRequester = remember { FocusRequester() }

    BackHandler { if (session.over) app.toMenu() else session.paused = !session.paused }

    // The game loop: one tick per display frame. It only ticks while this screen is composed and
    // the app is in the foreground (frames stop when backgrounded; AppState also pauses the run).
    LaunchedEffect(session) {
        var last = 0L
        while (true) {
            withFrameNanosCompat { now ->
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
        val landscape = maxWidth > maxHeight

        Canvas(
            Modifier
                .fillMaxSize()
                .systemGestureExclusion()
                .pointerInput(settings.controlMode) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var activeId = down.id
                        val joystick = settings.controlMode == 1
                        val dead = 14.dp.toPx()
                        val headDead = 30.dp.toPx() // finger right on the snake: keep the last heading
                        val maxKnob = 56.dp.toPx()
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (joystick) {
                            session.joystick.base = down.position
                            session.joystick.knob = down.position
                            session.joystick.active = true
                        } else {
                            steerDrag(session, down.position, w, h, headDead)
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            // If the steering finger lifts while another is still down, carry on with that one.
                            val ch = event.changes.firstOrNull { it.id == activeId && it.pressed }
                                ?: event.changes.firstOrNull { it.pressed }
                                ?: break
                            if (ch.id != activeId) {
                                activeId = ch.id
                                if (joystick) {
                                    session.joystick.base = ch.position
                                    session.joystick.knob = ch.position
                                }
                            }
                            if (joystick) {
                                val base = session.joystick.base
                                val dx = ch.position.x - base.x
                                val dy = ch.position.y - base.y
                                val dist = hypot(dx, dy)
                                if (dist > dead) session.input.angle = atan2(dy, dx)
                                val k = if (dist > maxKnob) maxKnob / dist else 1f
                                session.joystick.knob = Offset(base.x + dx * k, base.y + dy * k)
                            } else {
                                steerDrag(session, ch.position, w, h, headDead)
                            }
                            ch.consume()
                        }
                        session.joystick.active = false
                    }
                }
                .pointerInput(Unit) {
                    // Desktop: a hovering mouse steers without any button pressed.
                    awaitPointerEventScope {
                        while (true) {
                            val e = awaitPointerEvent()
                            if (settings.controlMode != 0) continue
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

        if (settings.controlMode == 1) {
            Canvas(Modifier.fillMaxSize()) {
                if (session.joystick.active) {
                    val b = session.joystick.base
                    val k = session.joystick.knob
                    drawCircle(Color.White.copy(alpha = 0.10f), 56.dp.toPx(), b)
                    drawCircle(Color.White.copy(alpha = 0.35f), 56.dp.toPx(), b, style = Stroke(2.dp.toPx()))
                    drawCircle(Neon.Violet.copy(alpha = 0.8f), 22.dp.toPx(), k)
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(10.dp),
        ) {
            StatsPanel(session, landscape, Modifier.align(Alignment.TopStart))
            Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HudIconButton({ app.setSound(!settings.sound) }) { drawSpeaker(settings.sound) }
                    HudIconButton({ session.paused = true }) { drawPauseBars() }
                }
                Leaderboard(session, if (landscape) 4 else Int.MAX_VALUE)
            }
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = if (landscape) 8.dp else 28.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Powers.inGame.forEach { power ->
                    val secs = session.activeSeconds(power)
                    val owned = settings.count(power.id)
                    if (secs > 0 || owned > 0) PowerButton(session, power, secs, owned)
                }
            }
            BoostButton(
                session,
                size = if (landscape) 88.dp else 100.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (landscape) 24.dp else 8.dp, bottom = if (landscape) 8.dp else 28.dp),
            )
        }

        val result = session.result
        if (session.over && result != null) {
            Scrim {
                Text(if (result.newHighScore) "NEW HIGH SCORE!" else "ELIMINATED", color = if (result.newHighScore) Neon.Lime else Neon.Pink, fontSize = 26.sp, fontWeight = FontWeight.Black)
                ResultRow("Final score", result.score.toString())
                ResultRow("Best score", result.best.toString())
                if (!result.solo) ResultRow("Final rank", "#${result.rank}")
                ResultRow("Snake length", result.length.toString())
                ResultRow("Coins earned", "+${result.earnedCoins}")
                NeonButton("PLAY AGAIN", { app.startGame(session.solo) }, Modifier.fillMaxWidth())
                NeonButton("MAIN MENU", { app.toMenu() }, Modifier.fillMaxWidth(), primary = false)
            }
        } else if (session.paused) {
            Scrim {
                Text("PAUSED", color = Neon.Text, fontSize = 28.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                NeonButton("RESUME", { session.paused = false }, Modifier.fillMaxWidth())
                NeonButton("RESTART", { app.startGame(session.solo) }, Modifier.fillMaxWidth(), primary = false)
                NeonButton("MAIN MENU", { app.toMenu() }, Modifier.fillMaxWidth(), primary = false)
            }
        }
    }
}

/** Wrapper kept separate so the loop reads clearly. */
private suspend fun <R> withFrameNanosCompat(block: (Long) -> R): R =
    androidx.compose.runtime.withFrameNanos(block)

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

// ------------------------------------------------------------------ HUD pieces

@Composable
private fun StatsPanel(session: GameSession, landscape: Boolean, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x66000000))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(session.hudScore.toString(), color = Neon.Text, fontSize = if (landscape) 24.sp else 32.sp, fontWeight = FontWeight.Black)
        Text("SCORE", color = Neon.Dim, fontSize = 10.sp, letterSpacing = 1.sp)
        val rankText = if (session.solo) "—" else "#${session.hudRank}/${session.hudAlive}"
        if (landscape) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MiniStat("LENGTH", session.hudLength.toString())
                if (!session.solo) MiniStat("RANK", rankText)
            }
        } else {
            MiniStat("LENGTH", session.hudLength.toString())
            if (!session.solo) MiniStat("RANK", rankText)
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Neon.Dim, fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = Neon.Cyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Leaderboard(session: GameSession, maxRows: Int) {
    val board = session.hudBoard
    if (board.isEmpty()) return
    Column(
        Modifier
            .widthIn(min = 140.dp, max = 170.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x66000000))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text("LEADERBOARD", color = Neon.Dim, fontSize = 9.sp, letterSpacing = 1.sp)
        board.take(maxRows).forEachIndexed { i, e ->
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "${i + 1}. ${e.name}",
                    color = if (e.isYou) Neon.Cyan else Neon.Text,
                    fontSize = 12.sp,
                    fontWeight = if (e.isYou) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
                Text(e.score.toString(), color = if (e.isYou) Neon.Cyan else Neon.Dim, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun HudIconButton(onClick: () -> Unit, draw: DrawScope.() -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0x66000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp), onDraw = draw)
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

@Composable
private fun BoostButton(session: GameSession, size: Dp, modifier: Modifier) {
    var pressed by remember { mutableStateOf(false) }
    val energy = session.hudEnergy
    val low = energy < 0.15f
    val brush = Brush.radialGradient(
        if (pressed && !low) listOf(Neon.Cyan, Color(0xFF0E7490)) else listOf(Neon.Violet, Color(0xFF5B21B6)),
    )
    Box(
        modifier
            .size(size)
            .pointerInput(Unit) {
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
        Box(Modifier.matchParentSize().clip(CircleShape).background(brush))
        Canvas(Modifier.matchParentSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2f
            drawArc(
                Color.White.copy(alpha = 0.15f), 0f, 360f, false,
                topLeft = Offset(inset, inset), size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(stroke),
            )
            drawArc(
                if (low) Neon.Pink else Neon.Lime, -90f, 360f * energy, false,
                topLeft = Offset(inset, inset), size = Size(this.size.width - stroke, this.size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Text("BOOST", color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun PowerButton(session: GameSession, power: Power, secs: Int, owned: Int) {
    val active = secs > 0
    Box(
        Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(Color(0xAA000000))
            .border(2.dp, if (active) Neon.Cyan else Neon.Violet, CircleShape)
            .clickable { session.usePower(power) },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(power.name.uppercase(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(
                if (active) "${secs}s" else "×$owned",
                color = if (active) Neon.Cyan else Neon.Lime,
                fontSize = 13.sp,
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
    ) {
        Column(
            Modifier
                .widthIn(max = 380.dp)
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Neon.Surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, color = Neon.Dim, modifier = Modifier.weight(1f))
        Text(value, color = Neon.Text, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
    }
}


package com.ncorti.kotlin.template.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import com.ncorti.kotlin.template.app.Session3D
import kotlin.math.hypot

/** The real 3D game: scene, HUD, controls, pause and game-over cards. */
@Composable
fun Game3DScreen(app: AppState, session: Session3D) {
    val settings = app.settings
    val focusRequester = remember { FocusRequester() }

    BackHandler {
        if (session.over) app.toMenu() else session.paused = !session.paused
    }

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
            .onPreviewKeyEvent { handleKey3D(session, it) }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
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
                        val j = session.joystick
                        if (settings.controlMode == 0) {
                            if (hypot(down.position.x - j.home.x, down.position.y - j.home.y) > j.radius * 1.9f) {
                                return@awaitEachGesture
                            }
                            j.active = true
                            var activeId = down.id
                            var first = true
                            try {
                                while (true) {
                                    val event = if (first) null else awaitPointerEvent()
                                    val ch = if (event == null) down else (
                                        event.changes.firstOrNull { it.id == activeId && it.pressed }
                                            ?: event.changes.firstOrNull { it.pressed }
                                            ?: break
                                        )
                                    first = false
                                    activeId = ch.id
                                    val dx = ch.position.x - j.home.x
                                    val dy = ch.position.y - j.home.y
                                    val dist = hypot(dx, dy)
                                    if (dist > 10.dp.toPx()) session.steerScreen(dx, dy)
                                    val reach = j.radius * 0.8f
                                    val k = if (dist > reach) reach / dist else 1f
                                    j.knob = Offset(j.home.x + dx * k, j.home.y + dy * k)
                                    ch.consume()
                                }
                            } finally {
                                j.active = false
                                j.knob = j.home
                            }
                        } else {
                            // Swipe: every flick of the finger turns the snake that way.
                            var origin = down.position
                            var id = down.id
                            while (true) {
                                val event = awaitPointerEvent()
                                val ch = event.changes.firstOrNull { it.id == id && it.pressed }
                                    ?: event.changes.firstOrNull { it.pressed }
                                    ?: break
                                id = ch.id
                                val dx = ch.position.x - origin.x
                                val dy = ch.position.y - origin.y
                                if (hypot(dx, dy) > 34.dp.toPx()) {
                                    session.steerScreen(dx, dy)
                                    origin = ch.position
                                }
                                ch.consume()
                            }
                        }
                    }
                },
        ) {
            @Suppress("UNUSED_VARIABLE")
            val tick = session.frame
            drawIntoNative(this, session, settings.quality)
        }

        if (settings.controlMode == 0) {
            Canvas(Modifier.fillMaxSize()) { drawJoystick3D(session) }
        }

        // ---------------------------------------------------------------- HUD
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(10.dp),
        ) {
            Row(Modifier.align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0x66000000))
                        .clickable { session.paused = true }
                        .semantics { contentDescription = "Pause" },
                    contentAlignment = Alignment.Center,
                ) { Text("II", color = Color.White, fontWeight = FontWeight.Black, fontSize = 16.sp) }
                Column(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x55000000))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
                        Text(session.hudScore.toString(), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                        Text("SCORE", color = Neon.Dim, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Hud3Stat("LENGTH", session.hudLength.toString())
                        Hud3Stat("SPEED", session.hudSpeed.toString())
                    }
                }
            }
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x55000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(session.def.title.uppercase(), color = Neon.Lime, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                Hud3Stat("BEST", maxOf(session.best, session.hudScore).toString())
                Hud3Stat("LEVEL", (session.hudLevel + 1).toString())
            }
            Text(
                "3D",
                color = Neon.Amber,
                fontWeight = FontWeight.Black,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x66000000))
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            )
            if (settings.controlMode == 0) {
                // Two arrow buttons on the right turn left or right relative to where the snake faces.
                Row(
                    Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TurnButton("◀", "Turn left") { session.turnLeft() }
                    TurnButton("▶", "Turn right") { session.turnRight() }
                }
            }
        }

        // ---------------------------------------------------------------- overlays
        if (session.paused && !session.over) {
            Scrim3D {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("PAUSED", color = Neon.Text, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
                    NeonButton("RESUME", { session.paused = false }, Modifier.widthIn(min = 220.dp))
                    NeonButton("RESTART", { app.start3D(session.mapIndex) }, Modifier.widthIn(min = 220.dp), primary = false)
                    NeonButton("MAIN MENU", { app.toMenu() }, Modifier.widthIn(min = 220.dp), primary = false)
                }
            }
        }
        val result = session.result
        if (session.over && result != null) {
            Scrim3D {
                Column(
                    Modifier.widthIn(max = 420.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("GAME OVER", color = Neon.Pink, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                    Text(result.reason, color = Neon.Text, fontSize = 13.sp, textAlign = TextAlign.Center)
                    if (result.newBest) Text("NEW BEST!", color = Neon.Lime, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Text("Score ${result.score}   ·   Best ${result.best}", color = Neon.Cyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Length ${result.length}   ·   +${result.coins} coins", color = Neon.Dim, fontSize = 13.sp)
                    NeonButton("PLAY AGAIN", { app.start3D(session.mapIndex) }, Modifier.fillMaxWidth())
                    NeonButton("OTHER MAPS", { app.toMenu(); app.open(com.ncorti.kotlin.template.app.Screen.MODES) }, Modifier.fillMaxWidth(), primary = false)
                    NeonButton("MAIN MENU", { app.toMenu() }, Modifier.fillMaxWidth(), primary = false)
                }
            }
        }
    }
}

private fun drawIntoNative(scope: DrawScope, session: Session3D, quality: Int) {
    scope.drawContext.canvas.nativeCanvas.let { c ->
        session.renderer.draw(
            c, scope.size.width.toInt(), scope.size.height.toInt(), session.game, session.camera, quality,
            session.skin.head, session.skin.tail, session.skin.rainbow,
        )
    }
}

@Composable
private fun Hud3Stat(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Neon.Dim, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = Neon.Cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TurnButton(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(62.dp)
            .clip(CircleShape)
            .background(Color(0x55000000))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun Scrim3D(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC07030F))
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun DrawScope.drawJoystick3D(session: Session3D) {
    val j = session.joystick
    val active = j.active
    drawCircle(Color.White.copy(alpha = if (active) 0.16f else 0.10f), j.radius, j.home)
    drawCircle(Color.White.copy(alpha = if (active) 0.65f else 0.40f), j.radius, j.home, style = Stroke(3.dp.toPx()))
    drawCircle(Color.White.copy(alpha = 0.85f), j.radius * 0.42f, j.knob)
    drawCircle(Color(0xFFB06BFF), j.radius * 0.24f, j.knob)
}

private fun handleKey3D(s: Session3D, e: KeyEvent): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    return when (e.key) {
        Key.DirectionLeft, Key.A -> { s.turnLeft(); true }
        Key.DirectionRight, Key.D -> { s.turnRight(); true }
        Key.P -> { if (!s.over) s.paused = !s.paused; true }
        else -> false
    }
}

package com.ncorti.kotlin.template.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncorti.kotlin.template.app.AppState
import com.ncorti.kotlin.template.app.Screen
import com.ncorti.kotlin.template.app.game.Challenge
import com.ncorti.kotlin.template.app.game.Challenges
import com.ncorti.kotlin.template.app.game.Powers
import com.ncorti.kotlin.template.app.game.Skins
import kotlin.math.cos
import kotlin.math.sin

/** Landscape main menu: top bar, event carousel, quick-access column, snake preview, mode toggle and Play. */
@Composable
fun MenuScreen(app: AppState) {
    val s = app.settings
    var editingName by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(BackgroundBrush)) {
        HexBackdrop()
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            val wide = maxWidth > 700.dp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // ------------------------------------------------ top bar
                Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundIconButton("Settings", { app.open(Screen.SETTINGS) }) { GearIcon() }
                    NamePill(app) { editingName = true }
                    if (wide) {
                        Text(
                            "INFINITY\nSNAKE",
                            color = Neon.Cyan,
                            fontSize = 15.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    BestPanel(s.highScore, s.bestRank)
                    CoinPill(s.coins) { app.open(Screen.SHOP) }
                }

                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // ------------------------------------------------ carousel + snake bar
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        EventCarousel(app, Modifier.weight(1f).fillMaxWidth())
                        SnakeBar(app)
                    }
                    // ------------------------------------------------ quick access + play
                    Column(
                        Modifier.width(156.dp).fillMaxHeight(),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SideChip("SHOP", Neon.Pink, Ico.BAG) { app.open(Screen.SHOP) }
                        SideChip("SKINS", Neon.Violet, Ico.SNAKE) { app.open(Screen.SKINS) }
                        SideChip("HELP", Neon.Cyan, null) { app.open(Screen.HOWTO) }
                        Spacer(Modifier.weight(1f))
                        ModeToggle(solo = s.soloMode) { s.updateSoloMode(it) }
                        val pulse = rememberInfiniteTransition(label = "play")
                        val playScale by pulse.animateFloat(
                            1f, 1.045f,
                            infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                            label = "playScale",
                        )
                        NeonButton(
                            "PLAY",
                            { app.startGame(s.soloMode) },
                            Modifier
                                .fillMaxWidth()
                                .height(62.dp)
                                .graphicsLayer {
                                    scaleX = playScale
                                    scaleY = playScale
                                },
                            accent = Neon.Lime,
                            icon = { Ico(Ico.PLAY, 22.dp, Color(0xFF14002B)) },
                        )
                    }
                }
            }
        }
    }

    if (editingName) NameDialog(app) { editingName = false }
}

// ------------------------------------------------------------------ top bar parts

@Composable
private fun RoundIconButton(description: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .bounceClick(onClick = onClick)
            .clip(CircleShape)
            .background(Neon.Surface)
            .border(1.5.dp, Neon.Violet.copy(alpha = 0.6f), CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { icon() }
}

@Composable
private fun GearIcon() {
    Canvas(Modifier.size(24.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.width * 0.34f
        for (i in 0 until 8) {
            val a = i * 0.7853982f
            drawLine(
                Neon.Text, Offset(c.x + cos(a) * r, c.y + sin(a) * r),
                Offset(c.x + cos(a) * size.width * 0.5f, c.y + sin(a) * size.width * 0.5f),
                4.dp.toPx(),
            )
        }
        drawCircle(Neon.Text, r, c, style = Stroke(4.dp.toPx()))
    }
}

@Composable
private fun NamePill(app: AppState, onEdit: () -> Unit) {
    val skin = Skins.byId(app.settings.selectedSkin)
    Row(
        Modifier
            .bounceClick(onClick = onEdit)
            .clip(RoundedCornerShape(23.dp))
            .background(Neon.Surface)
            .border(1.5.dp, Neon.Violet.copy(alpha = 0.6f), RoundedCornerShape(23.dp))
            .semantics { contentDescription = "Edit name" }
            .padding(start = 5.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(Modifier.size(36.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            drawCircle(Color(skin.head), size.width / 2f, c)
            drawCircle(Color.White, size.width * 0.14f, Offset(c.x + size.width * 0.12f, c.y - size.height * 0.14f))
            drawCircle(Color.White, size.width * 0.14f, Offset(c.x + size.width * 0.12f, c.y + size.height * 0.14f))
            drawCircle(Color.Black, size.width * 0.07f, Offset(c.x + size.width * 0.16f, c.y - size.height * 0.14f))
            drawCircle(Color.Black, size.width * 0.07f, Offset(c.x + size.width * 0.16f, c.y + size.height * 0.14f))
        }
        Text(app.settings.playerName, color = Neon.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
        Text("✎", color = Neon.Dim, fontSize = 15.sp)
    }
}

@Composable
private fun BestPanel(best: Int, rank: Int) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Neon.Surface)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ico(Ico.TROPHY, 28.dp, Color(0xFFFFD45C))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("BEST SCORE", color = Neon.Dim, fontSize = 9.sp, letterSpacing = 1.sp)
            Text(best.toString(), color = Neon.Cyan, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("BEST RANK", color = Neon.Dim, fontSize = 9.sp, letterSpacing = 1.sp)
            Text(if (rank == 0) "—" else "#$rank", color = Neon.Lime, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CoinPill(coins: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(23.dp))
            .background(Neon.Surface)
            .border(1.5.dp, Color(0xFFFFD45C).copy(alpha = 0.7f), RoundedCornerShape(23.dp))
            .semantics { contentDescription = "Coins, open shop" }
            .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Ico(Ico.COIN, 30.dp)
        Text(coins.toString(), color = Neon.Text, fontWeight = FontWeight.Black, fontSize = 17.sp)
        Text("+", color = Neon.Lime, fontWeight = FontWeight.Black, fontSize = 20.sp)
    }
}

/** A big faint icon that gently bobs, used as card decoration. */
@Composable
private fun FloatingIcon(kind: Ico, tint: Color, modifier: Modifier) {
    val t = rememberInfiniteTransition(label = "float")
    val dy by t.animateFloat(
        -8f, 8f,
        infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dy",
    )
    Ico(kind, 110.dp, tint, modifier.graphicsLayer { translationY = dy })
}

// ------------------------------------------------------------------ carousel

@Composable
private fun EventCarousel(app: AppState, modifier: Modifier) {
    val pages = Challenges.all.size + 1
    val pager = rememberPagerState(pageCount = { pages })
    Box(modifier) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            if (page < Challenges.all.size) ChallengeCard(app, Challenges.all[page]) else DailyCard(app)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(pages) { i ->
                Box(
                    Modifier
                        .size(if (pager.currentPage == i) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (pager.currentPage == i) Color.White else Color.White.copy(alpha = 0.35f)),
                )
            }
        }
    }
}

private val cardBrushes = listOf(
    Brush.linearGradient(listOf(Color(0xFF5B21B6), Color(0xFFB0306E))),
    Brush.linearGradient(listOf(Color(0xFF0E7490), Color(0xFF3B2A9C))),
    Brush.linearGradient(listOf(Color(0xFFB45309), Color(0xFF7B2FF7))),
    Brush.linearGradient(listOf(Color(0xFF166534), Color(0xFF0E7490))),
)

@Composable
private fun ChallengeCard(app: AppState, c: Challenge) {
    val s = app.settings
    val done = c.id in s.challengeDone
    val best = s.challengeBest[c.id] ?: 0
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBrushes[c.id % cardBrushes.size])
            .padding(16.dp),
    ) {
        FloatingIcon(
            when (c.id) { 0 -> Ico.TROPHY; 1 -> Ico.TARGET; else -> Ico.CROWN },
            Color.White.copy(alpha = 0.25f),
            Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
        )
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.62f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(c.title.uppercase(), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text(c.desc, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, lineHeight = 24.sp)
            Text("Reward: ${c.rewardText}", color = Color(0xFFFFE066), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            if (done) {
                Text("COMPLETED ✓", color = Neon.Lime, fontSize = 15.sp, fontWeight = FontWeight.Black)
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((best.toFloat() / c.goal).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Neon.Lime),
                    )
                }
                Text("Best so far: $best / ${c.goal}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DailyCard(app: AppState) {
    val s = app.settings
    val can = s.canClaimDaily()
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBrushes[3])
            .padding(16.dp),
    ) {
        FloatingIcon(Ico.COIN, Color.White, Modifier.align(Alignment.CenterEnd).padding(end = 24.dp))
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(0.62f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("DAILY REWARD", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("Free coins, every day", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
            if (can) {
                NeonButton("CLAIM ${com.ncorti.kotlin.template.app.data.Settings.DAILY_COINS} COINS", { s.claimDaily() }, Modifier.width(220.dp), accent = Color(0xFFFFD45C))
            } else {
                Text("Claimed today. Come back tomorrow!", color = Neon.Lime, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ------------------------------------------------------------------ bottom-left bar

@Composable
private fun SnakeBar(app: AppState) {
    val s = app.settings
    val skin = Skins.byId(s.selectedSkin)
    val owned = s.count(Powers.HEADSTART.id)
    val wiggle = rememberInfiniteTransition(label = "wiggle")
    val phase by wiggle.animateFloat(
        0f, 6.2831855f,
        infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Neon.Surface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable { app.open(Screen.SKINS) }
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SkinPreview(skin, Modifier.width(120.dp).height(44.dp), phase)
            Column {
                Text("EDIT SNAKE", color = Neon.Text, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text(skin.name, color = Neon.Dim, fontSize = 11.sp)
            }
        }
        val armed = app.headStartArmed
        val label = if (owned > 0) "START BIG ×$owned" else "START BIG"
        val fg = if (armed) Color(0xFF14002B) else if (owned > 0) Neon.Lime else Neon.Dim
        Row(
            Modifier
                .padding(end = 8.dp)
                .bounceClick { if (owned > 0) app.headStartArmed = !armed else app.open(Screen.SHOP) }
                .clip(RoundedCornerShape(12.dp))
                .background(if (armed) Neon.Lime else Color.Transparent)
                .border(2.dp, if (owned > 0) Neon.Lime else Neon.Dim, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Ico(Ico.CHEVRONS, 14.dp, fg)
            Text(label, color = fg, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }
}

// ------------------------------------------------------------------ right column

@Composable
private fun SideChip(label: String, accent: Color, icon: Ico?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(12.dp))
            .background(Neon.Surface)
            .border(2.dp, accent.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Ico(icon, 18.dp, accent)
        } else {
            Text("?", color = accent, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = Neon.Text, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 2.sp)
    }
}

@Composable
private fun ModeToggle(solo: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Neon.Surface),
    ) {
        ToggleHalf("ARENA", !solo, Modifier.weight(1f)) { onChange(false) }
        ToggleHalf("SOLO", solo, Modifier.weight(1f)) { onChange(true) }
    }
}

@Composable
private fun ToggleHalf(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxHeight()
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Neon.Violet else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) Color(0xFF14002B) else Neon.Dim, fontWeight = FontWeight.Black, fontSize = 13.sp)
    }
}

// ------------------------------------------------------------------ dialog + backdrop

@Composable
private fun NameDialog(app: AppState, onClose: () -> Unit) {
    var text by remember { mutableStateOf(app.settings.playerName) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Your name") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(12) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { app.settings.updateName(text); onClose() }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

/** Faint hexagon grid behind the menu. */
@Composable
private fun HexBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val r = 34.dp.toPx()
        val w = r * 1.7320508f
        val rowH = r * 1.5f
        val col = Color.White.copy(alpha = 0.045f)
        var row = 0
        var y = 0f
        while (y < size.height + r) {
            var x = if (row % 2 == 0) 0f else w / 2f
            while (x < size.width + w) {
                for (i in 0 until 6) {
                    val a1 = (-90f + 60f * i) * 0.017453292f
                    val a2 = (-90f + 60f * (i + 1)) * 0.017453292f
                    drawLine(
                        col,
                        Offset(x + cos(a1) * r, y + sin(a1) * r),
                        Offset(x + cos(a2) * r, y + sin(a2) * r),
                        1.5f,
                    )
                }
                x += w
            }
            y += rowH
            row++
        }
    }
}

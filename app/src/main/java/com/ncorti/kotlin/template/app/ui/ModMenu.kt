package com.ncorti.kotlin.template.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncorti.kotlin.template.app.AppState
import com.ncorti.kotlin.template.app.Screen
import kotlin.math.roundToInt

/**
 * The tester's mod menu: a small draggable button that stays on top of every screen (including
 * games) once the tester code has been entered. Tap it for quick cheats; HIDE puts it away, and
 * entering the tester code again brings it back.
 */
@Composable
fun ModMenu(app: AppState) {
    val s = app.settings
    // The full tester screen is already open, so the button would only be in the way there.
    if (!s.devUnlocked || !s.devButton || app.screen == Screen.TESTER) return
    var open by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        val density = LocalDensity.current
        val btn = with(density) { 44.dp.toPx() }
        val maxX = (constraints.maxWidth - btn).coerceAtLeast(0f)
        val maxY = (constraints.maxHeight - btn).coerceAtLeast(0f)
        var pos by remember {
            mutableStateOf(
                if (s.devX >= 0f && s.devY >= 0f) Offset(s.devX.coerceIn(0f, maxX), s.devY.coerceIn(0f, maxY))
                else Offset(maxX / 2f, maxY - with(density) { 16.dp.toPx() }), // bottom centre
            )
        }

        Box(
            Modifier
                .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                .size(44.dp)
                .clip(CircleShape)
                .background(Color(0xDD2A0F55))
                .border(2.dp, Neon.Pink, CircleShape)
                .semantics { contentDescription = "Mod menu" }
                .pointerInput(Unit) { detectTapGestures { open = !open } }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { s.devSetPos(pos.x, pos.y) },
                    ) { change, drag ->
                        change.consume()
                        pos = Offset((pos.x + drag.x).coerceIn(0f, maxX), (pos.y + drag.y).coerceIn(0f, maxY))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("MOD", color = Neon.Pink, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }

        if (open) {
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .width(290.dp)
                    .heightIn(max = 340.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xF00B0618))
                    .border(1.5.dp, Neon.Pink.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    .pointerInput(Unit) { detectTapGestures { } }
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("MOD MENU", color = Neon.Pink, fontWeight = FontWeight.Black, fontSize = 15.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
                    Text("${s.coins} coins", color = Neon.Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("  ✕", color = Neon.Text, fontWeight = FontWeight.Black, fontSize = 16.sp, modifier = Modifier.clickable { open = false }.padding(4.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModBtn("FULL MENU", Neon.Violet) {
                        open = false
                        app.open(Screen.TESTER)
                    }
                    ModBtn("HIDE", Neon.Pink) {
                        open = false
                        s.devSetButton(false)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModBtn("+100", Neon.Lime) { s.devAddCoins(100) }
                    ModBtn("+1000", Neon.Lime) { s.devAddCoins(1000) }
                    ModBtn("+10000", Neon.Lime) { s.devAddCoins(10000) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModBtn("-100", Neon.Pink) { s.devAddCoins(-100) }
                    ModBtn("-1000", Neon.Pink) { s.devAddCoins(-1000) }
                    ModBtn("COINS 0", Neon.Pink) { s.devSetCoins(0) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModBtn("MAX POWERS", Neon.Lime) { s.devSetAllPowers(5) }
                    ModBtn("ALL SKINS", Neon.Lime) { s.devUnlockAllSkins() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModBtn("FINISH QUESTS", Neon.Cyan) { s.devQuests(true) }
                    ModBtn("REBIRTH +1", Neon.Cyan) { s.devSetRebirths(s.rebirths + 1) }
                }
                ModToggle("No power cooldowns", s.devNoCooldown) { s.devSetNoCooldown(it) }
                ModToggle("God mode", s.devGod) { s.devSetGod(it) }
                ModToggle("Start big", s.devBigStart) { s.devSetBigStart(it) }
                Text("HIDE puts the button away. Enter the code TEST on the Redeem screen to bring it back.", color = Neon.Dim, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun ModBtn(label: String, accent: Color, onClick: () -> Unit) {
    Text(
        label,
        color = accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

@Composable
private fun ModToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Neon.Text, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

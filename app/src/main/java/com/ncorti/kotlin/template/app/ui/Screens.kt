package com.ncorti.kotlin.template.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncorti.kotlin.template.app.AppState
import com.ncorti.kotlin.template.app.audio.Sfx
import com.ncorti.kotlin.template.app.game.Skin
import com.ncorti.kotlin.template.app.game.Skins
import kotlin.math.sin

// ------------------------------------------------------------------ settings

@Composable
fun SettingsScreen(app: AppState) {
    val s = app.settings
    ScreenFrame("Settings", onBack = { app.back() }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SwitchRow("Sound effects", s.sound) { app.setSound(it) }
                SwitchRow("Music", s.music) { app.setMusic(it) }
                SwitchRow("Vibration", s.vibration) { s.updateVibration(it) }
                SwitchRow("Deadly arena walls", s.walls) { s.updateWalls(it) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card {
                    Text("Control sensitivity  ${"%.1f".format(s.sensitivity)}x", color = Neon.Text, fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = s.sensitivity,
                        onValueChange = { s.updateSensitivity(it) },
                        valueRange = 0.5f..2f,
                    )
                    Text("How quickly your snake turns toward where you steer.", color = Neon.Dim, fontSize = 12.sp)
                }
                ChipsRow("Controls", listOf("Joystick", "Touch & drag"), s.controlMode) { s.updateControlMode(it) }
                ChipsRow("Graphics quality", listOf("Low", "Medium", "High"), s.quality) { s.updateQuality(it) }
            }
        }
        Text(
            "Walls apply to new games. Everything is stored on this device only.",
            color = Neon.Dim,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Neon.Surface)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = Neon.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun ChipsRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Card {
        Text(label, color = Neon.Text, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { i, name ->
                FilterChip(
                    selected = selected == i,
                    onClick = { onSelect(i) },
                    label = { Text(name) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Neon.Violet,
                        selectedLabelColor = Color(0xFF14002B),
                    ),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ skins

@Composable
fun SkinsScreen(app: AppState) {
    val s = app.settings
    ScreenFrame("Skins", onBack = { app.back() }) {
        Text(
            "Coins: ${s.coins}   ·   Earn 1 coin for every 4 points you score.",
            color = Neon.Cyan,
            fontWeight = FontWeight.SemiBold,
        )
        // A non-lazy grid inside the scrolling frame: eight fixed cards.
        // Landscape: four cards across.
        Skins.all.chunked(4).forEach { group ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                group.forEach { skin -> SkinCard(app, skin, Modifier.weight(1f)) }
                repeat(4 - group.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SkinCard(app: AppState, skin: Skin, modifier: Modifier) {
    val s = app.settings
    val audio = LocalAudio.current
    val owned = skin.id in s.unlocked
    val selected = s.selectedSkin == skin.id
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Neon.Surface)
            .border(2.dp, if (selected) Neon.Cyan else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SkinPreview(skin, Modifier.fillMaxWidth().height(52.dp))
        Text(skin.name, color = Neon.Text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        when {
            selected -> Text("SELECTED", color = Neon.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Black)
            owned -> NeonButton("Select", { s.selectSkin(skin.id) }, Modifier.fillMaxWidth().heightIn(min = 40.dp), primary = false)
            else -> NeonButton(
                "Unlock · ${skin.cost}",
                { if (s.unlockSkin(skin.id)) audio.play(Sfx.COIN) else audio.play(Sfx.DENIED) },
                Modifier.fillMaxWidth().heightIn(min = 40.dp),
                primary = s.coins >= skin.cost,
                accent = Neon.Pink,
            )
        }
    }
}

@Composable
fun SkinPreview(skin: Skin, modifier: Modifier = Modifier, phase: Float = 0f) {
    Canvas(modifier) {
        val segs = 26
        val r = size.height * 0.2f
        for (i in segs - 1 downTo 0) {
            val t = i.toFloat() / (segs - 1)
            val x = size.width * (0.88f - 0.76f * t)
            val y = size.height / 2f + sin(t * 9f + phase) * size.height * 0.22f
            val col = if (skin.rainbow) Color.hsv((t * 320f) % 360f, 0.75f, 1f) else mix(Color(skin.head), Color(skin.tail), t)
            if (skin.glow) drawCircle(col.copy(alpha = 0.2f), r * 1.8f, Offset(x, y))
            drawCircle(col, r * (1f - 0.35f * t), Offset(x, y))
        }
    }
}

// ------------------------------------------------------------------ how to play

@Composable
fun HowToScreen(app: AppState) {
    ScreenFrame("How to Play", onBack = { app.back() }) {
        val tips = listOf(
            "STEER" to "Drag the on-screen joystick (bottom left): your snake turns the way you push it. In Settings you can switch to touch-and-drag, where the snake turns toward your finger anywhere on screen. On a keyboard use A/D or the arrow keys; with a mouse, just point.",
            "EAT" to "Glowing food makes you grow and raises your score. Bigger snakes turn a little slower.",
            "BOOST" to "Hold the BOOST button (or Space) to speed up. Boost uses its own energy ring and never costs you length or pellets. The ring refills when you let go. Upgrade the Boost Tank in the Shop for a longer boost.",
            "SURVIVE" to "Crash your head into another snake's body, or into the arena wall if walls are deadly, and you're out. Head-to-head, the lighter snake loses.",
            "CUT THEM OFF" to "Eliminated snakes explode into food. Circle a rival, let them hit you, and collect the feast.",
            "PLAY & SOLO" to "Play drops you into an arena with 14 offline bots of three difficulty levels. Solo Mode is the same arena with no opponents: grow in peace.",
            "SHOP" to "Spend coins on powers: Shield (6s invincible), Magnet (wide pickup range), Overdrive (free boost) and Head Start. Tap a power's round button at the bottom left during a run to use it.",
            "COINS" to "Every run earns coins (1 per 4 points). Spend them on skins and powers. No real-money purchases, no accounts, no ads: everything stays on your device.",
        )
        tips.forEach { (title, body) ->
            Card {
                Text(title, color = Neon.Cyan, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Text(body, color = Neon.Text, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.padding(4.dp))
    }
}

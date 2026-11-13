package com.ncorti.kotlin.template.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncorti.kotlin.template.app.AppState
import com.ncorti.kotlin.template.app.audio.Sfx
import com.ncorti.kotlin.template.app.game.Power
import com.ncorti.kotlin.template.app.game.Powers

@Composable
fun ShopScreen(app: AppState) {
    val s = app.settings
    ScreenFrame("Shop", onBack = { app.back() }) {
        Text(
            "Coins: ${s.coins}   ·   Earn 1 coin for every 4 points you score.",
            color = Neon.Cyan,
            fontWeight = FontWeight.SemiBold,
        )
        Text("PERMANENT UPGRADE", color = Neon.Dim, fontSize = 11.sp, letterSpacing = 1.sp)
        TankCard(app)
        Text("POWERS  ·  unlock once, use them every run, upgrade for longer use and shorter cooldown", color = Neon.Dim, fontSize = 11.sp, letterSpacing = 1.sp)
        Powers.all.forEach { PowerCard(app, it) }
    }
}

@Composable
private fun ShopCard(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Neon.Surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun TankCard(app: AppState) {
    val s = app.settings
    val level = s.tankLevel
    val audio = LocalAudio.current
    ShopCard {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ico(Ico.BOLT, 20.dp, Neon.Lime)
                Text("Boost Tank", color = Neon.Text, fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
            Text(
                "Boost runs on energy, never on your length. A bigger tank means a longer boost.",
                color = Neon.Dim,
                fontSize = 12.sp,
            )
            Text(
                "Level $level/${Powers.TANK_MAX}  ·  capacity ${Powers.tankCapacity(level).toInt()}",
                color = Neon.Cyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (level >= Powers.TANK_MAX) {
            Text("MAX", color = Neon.Lime, fontWeight = FontWeight.Black)
        } else {
            val cost = Powers.tankCost(level)
            NeonButton(
                "Upgrade · $cost",
                { if (s.upgradeTank()) audio.play(Sfx.COIN) else audio.play(Sfx.DENIED) },
                Modifier.width(190.dp),
                primary = s.coins >= cost,
                accent = Neon.Pink,
            )
        }
    }
}

@Composable
private fun PowerCard(app: AppState, power: Power) {
    val s = app.settings
    val audio = LocalAudio.current
    val level = s.powerLevel(power.id)
    val shown = level.coerceAtLeast(1)
    val maxed = level >= Powers.MAX_LEVEL
    ShopCard {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ico(if (level > 0) powerIcon(power.id) else Ico.LOCK, 20.dp, if (level > 0) Neon.Cyan else Neon.Dim)
                Text(power.name, color = Neon.Text, fontWeight = FontWeight.Black, fontSize = 17.sp)
                if (level > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(Powers.MAX_LEVEL) { i ->
                            Box(
                                Modifier
                                    .size(width = 14.dp, height = 6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (i < level) Neon.Lime else Neon.Dim.copy(alpha = 0.3f)),
                            )
                        }
                    }
                }
            }
            Text(power.desc, color = Neon.Dim, fontSize = 12.sp)
            val dur = power.duration(shown)
            val cd = power.cooldown(shown)
            val now = (if (dur > 0f) "Lasts ${dur.toInt()}s  ·  " else "Instant  ·  ") + "cooldown ${cd.toInt()}s"
            Text(
                if (level == 0) "Level 1: $now" else "Level $level/${Powers.MAX_LEVEL}: $now",
                color = Neon.Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            )
            if (level in 1 until Powers.MAX_LEVEL) {
                val nd = power.duration(level + 1)
                val nc = power.cooldown(level + 1)
                Text(
                    "Next: " + (if (nd > 0f) "${nd.toInt()}s use  ·  " else "") + "${nc.toInt()}s cooldown",
                    color = Neon.Lime, fontSize = 11.sp,
                )
            }
        }
        if (maxed) {
            Text("MAX", color = Neon.Lime, fontWeight = FontWeight.Black, modifier = Modifier.width(160.dp))
        } else {
            val cost = power.costFor(level)
            NeonButton(
                (if (level == 0) "Unlock" else "Upgrade") + " · $cost",
                { if (s.upgradePower(power)) audio.play(Sfx.COIN) else audio.play(Sfx.DENIED) },
                Modifier.width(160.dp),
                primary = s.coins >= cost,
                accent = Neon.Pink,
            )
        }
    }
}

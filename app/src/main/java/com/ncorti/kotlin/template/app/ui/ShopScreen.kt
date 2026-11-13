package com.ncorti.kotlin.template.app.ui

import androidx.compose.foundation.background
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
        Text("POWERS  ·  use them during a run", color = Neon.Dim, fontSize = 11.sp, letterSpacing = 1.sp)
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
    ShopCard {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Ico(powerIcon(power.id), 20.dp, Neon.Cyan)
                Text(power.name, color = Neon.Text, fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
            Text(power.desc, color = Neon.Dim, fontSize = 12.sp)
            Text("Owned: ${s.count(power.id)}", color = Neon.Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        NeonButton(
            "Buy · ${power.cost}",
            { if (s.buyPower(power)) audio.play(Sfx.COIN) else audio.play(Sfx.DENIED) },
            Modifier.width(160.dp),
            primary = s.coins >= power.cost,
            accent = Neon.Pink,
        )
    }
}

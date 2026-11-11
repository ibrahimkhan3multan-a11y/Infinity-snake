package com.ncorti.kotlin.template.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncorti.kotlin.template.app.AppState
import com.ncorti.kotlin.template.app.Screen

@Composable
fun MenuScreen(app: AppState) {
    val s = app.settings
    BoxWithConstraints(Modifier.fillMaxSize().background(BackgroundBrush)) {
        val landscape = maxWidth > maxHeight
        val stats: @Composable () -> Unit = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("HIGH SCORE", s.highScore.toString(), Modifier.weight(1f))
                StatCard("BEST RANK", if (s.bestRank == 0) "—" else "#${s.bestRank}", Modifier.weight(1f))
                StatCard("COINS", s.coins.toString(), Modifier.weight(1f))
            }
        }
        val buttons: @Composable () -> Unit = {
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                NeonButton("PLAY", { app.startGame(solo = false) }, Modifier.fillMaxWidth())
                NeonButton("SOLO MODE", { app.startGame(solo = true) }, Modifier.fillMaxWidth(), accent = Neon.Cyan)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NeonButton("Skins", { app.open(Screen.SKINS) }, Modifier.weight(1f), primary = false)
                    NeonButton("Settings", { app.open(Screen.SETTINGS) }, Modifier.weight(1f), primary = false)
                }
                NeonButton("How to Play", { app.open(Screen.HOWTO) }, Modifier.fillMaxWidth(), primary = false)
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp),
        ) {
            if (landscape) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LogoBlock(Modifier.widthIn(max = 360.dp), titleSize = 30.sp)
                        stats()
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { buttons() }
                }
            } else {
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
                ) {
                    LogoBlock(Modifier.widthIn(max = 420.dp))
                    stats()
                    buttons()
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Neon.Surface)
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Neon.Cyan)
        Text(label, fontSize = 10.sp, color = Neon.Dim, letterSpacing = 1.sp)
    }
}

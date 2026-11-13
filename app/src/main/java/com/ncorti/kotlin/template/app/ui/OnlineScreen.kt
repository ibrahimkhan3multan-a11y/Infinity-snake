package com.ncorti.kotlin.template.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncorti.kotlin.template.app.AppState
import com.ncorti.kotlin.template.app.game.Cfg
import kotlinx.coroutines.delay

/** Play with friends (room code) or strangers (quick match) through a relay server. */
@Composable
fun OnlineScreen(app: AppState) {
    val s = app.settings
    var code by remember { mutableStateOf("") }
    var withBots by remember { mutableStateOf(true) }

    LaunchedEffect(app.onlineBusy) {
        while (app.onlineBusy) {
            app.pollOnline()
            delay(50)
        }
    }
    DisposableEffect(Unit) { onDispose { app.cancelOnline() } }

    ScreenFrame("Play Online", onBack = { app.cancelOnline(); app.back() }) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = s.serverUrl,
                onValueChange = { s.updateServerUrl(it) },
                singleLine = true,
                label = { Text("Server address") },
                placeholder = { Text("my-relay.onrender.com or 192.168.1.20:8080") },
                colors = fieldColors(),
                modifier = Modifier.weight(1f),
            )
            Text("Bots", color = Neon.Text, fontWeight = FontWeight.SemiBold)
            Switch(checked = withBots, onCheckedChange = { withBots = it })
        }
        Text(
            app.onlineStatus.ifEmpty { "Rooms run on the host's phone; the server only passes codes and messages." },
            color = when {
                app.onlineBusy -> Neon.Cyan
                app.onlineStatus.isNotEmpty() -> Neon.Pink
                else -> Neon.Dim
            },
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Panel(Modifier.weight(1f)) {
                Text("PLAY WITH FRIENDS", color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text("Private room. Share the 4-letter code.", color = Neon.Dim, fontSize = 12.sp)
                NeonButton(
                    "CREATE ROOM",
                    { app.connectOnline("host", bots = if (withBots) Cfg.ONLINE_BOTS else 0) },
                    Modifier.fillMaxWidth().height(48.dp),
                    accent = Neon.Lime,
                )
            }
            Panel(Modifier.weight(1f)) {
                Text("JOIN A ROOM", color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter { c -> c.isLetterOrDigit() }.take(4).uppercase() },
                    singleLine = true,
                    placeholder = { Text("CODE") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                NeonButton(
                    "JOIN",
                    { app.connectOnline("join", code = code) },
                    Modifier.fillMaxWidth().height(48.dp),
                    primary = code.length == 4,
                )
            }
            Panel(Modifier.weight(1f)) {
                Text("PLAY WITH ANYONE", color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text("Open public room, or start one.", color = Neon.Dim, fontSize = 12.sp)
                NeonButton(
                    "QUICK MATCH",
                    { app.connectOnline("quick", bots = if (withBots) Cfg.ONLINE_BOTS else 0) },
                    Modifier.fillMaxWidth().height(48.dp),
                    accent = Neon.Pink,
                )
            }
        }
        Text(
            "Online is the only part of the game that uses the internet. No server yet? See server/README.md in the project.",
            color = Neon.Dim,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Neon.Text,
    unfocusedTextColor = Neon.Text,
    cursorColor = Neon.Cyan,
    focusedBorderColor = Neon.Cyan,
    unfocusedBorderColor = Neon.Violet,
    focusedPlaceholderColor = Neon.Dim,
    unfocusedPlaceholderColor = Neon.Dim,
)

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Neon.Surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

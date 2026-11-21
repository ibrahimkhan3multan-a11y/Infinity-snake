package com.ncorti.kotlin.template.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.ncorti.kotlin.template.app.audio.Sfx
import com.ncorti.kotlin.template.app.data.Settings
import com.ncorti.kotlin.template.app.game.Codes
import com.ncorti.kotlin.template.app.game.Quest
import com.ncorti.kotlin.template.app.game.Quests

@Composable
private fun XPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Neon.Surface)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

// ------------------------------------------------------------------ daily quests

@Composable
fun QuestsScreen(app: AppState) {
    val s = app.settings
    var message by remember { mutableStateOf("") }
    ScreenFrame("Daily Quests", onBack = { app.back() }) {
        Text(
            message.ifEmpty { "Three new quests every day. Finish them all for a +${Quests.ALL_DONE_BONUS} coin bonus." },
            color = if (message.isEmpty()) Neon.Dim else Neon.Lime,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        s.dailyQuests().forEach { q -> QuestRow(app, q) { message = it } }
        if (s.allQuestsClaimed()) {
            Text("All done for today. New quests tomorrow!", color = Neon.Lime, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun QuestRow(app: AppState, q: Quest, say: (String) -> Unit) {
    val s = app.settings
    val progress = s.questProgressOf(q)
    val done = s.questDone(q)
    val claimed = s.questClaimed(q)
    XPanel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(q.title.uppercase(), color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                Text(q.desc, color = Neon.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color0x55),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((progress.toFloat() / q.goal).coerceIn(0f, 1f))
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (done) Neon.Lime else Neon.Cyan),
                    )
                }
                Text("$progress / ${q.goal}   ·   Reward ${q.reward} coins", color = Neon.Dim, fontSize = 12.sp)
            }
            when {
                claimed -> Text("CLAIMED ✓", color = Neon.Lime, fontWeight = FontWeight.Black, fontSize = 13.sp)
                done -> NeonButton(
                    "CLAIM",
                    {
                        val got = s.claimQuest(q)
                        if (got > 0) {
                            app.audio.play(Sfx.FANFARE)
                            say("+$got coins!" + if (got > q.reward) " (all-quests bonus included)" else "")
                        }
                    },
                    Modifier.height(44.dp),
                    accent = Neon.Lime,
                )
                else -> Text("IN PROGRESS", color = Neon.Dim, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

private val Color0x55 = androidx.compose.ui.graphics.Color(0x55000000)

// ------------------------------------------------------------------ redeem codes

@Composable
private fun codeFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Neon.Text,
    unfocusedTextColor = Neon.Text,
    cursorColor = Neon.Cyan,
    focusedBorderColor = Neon.Cyan,
    unfocusedBorderColor = Neon.Violet,
    focusedLabelColor = Neon.Cyan,
    unfocusedLabelColor = Neon.Dim,
)

@Composable
fun CodesScreen(app: AppState) {
    val s = app.settings
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var good by remember { mutableStateOf(false) }
    ScreenFrame("Redeem Code", onBack = { app.back() }) {
        XPanel {
            Text("Got a code? Type it in to get coins, skins or powers.", color = Neon.Text, fontSize = 14.sp)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(24) },
                singleLine = true,
                label = { Text("Code") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                colors = codeFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            NeonButton(
                "REDEEM",
                {
                    when (val r = s.redeem(text)) {
                        is Settings.Redeem.Ok -> {
                            good = true
                            message = "Code accepted! You got ${r.text}."
                            app.audio.play(Sfx.FANFARE)
                            text = ""
                        }
                        Settings.Redeem.Used -> {
                            good = false
                            message = "You already used that code."
                        }
                        Settings.Redeem.Invalid -> {
                            good = false
                            message = "That code isn't valid."
                        }
                        Settings.Redeem.Secret -> {
                            text = ""
                            app.open(com.ncorti.kotlin.template.app.Screen.TESTER)
                        }
                    }
                },
                Modifier.fillMaxWidth().height(48.dp),
                primary = text.isNotBlank(),
                accent = Neon.Lime,
            )
            if (message.isNotEmpty()) {
                Text(message, color = if (good) Neon.Lime else Neon.Pink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        Text(
            "Each code works once. Codes you've used: ${s.redeemedNames.size} of ${Codes.count}.",
            color = Neon.Dim,
            fontSize = 12.sp,
        )
    }
}

// ------------------------------------------------------------------ secret tester menu

@Composable
fun TesterScreen(app: AppState) {
    val s = app.settings
    var note by remember { mutableStateOf("Tester menu. Everything here changes only this phone's saved data.") }
    fun act(msg: String, f: () -> Unit) {
        f()
        note = msg
    }
    ScreenFrame("Tester Menu", onBack = { app.open(com.ncorti.kotlin.template.app.Screen.SETTINGS) }) {
        Text(note, color = Neon.Cyan, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

        XPanel {
            Text("COINS  ·  now ${s.coins}", color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in listOf(100, 1000, 10000)) {
                    NeonButton("+$n", { act("Gave $n coins.") { s.devAddCoins(n) } }, Modifier.weight(1f).height(44.dp), accent = Neon.Lime)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in listOf(100, 1000)) {
                    NeonButton("-$n", { act("Took $n coins.") { s.devAddCoins(-n) } }, Modifier.weight(1f).height(44.dp), accent = Neon.Pink)
                }
                NeonButton("SET 0", { act("Coins set to 0.") { s.devSetCoins(0) } }, Modifier.weight(1f).height(44.dp), accent = Neon.Pink)
            }
        }

        XPanel {
            Text("POWERS AND SKINS", color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton("MAX ALL POWERS", { act("All powers at max level, tank maxed.") { s.devSetAllPowers(5) } }, Modifier.weight(1f).height(44.dp), accent = Neon.Lime)
                NeonButton("LOCK ALL POWERS", { act("All powers locked again.") { s.devSetAllPowers(0) } }, Modifier.weight(1f).height(44.dp), accent = Neon.Pink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton("UNLOCK ALL SKINS", { act("All skins unlocked.") { s.devUnlockAllSkins() } }, Modifier.weight(1f).height(44.dp), accent = Neon.Lime)
                NeonButton("LOCK SKINS", { act("Skins reset to the two free ones.") { s.devLockSkins() } }, Modifier.weight(1f).height(44.dp), accent = Neon.Pink)
            }
        }

        XPanel {
            Text("QUESTS, REWARDS AND CODES", color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton("FINISH QUESTS", { act("Today's quests are finished. Claim them on the Quests screen.") { s.devQuests(true) } }, Modifier.weight(1f).height(44.dp), accent = Neon.Lime)
                NeonButton("RESET QUESTS", { act("Today's quests are fresh again.") { s.devQuests(false) } }, Modifier.weight(1f).height(44.dp), accent = Neon.Pink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton("RESET DAILY REWARD", { act("The daily reward can be claimed again.") { s.devResetDailyReward() } }, Modifier.weight(1f).height(44.dp), accent = Neon.Pink)
                NeonButton("RESET CODES", { act("All codes can be redeemed again.") { s.devResetCodes() } }, Modifier.weight(1f).height(44.dp), accent = Neon.Pink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton("RESET CHALLENGES", { act("Menu challenges reset.") { s.devResetChallenges() } }, Modifier.weight(1f).height(44.dp), accent = Neon.Pink)
                NeonButton("HIGH SCORE 1000", { act("High score set to 1000.") { s.devSetHighScore(1000) } }, Modifier.weight(1f).height(44.dp), accent = Neon.Cyan)
            }
        }

        XPanel {
            Text("IN-GAME CHEATS (your own games)", color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
            ToggleRow("No power cooldowns", s.devNoCooldown) { s.devSetNoCooldown(it) }
            ToggleRow("God mode (always shielded)", s.devGod) { s.devSetGod(it) }
            ToggleRow("Start big (mass 400)", s.devBigStart) { s.devSetBigStart(it) }
            Text("Cheats apply to games you run yourself. In someone else's room the host decides.", color = Neon.Dim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Neon.Text, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

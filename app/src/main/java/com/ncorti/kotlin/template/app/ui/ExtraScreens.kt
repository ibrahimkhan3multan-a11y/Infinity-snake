package com.ncorti.kotlin.template.app.ui

import androidx.compose.foundation.background
import com.ncorti.kotlin.template.app.game.GameMode
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import com.ncorti.kotlin.template.app.game.Rebirth

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
            Text("REBIRTHS  ·  now ${s.rebirths}", color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton("+1 FREE", { act("Rebirth count raised (nothing lost).") { s.devSetRebirths(s.rebirths + 1) } }, Modifier.weight(1f).height(44.dp), accent = Neon.Lime)
                NeonButton("-1", { act("Rebirth count lowered.") { s.devSetRebirths(s.rebirths - 1) } }, Modifier.weight(1f).height(44.dp), accent = Neon.Pink)
                NeonButton("SET 0", { act("Rebirths reset to 0.") { s.devSetRebirths(0) } }, Modifier.weight(1f).height(44.dp), accent = Neon.Pink)
            }
        }

        XPanel {
            Text("IN-GAME CHEATS (your own games)", color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
            ToggleRow("Floating mod button", s.devButton) { s.devSetButton(it) }
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

// ------------------------------------------------------------------ rebirth

@Composable
fun RebirthScreen(app: AppState) {
    val s = app.settings
    var confirming by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val done = s.rebirths
    val cost = s.rebirthCost()
    ScreenFrame("Rebirth", onBack = { app.back() }) {
        XPanel {
            Text("YOUR REBIRTHS: $done of ${Rebirth.MAX}", color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text(
                "Coin bonus on every run: +${(done * 25)}%   ·   Start-of-life speed burst: " +
                    (if (done == 0) "none" else "${Rebirth.startBoost(done).toInt()} s"),
                color = Neon.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
        if (cost == null) {
            Text("You have reached the last rebirth. Nothing left to unlock!", color = Neon.Lime, fontWeight = FontWeight.Bold)
        } else {
            XPanel {
                Text("NEXT REBIRTH  ·  needs $cost coins", color = Neon.Pink, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Text("You gain: +${(done + 1) * 25}% coins and a ${Rebirth.startBoost(done + 1).toInt()} s speed burst at the start of every life.", color = Neon.Lime, fontSize = 13.sp)
                Text("You lose: ALL your coins and every skin you bought (you keep the two free skins).", color = Neon.Pink, fontSize = 13.sp)
                Text("You keep: powers, tank upgrades, scores, quests and codes.", color = Neon.Dim, fontSize = 13.sp)
                if (!confirming) {
                    NeonButton(
                        if (s.canRebirth()) "REBIRTH" else "NEED ${cost - s.coins} MORE COINS  (you have ${s.coins})",
                        { if (s.canRebirth()) confirming = true },
                        Modifier.fillMaxWidth().height(48.dp),
                        primary = s.canRebirth(),
                        accent = Neon.Pink,
                    )
                } else {
                    Text("Are you sure? This cannot be undone.", color = Neon.Pink, fontWeight = FontWeight.Black)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NeonButton(
                            "YES, REBIRTH",
                            {
                                if (s.rebirth()) {
                                    app.audio.play(Sfx.FANFARE)
                                    message = "Reborn! Rebirth ${s.rebirths} unlocked."
                                }
                                confirming = false
                            },
                            Modifier.weight(1f).height(48.dp),
                            accent = Neon.Pink,
                        )
                        NeonButton("CANCEL", { confirming = false }, Modifier.weight(1f).height(48.dp), primary = false)
                    }
                }
            }
        }
        if (message.isNotEmpty()) Text(message, color = Neon.Lime, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        XPanel {
            Text("ALL REBIRTH COSTS", color = Neon.Dim, fontSize = 11.sp, letterSpacing = 1.sp)
            Text(
                Rebirth.costs.mapIndexed { i, c -> if (i < done) "$c ✓" else "$c" }.joinToString("  ·  "),
                color = Neon.Text,
                fontSize = 13.sp,
            )
        }
    }
}

// ------------------------------------------------------------------ backup and restore

@Composable
fun BackupScreen(app: AppState) {
    val s = app.settings
    val context = androidx.compose.ui.platform.LocalContext.current
    var code by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var good by remember { mutableStateOf(false) }
    var restored by remember { mutableStateOf(false) }
    var mine by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(restored) {
        if (restored) {
            kotlinx.coroutines.delay(1800)
            // The saved data was replaced, so start fresh from it.
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    ScreenFrame("Backup & Restore", onBack = { app.back() }) {
        XPanel {
            Text("BACK UP YOUR PROGRESS", color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text(
                "Makes one line of text with your coins, skins, powers, scores and quests. Keep it somewhere safe " +
                    "(send it to yourself), then paste it into Restore after a reinstall or on a new phone.",
                color = Neon.Text, fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton(
                    "COPY BACKUP",
                    {
                        mine = s.exportBackup()
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Infinity Snake backup", mine))
                        good = true
                        message = "Backup copied. Paste it somewhere safe."
                    },
                    Modifier.weight(1f).height(46.dp),
                    accent = Neon.Lime,
                )
                NeonButton(
                    "SHARE BACKUP",
                    {
                        mine = s.exportBackup()
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, mine)
                        }
                        context.startActivity(android.content.Intent.createChooser(send, "Share backup").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    Modifier.weight(1f).height(46.dp),
                    accent = Neon.Cyan,
                )
            }
            if (mine.isNotEmpty()) {
                Text("Your backup (${mine.length} characters):", color = Neon.Dim, fontSize = 11.sp)
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(mine.take(90) + "…", color = Neon.Text, fontSize = 11.sp)
                }
            }
        }
        XPanel {
            Text("RESTORE", color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
            Text("Paste a backup here. This replaces everything saved on this phone.", color = Neon.Text, fontSize = 13.sp)
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                singleLine = true,
                label = { Text("Backup text") },
                colors = codeFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            NeonButton(
                "RESTORE",
                {
                    if (s.importBackup(code)) {
                        good = true
                        message = "Restored! The app will close. Open it again to continue."
                        restored = true
                    } else {
                        good = false
                        message = "That isn't a valid backup."
                    }
                },
                Modifier.fillMaxWidth().height(46.dp),
                primary = code.isNotBlank(),
                accent = Neon.Pink,
            )
        }
        if (message.isNotEmpty()) {
            Text(message, color = if (good) Neon.Lime else Neon.Pink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

// ------------------------------------------------------------------ game modes

/** Small mode picker used where space is tight (hosting a room). */
@Composable
fun ModeChips(app: AppState) {
    val s = app.settings
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until GameMode.COUNT step 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (m in row until minOf(row + 2, GameMode.COUNT)) {
                    androidx.compose.material3.FilterChip(
                        selected = s.gameMode == m,
                        onClick = { s.updateGameMode(m) },
                        label = { Text(GameMode.short(m), fontSize = 11.sp) },
                    )
                }
            }
        }
    }
}

@Composable
fun ModesScreen(app: AppState) {
    val s = app.settings
    ScreenFrame("Game Modes", onBack = { app.back() }) {
        Text(
            "Pick how the next match plays. Hosting on Wi-Fi? Everyone who joins plays the mode you pick.",
            color = Neon.Dim, fontSize = 13.sp,
        )
        for (m in 0 until GameMode.COUNT) {
            val on = s.gameMode == m
            XPanel(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(2.dp, if (on) Neon.Lime else Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { s.updateGameMode(m); app.audio.play(Sfx.CLICK) },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(GameMode.name(m).uppercase(), color = Neon.Text, fontWeight = FontWeight.Black, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    if (on) Text("SELECTED", color = Neon.Lime, fontWeight = FontWeight.Black, fontSize = 11.sp)
                }
                Text(GameMode.blurb(m), color = Neon.Dim, fontSize = 12.sp)
            }
        }
    }
}

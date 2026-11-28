package com.ncorti.kotlin.template.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.ncorti.kotlin.template.app.Screen
import com.ncorti.kotlin.template.app.audio.Sfx
import com.ncorti.kotlin.template.app.game.Skin
import com.ncorti.kotlin.template.app.game.SkinLab

/** Describe a snake in a few words, preview it, and buy it as your own skin. Everything runs on the phone. */
@Composable
fun SkinLabScreen(app: AppState) {
    val s = app.settings
    val audio = LocalAudio.current
    var prompt by remember { mutableStateOf("") }
    var variation by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    val look = if (prompt.isBlank()) null else SkinLab.generate(prompt, variation)

    ScreenFrame("Skin Lab", onBack = { app.open(Screen.SKINS) }) {
        Text(
            "Coins: ${s.coins}   ·   Your own skin costs ${SkinLab.COST} coins.",
            color = Neon.Cyan, fontWeight = FontWeight.SemiBold,
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Neon.Surface).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Describe your snake, for example “fire dragon”, “ocean shark”, “pink candy” or “golden king”. " +
                    "The Skin Lab designs the colours on your phone: it works offline and is not a cloud AI.",
                color = Neon.Text, fontSize = 13.sp,
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it.take(40); variation = 0; message = "" },
                singleLine = true,
                label = { Text("What should it look like?") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Neon.Text, unfocusedTextColor = Neon.Text, cursorColor = Neon.Cyan,
                    focusedBorderColor = Neon.Cyan, unfocusedBorderColor = Neon.Violet,
                    focusedLabelColor = Neon.Cyan, unfocusedLabelColor = Neon.Dim,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (look != null) {
                SkinPreview(Skin(-1, look.name, 0, look.head, look.tail, look.glow, look.rainbow), Modifier.fillMaxWidth().height(70.dp))
                Text(look.name, color = Neon.Text, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NeonButton(
                        "TRY ANOTHER",
                        { variation += 1; message = ""; audio.play(Sfx.CLICK) },
                        Modifier.weight(1f).height(46.dp),
                        primary = false,
                    )
                    val full = s.customSkins.size >= SkinLab.MAX_CUSTOM
                    NeonButton(
                        if (full) "SLOTS FULL" else "BUY · ${SkinLab.COST}",
                        {
                            if (s.createCustomSkin(look)) {
                                audio.play(Sfx.FANFARE)
                                message = "${look.name} is yours and equipped!"
                                prompt = ""
                                variation = 0
                            } else {
                                audio.play(Sfx.DENIED)
                                message = if (full) "Delete one of your skins below to free a slot." else "You need ${SkinLab.COST} coins."
                            }
                        },
                        Modifier.weight(1f).height(46.dp),
                        primary = !full && s.coins >= SkinLab.COST,
                        accent = Neon.Pink,
                    )
                }
            }
            if (message.isNotEmpty()) Text(message, color = Neon.Lime, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text("YOUR SKINS  ·  ${s.customSkins.size} of ${SkinLab.MAX_CUSTOM}", color = Neon.Dim, fontSize = 11.sp, letterSpacing = 1.sp)
        if (s.customSkins.isEmpty()) {
            Text("Nothing yet. Type a few words above.", color = Neon.Dim, fontSize = 13.sp)
        }
        s.customSkins.forEach { skin ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Neon.Surface).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SkinPreview(skin, Modifier.width(120.dp).height(48.dp))
                Text(skin.name, color = Neon.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                if (s.selectedSkin == skin.id) {
                    Text("SELECTED", color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 12.sp)
                } else {
                    NeonButton("Select", { s.selectSkin(skin.id) }, Modifier.width(120.dp), primary = false)
                }
                NeonButton("Delete", { s.deleteCustomSkin(skin.id) }, Modifier.width(120.dp), primary = false, accent = Neon.Pink)
            }
        }
        Text("Deleting a skin does not refund its coins. Your own skins are kept when you rebirth.", color = Neon.Dim, fontSize = 11.sp)
    }
}

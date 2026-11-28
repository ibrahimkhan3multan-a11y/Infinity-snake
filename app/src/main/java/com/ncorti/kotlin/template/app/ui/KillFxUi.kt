package com.ncorti.kotlin.template.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncorti.kotlin.template.app.AppState
import com.ncorti.kotlin.template.app.audio.Sfx
import com.ncorti.kotlin.template.app.game.KillFx
import com.ncorti.kotlin.template.app.game.KillFxs
import kotlin.math.cos
import kotlin.math.sin

/** Shop section: buy and equip kill animations. */
@Composable
fun KillFxSection(app: AppState) {
    val s = app.settings
    val audio = LocalAudio.current
    Text(
        "KILL ANIMATIONS  ·  play when you eliminate a snake, and some give coins, boost or speed on every kill",
        color = Neon.Dim, fontSize = 11.sp, letterSpacing = 1.sp,
    )
    KillFxs.all.forEach { fx ->
        val owned = fx.id in s.killFxOwned
        val equipped = s.killFxSelected == fx.id
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Neon.Surface)
                .border(2.dp, if (equipped) Neon.Cyan else Color.Transparent, RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KillFxPreview(fx, Modifier.size(64.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(fx.name, color = Neon.Text, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text(fx.desc, color = Neon.Dim, fontSize = 12.sp)
                Text(fx.perkText, color = Neon.Lime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            when {
                equipped -> Text("EQUIPPED", color = Neon.Cyan, fontWeight = FontWeight.Black, fontSize = 12.sp)
                owned -> NeonButton("Equip", { s.selectKillFx(fx.id); audio.play(Sfx.CLICK) }, Modifier.width(120.dp), primary = false)
                else -> NeonButton(
                    "Unlock · ${fx.cost}",
                    { if (s.buyKillFx(fx.id)) audio.play(Sfx.COIN) else audio.play(Sfx.DENIED) },
                    Modifier.width(150.dp),
                    primary = s.coins >= fx.cost,
                    accent = Neon.Pink,
                )
            }
        }
    }
}

/** A looping miniature of a kill animation. */
@Composable
fun KillFxPreview(fx: KillFx, modifier: Modifier = Modifier) {
    val t by rememberInfiniteTransition(label = "fx").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF0E0A1F))) { drawKillFx(fx, t) }
}

private val previewRainbow = listOf(
    Color(0xFFFF5252), Color(0xFFFFAB40), Color(0xFFFFEE58), Color(0xFF69F0AE), Color(0xFF40C4FF), Color(0xFF7C4DFF), Color(0xFFFF4081),
)

private fun DrawScope.drawKillFx(fx: KillFx, t: Float) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val R = size.minDimension / 2f
    val a = Color(fx.color)
    val b = Color(fx.color2)
    val fade = 1f - t

    fun ring(k: Float, col: Color, sz: Float = 1f) {
        drawCircle(col.copy(alpha = (1f - k) * 0.9f), R * sz * k, c, style = Stroke(width = 1.5f + 5f * (1f - k)))
    }
    fun dots(count: Int, spread: Float, col: (Int) -> Color, radius: Float = 3f, spin: Float = 0f) {
        for (i in 0 until count) {
            val ang = i * 6.2831855f / count + spin * t
            val d = R * spread * t
            drawCircle(col(i).copy(alpha = fade), radius * (0.6f + 0.6f * fade), Offset(c.x + cos(ang) * d, c.y + sin(ang) * d))
        }
    }

    when (fx.kind) {
        0 -> dots(12, 0.85f, { Color.White })
        1 -> dots(21, 0.9f, { previewRainbow[it % 7] })
        2 -> { ring(t, a); ring((t * 1.0f - 0.2f).coerceIn(0f, 1f), b, 0.8f); dots(8, 0.7f, { a }, 2.5f) }
        3 -> {
            var px = c.x - R * 0.8f
            var py = c.y - R * 0.7f
            for (k in 1..6) {
                val u = k / 6f
                val qx = c.x - R * 0.8f + (R * 1.6f) * u
                val qy = c.y - R * 0.7f + (R * 1.4f) * u + (if (k % 2 == 0) -R * 0.25f else R * 0.25f)
                drawLine(Color.White.copy(alpha = fade), Offset(px, py), Offset(qx, qy), 3f)
                px = qx
                py = qy
            }
            dots(8, 0.6f, { a }, 2.5f)
        }
        4 -> dots(16, 0.9f, { if (it % 2 == 0) a else b }, 4f)
        5 -> { ring(t, Color.White, 1f); ring((t - 0.15f).coerceIn(0f, 1f), b, 0.8f); dots(16, 0.95f, { if (it % 2 == 0) Color.White else b }, 3f) }
        6 -> {
            for (i in 0 until 14) {
                val ang = i * 6.2831855f / 14
                val d = R * 0.9f * (1f - t)
                drawCircle((if (i % 2 == 0) a else b).copy(alpha = 0.3f + 0.7f * t), 3f, Offset(c.x + cos(ang) * d, c.y + sin(ang) * d))
            }
            drawCircle(Color.Black, R * 0.22f, c)
            drawCircle(a.copy(alpha = 0.8f), R * 0.22f, c, style = Stroke(2f))
        }
        7 -> {
            ring(t, b, 0.9f)
            for (i in 0 until 12) {
                val x = c.x + (i - 5.5f) * R * 0.12f
                val y = c.y + R * 0.4f - R * 1.1f * ((t + i * 0.07f) % 1f)
                drawCircle((if (i % 2 == 0) a else b).copy(alpha = fade), 3.5f, Offset(x, y))
            }
        }
        8 -> {
            for (i in 0 until 26) {
                val ang = i * 0.55f + t * 2f
                val d = R * 0.9f * t * (i / 26f + 0.2f)
                drawCircle((if (i % 2 == 0) a else b).copy(alpha = fade), 2.5f, Offset(c.x + cos(ang) * d, c.y + sin(ang) * d))
            }
        }
        else -> { dots(21, 0.95f, { previewRainbow[it % 7] }, 3f); ring(t, previewRainbow[0]); ring((t - 0.2f).coerceIn(0f, 1f), previewRainbow[4], 0.8f) }
    }
}

package com.ncorti.kotlin.template.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.material3.Text
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ncorti.kotlin.template.app.game.TWO_PI
import kotlin.math.cos
import kotlin.math.sin

/** The logo: a snake gliding along an infinity symbol (a lemniscate), plus the wordmark. */
@Composable
fun LogoBlock(modifier: Modifier = Modifier, titleSize: TextUnit = 40.sp) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        InfinityLoop(Modifier.fillMaxWidth().aspectRatio(2.4f))
        val glow = TextStyle(shadow = Shadow(Neon.Violet, blurRadius = 28f))
        Text("INFINITY", fontSize = titleSize, fontWeight = FontWeight.Black, color = Neon.Text, letterSpacing = 6.sp, style = glow)
        Text("SNAKE", fontSize = titleSize, fontWeight = FontWeight.Black, color = Neon.Cyan, letterSpacing = 14.sp, style = TextStyle(shadow = Shadow(Neon.Cyan, blurRadius = 28f)))
    }
}

@Composable
fun InfinityLoop(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "logo")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    Canvas(modifier) {
        val segs = 72
        val a = size.width * 0.40f
        val cx = size.width / 2f
        val cy = size.height / 2f
        for (k in segs - 1 downTo 0) {
            val t = phase - k * 0.07f
            val s = sin(t)
            val den = 1f + s * s
            val x = cx + a * cos(t) / den
            val y = cy + a * 1.15f * sin(t) * cos(t) / den
            val f = k / (segs - 1f)
            val col = mix(Neon.Cyan, Neon.Violet, f)
            val r = size.height * (0.075f - 0.035f * f)
            drawCircle(col.copy(alpha = 0.18f), r * 1.9f, Offset(x, y))
            drawCircle(col, r, Offset(x, y))
            if (k == 0) {
                drawCircle(Color.White, r * 0.35f, Offset(x + r * 0.3f, y - r * 0.35f))
                drawCircle(Color.White, r * 0.35f, Offset(x + r * 0.3f, y + r * 0.35f))
            }
        }
    }
}

fun mix(a: Color, b: Color, t: Float): Color = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    1f,
)

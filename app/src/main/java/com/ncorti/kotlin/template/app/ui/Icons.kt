package com.ncorti.kotlin.template.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.cos
import kotlin.math.sin

enum class Ico { SHIELD, MAGNET, BOLT, COIN, BAG, SNAKE, PLAY, CROWN, TROPHY, TARGET, CHEVRONS, STAR, SNOW, REFILL, LOCK }

/** Small vector icons drawn with Canvas, so the whole UI shares one crisp, tintable icon set. */
@Composable
fun Ico(kind: Ico, size: Dp, tint: Color = Color.White, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { drawIco(kind, tint) }
}

fun DrawScope.drawIco(kind: Ico, tint: Color) {
    val w = size.width
    val h = size.height
    val sw = w * 0.09f
    when (kind) {
        Ico.SHIELD -> {
            val p = Path().apply {
                moveTo(0.5f * w, 0.04f * h)
                lineTo(0.9f * w, 0.18f * h)
                lineTo(0.9f * w, 0.5f * h)
                cubicTo(0.9f * w, 0.75f * h, 0.7f * w, 0.9f * h, 0.5f * w, 0.97f * h)
                cubicTo(0.3f * w, 0.9f * h, 0.1f * w, 0.75f * h, 0.1f * w, 0.5f * h)
                lineTo(0.1f * w, 0.18f * h)
                close()
            }
            drawPath(p, tint.copy(alpha = 0.25f))
            drawPath(p, tint, style = Stroke(sw, join = StrokeJoin.Round))
            val tick = Path().apply {
                moveTo(0.32f * w, 0.5f * h)
                lineTo(0.45f * w, 0.64f * h)
                lineTo(0.70f * w, 0.35f * h)
            }
            drawPath(tick, tint, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Ico.MAGNET -> {
            val stroke = w * 0.22f
            drawArc(
                tint, 180f, 180f, false,
                topLeft = Offset(0.17f * w, 0.1f * h), size = Size(0.66f * w, 0.66f * h),
                style = Stroke(stroke),
            )
            drawLine(tint, Offset(0.17f * w, 0.43f * h), Offset(0.17f * w, 0.74f * h), stroke)
            drawLine(tint, Offset(0.83f * w, 0.43f * h), Offset(0.83f * w, 0.74f * h), stroke)
            drawLine(Color(0xFFFF4D6D), Offset(0.17f * w, 0.74f * h), Offset(0.17f * w, 0.92f * h), stroke)
            drawLine(Color(0xFFE8F4FF), Offset(0.83f * w, 0.74f * h), Offset(0.83f * w, 0.92f * h), stroke)
        }
        Ico.BOLT -> {
            val p = Path().apply {
                moveTo(0.58f * w, 0.02f * h)
                lineTo(0.16f * w, 0.56f * h)
                lineTo(0.46f * w, 0.56f * h)
                lineTo(0.38f * w, 0.98f * h)
                lineTo(0.84f * w, 0.4f * h)
                lineTo(0.54f * w, 0.4f * h)
                close()
            }
            drawPath(p, tint)
        }
        Ico.COIN -> {
            drawCircle(Color(0xFFFFB300), w / 2f)
            drawCircle(Color(0xFFFFE066), w * 0.36f)
            drawCircle(Color(0xFFFFB300), w * 0.2f, style = Stroke(w * 0.07f))
        }
        Ico.BAG -> {
            drawRoundRect(tint, Offset(0.16f * w, 0.34f * h), Size(0.68f * w, 0.6f * h), CornerRadius(w * 0.1f))
            drawArc(
                tint, 180f, 180f, false,
                topLeft = Offset(0.32f * w, 0.08f * h), size = Size(0.36f * w, 0.5f * h),
                style = Stroke(sw * 1.2f, cap = StrokeCap.Round),
            )
        }
        Ico.SNAKE -> {
            for (i in 9 downTo 0) {
                val t = i / 9f
                val x = w * (0.88f - 0.76f * t)
                val y = h / 2f + sin(t * 8f) * h * 0.22f
                drawCircle(tint.copy(alpha = 1f - 0.45f * t), w * (0.13f - 0.04f * t), Offset(x, y))
            }
        }
        Ico.PLAY -> {
            val p = Path().apply {
                moveTo(0.22f * w, 0.1f * h)
                lineTo(0.88f * w, 0.5f * h)
                lineTo(0.22f * w, 0.9f * h)
                close()
            }
            drawPath(p, tint)
        }
        Ico.CROWN -> {
            val p = Path().apply {
                moveTo(0.08f * w, 0.8f * h)
                lineTo(0.08f * w, 0.28f * h)
                lineTo(0.32f * w, 0.52f * h)
                lineTo(0.5f * w, 0.16f * h)
                lineTo(0.68f * w, 0.52f * h)
                lineTo(0.92f * w, 0.28f * h)
                lineTo(0.92f * w, 0.8f * h)
                close()
            }
            drawPath(p, tint)
            drawRect(tint.copy(alpha = 0.7f), Offset(0.08f * w, 0.84f * h), Size(0.84f * w, 0.1f * h))
        }
        Ico.SNOW -> {
            for (k in 0 until 3) {
                val a = (k * 60f) * (Math.PI.toFloat() / 180f)
                val dx = kotlin.math.cos(a) * 0.42f * w
                val dy = kotlin.math.sin(a) * 0.42f * h
                drawLine(tint, Offset(0.5f * w - dx, 0.5f * h - dy), Offset(0.5f * w + dx, 0.5f * h + dy), sw, StrokeCap.Round)
            }
            drawCircle(tint, w * 0.09f, Offset(0.5f * w, 0.5f * h))
        }
        Ico.REFILL -> {
            drawArc(tint, 40f, 280f, false, topLeft = Offset(0.14f * w, 0.14f * h), size = Size(0.72f * w, 0.72f * h), style = Stroke(sw * 1.3f, cap = StrokeCap.Round))
            val p = Path().apply {
                moveTo(0.86f * w, 0.12f * h); lineTo(0.88f * w, 0.42f * h); lineTo(0.6f * w, 0.34f * h); close()
            }
            drawPath(p, tint)
            val b = Path().apply {
                moveTo(0.54f * w, 0.28f * h); lineTo(0.34f * w, 0.54f * h); lineTo(0.48f * w, 0.54f * h)
                lineTo(0.44f * w, 0.74f * h); lineTo(0.66f * w, 0.46f * h); lineTo(0.52f * w, 0.46f * h); close()
            }
            drawPath(b, tint)
        }
        Ico.LOCK -> {
            drawRoundRect(tint, Offset(0.2f * w, 0.45f * h), Size(0.6f * w, 0.48f * h), CornerRadius(w * 0.08f))
            drawArc(tint, 180f, 180f, false, topLeft = Offset(0.3f * w, 0.1f * h), size = Size(0.4f * w, 0.7f * h), style = Stroke(sw * 1.4f))
        }
        Ico.TROPHY -> {
            val cup = Path().apply {
                moveTo(0.26f * w, 0.08f * h)
                lineTo(0.74f * w, 0.08f * h)
                lineTo(0.74f * w, 0.38f * h)
                cubicTo(0.74f * w, 0.6f * h, 0.62f * w, 0.68f * h, 0.5f * w, 0.68f * h)
                cubicTo(0.38f * w, 0.68f * h, 0.26f * w, 0.6f * h, 0.26f * w, 0.38f * h)
                close()
            }
            drawPath(cup, tint)
            drawArc(tint, 90f, 180f, false, Offset(0.02f * w, 0.14f * h), Size(0.3f * w, 0.28f * h), style = Stroke(sw))
            drawArc(tint, -90f, 180f, false, Offset(0.68f * w, 0.14f * h), Size(0.3f * w, 0.28f * h), style = Stroke(sw))
            drawRect(tint, Offset(0.44f * w, 0.68f * h), Size(0.12f * w, 0.14f * h))
            drawRoundRect(tint, Offset(0.28f * w, 0.82f * h), Size(0.44f * w, 0.12f * h), CornerRadius(w * 0.03f))
        }
        Ico.TARGET -> {
            drawCircle(tint, w * 0.44f, style = Stroke(sw))
            drawCircle(tint, w * 0.28f, style = Stroke(sw))
            drawCircle(tint, w * 0.1f)
        }
        Ico.CHEVRONS -> {
            for (k in 0..1) {
                val dy = k * 0.3f * h
                val p = Path().apply {
                    moveTo(0.18f * w, 0.5f * h + dy)
                    lineTo(0.5f * w, 0.2f * h + dy)
                    lineTo(0.82f * w, 0.5f * h + dy)
                }
                drawPath(p, tint, style = Stroke(sw * 1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        Ico.STAR -> {
            val p = Path()
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 0.48f else 0.2f
                val a = (-90f + 36f * i) * 0.017453292f
                val x = w / 2f + cos(a) * r * w
                val y = h / 2f + sin(a) * r * h
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            drawPath(p, tint)
        }
    }
}

/** The icon used for each shop power. */
fun powerIcon(id: String): Ico = when (id) {
    "shield" -> Ico.SHIELD
    "magnet" -> Ico.MAGNET
    "over" -> Ico.BOLT
    "freeze" -> Ico.SNOW
    "recharge" -> Ico.REFILL
    else -> Ico.CHEVRONS
}

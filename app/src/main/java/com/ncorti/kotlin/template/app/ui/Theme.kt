package com.ncorti.kotlin.template.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Neon {
    val Bg = Color(0xFF0B0716)
    val BgTop = Color(0xFF1A0B33)
    val Surface = Color(0xFF161028)
    val Violet = Color(0xFFB06BFF)
    val Cyan = Color(0xFF2DE2E6)
    val Pink = Color(0xFFFF3DAA)
    val Lime = Color(0xFF7CFF3A)
    val Text = Color(0xFFEDE7FF)
    val Dim = Color(0xFF9C93BD)
}

@Composable
fun InfinityTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neon.Violet,
            onPrimary = Color(0xFF14002B),
            secondary = Neon.Cyan,
            onSecondary = Color.Black,
            tertiary = Neon.Pink,
            background = Neon.Bg,
            onBackground = Neon.Text,
            surface = Neon.Surface,
            onSurface = Neon.Text,
        ),
        content = content,
    )
}

val BackgroundBrush: Brush = Brush.verticalGradient(listOf(Neon.BgTop, Neon.Bg))

@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    accent: Color = Neon.Violet,
) {
    if (primary) {
        Button(
            onClick = onClick,
            modifier = modifier.height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color(0xFF14002B)),
        ) {
            Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Neon.Text),
        ) {
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Common frame for secondary screens: gradient background, safe-area padding, title row, scrolling body. */
@Composable
fun ScreenFrame(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(BackgroundBrush),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NeonButton("‹ Back", onClick = onBack, primary = false)
                Spacer(Modifier.padding(horizontal = 8.dp))
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Neon.Text)
            }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}

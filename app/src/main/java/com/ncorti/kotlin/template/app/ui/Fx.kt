package com.ncorti.kotlin.template.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.ncorti.kotlin.template.app.audio.AudioEngine
import com.ncorti.kotlin.template.app.audio.Sfx

/** The app's audio engine, so any button can click without plumbing it through every screen. */
val LocalAudio = compositionLocalOf { AudioEngine().apply { soundEnabled = false; musicEnabled = false } }

/** A clickable that squishes when pressed and plays the click sound. */
@Composable
fun Modifier.bounceClick(sound: Sfx? = Sfx.CLICK, onClick: () -> Unit): Modifier {
    val audio = LocalAudio.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounce",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = src, indication = null) {
            if (sound != null) audio.play(sound)
            onClick()
        }
}

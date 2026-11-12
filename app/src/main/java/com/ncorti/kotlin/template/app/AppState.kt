package com.ncorti.kotlin.template.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ncorti.kotlin.template.app.audio.AudioEngine
import com.ncorti.kotlin.template.app.audio.Haptics
import com.ncorti.kotlin.template.app.data.Settings

enum class Screen { MENU, PLAYING, SKINS, SHOP, SETTINGS, HOWTO }

/**
 * Process-wide UI state. The activity handles configuration changes itself (see the manifest), so
 * this object, and the running [GameSession] inside it, survive rotation untouched.
 */
class AppState(
    val settings: Settings,
    val audio: AudioEngine,
    private val haptics: Haptics,
) {
    var screen by mutableStateOf(Screen.MENU)
        private set
    var session by mutableStateOf<GameSession?>(null)
        private set

    init {
        audio.soundEnabled = settings.sound
        audio.musicEnabled = settings.music
    }

    fun startGame(solo: Boolean) {
        session = GameSession(this, solo)
        screen = Screen.PLAYING
    }

    fun toMenu() {
        session = null
        screen = Screen.MENU
    }

    fun open(target: Screen) {
        screen = target
    }

    /** Back button on the secondary screens. */
    fun back() {
        if (screen != Screen.MENU && screen != Screen.PLAYING) screen = Screen.MENU
    }

    fun vibrate(ms: Long) {
        if (settings.vibration) haptics.buzz(ms)
    }

    fun setSound(v: Boolean) {
        settings.updateSound(v)
        audio.soundEnabled = v
    }

    fun setMusic(v: Boolean) {
        settings.updateMusic(v)
        audio.musicEnabled = v
    }

    /** App went to the background or lost focus: pause a running game. */
    fun onBackground() {
        audio.foreground = false
        session?.let { if (!it.over) it.paused = true }
    }

    fun onForeground() {
        audio.foreground = true
    }
}

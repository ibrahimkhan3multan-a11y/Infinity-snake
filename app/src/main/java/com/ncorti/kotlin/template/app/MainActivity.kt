package com.ncorti.kotlin.template.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ncorti.kotlin.template.app.audio.AudioEngine
import com.ncorti.kotlin.template.app.audio.Haptics
import com.ncorti.kotlin.template.app.data.Settings
import com.ncorti.kotlin.template.app.ui.GameScreen
import com.ncorti.kotlin.template.app.ui.HowToScreen
import com.ncorti.kotlin.template.app.ui.InfinityTheme
import com.ncorti.kotlin.template.app.ui.LocalAudio
import com.ncorti.kotlin.template.app.ui.MenuScreen
import com.ncorti.kotlin.template.app.ui.OnlineScreen
import com.ncorti.kotlin.template.app.ui.SettingsScreen
import com.ncorti.kotlin.template.app.ui.ShopScreen
import com.ncorti.kotlin.template.app.ui.SkinsScreen

class MainActivity : ComponentActivity() {

    // Kept as a field: the manifest declares configChanges, so rotation does not recreate this
    // activity and the running game inside AppState is never lost.
    internal lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val lp = window.attributes
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = lp
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        app = AppState(Settings(applicationContext), AudioEngine(), Haptics(applicationContext))
        hideSystemBars()

        setContent {
            InfinityTheme { Root(app) }
        }
    }

    override fun onResume() {
        super.onResume()
        app.onForeground()
        hideSystemBars()
    }

    override fun onPause() {
        app.onBackground()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onDestroy() {
        if (isFinishing) app.audio.release()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun Root(app: AppState) {
    BackHandler(enabled = app.screen != Screen.MENU && app.screen != Screen.PLAYING) { app.back() }
    CompositionLocalProvider(LocalAudio provides app.audio) {
        Crossfade(targetState = app.screen, animationSpec = tween(220), label = "screens") { screen ->
            when (screen) {
                Screen.MENU -> MenuScreen(app)
                Screen.PLAYING -> app.session?.let { s -> key(s) { GameScreen(app, s) } }
                Screen.SKINS -> SkinsScreen(app)
                Screen.SHOP -> ShopScreen(app)
                Screen.SETTINGS -> SettingsScreen(app)
                Screen.HOWTO -> HowToScreen(app)
                Screen.ONLINE -> OnlineScreen(app)
            }
        }
    }
}

package com.ncorti.kotlin.template.app

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.ncorti.kotlin.template.app.audio.AudioEngine
import com.ncorti.kotlin.template.app.audio.Haptics
import com.ncorti.kotlin.template.app.data.Settings
import com.ncorti.kotlin.template.app.ui.GameScreen
import com.ncorti.kotlin.template.app.ui.HowToScreen
import com.ncorti.kotlin.template.app.ui.InfinityTheme
import com.ncorti.kotlin.template.app.ui.MenuScreen
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
        app.onOrientationChanged = { applyOrientation(it) }
        applyOrientation(app.settings.orientation)
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

    private fun applyOrientation(mode: Int) {
        requestedOrientation = when (mode) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

@Composable
private fun Root(app: AppState) {
    BackHandler(enabled = app.screen != Screen.MENU && app.screen != Screen.PLAYING) { app.back() }
    when (app.screen) {
        Screen.MENU -> MenuScreen(app)
        Screen.PLAYING -> app.session?.let { GameScreen(app, it) }
        Screen.SKINS -> SkinsScreen(app)
        Screen.SHOP -> ShopScreen(app)
        Screen.SETTINGS -> SettingsScreen(app)
        Screen.HOWTO -> HowToScreen(app)
    }
}

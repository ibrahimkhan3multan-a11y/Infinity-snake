package com.ncorti.kotlin.template.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import com.ncorti.kotlin.template.app.audio.Sfx
import com.ncorti.kotlin.template.app.game.Skins
import com.ncorti.kotlin.template.app.game3d.Camera3D
import com.ncorti.kotlin.template.app.game3d.Game3D
import com.ncorti.kotlin.template.app.game3d.GameMap
import com.ncorti.kotlin.template.app.game3d.MapData
import com.ncorti.kotlin.template.app.game3d.Renderer3D
import kotlin.math.atan2

/** What the game-over card shows. */
class Result3D(val score: Int, val best: Int, val newBest: Boolean, val coins: Int, val length: Int, val reason: String)

/**
 * One run of the 3D game: the rules ([Game3D]), the chase camera and the renderer, plus pause and
 * game-over handling. Like [GameSession] it lives in [AppState], so rotating the phone never
 * restarts the run.
 */
class Session3D(private val app: AppState, val mapIndex: Int) {
    private val settings = app.settings
    val def: MapData.Def = MapData.all[mapIndex.coerceIn(0, MapData.all.size - 1)]
    val map = GameMap(def)
    val game = Game3D(map)
    val camera = Camera3D()
    val renderer = Renderer3D(map)
    val joystick = JoystickState()
    val skin = Skins.byId(settings.selectedSkin)

    var frame by mutableLongStateOf(0L)
    var paused by mutableStateOf(false)
    var over by mutableStateOf(false)
        private set
    var result by mutableStateOf<Result3D?>(null)
        private set
    var autopilot = false
    var hudScore by mutableIntStateOf(0)
        private set
    var hudLength by mutableIntStateOf(0)
        private set
    var hudSpeed by mutableIntStateOf(1)
        private set
    var hudLevel by mutableIntStateOf(0)
        private set
    val best: Int get() = settings.best3d(def.id)

    private var lastFood = 0
    private var finalized = false

    init {
        camera.reset(game)
        refreshHud()
    }

    /** Steer from a screen direction: "up" on the screen is where the camera is looking. */
    fun steerScreen(dx: Float, dy: Float) {
        if (over || paused) return
        game.steerWorld(camera.yaw + atan2(dx, -dy))
    }

    fun turnLeft() { if (!over && !paused) game.turnLeft() }
    fun turnRight() { if (!over && !paused) game.turnRight() }

    fun tick(dtRaw: Float) {
        val dt = dtRaw.coerceIn(0f, 0.05f)
        if (!paused && !over) {
            if (autopilot) game.autopilot()
            game.advance(dt)
            afterAdvance()
        }
        camera.update(game, dt)
        frame++
    }

    /** Runs the game for [seconds] with fixed small steps (used by tests; time independent). */
    fun simulate(seconds: Float) {
        var t = 0f
        while (t < seconds && !over) {
            tick(1f / 60f)
            t += 1f / 60f
        }
    }

    private fun afterAdvance() {
        if (game.foodEaten != lastFood) {
            lastFood = game.foodEaten
            app.audio.playEat()
            refreshHud()
        }
        if (!game.alive && !finalized) finish()
    }

    private fun refreshHud() {
        hudScore = game.score
        hudLength = game.length
        hudSpeed = game.speedLevel
        hudLevel = map.cl(game.headCell)
    }

    private fun finish() {
        finalized = true
        over = true
        refreshHud()
        val rec = settings.record3d(def.id, game.score)
        val why = when (game.death) {
            Game3D.Death.FELL -> "You fell off the edge!"
            Game3D.Death.SELF -> "You bit yourself!"
            else -> "You crashed into an obstacle!"
        }
        result = Result3D(game.score, settings.best3d(def.id), rec.first, rec.second, game.length, why)
        app.audio.playBoom()
        app.vibrate(70)
    }

    fun close() {
        paused = true
    }
}

package com.ncorti.kotlin.template.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.ncorti.kotlin.template.app.game.Cfg
import com.ncorti.kotlin.template.app.game.PI_F
import com.ncorti.kotlin.template.app.game.Particles
import com.ncorti.kotlin.template.app.game.Skins
import com.ncorti.kotlin.template.app.game.Snake
import com.ncorti.kotlin.template.app.game.TWO_PI
import com.ncorti.kotlin.template.app.game.World
import com.ncorti.kotlin.template.app.game.WorldListener
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

class BoardEntry(val name: String, val score: Int, val isYou: Boolean)

class RunResult(
    val score: Int,
    val best: Int,
    val rank: Int,
    val length: Int,
    val earnedCoins: Int,
    val newHighScore: Boolean,
    val solo: Boolean,
)

/** Raw input, written by the UI and read once per tick. */
class InputState {
    var angle = 0f
    var touchBoost = false
    var keyBoost = false
    var left = false
    var right = false
    val keyTurn: Int get() = (if (right) 1 else 0) - (if (left) 1 else 0)
}

/** Virtual joystick visuals (state so the overlay recomposes while dragging). */
class JoystickState {
    var active by mutableStateOf(false)
    var base by mutableStateOf(Offset.Zero)
    var knob by mutableStateOf(Offset.Zero)
}

/**
 * One run of the game: the world plus camera, particles, HUD values and game-over bookkeeping.
 * It lives in [AppState], not in a composable, so rotating the device never restarts the run.
 */
class GameSession(private val app: AppState, val solo: Boolean) : WorldListener {
    private val settings = app.settings
    private val rnd = Random(System.nanoTime())
    val world = World(
        rnd = rnd,
        botCount = if (solo) 0 else Cfg.BOT_COUNT,
        deadlyWalls = settings.walls,
        playerSkin = Skins.byId(settings.selectedSkin),
        listener = null,
    )
    val particles = Particles(300)
    val input = InputState()
    val joystick = JoystickState()

    var paused by mutableStateOf(false)
    var over by mutableStateOf(false)
    var result by mutableStateOf<RunResult?>(null)
        private set

    // HUD mirrors, refreshed a few times a second (not every frame).
    var hudScore by mutableIntStateOf(Cfg.START_MASS.toInt())
        private set
    var hudLength by mutableIntStateOf(0)
        private set
    var hudRank by mutableIntStateOf(1)
        private set
    var hudAlive by mutableIntStateOf(1)
        private set
    var hudBoostOk by mutableStateOf(true)
        private set
    var hudBoard by mutableStateOf<List<BoardEntry>>(emptyList())
        private set

    /** Bumped every frame so the canvas redraws; read only inside the draw phase. */
    var frame by mutableLongStateOf(0L)
        private set

    // Camera and viewport (plain fields: written by the renderer, read by tick/input).
    var camX = 0f
    var camY = 0f
    var zoom = 1f
    var viewW = 1080f
    var viewH = 1920f
    private var camInit = false

    private var acc = 0f
    private var hudTimer = 0f
    private var overTimer = -1f
    private var finalized = false

    init {
        world.listener = this
        input.angle = world.player.angle
        camX = world.player.xs[0]
        camY = world.player.ys[0]
        refreshHud()
    }

    fun tick(dtRaw: Float) {
        frame++
        if (paused) return
        val dt = dtRaw.coerceIn(0f, 0.1f)
        val p = world.player

        if (p.alive) {
            if (input.keyTurn != 0) {
                input.angle += input.keyTurn * 3.2f * dt
                if (input.angle > PI_F) input.angle -= TWO_PI else if (input.angle < -PI_F) input.angle += TWO_PI
            }
            p.targetAngle = input.angle
            p.boosting = input.touchBoost || input.keyBoost
            p.turnMul = settings.sensitivity
        }

        acc += dt
        var steps = 0
        while (acc >= Cfg.STEP && steps < 5) {
            world.step(Cfg.STEP)
            acc -= Cfg.STEP
            steps++
        }
        if (steps == 5) acc = 0f

        particles.update(dt)
        updateCamera(dt, p)

        hudTimer -= dt
        if (hudTimer <= 0f) {
            hudTimer = 0.2f
            refreshHud()
        }

        if (!p.alive && !finalized) finalizeRun(p)
        if (overTimer >= 0f) {
            overTimer -= dt
            if (overTimer < 0f) over = true
        }
    }

    private fun updateCamera(dt: Float, p: Snake) {
        val minDim = minOf(viewW, viewH)
        val targetZoom = minDim / (680f + 16f * sqrt(p.mass))
        if (!camInit) {
            camInit = true
            zoom = targetZoom
            camX = p.xs[0]
            camY = p.ys[0]
            return
        }
        if (p.alive) {
            val k = 1f - exp(-12f * dt)
            camX += (p.xs[0] - camX) * k
            camY += (p.ys[0] - camY) * k
        }
        zoom += (targetZoom - zoom) * (1f - exp(-3f * dt))
    }

    private fun refreshHud() {
        val p = world.player
        hudScore = p.score
        hudLength = p.n
        hudRank = if (p.alive) world.rankOf(p) else p.lastRank
        hudAlive = world.aliveCount()
        hudBoostOk = p.mass > Cfg.MIN_BOOST_MASS
        val list = ArrayList<BoardEntry>(world.boardCount)
        for (i in 0 until world.boardCount) {
            val s = world.board[i] ?: continue
            list.add(BoardEntry(s.name, s.score, s.isPlayer))
        }
        hudBoard = list
    }

    private fun finalizeRun(p: Snake) {
        finalized = true
        val score = p.score
        val previousBest = settings.highScore
        val earned = settings.recordRun(score, p.lastRank, solo)
        result = RunResult(
            score = score,
            best = settings.highScore,
            rank = p.lastRank,
            length = p.n,
            earnedCoins = earned,
            newHighScore = score > previousBest,
            solo = solo,
        )
        hudScore = score
        hudRank = p.lastRank
        overTimer = 1.2f
    }

    // ---- WorldListener: effects only, never game logic.

    override fun onFoodEaten(snake: Snake) {
        if (!snake.isPlayer) return
        if (settings.quality > 0) {
            particles.burst(snake.xs[0], snake.ys[0], snake.headColor, 2, 90f, 3f, rnd)
        }
        app.audio.playEat()
    }

    override fun onSnakeDied(snake: Snake, killer: Snake?) {
        if (settings.quality > 0) {
            val count = if (settings.quality >= 2) 28 else 14
            particles.burst(snake.xs[0], snake.ys[0], snake.headColor, count, 260f, 6f, rnd)
        }
        if (snake.isPlayer) {
            app.audio.playBoom()
            app.vibrate(70)
        }
    }
}

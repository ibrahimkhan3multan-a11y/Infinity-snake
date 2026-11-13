package com.ncorti.kotlin.template.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.ncorti.kotlin.template.app.audio.Sfx
import com.ncorti.kotlin.template.app.game.Cfg
import com.ncorti.kotlin.template.app.game.PI_F
import com.ncorti.kotlin.template.app.game.Particles
import com.ncorti.kotlin.template.app.game.Power
import com.ncorti.kotlin.template.app.game.Powers
import com.ncorti.kotlin.template.app.game.Skins
import com.ncorti.kotlin.template.app.game.Snake
import com.ncorti.kotlin.template.app.game.TWO_PI
import com.ncorti.kotlin.template.app.game.World
import com.ncorti.kotlin.template.app.game.WorldListener
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class BoardEntry(val name: String, val score: Int, val isYou: Boolean)

class RunResult(
    val score: Int,
    val best: Int,
    val rank: Int,
    val length: Int,
    val earnedCoins: Int,
    val kills: Int,
    val challenges: List<String>,
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
    /** Where the fixed joystick ring sits (screen px) and how big it is; set by the layout. */
    var home = Offset.Zero
    var radius = 100f
    var active by mutableStateOf(false)
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
        playerName = settings.playerName,
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
    /** Boost energy 0..1; written every frame, read only by the boost button. */
    var hudEnergy by mutableFloatStateOf(1f)
        private set
    var hudBoard by mutableStateOf<List<BoardEntry>>(emptyList())
        private set
    /** Short event text such as "You eliminated Viper"; empty when nothing to show. */
    var banner by mutableStateOf("")
        private set
    private var bannerTimer = 0f

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

    // Screen shake offset (world units), applied by the renderer.
    var shakeX = 0f
    var shakeY = 0f
    private var shakeTime = 0f
    private var wasBoosting = false
    private var warned = false

    private var acc = 0f
    private var hudTimer = 0f
    private var overTimer = -1f
    private var finalized = false

    init {
        world.listener = this
        val me = world.player
        me.maxEnergy = Powers.tankCapacity(settings.tankLevel)
        me.energy = me.maxEnergy
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
        tickPowers(dt)
        updateCamera(dt, p)
        if (bannerTimer > 0f) {
            bannerTimer -= dt
            if (bannerTimer <= 0f) banner = ""
        }
        hudEnergy = if (p.maxEnergy > 0f) p.energy / p.maxEnergy else 0f

        // Boost feedback: whoosh on start, a particle trail while it runs, a warning when nearly empty.
        val boostNow = p.alive && p.boostActive
        if (boostNow && !wasBoosting) app.audio.play(Sfx.BOOST)
        wasBoosting = boostNow
        if (boostNow && p.overdrive <= 0f) {
            if (hudEnergy < 0.12f && !warned) {
                warned = true
                app.audio.play(Sfx.WARN)
            }
        } else if (hudEnergy > 0.3f) {
            warned = false
        }
        if (boostNow && settings.quality > 0 && (frame and 1L) == 0L) {
            val a = p.angle
            val jx = (rnd.nextFloat() - 0.5f) * 50f
            val jy = (rnd.nextFloat() - 0.5f) * 50f
            particles.spawn(
                p.xs[0] - cos(a) * p.radius,
                p.ys[0] - sin(a) * p.radius,
                -cos(a) * 70f + jx,
                -sin(a) * 70f + jy,
                0.35f,
                if (p.overdrive > 0f) 0xFFFFD45C.toInt() else p.headColor,
                3.5f,
            )
        }
        if (shakeTime > 0f && settings.quality > 0) {
            shakeTime -= dt
            val m = 16f * (shakeTime / 0.45f).coerceAtLeast(0f)
            shakeX = (rnd.nextFloat() - 0.5f) * 2f * m
            shakeY = (rnd.nextFloat() - 0.5f) * 2f * m
        } else {
            shakeX = 0f
            shakeY = 0f
        }

        hudTimer -= dt
        if (hudTimer <= 0f) {
            hudTimer = 0.2f
            refreshHud()
        }

        if (!p.alive && !finalized) finalizeRun(p)
        if (overTimer >= 0f) {
            overTimer -= dt
            if (overTimer < 0f) {
                over = true
                result?.let { if (it.newHighScore || it.challenges.isNotEmpty()) app.audio.play(Sfx.FANFARE) }
            }
        }
    }

    private fun updateCamera(dt: Float, p: Snake) {
        val minDim = minOf(viewW, viewH)
        val targetZoom = minDim / (400f + 12f * sqrt(p.mass))
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

    // ---- Powers: permanent, with a cooldown that starts when the effect ends.

    private val powerWait = FloatArray(Powers.all.size)   // seconds until ready (effect time + cooldown)
    /** Bumped ~10x a second so power buttons redraw their ring. */
    var hudPowerTick by mutableIntStateOf(0)
        private set
    private var powerTickTimer = 0f

    private fun isActive(power: Power): Boolean {
        val p = world.player
        return when (power.id) {
            Powers.SHIELD.id -> p.shield > 0f
            Powers.MAGNET.id -> p.magnet > 0f
            Powers.OVERDRIVE.id -> p.overdrive > 0f
            Powers.FREEZE.id -> p.freeze > 0f
            else -> false
        }
    }

    /** Whole seconds of an active effect left (0 when inactive). */
    fun activeSeconds(power: Power): Int {
        hudPowerTick
        val p = world.player
        val t = when (power.id) {
            Powers.SHIELD.id -> p.shield
            Powers.MAGNET.id -> p.magnet
            Powers.OVERDRIVE.id -> p.overdrive
            Powers.FREEZE.id -> p.freeze
            else -> 0f
        }
        return kotlin.math.ceil(t).toInt()
    }

    /** Whole seconds until a power can be used again, once its effect has ended (0 = ready). */
    fun cooldownSeconds(power: Power): Int {
        hudPowerTick
        val i = Powers.all.indexOf(power)
        val p = world.player
        val effect = when (power.id) {
            Powers.SHIELD.id -> p.shield
            Powers.MAGNET.id -> p.magnet
            Powers.OVERDRIVE.id -> p.overdrive
            Powers.FREEZE.id -> p.freeze
            else -> 0f
        }
        return kotlin.math.ceil((powerWait[i] - effect).coerceAtLeast(0f)).toInt()
    }

    /** 0 = ready, 1 = just started cooling down; drives the ring sweep. */
    fun cooldownFraction(power: Power): Float {
        val level = settings.powerLevel(power.id)
        if (level <= 0) return 0f
        val total = power.cooldown(level)
        return if (total <= 0f) 0f else (cooldownSeconds(power) / total).coerceIn(0f, 1f)
    }

    fun powerReady(power: Power): Boolean =
        settings.powerLevel(power.id) > 0 && !isActive(power) &&
            powerWait[Powers.all.indexOf(power)] <= 0f

    /** Activates a power if it is unlocked, off cooldown and you are alive. */
    fun usePower(power: Power) {
        val p = world.player
        if (!p.alive || paused || over || !powerReady(power)) return
        val level = settings.powerLevel(power.id)
        val dur = power.duration(level)
        when (power.id) {
            Powers.SHIELD.id -> p.shield = dur
            Powers.MAGNET.id -> p.magnet = dur
            Powers.OVERDRIVE.id -> p.overdrive = dur
            Powers.FREEZE.id -> p.freeze = dur
            else -> p.energy = p.maxEnergy
        }
        powerWait[Powers.all.indexOf(power)] = dur + power.cooldown(level)
        refreshHud()
        hudPowerTick++
        app.audio.play(
            when (power.id) {
                Powers.SHIELD.id -> Sfx.SHIELD
                Powers.MAGNET.id -> Sfx.MAGNET
                Powers.OVERDRIVE.id -> Sfx.OVERDRIVE
                else -> Sfx.POWER
            },
        )
        app.vibrate(25)
    }

    private fun tickPowers(dt: Float) {
        for (i in powerWait.indices) if (powerWait[i] > 0f) powerWait[i] = (powerWait[i] - dt).coerceAtLeast(0f)
        powerTickTimer -= dt
        if (powerTickTimer <= 0f) {
            powerTickTimer = 0.1f
            hudPowerTick++
        }
    }

    private fun refreshHud() {
        val p = world.player
        hudScore = p.score
        hudLength = p.n
        hudRank = if (p.alive) world.rankOf(p) else p.lastRank
        hudAlive = world.aliveCount()
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
        val earned = settings.recordRun(score, p.lastRank, solo, p.kills)
        val completed = settings.applyChallenges(score, p.kills, p.lastRank, solo)
        result = RunResult(
            score = score,
            best = settings.highScore,
            rank = p.lastRank,
            length = p.n,
            earnedCoins = earned,
            kills = p.kills,
            challenges = completed.map { "${it.title}: ${it.rewardText}" },
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
        if (killer != null && killer.isPlayer && !snake.isPlayer) {
            banner = "You eliminated ${snake.name}!"
            app.audio.play(Sfx.KILL)
            bannerTimer = 2.5f
        }
        if (settings.quality > 0) {
            val count = if (settings.quality >= 2) 28 else 14
            particles.burst(snake.xs[0], snake.ys[0], snake.headColor, count, 260f, 6f, rnd)
        }
        if (snake.isPlayer) {
            shakeTime = 0.45f
            app.audio.playBoom()
            app.vibrate(70)
        }
    }
}

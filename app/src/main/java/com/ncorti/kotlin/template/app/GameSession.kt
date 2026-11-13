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
import com.ncorti.kotlin.template.app.game.PowerState
import com.ncorti.kotlin.template.app.game.Powers
import com.ncorti.kotlin.template.app.game.Skins
import com.ncorti.kotlin.template.app.game.Snake
import com.ncorti.kotlin.template.app.game.TWO_PI
import com.ncorti.kotlin.template.app.game.World
import com.ncorti.kotlin.template.app.net.GuestLogic
import com.ncorti.kotlin.template.app.net.HostLogic
import com.ncorti.kotlin.template.app.net.RelayClient
import org.json.JSONObject
import com.ncorti.kotlin.template.app.game.WorldListener
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** The online room a session belongs to. */
class OnlineRoom(
    val client: RelayClient,
    val code: String,
    val isHost: Boolean,
    val bots: Int = 0,
    /** Guests only: the introduction sent to the host (skin, upgrade levels). */
    val hello: JSONObject? = null,
)

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
class GameSession(
    private val app: AppState,
    val solo: Boolean,
    /** Non-null for an online room: this phone is either the host (runs the arena) or a guest. */
    val room: OnlineRoom? = null,
) : WorldListener {
    private val settings = app.settings
    private val rnd = Random(System.nanoTime())
    private val isGuest = room != null && !room.isHost
    val world = World(
        rnd = rnd,
        botCount = when {
            room != null -> if (room.isHost) room.bots else 0
            solo -> 0
            else -> Cfg.BOT_COUNT
        },
        deadlyWalls = settings.walls,
        playerSkin = Skins.byId(settings.selectedSkin),
        listener = null,
        ambientFood = if (isGuest) 0 else Cfg.AMBIENT_FOOD,
        playerName = settings.playerName,
    )
    private val host: HostLogic? = if (room != null && room.isHost) HostLogic(world, room.client) else null
    private val guest: GuestLogic? = if (isGuest) {
        GuestLogic(
            world, room!!.client, room.hello!!,
            object : GuestLogic.Listener {
                override fun onKilled(victim: String) = showBanner("You eliminated $victim!", Sfx.KILL)
                override fun onOver(score: Int, rank: Int, length: Int, kills: Int) = finalizeWith(score, rank, length, kills)
                override fun onRespawned() = resetAfterRespawn()
            },
        )
    } else {
        null
    }

    /** Set when the connection to the room is lost: a message to show. */
    var disconnected by mutableStateOf<String?>(null)
        private set
    val isOnline: Boolean get() = room != null
    var peerCount by mutableIntStateOf(0)
        private set

    /** Power (active, cooldown) pairs mirrored from the host, for guests. */
    private val remotePower: Array<Pair<Int, Int>>? get() = guest?.powers
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
        guest?.sendHello()
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
        // An online arena keeps running while the pause menu is open: other people are in it.
        if (paused && room == null) return
        val dt = dtRaw.coerceIn(0f, 0.1f)
        val p = world.player

        if (room != null) pollNet()
        if (guest != null) {
            tickGuest(dt, p)
            return
        }

        if (p.alive && !paused) {
            if (input.keyTurn != 0) {
                input.angle += input.keyTurn * 3.2f * dt
                if (input.angle > PI_F) input.angle -= TWO_PI else if (input.angle < -PI_F) input.angle += TWO_PI
            }
            p.targetAngle = input.angle
            p.boosting = input.touchBoost || input.keyBoost
            p.turnMul = settings.sensitivity
        }

        host?.beforeStep()
        acc += dt
        var steps = 0
        while (acc >= Cfg.STEP && steps < 5) {
            world.step(Cfg.STEP)
            acc -= Cfg.STEP
            steps++
        }
        if (steps == 5) acc = 0f

        host?.afterFrame(dt)
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

    private fun pollNet() {
        val client = room!!.client
        while (true) {
            val f = client.inbox.poll() ?: break
            when (f.optString("t")) {
                "_closed" -> disconnected = if (disconnected == null) "Connection lost" else disconnected
                "closed" -> disconnected = "The host left the room"
                "error" -> disconnected = f.optString("msg", "Error")
                else -> {
                    host?.handle(f)
                    guest?.handle(f)
                }
            }
        }
        peerCount = host?.guestCount ?: 0
    }

    private fun tickGuest(dt: Float, p: Snake) {
        val g = guest!!
        if (input.keyTurn != 0) {
            input.angle += input.keyTurn * 3.2f * dt
            if (input.angle > PI_F) input.angle -= TWO_PI else if (input.angle < -PI_F) input.angle += TWO_PI
        }
        val before = g.snapshots
        g.sendInput(input.angle, input.touchBoost || input.keyBoost, dt)
        if (g.update(dt)) app.audio.playEat()
        if (before == 0 && g.snapshots > 0) camInit = false
        particles.update(dt)
        updateCamera(dt, p)
        hudEnergy = if (g.maxEnergy > 0f) g.energy / g.maxEnergy else 0f
        if (bannerTimer > 0f) {
            bannerTimer -= dt
            if (bannerTimer <= 0f) banner = ""
        }
        val boostNow = p.alive && p.boostActive
        if (boostNow && !wasBoosting) app.audio.play(Sfx.BOOST)
        wasBoosting = boostNow
        tickPowers(dt)
        hudTimer -= dt
        if (hudTimer <= 0f) {
            hudTimer = 0.2f
            refreshHud()
        }
        if (overTimer >= 0f) {
            overTimer -= dt
            if (overTimer < 0f) {
                over = true
                result?.let { if (it.newHighScore || it.challenges.isNotEmpty()) app.audio.play(Sfx.FANFARE) }
            }
        }
    }

    private fun showBanner(text: String, sfx: Sfx) {
        banner = text
        bannerTimer = 2.5f
        app.audio.play(sfx)
    }

    /** A guest asks the host for a fresh snake after elimination. */
    private fun resetAfterRespawn() {
        finalized = false
        overTimer = -1f
        over = false
        result = null
        camInit = false
    }

    /** Online: bring the local player back into the same running arena (host) or ask the host (guest). */
    fun respawnInRoom() {
        if (guest != null) {
            guest.requestRespawn()
            return
        }
        val me = world.player
        world.respawnRemote(me)
        me.maxEnergy = Powers.tankCapacity(settings.tankLevel)
        me.energy = me.maxEnergy
        powers.reset()
        resetAfterRespawn()
        input.angle = me.angle
        hudEnergy = 1f
    }

    /** Leaves the room and drops the connection. */
    fun close() {
        room?.client?.close()
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

    private val powers = PowerState()
    /** Bumped ~10x a second so power buttons redraw their ring. */
    var hudPowerTick by mutableIntStateOf(0)
        private set
    private var powerTickTimer = 0f

    /** Whole seconds of an active effect left (0 when inactive). */
    fun activeSeconds(power: Power): Int {
        hudPowerTick
        return remotePower?.get(Powers.all.indexOf(power))?.first ?: powers.activeSeconds(world.player, power)
    }

    /** Whole seconds until a power can be used again, once its effect has ended (0 = ready). */
    fun cooldownSeconds(power: Power): Int {
        hudPowerTick
        return remotePower?.get(Powers.all.indexOf(power))?.second ?: powers.cooldownSeconds(world.player, power)
    }

    /** 0 = ready, 1 = just started cooling down; drives the ring sweep. */
    fun cooldownFraction(power: Power): Float {
        val level = settings.powerLevel(power.id)
        if (level <= 0) return 0f
        val total = power.cooldown(level)
        return if (total <= 0f) 0f else (cooldownSeconds(power) / total).coerceIn(0f, 1f)
    }

    fun powerReady(power: Power): Boolean {
        val level = settings.powerLevel(power.id)
        if (level <= 0) return false
        val rp = remotePower
        if (rp != null) {
            val i = Powers.all.indexOf(power)
            return world.player.alive && rp[i].first <= 0 && rp[i].second <= 0
        }
        return powers.ready(world.player, power, level)
    }

    /** Activates a power if it is unlocked, off cooldown and you are alive. */
    fun usePower(power: Power) {
        val p = world.player
        if (!p.alive || paused || over || !powerReady(power)) return
        if (guest != null) {
            guest.requestPower(Powers.all.indexOf(power))
        } else {
            val level = settings.powerLevel(power.id)
            if (!powers.use(p, power, level)) return
            refreshHud()
        }
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
        powers.tick(dt)
        powerTickTimer -= dt
        if (powerTickTimer <= 0f) {
            powerTickTimer = 0.1f
            hudPowerTick++
        }
    }

    private fun refreshHud() {
        val g = guest
        if (g != null) {
            hudScore = g.score
            hudLength = g.length
            hudRank = g.rank
            hudAlive = g.alive
            hudBoard = g.board
            return
        }
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

    private fun finalizeRun(p: Snake) = finalizeWith(p.score, p.lastRank, p.n, p.kills)

    private fun finalizeWith(score: Int, rank: Int, length: Int, kills: Int) {
        if (finalized) return
        finalized = true
        val previousBest = settings.highScore
        val earned = settings.recordRun(score, rank, solo, kills)
        val completed = settings.applyChallenges(score, kills, rank, solo)
        result = RunResult(
            score = score,
            best = settings.highScore,
            rank = rank,
            length = length,
            earnedCoins = earned,
            kills = kills,
            challenges = completed.map { "${it.title}: ${it.rewardText}" },
            newHighScore = score > previousBest,
            solo = solo,
        )
        hudScore = score
        hudRank = rank
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
        host?.snakeDied(snake, killer)
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

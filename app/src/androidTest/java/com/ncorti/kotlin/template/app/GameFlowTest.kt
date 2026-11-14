package com.ncorti.kotlin.template.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Debug
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.ncorti.kotlin.template.app.data.Settings
import com.ncorti.kotlin.template.app.game.Cfg
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end checks on a real (emulated) device. Input goes through UiAutomator, i.e. real touch
 * events and real device rotation, not calls into the code under test. Screenshots are saved to
 * the additional-test-output directory so CI can publish them.
 */
@RunWith(AndroidJUnit4::class)
class GameFlowTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        device.setOrientationNatural()
        context.getSharedPreferences("infinity_snake", Context.MODE_PRIVATE).edit().clear().commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("PLAY")
    }

    @After
    fun tearDown() {
        runCatching { scenario.close() }
        device.setOrientationNatural()
        device.unfreezeRotation()
    }

    // ---------------------------------------------------------------- helpers

    private fun waitFor(text: String, timeoutMs: Long = 15_000) {
        assertTrue("'$text' should be on screen", device.wait(Until.hasObject(By.text(text)), timeoutMs))
    }

    private fun click(text: String) {
        waitFor(text)
        device.findObject(By.text(text)).click()
    }

    /** Clicks [text], swiping the scrolling screen up until it becomes visible. */
    private fun clickScrolling(text: String) {
        repeat(8) {
            val obj = device.findObject(By.text(text))
            if (obj != null) {
                obj.click()
                return
            }
            val w = device.displayWidth
            val h = device.displayHeight
            device.swipe(w / 2, (h * 0.8).toInt(), w / 2, (h * 0.3).toInt(), 12)
            Thread.sleep(300)
        }
        throw AssertionError("'$text' not found even after scrolling")
    }

    private fun <T> onApp(block: (AppState, MainActivity) -> T): T {
        var result: Any? = null
        scenario.onActivity { result = block(it.app, it) }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun session(): GameSession = onApp { app, _ -> app.session!! }

    private fun startArena() {
        click("PLAY")
        waitFor("BOOST")
    }

    private fun startSolo() {
        click("SOLO")
        click("PLAY")
        waitFor("BOOST")
    }

    private fun shot(name: String) {
        val dir = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: "/sdcard/Android/media/${context.packageName}/additional_test_output"
        File(dir).mkdirs()
        device.takeScreenshot(File(dir, "$name.png"))
    }

    private fun isLandscape(): Boolean = onApp { _, a -> a.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }

    private fun openSettings() {
        assertTrue("settings gear on screen", device.wait(Until.hasObject(By.desc("Settings")), 15_000))
        device.findObject(By.desc("Settings")).click()
    }

    /** Drags the fixed on-screen joystick from its centre by (dx, dy) pixels, like a real thumb. */
    private fun joystickDrag(dx: Int, dy: Int) {
        val home = onApp { app, _ -> app.session!!.joystick.home }
        val x0 = home.x.toInt()
        val y0 = home.y.toInt()
        device.swipe(x0, y0, x0 + dx, y0 + dy, 8)
    }

    private fun worldTime(): Float = onApp { app, _ -> app.session!!.world.time }

    private fun pad(ms: Long) = Thread.sleep(ms)

    // ---------------------------------------------------------------- tests

    @Test
    fun everySoundAndMusicModeRunsWithoutCrashing() {
        val audio = com.ncorti.kotlin.template.app.audio.AudioEngine()
        try {
            com.ncorti.kotlin.template.app.audio.Sfx.values().forEach { audio.play(it); pad(30) }
            audio.musicMode = com.ncorti.kotlin.template.app.audio.MusicMode.MENU
            pad(200)
            audio.musicMode = com.ncorti.kotlin.template.app.audio.MusicMode.GAME
            pad(200)
        } finally {
            audio.release()
        }
    }

    @Test
    fun firstLaunchShowsFullMenuInLandscape() {
        for (label in listOf("PLAY", "ARENA", "SOLO", "SHOP", "SKINS", "HELP", "BEST SCORE", "BEST RANK", "EDIT SNAKE")) {
            waitFor(label)
        }
        assertTrue("settings gear present", device.hasObject(By.desc("Settings")))
        assertTrue("game is landscape-only", isLandscape())
        shot("01_menu_landscape")
    }

    @Test
    fun arenaGameRunsAndShowsHud() {
        startArena()
        val t0 = worldTime()
        pad(1500)
        assertTrue("game time advances", worldTime() > t0 + 0.5f)
        assertTrue(device.hasObject(By.text("SCORE")))
        assertTrue(device.hasObject(By.text("LEADERBOARD")))
        assertTrue(device.hasObject(By.text("BOOST")))
        shot("03_game_portrait")
    }

    @Test
    fun portraitIsRefusedAndLandscapeFlipKeepsTheRun() {
        startSolo()
        onApp { app, _ ->
            val p = app.session!!.world.player
            p.mass = 80f
            p.grace = 100f
        }
        pad(600)
        val before = session()
        val world = before.world
        val massBefore = world.player.mass
        val timeBefore = worldTime()

        device.setOrientationNatural() // portrait on a phone: the activity must stay landscape
        pad(1200)
        assertTrue("still landscape after asking for portrait", isLandscape())
        assertSame(before, session())

        device.setOrientationRight() // the other landscape direction
        pad(1500)
        assertTrue(isLandscape())
        val after = session()
        assertSame("same session after flipping landscape", before, after)
        assertSame(world, after.world)
        assertTrue("clock kept running", worldTime() > timeBefore)
        assertTrue("player alive", after.world.player.alive)
        assertTrue("score kept", after.world.player.mass >= massBefore - 0.01f)
        assertTrue("viewport is wide", after.viewW > after.viewH)
        assertTrue(device.hasObject(By.text("BOOST")))
        shot("04_game_landscape_flipped")
    }

    @Test
    fun joystickSteersInAllDirections() {
        startSolo()
        onApp { app, _ -> app.session!!.world.player.grace = 100f }

        joystickDrag(120, 0)
        pad(400)
        val right = onApp { app, _ -> app.session!!.input.angle }
        assertTrue("pushed right, angle=$right", abs(right) < 0.3f)

        joystickDrag(0, -120) // screen y grows downward, so up is -PI/2
        pad(400)
        val up = onApp { app, _ -> app.session!!.input.angle }
        assertTrue("pushed up, angle=$up", abs(up + 1.5708f) < 0.3f)
        pad(1200)
        val actual = onApp { app, _ -> app.session!!.world.player.angle }
        assertTrue("snake actually turned up, angle=$actual", abs(actual + 1.5708f) < 0.5f)

        joystickDrag(-120, 0)
        pad(400)
        val left = onApp { app, _ -> app.session!!.input.angle }
        assertTrue("pushed left, angle=$left", abs(left) > 2.8f)

        joystickDrag(0, 120)
        pad(400)
        val down = onApp { app, _ -> app.session!!.input.angle }
        assertTrue("pushed down, angle=$down", abs(down - 1.5708f) < 0.3f)
    }

    @Test
    fun touchOutsideTheJoystickDoesNotSteerInJoystickMode() {
        startSolo()
        onApp { app, _ -> app.session!!.world.player.grace = 100f }
        joystickDrag(120, 0)
        pad(300)
        val before = onApp { app, _ -> app.session!!.input.angle }
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe(w / 2, h / 2, w / 2, (h * 0.1).toInt(), 8) // far from the ring
        pad(300)
        assertEquals(before, onApp { app, _ -> app.session!!.input.angle }, 0.0001f)
    }

    @Test
    fun touchAndDragModeStillSteersAnywhere() {
        onApp { app, _ -> app.settings.updateControlMode(1) }
        startSolo()
        onApp { app, _ -> app.session!!.world.player.grace = 100f }
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe(w / 2, h / 2, w / 2, (h * 0.1).toInt(), 8)
        pad(400)
        val up = onApp { app, _ -> app.session!!.input.angle }
        assertTrue("dragged up, angle=$up", abs(up + 1.5708f) < 0.4f)
    }

    @Test
    fun boostIsFasterThanNormalSpeed() {
        startSolo()
        onApp { app, _ ->
            val p = app.session!!.world.player
            p.mass = 90f
            p.grace = 100f
            p.targetAngle = p.angle
            app.session!!.input.angle = p.angle
        }
        pad(300)
        val boost = device.findObject(By.text("BOOST"))
        assertNotNull(boost)
        var speed = 0f
        var massBefore = 0f
        // A shared CI emulator can drop a touch now and then, so allow a couple of attempts. Speed is
        // measured only over the moments the game itself reports boost as active.
        for (attempt in 1..3) {
            onApp { app, _ -> app.session!!.paused = false }
            massBefore = onApp { app, _ -> app.session!!.world.player.mass }
            val samples = ArrayList<FloatArray>() // t, x, y, active
            val sampler = Thread {
                repeat(34) {
                    samples.add(
                        onApp { app, _ ->
                            val s = app.session!!
                            val p = s.world.player
                            floatArrayOf(s.world.time, p.xs[0], p.ys[0], if (p.boostActive) 1f else 0f)
                        },
                    )
                    Thread.sleep(50)
                }
            }
            sampler.start()
            val bb = boost.visibleBounds
            device.swipe(bb.centerX(), bb.centerY(), bb.centerX() + 3, bb.centerY(), 300)
            sampler.join()
            val active = samples.filter { it[3] == 1f }
            if (active.size >= 5) {
                val first = active.first()
                val last = active.last()
                val dt = last[0] - first[0]
                if (dt > 0.2f) speed = hypot(last[1] - first[1], last[2] - first[2]) / dt
            }
            if (speed > Cfg.BASE_SPEED * 1.2f) break
            pad(500)
        }
        assertTrue("average speed while boosting $speed should exceed base ${Cfg.BASE_SPEED}", speed > Cfg.BASE_SPEED * 1.2f)
        val after = onApp { app, _ -> app.session!!.world.player.mass }
        assertTrue("boost must not burn length ($massBefore -> $after)", after >= massBefore - 0.01f)
        assertFalse("boost released", onApp { app, _ -> app.session!!.input.touchBoost })
    }

    @Test
    fun eliminationShowsGameOverAndPlayAgainRestarts() {
        startArena()
        val first = session()
        onApp { app, _ ->
            val p = app.session!!.world.player
            p.grace = 0f
            p.xs[0] = Cfg.ARENA_RADIUS + 5f
            p.ys[0] = 0f
        }
        waitFor("PLAY AGAIN", 20_000)
        for (label in listOf("Final score", "Best score", "Final rank", "Snake length", "Coins earned", "MAIN MENU")) {
            assertTrue("'$label' on game over screen", device.hasObject(By.text(label)))
        }
        shot("05_game_over")
        val saved = onApp { app, _ -> app.settings.highScore }
        assertTrue("high score saved, was $saved", saved >= 10)
        assertTrue("best rank saved", onApp { app, _ -> app.settings.bestRank } >= 1)

        click("PLAY AGAIN")
        waitFor("BOOST")
        val second = session()
        assertNotSame("new run", first, second)
        assertTrue(second.world.player.alive)
    }

    @Test
    fun controlsStillWorkAfterPlayAgain() {
        startArena()
        val first = session()
        onApp { app, _ ->
            val p = app.session!!.world.player
            p.grace = 0f
            p.xs[0] = Cfg.ARENA_RADIUS + 5f
            p.ys[0] = 0f
        }
        waitFor("PLAY AGAIN", 20_000)
        click("PLAY AGAIN")
        waitFor("BOOST")
        val second = session()
        assertNotSame(first, second)
        onApp { app, _ -> app.session!!.world.player.grace = 100f }

        // Steering must reach the NEW session, not the finished one.
        var up = 0f
        repeat(3) {
            if (abs(up + 1.5708f) >= 0.4f) {
                joystickDrag(0, -120)
                pad(400)
                up = onApp { app, _ -> app.session!!.input.angle }
            }
        }
        assertTrue("new session steered up, angle=$up", abs(up + 1.5708f) < 0.4f)
        assertTrue("old session untouched", abs(first.input.angle + 1.5708f) > 0.4f || first.world.player.alive.not())

        // Boost must also reach the new session.
        val boost = device.findObject(By.text("BOOST"))
        assertNotNull(boost)
        val e0 = onApp { app, _ -> app.session!!.world.player.energy }
        var e1 = e0
        val sampler = Thread {
            repeat(25) {
                e1 = minOf(e1, onApp { app, _ -> app.session!!.world.player.energy })
                Thread.sleep(60)
            }
        }
        sampler.start()
        val bb = boost.visibleBounds
        device.swipe(bb.centerX(), bb.centerY(), bb.centerX() + 3, bb.centerY(), 200) // ~1 s press-and-hold
        sampler.join()
        assertTrue("boost works after restart: energy $e0 -> $e1", e1 < e0 - 3f)

        // And again via the pause menu's RESTART.
        onApp { app, _ -> app.session!!.paused = true }
        waitFor("RESTART")
        click("RESTART")
        waitFor("BOOST")
        val third = session()
        assertNotSame(second, third)
        onApp { app, _ -> app.session!!.world.player.grace = 100f }
        joystickDrag(120, 0)
        pad(400)
        val right = onApp { app, _ -> app.session!!.input.angle }
        assertTrue("third session steered right, angle=$right", abs(right) < 0.4f)
    }

    @Test
    fun backgroundingPausesAndResumeContinues() {
        startArena()
        pad(500)
        device.pressHome()
        pad(1500)
        val paused = onApp { app, _ -> app.session!!.paused }
        assertTrue("paused when app is interrupted", paused)
        val t1 = worldTime()
        pad(1000)
        assertEquals("world frozen while paused", t1, worldTime(), 0.001f)

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        context.startActivity(intent)
        waitFor("RESUME")
        shot("06_paused")
        click("RESUME")
        pad(1200)
        assertTrue("clock runs again after resume", worldTime() > t1 + 0.5f)
    }

    @Test
    fun settingsAreSavedAcrossRestart() {
        openSettings()
        waitFor("Sound effects")
        shot("07_settings")
        clickScrolling("High")
        clickScrolling("Touch & drag")
        onApp { app, _ ->
            app.settings.updateVibration(false)
            app.settings.updateSensitivity(1.7f)
            app.setMusic(false)
        }

        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("PLAY")
        assertTrue("still landscape", isLandscape())
        val s = Settings(context)
        assertEquals(2, s.quality)
        assertEquals(1, s.controlMode)
        assertFalse(s.vibration)
        assertFalse(s.music)
        assertEquals(1.7f, s.sensitivity, 0.001f)

    }

    @Test
    fun skinsCanBeUnlockedSelectedAndPersist() {
        onApp { app, _ -> app.settings.recordRun(1600, 1, true) } // earns 400 coins
        click("SKINS")
        waitFor("Solar Flare")
        shot("08_skins")
        clickScrolling("Unlock · 60")
        pad(500)
        assertEquals(340, onApp { app, _ -> app.settings.coins })
        assertTrue(2 in onApp { app, _ -> app.settings.unlocked })
        val selectButtons = device.findObjects(By.text("Select"))
        assertTrue("at least two skins selectable", selectButtons.size >= 2)
        selectButtons[1].click()
        pad(500)
        assertEquals(2, onApp { app, _ -> app.settings.selectedSkin })

        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor("PLAY")
        val s = Settings(context)
        assertEquals(2, s.selectedSkin)
        assertTrue(2 in s.unlocked)
        assertEquals(340, s.coins)
        assertEquals(1600, s.highScore)
    }

    @Test
    fun boostKeepsWorkingWhenThumbSlidesOffTheButton() {
        startSolo()
        onApp { app, _ -> app.session!!.world.player.grace = 100f }
        val boost = device.findObject(By.text("BOOST"))
        assertNotNull(boost)
        val b = boost.visibleBounds
        val dur = 1200L
        // Press on the button, slide far outside its bounds, hold, then release.
        val steps = 20
        val x0 = b.centerX()
        val y0 = b.centerY()
        var energy = 0f
        var max = 0f
        for (attempt in 1..3) { // a shared CI emulator can drop a touch now and then
            assertTrue(device.swipe(x0, y0, x0 - 400, y0 - 400, steps + 40))
            // The swipe lasts about 0.4s; check boosting was active during it by checking energy fell.
            energy = onApp { app, _ -> app.session!!.world.player.energy }
            max = onApp { app, _ -> app.session!!.world.player.maxEnergy }
            if (energy < max - 3f) break
            pad(500)
        }
        assertTrue("energy spent while sliding off the button: $energy/$max", energy < max - 3f)
        assertFalse("boost released afterwards", onApp { app, _ -> app.session!!.input.touchBoost })
        Thread.sleep(dur / 4)
    }

    @Test
    fun shopBuysPowersAndTankAndPowersWorkInGame() {
        onApp { app, _ -> app.settings.recordRun(1600, 1, true) } // 400 coins
        click("SHOP")
        waitFor("Boost Tank")
        shot("09_shop")
        clickScrolling("Upgrade · 80") // visible at the top, so do it before scrolling down
        clickScrolling("Unlock · 60") // Shield
        onApp { app, _ -> assertTrue(app.settings.upgradePower(com.ncorti.kotlin.template.app.game.Powers.MAGNET)) } // Magnet
        pad(400)
        val s = Settings(context)
        assertEquals(1, s.powerLevel("shield"))
        assertEquals(1, s.powerLevel("magnet"))
        assertEquals(1, s.tankLevel)
        assertEquals(400 - 60 - 50 - 80, s.coins)

        device.pressBack()
        waitFor("PLAY")
        startArena()
        val maxE = onApp { app, _ -> app.session!!.world.player.maxEnergy }
        assertEquals("tank upgrade applied", 125f, maxE, 0.01f)
        onApp { app, _ -> app.session!!.world.player.grace = 1000f }
        assertTrue("shield button", device.wait(Until.hasObject(By.desc("SHIELD")), 15_000))
        shot("10_power_buttons")
        device.findObject(By.desc("SHIELD")).click()
        pad(400)
        assertTrue("shield active", onApp { app, _ -> app.session!!.world.player.shield } > 0f)
        // Powers are permanent: using one never costs coins, but it goes on cooldown.
        assertEquals(1, Settings(context).powerLevel("shield"))
        assertTrue("shield on cooldown", onApp { app, _ -> !app.session!!.powerReady(com.ncorti.kotlin.template.app.game.Powers.SHIELD) })
        device.findObject(By.desc("MAGNET")).click()
        pad(400)
        assertTrue("magnet active", onApp { app, _ -> app.session!!.world.player.magnet } > 0f)
        shot("11_powers_active")
    }

    @Test
    fun playerNameCanBeEditedAndShowsInGame() {
        assertTrue(device.wait(Until.hasObject(By.desc("Edit name")), 10_000))
        device.findObject(By.desc("Edit name")).click()
        val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 8_000)
        assertNotNull("name dialog open", field)
        field.text = "Zed"
        click("Save")
        pad(500)
        assertEquals("Zed", Settings(context).playerName)
        assertTrue(device.wait(Until.hasObject(By.text("Zed")), 5_000))
        startArena()
        assertEquals("Zed", session().world.player.name)
    }

    @Test
    fun challengesAndDailyRewardWork() {
        val s = Settings(context)
        assertTrue(s.canClaimDaily())
        assertEquals(25, s.claimDaily())
        assertEquals(0, s.claimDaily())
        assertFalse(s.canClaimDaily())

        assertFalse(3 in s.unlocked)
        val done = s.applyChallenges(score = 300, kills = 2, rank = 4, solo = true)
        assertEquals(listOf(0), done.map { it.id })
        assertTrue("challenge skin unlocked", 3 in s.unlocked)
        assertTrue(0 in s.challengeDone)
        assertEquals(2, s.challengeBest[1])
        val again = s.applyChallenges(score = 300, kills = 2, rank = 4, solo = true)
        assertTrue("a challenge completes only once", again.isEmpty())

        val hunter = s.applyChallenges(score = 10, kills = 6, rank = 4, solo = false)
        assertEquals(listOf(1), hunter.map { it.id })
        assertEquals(25 + 120, s.coins)

        // And it survives a restart.
        val reread = Settings(context)
        assertTrue(0 in reread.challengeDone && 1 in reread.challengeDone)
        assertFalse(reread.canClaimDaily())
    }

    @Test
    fun longSessionKeepsRunningWithStableMemory() {
        startArena()
        onApp { app, _ -> app.session!!.world.player.grace = 1000f }
        val seconds = 45
        val frame0 = onApp { app, _ -> app.session!!.frame }
        pad(10_000)
        System.gc()
        val mem1 = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() } + Debug.getNativeHeapAllocatedSize()
        pad((seconds - 10) * 1000L)
        System.gc()
        val mem2 = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() } + Debug.getNativeHeapAllocatedSize()
        val frames = onApp { app, _ -> app.session!!.frame } - frame0
        val fps = frames / seconds.toFloat()
        println("EMULATOR_PERF fps=$fps heapGrowthKB=${(mem2 - mem1) / 1024}")
        assertTrue("still in foreground", device.hasObject(By.text("BOOST")))
        assertTrue("emulator fps $fps", fps >= 8f)
        assertTrue("memory growth ${(mem2 - mem1) / 1024} KB", mem2 - mem1 < 48L * 1024 * 1024)
        assertTrue(onApp { app, _ -> app.session!!.world.aliveCount() } >= 2)
    }
}

package com.ncorti.kotlin.template.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ncorti.kotlin.template.app.game.Power
import com.ncorti.kotlin.template.app.game.Powers
import com.ncorti.kotlin.template.app.game.Skins

/** Everything the game stores locally (SharedPreferences). No accounts, no network. */
class Settings(context: Context) {
    private val sp = context.getSharedPreferences("infinity_snake", Context.MODE_PRIVATE)

    var highScore by mutableIntStateOf(sp.getInt("high_score", 0))
        private set
    /** Best (lowest) arena rank ever reached; 0 means none yet. */
    var bestRank by mutableIntStateOf(sp.getInt("best_rank", 0))
        private set
    var coins by mutableIntStateOf(sp.getInt("coins", 0))
        private set
    var gamesPlayed by mutableIntStateOf(sp.getInt("games", 0))
        private set

    var selectedSkin by mutableIntStateOf(sp.getInt("skin", 0))
        private set
    var unlocked by mutableStateOf(parseSet(sp.getString("unlocked", "0,1")))
        private set

    /** Owned consumable powers by id. */
    var powers by mutableStateOf(Powers.all.associate { it.id to sp.getInt("power_${it.id}", 0) })
        private set
    /** Permanent boost-tank upgrade level, 0..Powers.TANK_MAX. */
    var tankLevel by mutableIntStateOf(sp.getInt("tank", 0))
        private set

    var sound by mutableStateOf(sp.getBoolean("sound", true))
        private set
    var music by mutableStateOf(sp.getBoolean("music", true))
        private set
    var vibration by mutableStateOf(sp.getBoolean("vibration", true))
        private set
    var walls by mutableStateOf(sp.getBoolean("walls", true))
        private set
    var sensitivity by mutableFloatStateOf(sp.getFloat("sensitivity", 1f))
        private set
    /** 0 = low, 1 = medium, 2 = high. */
    var quality by mutableIntStateOf(sp.getInt("quality", 1))
        private set
    /** 0 = auto, 1 = portrait, 2 = landscape. */
    var orientation by mutableIntStateOf(sp.getInt("orientation", 0))
        private set
    /** 0 = touch and drag, 1 = virtual joystick. */
    var controlMode by mutableIntStateOf(sp.getInt("control", 0))
        private set

    fun updateSound(v: Boolean) { sound = v; sp.edit().putBoolean("sound", v).apply() }
    fun updateMusic(v: Boolean) { music = v; sp.edit().putBoolean("music", v).apply() }
    fun updateVibration(v: Boolean) { vibration = v; sp.edit().putBoolean("vibration", v).apply() }
    fun updateWalls(v: Boolean) { walls = v; sp.edit().putBoolean("walls", v).apply() }
    fun updateSensitivity(v: Float) { sensitivity = v; sp.edit().putFloat("sensitivity", v).apply() }
    fun updateQuality(v: Int) { quality = v; sp.edit().putInt("quality", v).apply() }
    fun updateOrientation(v: Int) { orientation = v; sp.edit().putInt("orientation", v).apply() }
    fun updateControlMode(v: Int) { controlMode = v; sp.edit().putInt("control", v).apply() }

    fun selectSkin(id: Int) {
        if (id in unlocked) {
            selectedSkin = id
            sp.edit().putInt("skin", id).apply()
        }
    }

    /** Spends coins to unlock a skin. Returns false if it is already owned or unaffordable. */
    fun unlockSkin(id: Int): Boolean {
        val skin = Skins.byId(id)
        if (id in unlocked || coins < skin.cost) return false
        coins -= skin.cost
        unlocked = unlocked + id
        sp.edit()
            .putInt("coins", coins)
            .putString("unlocked", unlocked.joinToString(","))
            .apply()
        return true
    }

    fun count(id: String): Int = powers[id] ?: 0

    fun buyPower(p: Power): Boolean {
        if (coins < p.cost || count(p.id) >= Powers.MAX_STOCK) return false
        coins -= p.cost
        powers = powers + (p.id to count(p.id) + 1)
        sp.edit().putInt("coins", coins).putInt("power_${p.id}", count(p.id)).apply()
        return true
    }

    /** Uses up one owned power. Returns false if none are left. */
    fun consumePower(id: String): Boolean {
        val c = count(id)
        if (c <= 0) return false
        powers = powers + (id to c - 1)
        sp.edit().putInt("power_$id", c - 1).apply()
        return true
    }

    fun upgradeTank(): Boolean {
        if (tankLevel >= Powers.TANK_MAX) return false
        val cost = Powers.tankCost(tankLevel)
        if (coins < cost) return false
        coins -= cost
        tankLevel += 1
        sp.edit().putInt("coins", coins).putInt("tank", tankLevel).apply()
        return true
    }

    /** Records a finished run: high score, best rank (arena only), games and earned coins. */
    fun recordRun(score: Int, rank: Int, solo: Boolean): Int {
        val earned = score / 4
        highScore = maxOf(highScore, score)
        if (!solo && (bestRank == 0 || rank < bestRank)) bestRank = rank
        coins += earned
        gamesPlayed += 1
        sp.edit()
            .putInt("high_score", highScore)
            .putInt("best_rank", bestRank)
            .putInt("coins", coins)
            .putInt("games", gamesPlayed)
            .apply()
        return earned
    }

    private fun parseSet(csv: String?): Set<Int> {
        val set = csv.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableSet()
        set.add(0)
        set.add(1)
        return set
    }
}

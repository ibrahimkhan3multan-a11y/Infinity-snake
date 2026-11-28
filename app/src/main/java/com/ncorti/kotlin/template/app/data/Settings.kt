package com.ncorti.kotlin.template.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ncorti.kotlin.template.app.game.Cfg
import com.ncorti.kotlin.template.app.game.Challenge
import com.ncorti.kotlin.template.app.game.KillFx
import com.ncorti.kotlin.template.app.game.KillFxs
import com.ncorti.kotlin.template.app.game.Skin
import com.ncorti.kotlin.template.app.game.SkinLab
import com.ncorti.kotlin.template.app.game.Challenges
import com.ncorti.kotlin.template.app.game.Codes
import com.ncorti.kotlin.template.app.game.Quest
import com.ncorti.kotlin.template.app.game.Quests
import com.ncorti.kotlin.template.app.game.Rebirth
import com.ncorti.kotlin.template.app.game.RunStats
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
    /** How many times you have been reborn (see [Rebirth]). */
    var rebirths by mutableIntStateOf(sp.getInt("rebirths", 0))
        private set

    var selectedSkin by mutableIntStateOf(sp.getInt("skin", 0))
        private set
    var unlocked by mutableStateOf(parseSet(sp.getString("unlocked", "0,1")))
        private set

    /** Skins made in the Skin Lab. */
    var customSkins by mutableStateOf(loadCustomSkins().also { Skins.custom = it })
        private set

    /** Owned kill animations and the equipped one (0 = the free Pop). */
    var killFxOwned by mutableStateOf(parseSet(sp.getString("killfx_owned", "0"), addDefaults = false) + 0)
        private set
    var killFxSelected by mutableIntStateOf(sp.getInt("killfx", 0))
        private set

    /** Permanent power levels by id (0 = locked). */
    var powers by mutableStateOf(Powers.all.associate { it.id to sp.getInt("plevel_${it.id}", 0) })
        private set
    /** Permanent boost-tank upgrade level, 0..Powers.TANK_MAX. */
    var tankLevel by mutableIntStateOf(sp.getInt("tank", 0))
        private set

    var playerName by mutableStateOf(sp.getString("name", null) ?: "Player${100 + (System.nanoTime() % 900).toInt().let { if (it < 0) -it else it }}")
        private set
    /** Menu mode toggle: false = Arena (with bots), true = Solo. */
    /** One of GameMode (Classic, Bomb Rain, ...). */
    var gameMode by mutableStateOf(sp.getInt("game_mode", 0).coerceIn(0, 3))
        private set
    /** Tilted 3D camera instead of the flat top-down view. */
    var view3d by mutableStateOf(sp.getBoolean("view3d", false))
        private set
    var arenaSize by mutableStateOf(sp.getInt("arena_size", 1).coerceIn(0, Cfg.ARENA_SIZES.size - 1))
    fun updateArenaSize(v: Int) { arenaSize = v.coerceIn(0, Cfg.ARENA_SIZES.size - 1); sp.edit().putInt("arena_size", arenaSize).apply() }
    fun updateGameMode(v: Int) { gameMode = v.coerceIn(0, 3); sp.edit().putInt("game_mode", gameMode).apply() }
    fun updateView3d(v: Boolean) { view3d = v; sp.edit().putBoolean("view3d", v).apply() }

    var soloMode by mutableStateOf(sp.getBoolean("solo_mode", false))
        private set
    var challengeDone by mutableStateOf(parseSet(sp.getString("ch_done", ""), addDefaults = false))
        private set
    var challengeBest by mutableStateOf(Challenges.all.associate { it.id to sp.getInt("ch_best_${it.id}", 0) })
        private set
    /** Day number (UTC days since 1970) of the last daily reward claim; -1 = never. */
    var dailyDay by mutableIntStateOf(sp.getInt("daily_day", -1))
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
    /** 0 = on-screen joystick (default), 1 = touch and drag. */
    var controlMode by mutableIntStateOf(sp.getInt("control", 0))

    /** Address of the relay server used for online rooms (empty until the player enters one). */
    var serverUrl by mutableStateOf(sp.getString("server", "") ?: "")

    /** Last Wi-Fi host address typed when joining a friend on the same network. */
    var lanHost by mutableStateOf(sp.getString("lan", "") ?: "")
        private set

    fun updateName(v: String) {
        val clean = v.trim().take(12)
        if (clean.isEmpty()) return
        playerName = clean
        sp.edit().putString("name", clean).apply()
    }

    fun updateSoloMode(v: Boolean) { soloMode = v; sp.edit().putBoolean("solo_mode", v).apply() }

    fun today(): Int = (System.currentTimeMillis() / 86_400_000L).toInt()
    fun canClaimDaily(): Boolean = dailyDay != today()

    /** Claims the once-a-day coin reward. Returns the coins granted, or 0 if already claimed today. */
    fun claimDaily(): Int {
        if (!canClaimDaily()) return 0
        dailyDay = today()
        coins += DAILY_COINS
        sp.edit().putInt("daily_day", dailyDay).putInt("coins", coins).apply()
        return DAILY_COINS
    }

    /** Applies a finished run to the challenges; returns the ones completed by it (rewards are granted). */
    fun applyChallenges(score: Int, kills: Int, rank: Int, solo: Boolean): List<Challenge> {
        val done = ArrayList<Challenge>()
        val best = challengeBest.toMutableMap()
        val ed = sp.edit()
        for (c in Challenges.all) {
            if (c.id in challengeDone) continue
            val p = Challenges.progress(c.id, score, kills, rank, solo)
            if (p > (best[c.id] ?: 0)) {
                best[c.id] = minOf(p, c.goal)
                ed.putInt("ch_best_${c.id}", best[c.id]!!)
            }
            if (p >= c.goal) {
                done.add(c)
                challengeDone = challengeDone + c.id
                coins += c.rewardCoins
                c.rewardSkin?.let { unlocked = unlocked + it }
            }
        }
        challengeBest = best
        ed.putString("ch_done", challengeDone.joinToString(","))
            .putInt("coins", coins)
            .putString("unlocked", unlocked.joinToString(","))
            .apply()
        return done
    }

    fun updateSound(v: Boolean) { sound = v; sp.edit().putBoolean("sound", v).apply() }
    fun updateMusic(v: Boolean) { music = v; sp.edit().putBoolean("music", v).apply() }
    fun updateVibration(v: Boolean) { vibration = v; sp.edit().putBoolean("vibration", v).apply() }
    fun updateWalls(v: Boolean) { walls = v; sp.edit().putBoolean("walls", v).apply() }
    fun updateSensitivity(v: Float) { sensitivity = v; sp.edit().putFloat("sensitivity", v).apply() }
    fun updateQuality(v: Int) { quality = v; sp.edit().putInt("quality", v).apply() }
    fun updateLanHost(v: String) { lanHost = v.trim(); sp.edit().putString("lan", lanHost).apply() }
    fun updateServerUrl(v: String) { serverUrl = v.trim(); sp.edit().putString("server", serverUrl).apply() }
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

    // ---------------------------------------------------------------- skin lab

    private fun loadCustomSkins(): List<Skin> {
        val raw = sp.getString("custom_skins", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return try {
            val a = org.json.JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Skin(o.getInt("id"), o.getString("name"), 0, o.getInt("head"), o.getInt("tail"), o.getBoolean("glow"), o.getBoolean("rainbow"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveCustomSkins() {
        val a = org.json.JSONArray()
        for (k in customSkins) {
            a.put(
                org.json.JSONObject().put("id", k.id).put("name", k.name).put("head", k.head).put("tail", k.tail)
                    .put("glow", k.glow).put("rainbow", k.rainbow),
            )
        }
        sp.edit().putString("custom_skins", a.toString()).apply()
        Skins.custom = customSkins
    }

    /** Buys a Skin Lab creation for [SkinLab.COST] coins and equips it. False if unaffordable or the slots are full. */
    fun createCustomSkin(look: SkinLab.Look): Boolean {
        if (coins < SkinLab.COST || customSkins.size >= SkinLab.MAX_CUSTOM) return false
        val id = maxOf(SkinLab.FIRST_ID, (customSkins.maxOfOrNull { it.id } ?: 0) + 1)
        coins -= SkinLab.COST
        customSkins = customSkins + Skin(id, look.name, 0, look.head, look.tail, look.glow, look.rainbow)
        unlocked = unlocked + id
        selectedSkin = id
        sp.edit()
            .putInt("coins", coins)
            .putString("unlocked", unlocked.joinToString(","))
            .putInt("skin", id)
            .apply()
        saveCustomSkins()
        return true
    }

    /** Removes a Skin Lab creation (no refund) to free a slot. */
    fun deleteCustomSkin(id: Int) {
        if (customSkins.none { it.id == id }) return
        customSkins = customSkins.filter { it.id != id }
        unlocked = unlocked - id
        if (selectedSkin == id) selectedSkin = 0
        sp.edit().putString("unlocked", unlocked.joinToString(",")).putInt("skin", selectedSkin).apply()
        saveCustomSkins()
    }

    // ---------------------------------------------------------------- kill animations

    val killFx: KillFx get() = KillFxs.byId(killFxSelected)

    fun buyKillFx(id: Int): Boolean {
        val fx = KillFxs.byId(id)
        if (id in killFxOwned || coins < fx.cost) return false
        coins -= fx.cost
        killFxOwned = killFxOwned + id
        sp.edit().putInt("coins", coins).putString("killfx_owned", killFxOwned.joinToString(",")).apply()
        selectKillFx(id)
        return true
    }

    fun selectKillFx(id: Int) {
        if (id !in killFxOwned) return
        killFxSelected = id
        sp.edit().putInt("killfx", id).apply()
    }

    /** Level of a permanent power: 0 = locked, 1..Powers.MAX_LEVEL. */
    fun powerLevel(id: String): Int = powers[id] ?: 0

    /** Unlocks the power (level 0 -> 1) or upgrades it one level. False if maxed or unaffordable. */
    fun upgradePower(p: Power): Boolean {
        val level = powerLevel(p.id)
        if (level >= Powers.MAX_LEVEL) return false
        val cost = p.costFor(level)
        if (coins < cost) return false
        coins -= cost
        powers = powers + (p.id to level + 1)
        sp.edit().putInt("coins", coins).putInt("plevel_${p.id}", level + 1).apply()
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
    fun recordRun(score: Int, rank: Int, solo: Boolean, kills: Int = 0, bonusCoins: Int = 0): Int {
        val mult = Rebirth.coinMultiplier(rebirths) + killFx.coinPercent / 100f
        val earned = ((score / 4 + kills * 5 + bonusCoins) * mult).toInt()
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


    // ---------------------------------------------------------------- backup and restore

    /**
     * Everything saved on this phone as one line of text, so progress can be kept across a reinstall
     * or moved to another phone. Paste it into Restore on the new install.
     */
    fun exportBackup(): String {
        val o = org.json.JSONObject()
        for ((k, v) in sp.all) {
            val entry = org.json.JSONArray()
            when (v) {
                is Int -> entry.put("i").put(v)
                is Long -> entry.put("l").put(v)
                is Float -> entry.put("f").put(v.toDouble())
                is Boolean -> entry.put("b").put(v)
                is String -> entry.put("s").put(v)
                else -> continue
            }
            o.put(k, entry)
        }
        val bytes = o.toString().toByteArray(Charsets.UTF_8)
        return BACKUP_PREFIX + android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP)
    }

    /** Replaces everything saved with the backup. False (and nothing changed) if the text isn't a backup. */
    fun importBackup(code: String): Boolean {
        val text = code.trim().filter { !it.isWhitespace() }
        if (!text.startsWith(BACKUP_PREFIX)) return false
        val o = try {
            val raw = android.util.Base64.decode(text.removePrefix(BACKUP_PREFIX), android.util.Base64.URL_SAFE)
            org.json.JSONObject(String(raw, Charsets.UTF_8))
        } catch (e: Exception) {
            return false
        }
        val ed = sp.edit().clear()
        for (k in o.keys()) {
            val e = o.optJSONArray(k) ?: return false
            when (e.optString(0)) {
                "i" -> ed.putInt(k, e.getInt(1))
                "l" -> ed.putLong(k, e.getLong(1))
                "f" -> ed.putFloat(k, e.getDouble(1).toFloat())
                "b" -> ed.putBoolean(k, e.getBoolean(1))
                "s" -> ed.putString(k, e.getString(1))
                else -> return false
            }
        }
        return ed.commit()
    }

    // ---------------------------------------------------------------- rebirth

    fun rebirthCost(): Int? = Rebirth.cost(rebirths)
    fun canRebirth(): Boolean = rebirthCost()?.let { coins >= it } ?: false

    /**
     * Starts over: you lose ALL coins and every skin except the two free ones, and gain the next
     * rebirth's bonuses. Powers, upgrades, scores and quests are kept. False if you can't afford it.
     */
    fun rebirth(): Boolean {
        if (!canRebirth()) return false
        coins = 0
        unlocked = setOf(0, 1) + customSkins.map { it.id }
        selectedSkin = 0
        rebirths += 1
        sp.edit()
            .putInt("coins", 0)
            .putString("unlocked", unlocked.joinToString(","))
            .putInt("skin", 0)
            .putInt("rebirths", rebirths)
            .apply()
        return true
    }

    fun devSetRebirths(n: Int) {
        rebirths = n.coerceIn(0, Rebirth.MAX)
        sp.edit().putInt("rebirths", rebirths).apply()
    }

    // ---------------------------------------------------------------- daily quests

    /** Day number the saved quest progress belongs to. */
    private var questDay by mutableIntStateOf(sp.getInt("quest_day", -1))
    private var questProgress by mutableStateOf(parseMap(sp.getString("quest_prog", "")))
    private var questClaimed by mutableStateOf(parseSet(sp.getString("quest_claimed", ""), addDefaults = false))
    private var questBonusTaken by mutableStateOf(sp.getBoolean("quest_bonus", false))

    fun dailyQuests(): List<Quest> = Quests.forDay(today())

    private fun rollQuestDay() {
        if (questDay == today()) return
        questDay = today()
        questProgress = emptyMap()
        questClaimed = emptySet()
        questBonusTaken = false
        saveQuests()
    }

    private fun saveQuests() {
        sp.edit()
            .putInt("quest_day", questDay)
            .putString("quest_prog", questProgress.entries.joinToString(",") { "${it.key}:${it.value}" })
            .putString("quest_claimed", questClaimed.joinToString(","))
            .putBoolean("quest_bonus", questBonusTaken)
            .apply()
    }

    fun questProgressOf(q: Quest): Int = if (questDay == today()) questProgress[q.id] ?: 0 else 0
    fun questDone(q: Quest): Boolean = questProgressOf(q) >= q.goal
    fun questClaimed(q: Quest): Boolean = questDay == today() && q.id in questClaimed
    fun questsClaimable(): Int = dailyQuests().count { questDone(it) && !questClaimed(it) }
    fun allQuestsClaimed(): Boolean = dailyQuests().all { questClaimed(it) }
    fun bonusTaken(): Boolean = questDay == today() && questBonusTaken

    /** Adds a finished run to today's quests. */
    fun recordQuests(run: RunStats) {
        rollQuestDay()
        val map = questProgress.toMutableMap()
        for (q in dailyQuests()) map[q.id] = q.after(map[q.id] ?: 0, run)
        questProgress = map
        saveQuests()
    }

    /** Pays out a finished quest. Returns the coins granted (0 if not done or already claimed). */
    fun claimQuest(q: Quest): Int {
        rollQuestDay()
        if (!questDone(q) || q.id in questClaimed) return 0
        questClaimed = questClaimed + q.id
        coins += q.reward
        var granted = q.reward
        if (!questBonusTaken && dailyQuests().all { it.id in questClaimed }) {
            questBonusTaken = true
            coins += Quests.ALL_DONE_BONUS
            granted += Quests.ALL_DONE_BONUS
        }
        sp.edit().putInt("coins", coins).apply()
        saveQuests()
        return granted
    }

    // ---------------------------------------------------------------- redeem codes

    var redeemedNames by mutableStateOf((sp.getString("redeemed_codes", "") ?: "").split(",").filter { it.isNotEmpty() }.toSet())
        private set

    sealed class Redeem {
        class Ok(val text: String) : Redeem()
        object Used : Redeem()
        object Invalid : Redeem()
        object Secret : Redeem()
    }

    fun redeem(raw: String): Redeem {
        if (Codes.isSecret(raw)) {
            devUnlock()
            return Redeem.Secret
        }
        val code = Codes.normalize(raw)
        val reward = Codes.find(raw) ?: return Redeem.Invalid
        if (code in redeemedNames) return Redeem.Used
        redeemedNames = redeemedNames + code
        coins += reward.coins
        reward.skin?.let { unlocked = unlocked + it }
        reward.power?.let { id ->
            if (powerLevel(id) == 0) {
                powers = powers + (id to 1)
                sp.edit().putInt("plevel_$id", 1).apply()
            }
        }
        sp.edit()
            .putString("redeemed_codes", redeemedNames.joinToString(","))
            .putInt("coins", coins)
            .putString("unlocked", unlocked.joinToString(","))
            .apply()
        return Redeem.Ok(reward.text)
    }

    // ---------------------------------------------------------------- tester menu

    /** Tester options. Only the tester menu can turn these on. */
    var devNoCooldown by mutableStateOf(sp.getBoolean("dev_nocd", false))
        private set
    var devGod by mutableStateOf(sp.getBoolean("dev_god", false))
        private set
    var devBigStart by mutableStateOf(sp.getBoolean("dev_big", false))
        private set

    /** True once the tester code was entered: the floating mod-menu button may be shown. */
    var devUnlocked by mutableStateOf(sp.getBoolean("dev_unlocked", false))
        private set
    /** Whether the floating button is currently on screen. */
    var devButton by mutableStateOf(sp.getBoolean("dev_button", false))
        private set
    /** Saved position of the floating button in pixels (-1 = not placed yet). */
    var devX by mutableFloatStateOf(sp.getFloat("dev_x", -1f))
        private set
    var devY by mutableFloatStateOf(sp.getFloat("dev_y", -1f))
        private set

    fun devUnlock() {
        devUnlocked = true
        devButton = true
        sp.edit().putBoolean("dev_unlocked", true).putBoolean("dev_button", true).apply()
    }

    fun devSetButton(v: Boolean) { devButton = v; sp.edit().putBoolean("dev_button", v).apply() }

    fun devSetPos(x: Float, y: Float) {
        devX = x
        devY = y
        sp.edit().putFloat("dev_x", x).putFloat("dev_y", y).apply()
    }

    fun devSetNoCooldown(v: Boolean) { devNoCooldown = v; sp.edit().putBoolean("dev_nocd", v).apply() }
    fun devSetGod(v: Boolean) { devGod = v; sp.edit().putBoolean("dev_god", v).apply() }
    fun devSetBigStart(v: Boolean) { devBigStart = v; sp.edit().putBoolean("dev_big", v).apply() }

    fun devAddCoins(n: Int) {
        coins = (coins + n).coerceAtLeast(0)
        sp.edit().putInt("coins", coins).apply()
    }

    fun devSetCoins(n: Int) {
        coins = n.coerceAtLeast(0)
        sp.edit().putInt("coins", coins).apply()
    }

    fun devUnlockAllSkins() {
        unlocked = Skins.all.map { it.id }.toSet()
        sp.edit().putString("unlocked", unlocked.joinToString(",")).apply()
    }

    fun devLockSkins() {
        unlocked = setOf(0, 1)
        selectedSkin = 0
        sp.edit().putString("unlocked", "0,1").putInt("skin", 0).apply()
    }

    fun devSetAllPowers(level: Int) {
        powers = Powers.all.associate { it.id to level.coerceIn(0, Powers.MAX_LEVEL) }
        tankLevel = if (level > 0) Powers.TANK_MAX else 0
        val ed = sp.edit().putInt("tank", tankLevel)
        for ((id, l) in powers) ed.putInt("plevel_$id", l)
        ed.apply()
    }

    /** Makes today's quests brand new and unclaimed, or finishes them all. */
    fun devQuests(complete: Boolean) {
        questDay = today()
        questClaimed = emptySet()
        questBonusTaken = false
        questProgress = if (complete) dailyQuests().associate { it.id to it.goal } else emptyMap()
        saveQuests()
    }

    fun devResetDailyReward() {
        dailyDay = -1
        sp.edit().putInt("daily_day", -1).apply()
    }

    fun devResetCodes() {
        redeemedNames = emptySet()
        sp.edit().putString("redeemed_codes", "").apply()
    }

    fun devResetChallenges() {
        challengeDone = emptySet()
        challengeBest = Challenges.all.associate { it.id to 0 }
        val ed = sp.edit().putString("ch_done", "")
        for (c in Challenges.all) ed.putInt("ch_best_${c.id}", 0)
        ed.apply()
    }

    fun devSetHighScore(n: Int) {
        highScore = n.coerceAtLeast(0)
        sp.edit().putInt("high_score", highScore).apply()
    }

    private fun parseMap(csv: String?): Map<Int, Int> =
        csv.orEmpty().split(",").mapNotNull {
            val p = it.split(":")
            val k = p.getOrNull(0)?.toIntOrNull()
            val v = p.getOrNull(1)?.toIntOrNull()
            if (k != null && v != null) k to v else null
        }.toMap()

    private fun parseSet(csv: String?, addDefaults: Boolean = true): Set<Int> {
        val set = csv.orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }.toMutableSet()
        if (addDefaults) {
            set.add(0)
            set.add(1)
        }
        return set
    }

    companion object {
        const val DAILY_COINS = 25
        const val BACKUP_PREFIX = "IS1-"
    }
}

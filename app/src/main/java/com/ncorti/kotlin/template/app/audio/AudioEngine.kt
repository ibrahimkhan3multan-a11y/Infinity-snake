package com.ncorti.kotlin.template.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** Every sound effect in the game. All of them are synthesized in code (no audio files). */
enum class Sfx(val volume: Float, val minGapMs: Long) {
    CLICK(0.45f, 40),
    EAT(0.5f, 90),
    BOOM(0.9f, 200),
    KILL(0.7f, 150),
    BOOST(0.5f, 350),
    SHIELD(0.6f, 200),
    MAGNET(0.6f, 200),
    OVERDRIVE(0.6f, 200),
    COIN(0.6f, 120),
    FANFARE(0.75f, 500),
    START(0.6f, 300),
    WARN(0.5f, 800),
    DENIED(0.5f, 150),
}

enum class MusicMode { MENU, GAME }

/**
 * Sound and music, generated at runtime so the app stays tiny and fully offline. Every call is
 * wrapped so an audio failure can never crash the game.
 */
class AudioEngine {
    var soundEnabled = true
    var musicEnabled = true
        set(value) {
            field = value
            syncMusic()
        }
    var foreground = true
        set(value) {
            field = value
            syncMusic()
        }
    var musicMode = MusicMode.MENU
        set(value) {
            if (field != value) {
                field = value
                syncMusic()
            }
        }

    private val music = arrayOfNulls<AudioTrack>(2)
    private val musicBuilding = BooleanArray(2)
    private val sfxTracks = HashMap<Sfx, AudioTrack?>()
    private val lastPlayed = HashMap<Sfx, Long>()

    // ------------------------------------------------------------------ music

    private fun syncMusic() {
        try {
            val want = musicMode.ordinal
            for (i in 0..1) {
                val t = music[i]
                if (i != want || !musicEnabled || !foreground) {
                    if (t != null && t.playState == AudioTrack.PLAYSTATE_PLAYING) t.pause()
                }
            }
            if (musicEnabled && foreground) {
                val t = music[want]
                if (t == null) {
                    buildMusicAsync(want)
                } else {
                    t.setVolume(if (want == 0) 0.30f else 0.34f)
                    t.play()
                }
            }
        } catch (e: IllegalStateException) {
            music[musicMode.ordinal] = null
        }
    }

    private fun buildMusicAsync(slot: Int) {
        if (musicBuilding[slot]) return
        musicBuilding[slot] = true
        Thread {
            val track = makeStatic(if (slot == 0) genMenuMusic() else genGameMusic(), loop = true)
            music[slot] = track
            musicBuilding[slot] = false
            if (musicEnabled && foreground && musicMode.ordinal == slot) {
                try {
                    track?.setVolume(if (slot == 0) 0.30f else 0.34f)
                    track?.play()
                } catch (e: IllegalStateException) {
                    music[slot] = null
                }
            }
        }.start()
    }

    // ------------------------------------------------------------------ effects

    fun play(sfx: Sfx) {
        if (!soundEnabled || !foreground) return
        val now = SystemClock.uptimeMillis()
        val last = lastPlayed[sfx] ?: 0L
        if (now - last < sfx.minGapMs) return
        lastPlayed[sfx] = now
        val track = sfxTracks.getOrPut(sfx) { makeStatic(generate(sfx), loop = false) }
        try {
            track ?: return
            track.stop()
            track.reloadStaticData()
            track.setVolume(sfx.volume)
            track.play()
        } catch (e: IllegalStateException) {
            sfxTracks[sfx] = null
        }
    }

    fun playEat() = play(Sfx.EAT)
    fun playBoom() = play(Sfx.BOOM)

    fun release() {
        try {
            for (t in music) t?.release()
            for (t in sfxTracks.values) t?.release()
        } catch (e: IllegalStateException) {
            // Ignore.
        }
        for (i in 0..1) music[i] = null
        sfxTracks.clear()
    }

    private fun makeStatic(samples: ShortArray, loop: Boolean): AudioTrack? = try {
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(samples, 0, samples.size)
        if (loop) t.setLoopPoints(0, samples.size, -1)
        t
    } catch (e: IllegalArgumentException) {
        null
    } catch (e: UnsupportedOperationException) {
        null
    }

    // ------------------------------------------------------------------ synthesis

    /** Renders [ms] milliseconds of f(t seconds, p progress 0..1) in -1..1, with click-free edges. */
    private fun synth(ms: Int, vol: Double, f: (Double, Double) -> Double): ShortArray {
        val n = RATE * ms / 1000
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / RATE
            val p = i.toDouble() / n
            val attack = min(1.0, i / (RATE * 0.004))
            val release = min(1.0, (n - i) / (RATE * 0.012))
            val v = f(t, p) * attack * release * vol
            out[i] = (v * 32767).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun tau(x: Double) = 2 * PI * x

    private fun generate(sfx: Sfx): ShortArray {
        val rnd = Random(sfx.ordinal + 11)
        return when (sfx) {
            Sfx.CLICK -> synth(45, 0.35) { t, p -> sin(tau(1000 * t)) * exp(-5 * p) }
            Sfx.EAT -> {
                var ph = 0.0
                synth(70, 0.3) { _, p ->
                    ph += tau(600 + 500 * p) / RATE
                    sin(ph) * exp(-4 * p)
                }
            }
            Sfx.BOOM -> {
                var ph = 0.0
                var lp = 0.0
                synth(450, 0.45) { _, p ->
                    ph += tau(130 - 90 * p) / RATE
                    lp += 0.15 * ((rnd.nextDouble() * 2 - 1) - lp)
                    (sin(ph) * 0.7 + lp * 0.9) * exp(-5 * p)
                }
            }
            Sfx.KILL -> synth(300, 0.4) { t, p ->
                val seg = (p * 3).toInt().coerceAtMost(2)
                val lp = p * 3 - seg
                val f = doubleArrayOf(659.0, 880.0, 1175.0)[seg]
                (sin(tau(f * t)) + 0.3 * sin(tau(2 * f * t))) * exp(-3.5 * lp)
            }
            Sfx.BOOST -> {
                var lp = 0.0
                var ph = 0.0
                synth(280, 0.5) { _, p ->
                    lp += (0.04 + 0.5 * p) * ((rnd.nextDouble() * 2 - 1) - lp)
                    ph += tau(170 + 700 * p) / RATE
                    val env = sin(PI * p)
                    (lp * 0.9 + sin(ph) * 0.3) * env
                }
            }
            Sfx.SHIELD -> synth(480, 0.4) { t, p ->
                val tr = 0.6 + 0.4 * sin(tau(14 * t))
                (sin(tau(660 * t)) + 0.6 * sin(tau(990 * t)) + 0.4 * sin(tau(1320 * t))) * 0.5 * tr * exp(-2.5 * p)
            }
            Sfx.MAGNET -> synth(400, 0.45) { t, p ->
                sin(tau(400 * t) + 3.0 * sin(tau(9 * t))) * exp(-3 * p)
            }
            Sfx.OVERDRIVE -> {
                var ph = 0.0
                synth(480, 0.4) { _, p ->
                    ph += tau(200 + 1100 * p * p) / RATE
                    (sin(ph) + 0.4 * sin(2 * ph)) * exp(-1.2 * p)
                }
            }
            Sfx.COIN -> synth(280, 0.4) { t, p ->
                val first = p < 0.4
                val f = if (first) 1319.0 else 1760.0
                val lp = if (first) p / 0.4 else (p - 0.4) / 0.6
                (sin(tau(f * t)) + 0.35 * sin(tau(2 * f * t))) * exp(-4 * lp)
            }
            Sfx.FANFARE -> synth(900, 0.4) { t, _ ->
                val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
                val idx = (t / 0.14).toInt().coerceAtMost(3)
                val lt = t - idx * 0.14
                val f = notes[idx]
                (sin(tau(f * t)) + 0.4 * sin(tau(2 * f * t)) + 0.2 * sin(tau(3 * f * t))) * exp((if (idx == 3) -2.0 else -3.0) * lt)
            }
            Sfx.START -> {
                var ph = 0.0
                synth(320, 0.4) { _, p ->
                    ph += tau(300 + 900 * p) / RATE
                    sin(ph) * sin(PI * p)
                }
            }
            Sfx.WARN -> synth(250, 0.35) { t, _ ->
                if (t % 0.125 < 0.06) sin(tau(880 * t)) else 0.0
            }
            Sfx.DENIED -> {
                var ph = 0.0
                synth(150, 0.3) { _, p ->
                    ph += tau(180 - 60 * p) / RATE
                    (if (sin(ph) > 0) 1.0 else -1.0) * exp(-3 * p)
                }
            }
        }
    }

    private fun kick(t: Double) = sin(tau((55 + 95 * exp(-26 * t)) * t)) * exp(-11 * t)

    /** Calm slow arpeggio over a soft bass pulse (about 10 seconds). */
    private fun genMenuMusic(): ShortArray {
        val chords = arrayOf(
            doubleArrayOf(220.00, 261.63, 329.63), // Am
            doubleArrayOf(174.61, 220.00, 261.63), // F
            doubleArrayOf(261.63, 329.63, 392.00), // C
            doubleArrayOf(196.00, 246.94, 293.66), // G
        )
        val bass = doubleArrayOf(110.00, 87.31, 130.81, 98.00)
        val noteLen = RATE * 3 / 10
        val notes = 32
        val out = ShortArray(noteLen * notes)
        val pattern = intArrayOf(0, 1, 2, 1, 0, 1, 2, 1)
        for (e in 0 until notes) {
            val chord = e / 8
            val f = chords[chord][pattern[e % 8]] * 2
            for (i in 0 until noteLen) {
                val t = i.toDouble() / RATE
                val env = exp(-5.0 * t)
                val lead = (sin(tau(f * t)) + 0.3 * sin(tau(2 * f * t))) * env * 0.16
                var b = 0.0
                if (e % 4 == 0) b = sin(tau(bass[chord] * t)) * exp(-2.5 * t) * 0.28
                out[e * noteLen + i] = ((lead + b) * 32767).toInt().coerceIn(-32767, 32767).toShort()
            }
        }
        return out
    }

    /** Faster driving loop for gameplay: arpeggio, bass, kick and hi-hat (about 9.6 seconds). */
    private fun genGameMusic(): ShortArray {
        val chords = arrayOf(
            doubleArrayOf(220.00, 261.63, 329.63),
            doubleArrayOf(174.61, 220.00, 261.63),
            doubleArrayOf(261.63, 329.63, 392.00),
            doubleArrayOf(196.00, 246.94, 293.66),
        )
        val bass = doubleArrayOf(55.00, 43.65, 65.41, 49.00)
        val noteLen = RATE * 15 / 100
        val notes = 64
        val out = ShortArray(noteLen * notes)
        val pattern = intArrayOf(0, 2, 1, 2, 0, 2, 1, 2)
        val rnd = Random(3)
        for (e in 0 until notes) {
            val chord = e / 16
            val f = chords[chord][pattern[e % 8]] * 2
            for (i in 0 until noteLen) {
                val t = i.toDouble() / RATE
                val lead = (sin(tau(f * t)) + 0.25 * sin(tau(2 * f * t))) * exp(-7.0 * t) * 0.13
                val bassV = sin(tau(bass[chord] * 2 * t)) * 0.16 * exp(-1.5 * t)
                val k = if (e % 4 == 0) kick(t) * 0.34 else 0.0
                val hat = if (e % 2 == 1) (rnd.nextDouble() * 2 - 1) * exp(-70 * t) * 0.07 else 0.0
                out[e * noteLen + i] = ((lead + bassV + k + hat) * 32767).toInt().coerceIn(-32767, 32767).toShort()
            }
        }
        return out
    }

    companion object {
        const val RATE = 22050
    }
}

/** Vibration without any View dependency; requires the VIBRATE permission declared in the manifest. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: SecurityException) {
        null
    }

    fun buzz(ms: Long) {
        try {
            val v = vibrator ?: return
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        } catch (e: SecurityException) {
            // Ignore.
        }
    }
}

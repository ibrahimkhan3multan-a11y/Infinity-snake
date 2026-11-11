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
import kotlin.math.sin
import kotlin.random.Random

/**
 * All sound is synthesized in code (no audio files), so the app stays tiny and fully offline.
 * Every call is wrapped so an audio failure can never crash the game.
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

    private var music: AudioTrack? = null
    private var musicBuilding = false
    private var pop: AudioTrack? = null
    private var boom: AudioTrack? = null
    private var lastPop = 0L

    private fun syncMusic() {
        try {
            if (musicEnabled && foreground) {
                if (music == null) {
                    buildMusicAsync()
                } else {
                    music?.setVolume(0.35f)
                    music?.play()
                }
            } else {
                music?.pause()
            }
        } catch (e: IllegalStateException) {
            music = null
        }
    }

    private fun buildMusicAsync() {
        if (musicBuilding) return
        musicBuilding = true
        Thread {
            val track = makeStatic(genMusic(), loop = true)
            music = track
            musicBuilding = false
            if (musicEnabled && foreground) {
                try {
                    track?.setVolume(0.35f)
                    track?.play()
                } catch (e: IllegalStateException) {
                    music = null
                }
            }
        }.start()
    }

    fun playEat() {
        if (!soundEnabled || !foreground) return
        val now = SystemClock.uptimeMillis()
        if (now - lastPop < 90) return
        lastPop = now
        if (pop == null) pop = makeStatic(genPop(), loop = false)
        restart(pop, 0.5f)
    }

    fun playBoom() {
        if (!soundEnabled || !foreground) return
        if (boom == null) boom = makeStatic(genBoom(), loop = false)
        restart(boom, 0.9f)
    }

    private fun restart(t: AudioTrack?, volume: Float) {
        try {
            t ?: return
            t.stop()
            t.reloadStaticData()
            t.setVolume(volume)
            t.play()
        } catch (e: IllegalStateException) {
            // Ignore: sound effects are best effort.
        }
    }

    fun release() {
        try {
            music?.release()
            pop?.release()
            boom?.release()
        } catch (e: IllegalStateException) {
            // Ignore.
        }
        music = null
        pop = null
        boom = null
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

    private fun genPop(): ShortArray {
        val n = RATE * 60 / 1000
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            phase += 2 * PI * (600 + 500 * t) / RATE
            out[i] = (sin(phase) * exp(-4 * t) * 9000).toInt().toShort()
        }
        return out
    }

    private fun genBoom(): ShortArray {
        val n = RATE * 450 / 1000
        val out = ShortArray(n)
        val rnd = Random(5)
        var phase = 0.0
        var lp = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / n
            phase += 2 * PI * (130 - 90 * t) / RATE
            lp += 0.15 * ((rnd.nextDouble() * 2 - 1) - lp)
            out[i] = ((sin(phase) * 0.7 + lp * 0.9) * exp(-5 * t) * 14000).toInt().toShort()
        }
        return out
    }

    /** A short synth loop: slow minor-key arpeggio over a soft bass pulse (about 10 seconds). */
    private fun genMusic(): ShortArray {
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
                val lead = (sin(2 * PI * f * t) + 0.3 * sin(4 * PI * f * t)) * env * 0.16
                var b = 0.0
                if (e % 4 == 0) b = sin(2 * PI * bass[chord] * t) * exp(-2.5 * t) * 0.28
                val idx = e * noteLen + i
                out[idx] = ((lead + b) * 32767).toInt().coerceIn(-32767, 32767).toShort()
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

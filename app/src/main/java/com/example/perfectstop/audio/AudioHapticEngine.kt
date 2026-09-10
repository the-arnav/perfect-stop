package com.example.perfectstop.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class AudioHapticEngine(private val context: Context) {
    var isSoundEnabled: Boolean = true
    var isHapticsEnabled: Boolean = true

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_GAME)
                .build()
        )
        .build()

    private var soundBeepCountdown: Int = 0
    private var soundBeepGo: Int = 0
    private var soundTick: Int = 0
    private var soundStopSlam: Int = 0
    private var soundFanfare: Int = 0
    private var soundBuzzer: Int = 0

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    init {
        loadSounds()
    }

    private fun loadSounds() {
        try {
            val am = context.assets
            soundBeepCountdown = soundPool.load(am.openFd("audio/beep_countdown.wav"), 1)
            soundBeepGo = soundPool.load(am.openFd("audio/beep_go.wav"), 1)
            soundTick = soundPool.load(am.openFd("audio/tick.wav"), 1)
            soundStopSlam = soundPool.load(am.openFd("audio/stop_slam.wav"), 1)
            soundFanfare = soundPool.load(am.openFd("audio/fanfare.wav"), 1)
            soundBuzzer = soundPool.load(am.openFd("audio/buzzer.wav"), 1)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playBeepCountdown() {
        if (!isSoundEnabled) return
        if (soundBeepCountdown != 0) soundPool.play(soundBeepCountdown, 0.8f, 0.8f, 1, 0, 1.0f)
    }

    fun playBeepGo() {
        if (!isSoundEnabled) return
        if (soundBeepGo != 0) soundPool.play(soundBeepGo, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    fun playTick() {
        if (!isSoundEnabled) return
        if (soundTick != 0) soundPool.play(soundTick, 0.35f, 0.35f, 1, 0, 1.0f)
    }

    fun playStopSlam() {
        if (!isSoundEnabled) return
        if (soundStopSlam != 0) soundPool.play(soundStopSlam, 1.0f, 1.0f, 1, 0, 1.0f)
    }

    fun playFanfare() {
        if (!isSoundEnabled) return
        if (soundFanfare != 0) soundPool.play(soundFanfare, 0.9f, 0.9f, 1, 0, 1.0f)
    }

    fun playBuzzer() {
        if (!isSoundEnabled) return
        if (soundBuzzer != 0) soundPool.play(soundBuzzer, 0.7f, 0.7f, 1, 0, 1.0f)
    }

    // --- Tactile Haptics ---

    fun triggerMechanicalStopClick() {
        if (!isHapticsEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_THUD,
                VibrationEffect.Composition.PRIMITIVE_CLICK
            )) {
            val comp = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 15)
            vibrator.vibrate(comp.compose())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 35, 15, 60)
            val amplitudes = intArrayOf(0, 255, 0, 220)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(70)
        }
    }

    fun triggerSubtleTick() {
        if (!isHapticsEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
            val comp = VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.4f)
            vibrator.vibrate(comp.compose())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 12)
            val amplitudes = intArrayOf(0, 80)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(10)
        }
    }

    fun triggerPairingBuzz() {
        if (!isHapticsEnabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 60, 50, 100)
            val amplitudes = intArrayOf(0, 200, 0, 255)
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(120)
        }
    }

    fun release() {
        soundPool.release()
    }
}

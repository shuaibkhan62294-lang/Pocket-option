package com.example.engine.alert

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.model.NextCandleSignal

/**
 * 9. Alert/Vibration Manager
 *
 * Provides subtle haptic feedback for analyzer control states and foundation hooks
 * for Part 8 candle close / signal alerts.
 */
interface AlertVibrationManager {
  fun onAnalyzerStateChanged()
  fun onSignalAlert(signal: NextCandleSignal)
}

class AndroidAlertVibrationManager(
  private val context: Context? = null
) : AlertVibrationManager {

  override fun onAnalyzerStateChanged() {
    triggerHapticPulse(durationMs = 20L)
  }

  override fun onSignalAlert(signal: NextCandleSignal) {
    if (signal == NextCandleSignal.UP || signal == NextCandleSignal.DOWN) {
      triggerHapticPulse(durationMs = 45L)
    }
  }

  private fun triggerHapticPulse(durationMs: Long) {
    val ctx = context ?: return
    try {
      val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }
      if (vibrator?.hasVibrator() == true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator.vibrate(
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
          )
        } else {
          @Suppress("DEPRECATION")
          vibrator.vibrate(durationMs)
        }
      }
    } catch (_: Exception) {
      // Safely ignore if vibrator hardware is unavailable in test/emulator environments
    }
  }
}

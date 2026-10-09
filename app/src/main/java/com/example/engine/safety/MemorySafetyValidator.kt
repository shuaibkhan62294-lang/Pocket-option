package com.example.engine.safety

import com.example.model.Candle
import com.example.model.ExtractionQuality
import com.example.model.MarketStateSnapshot
import com.example.model.MemoryRejectionReason
import com.example.model.MemorySafetyValidationResult
import kotlin.math.max
import kotlin.math.min

/**
 * Configurable safety rules for validating candle sequences before recording
 * them into [com.example.engine.memory.ProTraderBrainMemory] (Part 5 Requirement 9).
 */
data class MemorySafetyConfig(
  val minRequiredCandles: Int = 5,
  val minCandleQualityScore: Float = 0.45f,
  val maxGapMultiplier: Double = 12.0
)

/**
 * 9. Memory Safety Validator (Part 5 Requirement 9).
 *
 * Prevents bad-quality or corrupted data from contaminating historical memory.
 * Rejects or marks:
 * - Missing candles
 * - Unreadable candles
 * - Duplicate candles
 * - Impossible OHLC relationships
 * - Unstable screen detection
 * - Incorrect timestamps
 */
interface MemorySafetyValidator {
  fun validateSequence(
    candles: List<Candle>,
    dataQuality: ExtractionQuality = ExtractionQuality.HIGH,
    isScreenDetectionStable: Boolean = true,
    config: MemorySafetyConfig = MemorySafetyConfig()
  ): MemorySafetyValidationResult

  fun validateSnapshot(
    snapshot: MarketStateSnapshot,
    config: MemorySafetyConfig = MemorySafetyConfig()
  ): MemorySafetyValidationResult

  fun isValidSingleCandle(candle: Candle, minQualityScore: Float = 0.45f): Boolean
}

class DefaultMemorySafetyValidator : MemorySafetyValidator {

  override fun validateSnapshot(
    snapshot: MarketStateSnapshot,
    config: MemorySafetyConfig
  ): MemorySafetyValidationResult {
    return validateSequence(
      candles = snapshot.recentCandleSequence,
      dataQuality = snapshot.dataQuality,
      isScreenDetectionStable = snapshot.isScreenDetectionStable,
      config = config
    )
  }

  override fun validateSequence(
    candles: List<Candle>,
    dataQuality: ExtractionQuality,
    isScreenDetectionStable: Boolean,
    config: MemorySafetyConfig
  ): MemorySafetyValidationResult {
    val reasons = ArrayList<MemoryRejectionReason>()

    // 1. Check screen detection stability
    if (!isScreenDetectionStable) {
      reasons.add(MemoryRejectionReason.UNSTABLE_SCREEN_DETECTION)
    }

    // 2. Check data quality / unreadable candles
    if (dataQuality == ExtractionQuality.UNREADABLE || dataQuality == ExtractionQuality.LOW) {
      reasons.add(MemoryRejectionReason.UNREADABLE_CANDLES)
    }

    // 3. Check missing candles (minimum count)
    if (candles.size < config.minRequiredCandles) {
      reasons.add(MemoryRejectionReason.MISSING_CANDLES)
    }

    var hasUnreadableCandle = false
    var hasImpossibleOhlc = false
    var hasDuplicateTimestamp = false
    var hasIncorrectTimestamp = false
    val intervals = ArrayList<Long>()

    for (i in candles.indices) {
      val c = candles[i]

      if (c.qualityScore < config.minCandleQualityScore) {
        hasUnreadableCandle = true
      }

      if (!hasValidOhlcGeometry(c)) {
        hasImpossibleOhlc = true
      }

      if (c.timestamp <= 0L) {
        hasIncorrectTimestamp = true
      }

      if (i > 0) {
        val prev = candles[i - 1]
        val delta = c.timestamp - prev.timestamp
        if (delta == 0L) {
          hasDuplicateTimestamp = true
        } else if (delta < 0L) {
          hasIncorrectTimestamp = true
        } else {
          intervals.add(delta)
        }
      }
    }

    // Detect missing candles via large unexpected timestamp gap
    if (intervals.size >= 4 && !reasons.contains(MemoryRejectionReason.MISSING_CANDLES)) {
      val sorted = intervals.sorted()
      val medianInterval = sorted[sorted.size / 2].coerceAtLeast(1L)
      val maxObservedInterval = sorted.last()
      if (maxObservedInterval > medianInterval * config.maxGapMultiplier) {
        reasons.add(MemoryRejectionReason.MISSING_CANDLES)
      }
    }

    if (hasUnreadableCandle && !reasons.contains(MemoryRejectionReason.UNREADABLE_CANDLES)) {
      reasons.add(MemoryRejectionReason.UNREADABLE_CANDLES)
    }
    if (hasDuplicateTimestamp) {
      reasons.add(MemoryRejectionReason.DUPLICATE_CANDLES)
    }
    if (hasImpossibleOhlc) {
      reasons.add(MemoryRejectionReason.IMPOSSIBLE_OHLC_RELATIONSHIPS)
    }
    if (hasIncorrectTimestamp) {
      reasons.add(MemoryRejectionReason.INCORRECT_TIMESTAMPS)
    }

    val distinctReasons = reasons.distinct()
    val isValid = distinctReasons.isEmpty()
    val summary = if (isValid) {
      "VALID"
    } else {
      "REJECTED: ${distinctReasons.joinToString(", ") { it.name }}"
    }

    return MemorySafetyValidationResult(
      isValidForMemory = isValid,
      rejectionReasons = distinctReasons,
      diagnosticSummary = summary
    )
  }

  override fun isValidSingleCandle(candle: Candle, minQualityScore: Float): Boolean {
    return candle.timestamp > 0L &&
      candle.qualityScore >= minQualityScore &&
      hasValidOhlcGeometry(candle)
  }

  private fun hasValidOhlcGeometry(c: Candle): Boolean {
    if (!c.open.isFinite() || !c.high.isFinite() || !c.low.isFinite() || !c.close.isFinite()) {
      return false
    }
    if (c.open <= 0.0 || c.high <= 0.0 || c.low <= 0.0 || c.close <= 0.0) {
      return false
    }
    val bodyMax = max(c.open, c.close)
    val bodyMin = min(c.open, c.close)
    val epsilon = 1e-9
    if (c.high + epsilon < bodyMax) return false
    if (c.low - epsilon > bodyMin) return false
    if (c.high + epsilon < c.low) return false
    return true
  }
}

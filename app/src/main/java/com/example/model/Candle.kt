package com.example.model

/**
 * Source of an OHLC candle in OTC Vision AI.
 */
enum class CandleSource {
  LIVE_CAPTURE,
  DEMO_PLACEHOLDER,
  UNKNOWN
}

/**
 * Price movement direction for a candle or micro-structure tick.
 */
enum class PriceDirection {
  UP,
  DOWN,
  FLAT
}

/**
 * Core Candlestick Data Model for OTC Vision AI (Parts 1–8).
 *
 * Every detected or tracked candle contains:
 * - [timestamp]
 * - [open]
 * - [high]
 * - [low]
 * - [close]
 * - [volume] (optional)
 * - [isBullish] / [isBearish]
 * - [isComplete]
 * - [source]
 * - [qualityScore]
 */
data class Candle(
  val timestamp: Long,
  val open: Double,
  val high: Double,
  val low: Double,
  val close: Double,
  val volume: Double? = null,
  val isComplete: Boolean = false,
  val source: CandleSource = CandleSource.UNKNOWN,
  val qualityScore: Float = 1.0f,
  val openedAtMillis: Long = timestamp
) {
  val isBullish: Boolean
    get() = close >= open

  val isBearish: Boolean
    get() = close < open

  val direction: PriceDirection
    get() = when {
      close > open -> PriceDirection.UP
      close < open -> PriceDirection.DOWN
      else -> PriceDirection.FLAT
    }

  val bodyTop: Double
    get() = maxOf(open, close)

  val bodyBottom: Double
    get() = minOf(open, close)

  val bodySize: Double
    get() = kotlin.math.abs(close - open)

  val totalRange: Double
    get() = (high - low).coerceAtLeast(0.0)

  val upperWick: Double
    get() = (high - bodyTop).coerceAtLeast(0.0)

  val lowerWick: Double
    get() = (bodyBottom - low).coerceAtLeast(0.0)

  fun elapsedTimeMs(nowMillis: Long = System.currentTimeMillis()): Long {
    return (nowMillis - openedAtMillis).coerceAtLeast(0L)
  }

  /**
   * Returns an updated copy of this candle after a live price tick.
   */
  fun withLiveTick(
    newPrice: Double,
    tickVolumeDelta: Double? = null,
    updatedQualityScore: Float = qualityScore
  ): Candle {
    val updatedVolume = when {
      volume != null && tickVolumeDelta != null -> volume + tickVolumeDelta
      tickVolumeDelta != null -> tickVolumeDelta
      else -> volume
    }
    return copy(
      high = maxOf(high, newPrice),
      low = minOf(low, newPrice),
      close = newPrice,
      volume = updatedVolume,
      qualityScore = updatedQualityScore
    )
  }

  /**
   * Marks the current candle as complete when its interval closes.
   */
  fun markComplete(): Candle = copy(isComplete = true)
}

/**
 * Snapshot of the currently forming candle tracked in real time (Requirement 5).
 */
data class CurrentCandleTrackerSnapshot(
  val open: Double,
  val currentPrice: Double,
  val high: Double,
  val low: Double,
  val direction: PriceDirection,
  val bodySize: Double,
  val upperWick: Double,
  val lowerWick: Double,
  val elapsedCandleTimeMs: Long,
  val isComplete: Boolean,
  val qualityScore: Float
) {
  companion object {
    fun fromCandle(
      candle: Candle,
      nowMillis: Long = System.currentTimeMillis()
    ): CurrentCandleTrackerSnapshot {
      return CurrentCandleTrackerSnapshot(
        open = candle.open,
        currentPrice = candle.close,
        high = candle.high,
        low = candle.low,
        direction = candle.direction,
        bodySize = candle.bodySize,
        upperWick = candle.upperWick,
        lowerWick = candle.lowerWick,
        elapsedCandleTimeMs = candle.elapsedTimeMs(nowMillis),
        isComplete = candle.isComplete,
        qualityScore = candle.qualityScore
      )
    }
  }
}

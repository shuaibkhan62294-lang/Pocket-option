package com.example.engine.priceaction

import com.example.model.Candle
import com.example.model.InternalDirectionState
import com.example.model.MomentumAnalysisState
import com.example.model.PriceActionAnalysisState
import kotlin.math.abs
import kotlin.math.min

/**
 * Internal background model for market structure, support/resistance context,
 * and momentum analysis (Part 2 + Part 3 Requirements 13 & 14).
 */
data class PriceActionSnapshot(
  val nearestSupport: Double? = null,
  val nearestResistance: Double? = null,
  val structureBias: String = "NEUTRAL",
  val priceActionState: PriceActionAnalysisState = PriceActionAnalysisState(),
  val isReady: Boolean = false
)

/**
 * 13. Price Action Engine & 14. Momentum Engine
 *
 * Analyzes Higher Highs (HH), Higher Lows (HL), Lower Highs (LH), Lower Lows (LL),
 * uptrend/downtrend/range/breakout/breakdown/reversal/consolidation structures,
 * and candle-to-candle momentum (body size, wick size, consecutive runs, acceleration,
 * deceleration, exhaustion).
 */
interface PriceActionEngine {
  fun analyzePriceAction(candles: List<Candle>): PriceActionSnapshot
}

class DefaultPriceActionEngine : PriceActionEngine {

  private var previousStructureBias: String = "NEUTRAL"

  @Synchronized
  override fun analyzePriceAction(candles: List<Candle>): PriceActionSnapshot {
    if (candles.size < 6) {
      return PriceActionSnapshot(isReady = false)
    }

    val window = candles.takeLast(40)
    val momentumState = evaluateMomentum(window)

    // Split recent window into two halves or swing segments to evaluate HH / HL / LH / LL
    val recentHalf = window.takeLast(min(8, window.size / 2))
    val priorHalf = window.dropLast(recentHalf.size).takeLast(min(12, window.size - recentHalf.size))

    val recentHigh = recentHalf.maxOf { it.high }
    val recentLow = recentHalf.minOf { it.low }
    val priorHigh = priorHalf.maxOfOrNull { it.high } ?: recentHigh
    val priorLow = priorHalf.minOfOrNull { it.low } ?: recentLow

    val avgRange = window.map { it.totalRange }.average().coerceAtLeast(0.00008)
    val minSwingDelta = avgRange * 0.20

    val higherHigh = recentHigh > priorHigh + minSwingDelta
    val higherLow = recentLow > priorLow + minSwingDelta
    val lowerHigh = recentHigh < priorHigh - minSwingDelta
    val lowerLow = recentLow < priorLow - minSwingDelta

    val latest = window.last()
    val prevMaxHigh = window.dropLast(1).maxOf { it.high }
    val prevMinLow = window.dropLast(1).minOf { it.low }

    val isBreakout = latest.close > prevMaxHigh && latest.bodySize > avgRange * 0.6
    val isBreakdown = latest.close < prevMinLow && latest.bodySize > avgRange * 0.6

    val isUptrend = higherHigh && higherLow && !isBreakdown
    val isDowntrend = lowerHigh && lowerLow && !isBreakout

    val totalSpan = (window.maxOf { it.high } - window.minOf { it.low }).coerceAtLeast(1e-7)
    val isConsolidation = !isUptrend && !isDowntrend && !isBreakout && !isBreakdown &&
      (totalSpan < avgRange * 3.2)
    val isRange = !isUptrend && !isDowntrend && !isBreakout && !isBreakdown && !isConsolidation

    val isReversalStructure = (higherHigh && lowerLow) ||
      (isUptrend && momentumState.suddenReversal && latest.isBearish) ||
      (isDowntrend && momentumState.suddenReversal && latest.isBullish)

    val currentBias = when {
      isBreakout || isUptrend -> "UPTREND"
      isBreakdown || isDowntrend -> "DOWNTREND"
      isReversalStructure -> "REVERSAL_RISK"
      isConsolidation -> "CONSOLIDATION"
      else -> "RANGE"
    }

    val structuralChange = previousStructureBias != "NEUTRAL" && previousStructureBias != currentBias
    previousStructureBias = currentBias

    val direction = when {
      isReversalStructure -> InternalDirectionState.REVERSAL_RISK
      isBreakout || isUptrend -> InternalDirectionState.BULLISH
      isBreakdown || isDowntrend -> InternalDirectionState.BEARISH
      isConsolidation -> InternalDirectionState.CONSOLIDATING
      else -> InternalDirectionState.NEUTRAL
    }

    val state = PriceActionAnalysisState(
      higherHigh = higherHigh,
      higherLow = higherLow,
      lowerHigh = lowerHigh,
      lowerLow = lowerLow,
      isUptrend = isUptrend,
      isDowntrend = isDowntrend,
      isRange = isRange,
      isBreakout = isBreakout,
      isBreakdown = isBreakdown,
      isReversalStructure = isReversalStructure,
      isConsolidation = isConsolidation,
      structuralChangeDetected = structuralChange,
      direction = direction,
      momentum = momentumState
    )

    return PriceActionSnapshot(
      nearestSupport = min(priorLow, recentLow),
      nearestResistance = maxOf(priorHigh, recentHigh),
      structureBias = currentBias,
      priceActionState = state,
      isReady = true
    )
  }

  private fun evaluateMomentum(candles: List<Candle>): MomentumAnalysisState {
    val avgBody = candles.map { it.bodySize }.average().coerceAtLeast(1e-7)
    val avgWick = candles.map { it.upperWick + it.lowerWick }.average()

    var consecutiveBullish = 0
    var consecutiveBearish = 0
    for (i in candles.indices.reversed()) {
      val c = candles[i]
      if (c.isBullish) {
        if (consecutiveBearish > 0) break
        consecutiveBullish++
      } else {
        if (consecutiveBullish > 0) break
        consecutiveBearish++
      }
    }

    val recent3Bodies = candles.takeLast(min(3, candles.size)).map { it.bodySize }.average()
    val priorBodies = candles.dropLast(min(3, candles.size - 1)).takeLast(6).map { it.bodySize }.average()
      .coerceAtLeast(1e-7)

    val isAccelerating = recent3Bodies > priorBodies * 1.28
    val isDecelerating = recent3Bodies < priorBodies * 0.72

    val latest = candles.last()
    val prev = if (candles.size >= 2) candles[candles.size - 2] else latest

    // Momentum exhaustion: long streak of 4+ same-color candles followed by shrinking body or large opposing wick
    val isExhausted = (consecutiveBullish >= 4 && (latest.upperWick > latest.bodySize * 1.2 || isDecelerating)) ||
      (consecutiveBearish >= 4 && (latest.lowerWick > latest.bodySize * 1.2 || isDecelerating))

    val strongBullish = consecutiveBullish >= 3 && isAccelerating && !isExhausted
    val strongBearish = consecutiveBearish >= 3 && isAccelerating && !isExhausted
    val weakening = isDecelerating || isExhausted

    val suddenReversal = (prev.isBullish && latest.isBearish && latest.bodySize > prev.bodySize * 1.25 && latest.bodySize > avgBody * 1.2) ||
      (prev.isBearish && latest.isBullish && latest.bodySize > prev.bodySize * 1.25 && latest.bodySize > avgBody * 1.2)

    val direction = when {
      suddenReversal || isExhausted -> InternalDirectionState.REVERSAL_RISK
      strongBullish || (consecutiveBullish >= 2 && latest.bodySize >= avgBody) ->
        InternalDirectionState.BULLISH
      strongBearish || (consecutiveBearish >= 2 && latest.bodySize >= avgBody) ->
        InternalDirectionState.BEARISH
      weakening -> InternalDirectionState.CONSOLIDATING
      else -> InternalDirectionState.NEUTRAL
    }

    return MomentumAnalysisState(
      averageBodySize = avgBody,
      averageWickSize = avgWick,
      consecutiveBullishCandles = consecutiveBullish,
      consecutiveBearishCandles = consecutiveBearish,
      isAccelerating = isAccelerating,
      isDecelerating = isDecelerating,
      isMomentumExhausted = isExhausted,
      strongBullishMomentum = strongBullish,
      strongBearishMomentum = strongBearish,
      weakeningMomentum = weakening,
      suddenReversal = suddenReversal,
      direction = direction
    )
  }
}

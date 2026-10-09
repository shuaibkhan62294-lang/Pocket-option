package com.example.engine.liquidity

import com.example.model.Candle
import com.example.model.InternalDirectionState
import com.example.model.LiquiditySweepClassification
import com.example.model.LiquiditySweepState
import com.example.model.SupportResistanceAnalysis
import kotlin.math.abs

/**
 * 5. Liquidity / Stop-Run Style Price Action Engine (Part 4 Requirement 5).
 *
 * Recognizes liquidity-style movements using ONLY visible price-action behavior:
 * - Equal highs & equal lows
 * - Previous swing liquidity areas
 * - Sweep above previous high / sweep below previous low
 * - Rejection after sweep
 * - Failed breakout / failed breakdown
 *
 * Never claims knowledge of hidden broker orders or non-visible order books.
 */
interface LiquiditySweepEngine {
  fun analyzeLiquiditySweeps(
    candles: List<Candle>,
    supportResistance: SupportResistanceAnalysis = SupportResistanceAnalysis()
  ): LiquiditySweepState
}

class DefaultLiquiditySweepEngine : LiquiditySweepEngine {

  override fun analyzeLiquiditySweeps(
    candles: List<Candle>,
    supportResistance: SupportResistanceAnalysis
  ): LiquiditySweepState {
    if (candles.size < 8) {
      return LiquiditySweepState()
    }

    val window = candles.takeLast(35)
    val avgRange = window.map { it.totalRange }.average().coerceAtLeast(0.00008)
    val equalTol = avgRange * 0.16

    val historical = window.dropLast(1)
    val latest = window.last()
    val prev = historical.last()

    // Identify swing highs and swing lows in historical candles
    val swingHighs = ArrayList<Double>()
    val swingLows = ArrayList<Double>()

    for (i in 1 until (historical.size - 1)) {
      val c = historical[i]
      if (c.high >= historical[i - 1].high && c.high >= historical[i + 1].high) {
        swingHighs.add(c.high)
      }
      if (c.low <= historical[i - 1].low && c.low <= historical[i + 1].low) {
        swingLows.add(c.low)
      }
    }

    // Detect Equal Highs / Equal Lows (2+ swing points within tight tolerance)
    val equalHighs = hasEqualLevels(swingHighs, equalTol)
    val equalLows = hasEqualLevels(swingLows, equalTol)

    val liquidityAreas = (swingHighs.takeLast(3) + swingLows.takeLast(3)).distinct()

    val highestPriorSwing = swingHighs.maxOrNull() ?: historical.maxOf { it.high }
    val lowestPriorSwing = swingLows.minOrNull() ?: historical.minOf { it.low }

    // Sweep above previous high: wick pierces above prior high, but body closes back below it
    val sweepAboveHigh = (latest.high > highestPriorSwing + equalTol * 0.3 && latest.close < highestPriorSwing) ||
      (prev.high > highestPriorSwing + equalTol * 0.3 && latest.close < highestPriorSwing && latest.isBearish)

    // Sweep below previous low: wick pierces below prior low, but body closes back above it
    val sweepBelowLow = (latest.low < lowestPriorSwing - equalTol * 0.3 && latest.close > lowestPriorSwing) ||
      (prev.low < lowestPriorSwing - equalTol * 0.3 && latest.close > lowestPriorSwing && latest.isBullish)

    val rejectionAfterUpperSweep = sweepAboveHigh &&
      (latest.upperWick >= latest.bodySize * 1.1 || latest.isBearish)

    val rejectionAfterLowerSweep = sweepBelowLow &&
      (latest.lowerWick >= latest.bodySize * 1.1 || latest.isBullish)

    val rejectionAfterSweep = rejectionAfterUpperSweep || rejectionAfterLowerSweep

    val failedBreakout = supportResistance.possibleFakeBreakout || sweepAboveHigh
    val failedBreakdown = supportResistance.fakeBreakdown || sweepBelowLow

    val strongCloseAboveHigh = latest.close > highestPriorSwing + avgRange * 0.3 && latest.isBullish
    val strongCloseBelowLow = latest.close < lowestPriorSwing - avgRange * 0.3 && latest.isBearish

    val classification = when {
      rejectionAfterSweep -> LiquiditySweepClassification.CONFIRMED_REJECTION_AFTER_SWEEP
      sweepAboveHigh || sweepBelowLow || equalHighs || equalLows ->
        LiquiditySweepClassification.POSSIBLE_LIQUIDITY_SWEEP
      strongCloseAboveHigh || strongCloseBelowLow ->
        LiquiditySweepClassification.POSSIBLE_CONTINUATION
      else -> LiquiditySweepClassification.UNCLEAR
    }

    val bias = when {
      rejectionAfterLowerSweep || strongCloseAboveHigh -> InternalDirectionState.BULLISH
      rejectionAfterUpperSweep || strongCloseBelowLow -> InternalDirectionState.BEARISH
      sweepAboveHigh || sweepBelowLow -> InternalDirectionState.REVERSAL_RISK
      else -> InternalDirectionState.NEUTRAL
    }

    return LiquiditySweepState(
      equalHighs = equalHighs,
      equalLows = equalLows,
      previousSwingLiquidityAreas = liquidityAreas,
      sweepAbovePreviousHigh = sweepAboveHigh,
      sweepBelowPreviousLow = sweepBelowLow,
      rejectionAfterSweep = rejectionAfterSweep,
      failedBreakout = failedBreakout,
      failedBreakdown = failedBreakdown,
      classification = classification,
      sweepDirectionBias = bias
    )
  }

  private fun hasEqualLevels(levels: List<Double>, tolerance: Double): Boolean {
    if (levels.size < 2) return false
    for (i in 0 until levels.size) {
      for (j in (i + 1) until levels.size) {
        if (abs(levels[i] - levels[j]) <= tolerance) {
          return true
        }
      }
    }
    return false
  }
}

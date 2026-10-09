package com.example.engine.breakout

import com.example.engine.indicator.IndicatorSnapshot
import com.example.model.BreakoutAnalysisState
import com.example.model.BreakoutClassification
import com.example.model.Candle
import com.example.model.InternalDirectionState
import com.example.model.MomentumAnalysisState
import com.example.model.SupportResistanceAnalysis
import kotlin.math.min

/**
 * 6. Breakout Intelligence Engine (Part 4 Requirement 6).
 *
 * Analyzes:
 * - Before breakout: compression, narrow candle ranges, repeated level tests,
 *   momentum buildup, volatility contraction
 * - During breakout: candle body strength, wick behavior, momentum, range expansion,
 *   direction, level penetration
 * - After breakout: continuation, retest, rejection, fake breakout
 *
 * Classifies as BREAKOUT_CONFIRMED, BREAKOUT_ATTEMPT, FAKE_BREAKOUT, or BREAKOUT_UNCLEAR.
 * Never triggers a strong conclusion from a single wick.
 */
interface BreakoutEngine {
  fun analyzeBreakout(
    candles: List<Candle>,
    supportResistance: SupportResistanceAnalysis,
    momentum: MomentumAnalysisState,
    indicators: IndicatorSnapshot
  ): BreakoutAnalysisState
}

class DefaultBreakoutEngine : BreakoutEngine {

  override fun analyzeBreakout(
    candles: List<Candle>,
    supportResistance: SupportResistanceAnalysis,
    momentum: MomentumAnalysisState,
    indicators: IndicatorSnapshot
  ): BreakoutAnalysisState {
    if (candles.size < 10) {
      return BreakoutAnalysisState()
    }

    val window = candles.takeLast(30)
    val avgBody = window.map { it.bodySize }.average().coerceAtLeast(1e-7)
    val avgRange = window.map { it.totalRange }.average().coerceAtLeast(1e-7)

    val latest = window.last()
    val preBreakoutSlice = window.dropLast(1).takeLast(min(6, window.size - 1))

    // 1. Pre-breakout conditions
    val preAvgRange = preBreakoutSlice.map { it.totalRange }.average()
    val narrowCandleRanges = preAvgRange < avgRange * 0.82
    val volatilityContraction = indicators.bollingerSignal.bandContraction ||
      indicators.atrState.volatilityContraction
    val compression = narrowCandleRanges || volatilityContraction

    val repeatedTests = (supportResistance.nearestResistance?.touchCount ?: 0) >= 2 ||
      (supportResistance.nearestSupport?.touchCount ?: 0) >= 2
    val momentumBuildup = momentum.consecutiveBullishCandles >= 2 ||
      momentum.consecutiveBearishCandles >= 2 ||
      momentum.isAccelerating

    // 2. During-breakout conditions (requires real body strength, not just a wick!)
    val strongBody = latest.bodySize >= avgBody * 1.28 &&
      latest.bodySize >= latest.totalRange * 0.58
    val cleanWick = if (latest.isBullish) {
      latest.upperWick <= latest.bodySize * 0.35
    } else {
      latest.lowerWick <= latest.bodySize * 0.35
    }
    val rangeExpansion = latest.totalRange >= avgRange * 1.20 ||
      indicators.atrState.volatilityExpansion

    val brokeUp = supportResistance.resistanceBreakout ||
      indicators.bollingerSignal.possibleBreakout && latest.isBullish
    val brokeDown = supportResistance.supportBreakout ||
      indicators.bollingerSignal.possibleBreakout && latest.isBearish
    val levelPenetration = brokeUp || brokeDown

    val breakoutDir = when {
      brokeUp -> InternalDirectionState.BULLISH
      brokeDown -> InternalDirectionState.BEARISH
      supportResistance.isNearResistance && latest.isBullish -> InternalDirectionState.BULLISH
      supportResistance.isNearSupport && latest.isBearish -> InternalDirectionState.BEARISH
      else -> InternalDirectionState.NEUTRAL
    }

    // 3. Post-breakout conditions
    val isFake = supportResistance.possibleFakeBreakout || supportResistance.fakeBreakdown ||
      supportResistance.isFailedRetest
    val isRetest = supportResistance.isRetest
    val isRejection = supportResistance.resistanceRejection || supportResistance.supportRejection
    val prev = window[window.size - 2]
    val hasContinuation = (brokeUp && prev.isBullish && latest.isBullish && latest.close > prev.high) ||
      (brokeDown && prev.isBearish && latest.isBearish && latest.close < prev.low)

    val classification = when {
      isFake -> BreakoutClassification.FAKE_BREAKOUT
      levelPenetration && strongBody && cleanWick && (hasContinuation || isRetest || rangeExpansion) ->
        BreakoutClassification.BREAKOUT_CONFIRMED
      levelPenetration || (compression && strongBody && (supportResistance.isNearResistance || supportResistance.isNearSupport)) ->
        BreakoutClassification.BREAKOUT_ATTEMPT
      else -> BreakoutClassification.BREAKOUT_UNCLEAR
    }

    return BreakoutAnalysisState(
      preBreakoutCompression = compression,
      narrowCandleRanges = narrowCandleRanges,
      repeatedLevelTests = repeatedTests,
      momentumBuildup = momentumBuildup,
      volatilityContraction = volatilityContraction,
      strongBreakoutBody = strongBody,
      cleanWickBehavior = cleanWick,
      rangeExpansion = rangeExpansion,
      breakoutDirection = breakoutDir,
      levelPenetration = levelPenetration,
      postBreakoutContinuation = hasContinuation,
      postBreakoutRetest = isRetest,
      postBreakoutRejection = isRejection,
      classification = classification
    )
  }
}

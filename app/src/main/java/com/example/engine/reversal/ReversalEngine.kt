package com.example.engine.reversal

import com.example.engine.indicator.IndicatorSnapshot
import com.example.engine.pattern.CandlestickPatternResult
import com.example.model.BosClassification
import com.example.model.Candle
import com.example.model.CandleSequenceFeatures
import com.example.model.ChochClassification
import com.example.model.InternalDirectionState
import com.example.model.MarketStructureAnalysis
import com.example.model.MomentumAnalysisState
import com.example.model.ReversalAnalysisState
import com.example.model.ReversalClassification
import com.example.model.SupportResistanceAnalysis

/**
 * 7. Reversal Intelligence Engine (Part 4 Requirement 7).
 *
 * Detects possible reversals only when multiple independent confirming factors align:
 * - Trend exhaustion
 * - Large opposite candle
 * - Long rejection wick
 * - Engulfing or strong reversal candlestick patterns
 * - Momentum weakening
 * - RSI / MACD divergence or extreme context from Part 3
 * - Support / resistance proximity or rejection
 * - HH/HL/LH/LL structure weakening
 * - BOS / CHoCH character change
 * - Candle sequence reversal traits
 */
interface ReversalEngine {
  fun analyzeReversal(
    candles: List<Candle>,
    indicators: IndicatorSnapshot,
    supportResistance: SupportResistanceAnalysis,
    marketStructure: MarketStructureAnalysis,
    momentum: MomentumAnalysisState,
    patterns: CandlestickPatternResult,
    sequenceFeatures: CandleSequenceFeatures
  ): ReversalAnalysisState
}

class DefaultReversalEngine : ReversalEngine {

  override fun analyzeReversal(
    candles: List<Candle>,
    indicators: IndicatorSnapshot,
    supportResistance: SupportResistanceAnalysis,
    marketStructure: MarketStructureAnalysis,
    momentum: MomentumAnalysisState,
    patterns: CandlestickPatternResult,
    sequenceFeatures: CandleSequenceFeatures
  ): ReversalAnalysisState {
    if (candles.size < 8) {
      return ReversalAnalysisState()
    }

    val latest = candles.last()
    val prev = candles[candles.size - 2]
    val avgBody = momentum.averageBodySize.coerceAtLeast(1e-7)
    val avgRange = candles.takeLast(20).map { it.totalRange }.average().coerceAtLeast(1e-7)

    // 1. Trend exhaustion
    val trendExhaustion = momentum.isMomentumExhausted || sequenceFeatures.hasExhaustion

    // 2. Large opposite candle
    val largeOppositeCandle = (prev.isBullish && latest.isBearish && latest.bodySize >= avgBody * 1.25) ||
      (prev.isBearish && latest.isBullish && latest.bodySize >= avgBody * 1.25)

    // 3. Long rejection wick
    val longRejectionWick = latest.upperWick >= maxOf(latest.bodySize * 1.5, avgRange * 0.45) ||
      latest.lowerWick >= maxOf(latest.bodySize * 1.5, avgRange * 0.45)

    // 4. Engulfing or 3-candle star reversal pattern
    val engulfingPresent = patterns.patterns.any {
      it.patternName.contains("Engulfing") ||
        it.patternName.contains("Star") ||
        it.patternName.contains("Three Inside") ||
        it.patternName.contains("Three Outside") ||
        it.patternName.contains("Pin") ||
        it.patternName.contains("Hammer") ||
        it.patternName.contains("Shooting Star")
    }

    // 5. Momentum weakening
    val momentumWeakening = momentum.weakeningMomentum || momentum.isDecelerating || momentum.suddenReversal

    // 6. RSI / MACD context from Part 3
    val rsiMacdSupports = indicators.rsiSignal.bullishDivergence ||
      indicators.rsiSignal.bearishDivergence ||
      indicators.rsiSignal.directionWithPriceAction == InternalDirectionState.REVERSAL_RISK ||
      indicators.macdSignal.bullishDivergence ||
      indicators.macdSignal.bearishDivergence ||
      indicators.macdSignal.momentumTransition

    // 7. Support / Resistance proximity or rejection
    val atSr = supportResistance.supportRejection ||
      supportResistance.resistanceRejection ||
      supportResistance.isNearSupport ||
      supportResistance.isNearResistance ||
      supportResistance.possibleFakeBreakout

    // 8. HH/HL/LH/LL structure weakening or reversal
    val structureSupports = marketStructure.isPossibleReversal || marketStructure.isStructureWeakening

    // 9. CHoCH / BOS character change
    val chochOrBos = marketStructure.chochState != ChochClassification.NONE ||
      marketStructure.bosState != BosClassification.NONE

    // 10. Reversal candle sequence
    val revSequence = sequenceFeatures.hasReversalSequence ||
      sequenceFeatures.multiTimeframe.hasReversalPossibility ||
      sequenceFeatures.hasRepeatedRejection

    val factors = listOf(
      trendExhaustion,
      largeOppositeCandle,
      longRejectionWick,
      engulfingPresent,
      momentumWeakening,
      rsiMacdSupports,
      atSr,
      structureSupports,
      chochOrBos,
      revSequence
    )
    val confirmingCount = factors.count { it }

    val reversalDir = when {
      supportResistance.supportRejection ||
        marketStructure.chochState == ChochClassification.BULLISH_CHOCH ||
        indicators.rsiSignal.bullishDivergence ||
        (latest.isBullish && (largeOppositeCandle || engulfingPresent)) ->
        InternalDirectionState.BULLISH
      supportResistance.resistanceRejection ||
        marketStructure.chochState == ChochClassification.BEARISH_CHOCH ||
        indicators.rsiSignal.bearishDivergence ||
        (latest.isBearish && (largeOppositeCandle || engulfingPresent)) ->
        InternalDirectionState.BEARISH
      else -> InternalDirectionState.NEUTRAL
    }

    // Require multiple independent confirming factors for strong or moderate reversal evidence
    val classification = when {
      confirmingCount >= 5 && atSr && (engulfingPresent || longRejectionWick || chochOrBos) ->
        ReversalClassification.STRONG_REVERSAL_EVIDENCE
      confirmingCount >= 3 && (atSr || structureSupports || rsiMacdSupports) ->
        ReversalClassification.MODERATE_REVERSAL_EVIDENCE
      confirmingCount == 2 ->
        ReversalClassification.EARLY_REVERSAL_WARNING
      confirmingCount <= 1 ->
        ReversalClassification.NO_REVERSAL
      else ->
        ReversalClassification.UNCLEAR
    }

    return ReversalAnalysisState(
      trendExhaustion = trendExhaustion,
      largeOppositeCandle = largeOppositeCandle,
      longRejectionWick = longRejectionWick,
      engulfingPresent = engulfingPresent,
      momentumWeakening = momentumWeakening,
      rsiMacdContextSupports = rsiMacdSupports,
      atSupportOrResistance = atSr,
      structureSupportsReversal = structureSupports,
      bosOrChochPresent = chochOrBos,
      reversalSequencePresent = revSequence,
      confirmingFactorsCount = confirmingCount,
      reversalDirection = reversalDir,
      classification = classification
    )
  }
}

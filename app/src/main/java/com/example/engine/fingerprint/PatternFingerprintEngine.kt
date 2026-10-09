package com.example.engine.fingerprint

import com.example.engine.indicator.IndicatorSnapshot
import com.example.model.Candle
import com.example.model.CandleSrPosition
import com.example.model.MarketRegime
import com.example.model.MarketStateSnapshot
import com.example.model.NormalizedIndicatorFingerprint
import com.example.model.PatternFingerprint
import com.example.model.PriceActionContext
import kotlin.math.min

/**
 * 2. Pattern Fingerprint Engine (Part 5 Requirement 2).
 *
 * Creates a normalized [PatternFingerprint] for every meaningful setup so that
 * different price levels and sessions can be compared fairly.
 */
interface PatternFingerprintEngine {
  fun createFingerprint(snapshot: MarketStateSnapshot): PatternFingerprint

  fun createFingerprintFromContext(
    candles: List<Candle>,
    indicators: IndicatorSnapshot,
    priceActionContext: PriceActionContext,
    marketRegime: MarketRegime
  ): PatternFingerprint
}

class DefaultPatternFingerprintEngine(
  private val sequenceWindowSize: Int = 8
) : PatternFingerprintEngine {

  override fun createFingerprint(snapshot: MarketStateSnapshot): PatternFingerprint {
    val candles = snapshot.recentCandleSequence
    if (candles.isEmpty()) return PatternFingerprint()

    val recent = candles.takeLast(min(sequenceWindowSize, candles.size))
    val avgBody = candles.takeLast(min(25, candles.size)).map { it.bodySize }.average().coerceAtLeast(1e-7)

    val directions = recent.map {
      when {
        it.close > it.open -> 1
        it.close < it.open -> -1
        else -> 0
      }
    }

    val relBodies = recent.map { (it.bodySize / avgBody).coerceIn(0.0, 4.0) }
    val wickRatios = recent.map {
      val range = it.totalRange.coerceAtLeast(1e-7)
      ((it.upperWick + it.lowerWick) / range).coerceIn(0.0, 1.0)
    }
    val upperWickRatios = recent.map {
      val range = it.totalRange.coerceAtLeast(1e-7)
      (it.upperWick / range).coerceIn(0.0, 1.0)
    }
    val lowerWickRatios = recent.map {
      val range = it.totalRange.coerceAtLeast(1e-7)
      (it.lowerWick / range).coerceIn(0.0, 1.0)
    }

    val srRel = snapshot.latestCandleStructure?.positionRelativeToSr ?: when {
      snapshot.supportResistance.resistanceBreakout -> CandleSrPosition.BREAKING_RESISTANCE
      snapshot.supportResistance.supportBreakout -> CandleSrPosition.BREAKING_SUPPORT
      snapshot.supportResistance.isNearResistance || snapshot.supportResistance.resistanceRejection ->
        CandleSrPosition.AT_RESISTANCE
      snapshot.supportResistance.isNearSupport || snapshot.supportResistance.supportRejection ->
        CandleSrPosition.AT_SUPPORT
      else -> CandleSrPosition.MID_RANGE
    }

    val ind = snapshot.indicatorStates
    val rsiZone = when {
      ind.rsiSignal.isOverbought -> 1
      ind.rsiSignal.isOversold -> -1
      else -> 0
    }
    val bbPos = when {
      ind.bollingerSignal.priceNearUpperBand -> 1
      ind.bollingerSignal.priceNearLowerBand -> -1
      else -> 0
    }

    val normalizedIndicators = NormalizedIndicatorFingerprint(
      emaDirection = ind.emaSignal.direction,
      smaDirection = ind.smaSignal.direction,
      rsiZone = rsiZone,
      rsiMomentum = ind.rsiSignal.directionWithPriceAction,
      macdDirection = ind.macdSignal.direction,
      bollingerPosition = bbPos,
      stochasticDirection = ind.stochasticSignal.directionWithTrend,
      adxStrongTrend = ind.adxSignal.isStrongTrend || ind.adxSignal.isTrendingMarket,
      cciMomentum = ind.cciSignal.momentum,
      williamsDirection = ind.williamsSignal.direction
    )

    val structureType = when {
      snapshot.priceAction.isUptrend -> com.example.model.MarketStructureType.UPTREND_STRUCTURE
      snapshot.priceAction.isDowntrend -> com.example.model.MarketStructureType.DOWNTREND_STRUCTURE
      snapshot.priceAction.isBreakout || snapshot.priceAction.isBreakdown ->
        com.example.model.MarketStructureType.STRUCTURE_BREAK
      snapshot.priceAction.isReversalStructure ->
        com.example.model.MarketStructureType.POSSIBLE_REVERSAL
      else -> com.example.model.MarketStructureType.RANGE_STRUCTURE
    }

    return PatternFingerprint(
      candleDirectionSequence = directions,
      relativeCandleBodySizes = relBodies,
      wickRatios = wickRatios,
      upperWickRatios = upperWickRatios,
      lowerWickRatios = lowerWickRatios,
      momentumBehavior = snapshot.momentum.direction,
      isMomentumAccelerating = snapshot.momentum.isAccelerating,
      isMomentumExhausted = snapshot.momentum.isMomentumExhausted,
      volatilityBehavior = snapshot.volatility.classification,
      isVolatilityExpanding = snapshot.volatility.volatilityExpansion,
      isVolatilityContracting = snapshot.volatility.volatilityContraction,
      trendStructure = structureType,
      bosState = snapshot.bosState,
      chochState = snapshot.chochState,
      supportResistanceRelationship = srRel,
      indicatorStates = normalizedIndicators,
      candlestickPatterns = snapshot.candlestickPatterns.map { it.patternName },
      breakoutState = snapshot.breakoutRetestState.classification,
      reversalState = snapshot.reversalState.classification,
      liquiditySweepState = snapshot.liquiditySweepState.classification,
      marketRegime = snapshot.marketRegime
    )
  }

  override fun createFingerprintFromContext(
    candles: List<Candle>,
    indicators: IndicatorSnapshot,
    priceActionContext: PriceActionContext,
    marketRegime: MarketRegime
  ): PatternFingerprint {
    val snapshot = MarketStateSnapshot(
      timestamp = candles.lastOrNull()?.timestamp ?: 0L,
      recentCandleSequence = candles,
      candleStructure = priceActionContext.recentCandleStructures,
      latestCandleStructure = priceActionContext.latestCandleStructure,
      higherHigh = priceActionContext.swingStructure.higherHigh,
      higherLow = priceActionContext.swingStructure.higherLow,
      lowerHigh = priceActionContext.swingStructure.lowerHigh,
      lowerLow = priceActionContext.swingStructure.lowerLow,
      bosState = priceActionContext.bosState,
      chochState = priceActionContext.chochState,
      breakoutRetestState = priceActionContext.breakoutState,
      reversalState = priceActionContext.reversalState,
      liquiditySweepState = priceActionContext.liquiditySweepState,
      momentum = priceActionContext.momentumState,
      volatility = indicators.atrState,
      marketRegime = marketRegime,
      indicatorStates = indicators,
      candlestickPatterns = priceActionContext.candlePatternState,
      candleSequenceFeatures = priceActionContext.candleSequenceState,
      dataQuality = priceActionContext.dataQuality
    )
    return createFingerprint(snapshot).copy(
      trendStructure = priceActionContext.swingStructure.structureType
    )
  }
}

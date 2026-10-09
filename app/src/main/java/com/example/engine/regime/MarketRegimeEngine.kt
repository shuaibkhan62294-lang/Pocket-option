package com.example.engine.regime

import com.example.engine.indicator.IndicatorSnapshot
import com.example.model.Candle
import com.example.model.CandleSequenceFeatures
import com.example.model.InternalDirectionState
import com.example.model.MarketRegime
import com.example.model.PriceActionAnalysisState
import com.example.model.SupportResistanceAnalysis
import com.example.model.VolatilityClassification

/**
 * 17. Market Regime Detector
 *
 * Classifies the current market internally as:
 * - TRENDING_UP
 * - TRENDING_DOWN
 * - SIDEWAYS
 * - BREAKOUT
 * - BREAKDOWN
 * - HIGH_VOLATILITY
 * - LOW_VOLATILITY
 * - REVERSAL_RISK
 * - UNCERTAIN
 */
interface MarketRegimeEngine {
  fun detectRegime(
    candles: List<Candle>,
    indicators: IndicatorSnapshot,
    priceAction: PriceActionAnalysisState,
    supportResistance: SupportResistanceAnalysis,
    sequenceFeatures: CandleSequenceFeatures
  ): MarketRegime
}

class DefaultMarketRegimeEngine : MarketRegimeEngine {

  override fun detectRegime(
    candles: List<Candle>,
    indicators: IndicatorSnapshot,
    priceAction: PriceActionAnalysisState,
    supportResistance: SupportResistanceAnalysis,
    sequenceFeatures: CandleSequenceFeatures
  ): MarketRegime {
    if (candles.size < 10 || !indicators.isReady) {
      return MarketRegime.UNCERTAIN
    }

    // 1. Check Breakout / Breakdown first
    if (priceAction.isBreakout || supportResistance.resistanceBreakout) {
      return MarketRegime.BREAKOUT
    }
    if (priceAction.isBreakdown || supportResistance.supportBreakout) {
      return MarketRegime.BREAKDOWN
    }

    // 2. Check abnormal High Volatility
    if (indicators.atrState.classification == VolatilityClassification.HIGH_VOLATILITY &&
      indicators.atrState.unusuallyLargeCandles
    ) {
      return MarketRegime.HIGH_VOLATILITY
    }

    // 3. Check Reversal Risk (exhaustion, rejection at S/R, or multi-timeframe divergence)
    if (priceAction.isReversalStructure ||
      supportResistance.supportRejection ||
      supportResistance.resistanceRejection ||
      sequenceFeatures.multiTimeframe.hasReversalPossibility
    ) {
      return MarketRegime.REVERSAL_RISK
    }

    // 4. Check Trending Up / Trending Down
    val mtf = sequenceFeatures.multiTimeframe
    if ((priceAction.isUptrend || mtf.overallDirection == InternalDirectionState.BULLISH) &&
      indicators.emaSignal.direction == InternalDirectionState.BULLISH
    ) {
      return MarketRegime.TRENDING_UP
    }

    if ((priceAction.isDowntrend || mtf.overallDirection == InternalDirectionState.BEARISH) &&
      indicators.emaSignal.direction == InternalDirectionState.BEARISH
    ) {
      return MarketRegime.TRENDING_DOWN
    }

    // 5. Check Low Volatility compression
    if (indicators.atrState.classification == VolatilityClassification.LOW_VOLATILITY ||
      indicators.bollingerSignal.bandContraction
    ) {
      return MarketRegime.LOW_VOLATILITY
    }

    // 6. Check Sideways / Range
    if (priceAction.isConsolidation || priceAction.isRange || mtf.isConsolidating) {
      return MarketRegime.SIDEWAYS
    }

    return MarketRegime.UNCERTAIN
  }
}

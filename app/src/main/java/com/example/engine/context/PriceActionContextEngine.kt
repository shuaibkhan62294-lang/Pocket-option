package com.example.engine.context

import com.example.engine.breakout.BreakoutEngine
import com.example.engine.breakout.DefaultBreakoutEngine
import com.example.engine.candlestructure.AdvancedCandleStructureEngine
import com.example.engine.candlestructure.DefaultAdvancedCandleStructureEngine
import com.example.engine.indicator.IndicatorSnapshot
import com.example.engine.liquidity.DefaultLiquiditySweepEngine
import com.example.engine.liquidity.LiquiditySweepEngine
import com.example.engine.movement.PriceMicroStructureSnapshot
import com.example.engine.pattern.CandlestickPatternResult
import com.example.engine.priceaction.PriceActionSnapshot
import com.example.engine.reversal.DefaultReversalEngine
import com.example.engine.reversal.ReversalEngine
import com.example.engine.structure.DefaultMarketStructureEngine
import com.example.engine.structure.MarketStructureEngine
import com.example.model.Candle
import com.example.model.CandleSequenceFeatures
import com.example.model.ExtractionQuality
import com.example.model.InternalDirectionState
import com.example.model.MarketRegime
import com.example.model.MarketStructureType
import com.example.model.PriceActionContext
import com.example.model.SupportResistanceAnalysis

/**
 * 9. Price Action Context Engine (Part 4 Requirement 9).
 *
 * Synthesizes:
 * - TrendStructure
 * - SwingStructure
 * - BOSState
 * - CHoCHState
 * - SupportState
 * - ResistanceState
 * - BreakoutState
 * - LiquiditySweepState
 * - ReversalState
 * - MomentumState
 * - CandlePatternState
 * - CandleSequenceState
 * - MarketRegime
 * - DataQuality
 */
interface PriceActionContextEngine {
  fun buildContext(
    candles: List<Candle>,
    dataQuality: ExtractionQuality,
    marketRegime: MarketRegime,
    indicators: IndicatorSnapshot,
    supportResistance: SupportResistanceAnalysis,
    priceAction: PriceActionSnapshot,
    patterns: CandlestickPatternResult,
    sequenceFeatures: CandleSequenceFeatures,
    microStructure: PriceMicroStructureSnapshot = PriceMicroStructureSnapshot()
  ): PriceActionContext
}

class DefaultPriceActionContextEngine(
  private val candleStructureEngine: AdvancedCandleStructureEngine = DefaultAdvancedCandleStructureEngine(),
  private val marketStructureEngine: MarketStructureEngine = DefaultMarketStructureEngine(),
  private val liquiditySweepEngine: LiquiditySweepEngine = DefaultLiquiditySweepEngine(),
  private val breakoutEngine: BreakoutEngine = DefaultBreakoutEngine(),
  private val reversalEngine: ReversalEngine = DefaultReversalEngine()
) : PriceActionContextEngine {

  override fun buildContext(
    candles: List<Candle>,
    dataQuality: ExtractionQuality,
    marketRegime: MarketRegime,
    indicators: IndicatorSnapshot,
    supportResistance: SupportResistanceAnalysis,
    priceAction: PriceActionSnapshot,
    patterns: CandlestickPatternResult,
    sequenceFeatures: CandleSequenceFeatures,
    microStructure: PriceMicroStructureSnapshot
  ): PriceActionContext {
    if (candles.isEmpty()) {
      return PriceActionContext(
        marketRegime = marketRegime,
        dataQuality = dataQuality
      )
    }

    val candleStructures = candleStructureEngine.analyzeCandles(
      candles = candles,
      supportResistance = supportResistance,
      microStructure = microStructure
    )
    val swingStructure = marketStructureEngine.analyzeStructure(candles)
    val liquidityState = liquiditySweepEngine.analyzeLiquiditySweeps(
      candles = candles,
      supportResistance = supportResistance
    )
    val momentumState = priceAction.priceActionState.momentum
    val breakoutState = breakoutEngine.analyzeBreakout(
      candles = candles,
      supportResistance = supportResistance,
      momentum = momentumState,
      indicators = indicators
    )
    val reversalState = reversalEngine.analyzeReversal(
      candles = candles,
      indicators = indicators,
      supportResistance = supportResistance,
      marketStructure = swingStructure,
      momentum = momentumState,
      patterns = patterns,
      sequenceFeatures = sequenceFeatures
    )

    val trendDir = when (swingStructure.structureType) {
      MarketStructureType.UPTREND_STRUCTURE -> InternalDirectionState.BULLISH
      MarketStructureType.DOWNTREND_STRUCTURE -> InternalDirectionState.BEARISH
      MarketStructureType.POSSIBLE_REVERSAL -> InternalDirectionState.REVERSAL_RISK
      MarketStructureType.STRUCTURE_WEAKENING -> InternalDirectionState.REVERSAL_RISK
      MarketStructureType.STRUCTURE_BREAK -> priceAction.priceActionState.direction
      MarketStructureType.RANGE_STRUCTURE -> priceAction.priceActionState.direction
    }

    return PriceActionContext(
      trendStructure = trendDir,
      swingStructure = swingStructure,
      bosState = swingStructure.bosState,
      chochState = swingStructure.chochState,
      supportState = supportResistance.nearestSupport,
      resistanceState = supportResistance.nearestResistance,
      breakoutState = breakoutState,
      liquiditySweepState = liquidityState,
      reversalState = reversalState,
      momentumState = momentumState,
      candlePatternState = patterns.patterns,
      candleSequenceState = sequenceFeatures,
      latestCandleStructure = candleStructures.lastOrNull(),
      recentCandleStructures = candleStructures,
      marketRegime = marketRegime,
      dataQuality = dataQuality
    )
  }
}

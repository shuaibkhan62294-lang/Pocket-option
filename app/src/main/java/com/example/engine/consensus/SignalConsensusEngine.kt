package com.example.engine.consensus

import com.example.engine.brain.DefaultProTraderBrainEngine
import com.example.engine.brain.ProTraderBrainEngine
import com.example.engine.conflict.ConflictEngine
import com.example.engine.conflict.DefaultConflictEngine
import com.example.engine.context.DefaultPriceActionContextEngine
import com.example.engine.context.PriceActionContextEngine
import com.example.engine.indicator.IndicatorSnapshot
import com.example.engine.matching.DefaultHistoricalPatternMatcher
import com.example.engine.matching.HistoricalPatternMatcher
import com.example.engine.movement.PriceMicroStructureSnapshot
import com.example.engine.pattern.CandlestickPatternResult
import com.example.engine.priceaction.PriceActionSnapshot
import com.example.engine.setup.DefaultSetupQualityEngine
import com.example.engine.setup.SetupQualityEngine
import com.example.model.BosClassification
import com.example.model.BreakoutClassification
import com.example.model.Candle
import com.example.model.CandleSequenceFeatures
import com.example.model.ChochClassification
import com.example.model.ConflictAnalysisResult
import com.example.model.ExtractionQuality
import com.example.model.InternalDirectionState
import com.example.model.InternalStrengthLabel
import com.example.model.LiquiditySweepClassification
import com.example.model.MarketAnalysis
import com.example.model.MarketRegime
import com.example.model.NextCandleSignal
import com.example.model.NoTradeSafetyReason
import com.example.model.OutcomeMemoryRecord
import com.example.model.PriceActionContext
import com.example.model.ReversalClassification
import com.example.model.SetupQualityGrade
import com.example.model.SupportResistanceAnalysis
import com.example.model.VolatilityClassification

/**
 * 18–21 (Part 3), 10–12 (Part 4), & 1–8 (Part 5). Multi-Signal Confirmation & Consensus Engine
 *
 * Combines:
 * INDICATORS + PRICE ACTION + CANDLE PATTERNS + MARKET STRUCTURE +
 * SUPPORT/RESISTANCE + MOMENTUM + VOLATILITY + CANDLE SEQUENCE + DATA QUALITY
 * into a unified [MarketAnalysis] and [com.example.model.BrainAssessment].
 */
interface SignalConsensusEngine {
  fun buildMarketAnalysis(
    candles: List<Candle>,
    dataQuality: ExtractionQuality,
    indicators: IndicatorSnapshot,
    supportResistance: SupportResistanceAnalysis,
    priceAction: PriceActionSnapshot,
    patterns: CandlestickPatternResult,
    sequenceFeatures: CandleSequenceFeatures,
    marketRegime: MarketRegime,
    isChartRegionValid: Boolean = true,
    microStructure: PriceMicroStructureSnapshot = PriceMicroStructureSnapshot(),
    outcomeMemoryRecords: List<OutcomeMemoryRecord> = emptyList(),
    isScreenDetectionStable: Boolean = true
  ): MarketAnalysis
}

class DefaultSignalConsensusEngine(
  private val priceActionContextEngine: PriceActionContextEngine = DefaultPriceActionContextEngine(),
  private val conflictEngine: ConflictEngine = DefaultConflictEngine(),
  private val setupQualityEngine: SetupQualityEngine = DefaultSetupQualityEngine(),
  private val historicalPatternMatcher: HistoricalPatternMatcher = DefaultHistoricalPatternMatcher(),
  private val proTraderBrainEngine: ProTraderBrainEngine = DefaultProTraderBrainEngine(
    historicalPatternMatcher = historicalPatternMatcher
  )
) : SignalConsensusEngine {

  override fun buildMarketAnalysis(
    candles: List<Candle>,
    dataQuality: ExtractionQuality,
    indicators: IndicatorSnapshot,
    supportResistance: SupportResistanceAnalysis,
    priceAction: PriceActionSnapshot,
    patterns: CandlestickPatternResult,
    sequenceFeatures: CandleSequenceFeatures,
    marketRegime: MarketRegime,
    isChartRegionValid: Boolean,
    microStructure: PriceMicroStructureSnapshot,
    outcomeMemoryRecords: List<OutcomeMemoryRecord>,
    isScreenDetectionStable: Boolean
  ): MarketAnalysis {
    val paState = priceAction.priceActionState
    val momentumState = paState.momentum

    // Build complete Part 4 PriceActionContext
    val paContext = priceActionContextEngine.buildContext(
      candles = candles,
      dataQuality = dataQuality,
      marketRegime = marketRegime,
      indicators = indicators,
      supportResistance = supportResistance,
      priceAction = priceAction,
      patterns = patterns,
      sequenceFeatures = sequenceFeatures,
      microStructure = microStructure
    )

    val featureVector = historicalPatternMatcher.buildFeatureVector(
      candles = candles,
      indicators = indicators,
      priceActionContext = paContext,
      marketRegime = marketRegime
    )

    // 1. Check No-Trade Data Quality & Candle Count Safety Gates
    if (!isChartRegionValid || dataQuality == ExtractionQuality.LOW || dataQuality == ExtractionQuality.UNREADABLE) {
      val initialConflict = conflictEngine.evaluateConflicts(indicators, supportResistance, paContext, 0, 0)
      val noSetupEval = setupQualityEngine.evaluateSetupQuality(
        candleCount = candles.size,
        dataQuality = dataQuality,
        isChartRegionValid = isChartRegionValid,
        indicators = indicators,
        supportResistance = supportResistance,
        priceActionContext = paContext,
        conflictAnalysis = initialConflict,
        bullishEvidenceCount = 0,
        bearishEvidenceCount = 0
      )
      val snapshot = proTraderBrainEngine.buildMarketStateSnapshot(
        candles = candles,
        priceAction = priceAction,
        priceActionContext = paContext,
        supportResistance = supportResistance,
        indicators = indicators,
        sequenceFeatures = sequenceFeatures,
        marketRegime = MarketRegime.UNCERTAIN,
        setupQualityEvaluation = noSetupEval,
        dataQuality = dataQuality,
        isScreenDetectionStable = isScreenDetectionStable
      )
      val assessment = proTraderBrainEngine.assessMarketState(
        snapshot = snapshot,
        conflictAnalysis = initialConflict,
        outcomeMemoryRecords = outcomeMemoryRecords
      )
      return MarketAnalysis(
        marketRegime = MarketRegime.UNCERTAIN,
        priceActionContext = paContext,
        conflictAnalysis = initialConflict,
        setupQualityEvaluation = noSetupEval,
        setupQualityGrade = SetupQualityGrade.NO_SETUP,
        featureVector = featureVector,
        marketStateSnapshot = snapshot,
        patternFingerprint = assessment.patternFingerprint,
        brainAssessment = assessment,
        dataQuality = dataQuality,
        analysisState = InternalStrengthLabel.WEAK,
        safetyReason = NoTradeSafetyReason.LOW_DATA_QUALITY,
        recommendedSignal = NextCandleSignal.WAIT,
        marketContext = "Chart data quality is too low for reliable analysis",
        simpleWhyExplanations = listOf(
          "WAIT — Chart data unclear or not yet connected",
          "Waiting for clean candle data before analyzing"
        )
      )
    }

    if (candles.size < 15 || !indicators.isReady) {
      val initialConflict = conflictEngine.evaluateConflicts(indicators, supportResistance, paContext, 0, 0)
      val noSetupEval = setupQualityEngine.evaluateSetupQuality(
        candleCount = candles.size,
        dataQuality = dataQuality,
        isChartRegionValid = isChartRegionValid,
        indicators = indicators,
        supportResistance = supportResistance,
        priceActionContext = paContext,
        conflictAnalysis = initialConflict,
        bullishEvidenceCount = 0,
        bearishEvidenceCount = 0
      )
      val snapshot = proTraderBrainEngine.buildMarketStateSnapshot(
        candles = candles,
        priceAction = priceAction,
        priceActionContext = paContext,
        supportResistance = supportResistance,
        indicators = indicators,
        sequenceFeatures = sequenceFeatures,
        marketRegime = MarketRegime.UNCERTAIN,
        setupQualityEvaluation = noSetupEval,
        dataQuality = dataQuality,
        isScreenDetectionStable = isScreenDetectionStable
      )
      val assessment = proTraderBrainEngine.assessMarketState(
        snapshot = snapshot,
        conflictAnalysis = initialConflict,
        outcomeMemoryRecords = outcomeMemoryRecords
      )
      return MarketAnalysis(
        marketRegime = MarketRegime.UNCERTAIN,
        priceActionContext = paContext,
        conflictAnalysis = initialConflict,
        setupQualityEvaluation = noSetupEval,
        setupQualityGrade = SetupQualityGrade.NO_SETUP,
        featureVector = featureVector,
        marketStateSnapshot = snapshot,
        patternFingerprint = assessment.patternFingerprint,
        brainAssessment = assessment,
        dataQuality = dataQuality,
        analysisState = InternalStrengthLabel.WEAK,
        safetyReason = NoTradeSafetyReason.INSUFFICIENT_DATA,
        recommendedSignal = NextCandleSignal.WAIT,
        marketContext = "Collecting at least 30–40 candles for full multi-window analysis",
        simpleWhyExplanations = listOf(
          "Collecting candle data...",
          "More candles are needed for reliable trend & level detection"
        )
      )
    }

    val bullishEvidence = ArrayList<String>()
    val bearishEvidence = ArrayList<String>()

    // 2. Gather Evidence from Moving Averages & Oscillators
    if (indicators.emaSignal.direction == InternalDirectionState.BULLISH) {
      bullishEvidence.add("EMA alignment is bullish")
    } else if (indicators.emaSignal.direction == InternalDirectionState.BEARISH) {
      bearishEvidence.add("EMA alignment is bearish")
    }

    if (indicators.smaSignal.direction == InternalDirectionState.BULLISH) {
      bullishEvidence.add("Price confirmed above SMA trend")
    } else if (indicators.smaSignal.direction == InternalDirectionState.BEARISH) {
      bearishEvidence.add("Price confirmed below SMA trend")
    }

    if (indicators.macdSignal.direction == InternalDirectionState.BULLISH) {
      bullishEvidence.add("MACD momentum expanding upward")
    } else if (indicators.macdSignal.direction == InternalDirectionState.BEARISH) {
      bearishEvidence.add("MACD momentum expanding downward")
    }

    if (indicators.rsiSignal.directionWithPriceAction == InternalDirectionState.BULLISH) {
      bullishEvidence.add("RSI shows constructive bullish momentum")
    } else if (indicators.rsiSignal.directionWithPriceAction == InternalDirectionState.BEARISH) {
      bearishEvidence.add("RSI shows bearish downward momentum")
    }

    if (indicators.stochasticSignal.directionWithTrend == InternalDirectionState.BULLISH) {
      bullishEvidence.add("Stochastic aligned with upward trend")
    } else if (indicators.stochasticSignal.directionWithTrend == InternalDirectionState.BEARISH) {
      bearishEvidence.add("Stochastic aligned with downward trend")
    }

    if (indicators.cciSignal.momentum == InternalDirectionState.BULLISH) {
      bullishEvidence.add("CCI indicates positive impulse")
    } else if (indicators.cciSignal.momentum == InternalDirectionState.BEARISH) {
      bearishEvidence.add("CCI indicates negative impulse")
    }

    if (indicators.williamsSignal.direction == InternalDirectionState.BULLISH) {
      bullishEvidence.add("Williams %R confirms buying pressure")
    } else if (indicators.williamsSignal.direction == InternalDirectionState.BEARISH) {
      bearishEvidence.add("Williams %R confirms selling pressure")
    }

    // 3. Gather Evidence from Part 4 Market Structure, BOS / CHoCH, Liquidity Sweeps, Breakouts & Reversals
    if (paState.isUptrend || paContext.bosState == BosClassification.BULLISH_BOS) {
      bullishEvidence.add("Strong bullish structure (HH/HL)")
    } else if (paState.isDowntrend || paContext.bosState == BosClassification.BEARISH_BOS) {
      bearishEvidence.add("Strong bearish structure (LH/LL)")
    }

    if (paContext.chochState == ChochClassification.BULLISH_CHOCH) {
      bullishEvidence.add("Bullish change of character (CHoCH)")
    } else if (paContext.chochState == ChochClassification.BEARISH_CHOCH) {
      bearishEvidence.add("Bearish change of character (CHoCH)")
    }

    if (supportResistance.supportRejection) {
      bullishEvidence.add("Support rejection")
    }
    if (supportResistance.resistanceRejection) {
      bearishEvidence.add("Resistance rejection")
    }

    if (paContext.liquiditySweepState.classification == LiquiditySweepClassification.CONFIRMED_REJECTION_AFTER_SWEEP) {
      if (paContext.liquiditySweepState.sweepDirectionBias == InternalDirectionState.BULLISH) {
        bullishEvidence.add("Bullish rejection after lower liquidity sweep")
      } else if (paContext.liquiditySweepState.sweepDirectionBias == InternalDirectionState.BEARISH) {
        bearishEvidence.add("Bearish rejection after upper liquidity sweep")
      }
    }

    if (paContext.breakoutState.classification == BreakoutClassification.BREAKOUT_CONFIRMED) {
      if (paContext.breakoutState.breakoutDirection == InternalDirectionState.BULLISH) {
        bullishEvidence.add("Confirmed upside breakout")
      } else if (paContext.breakoutState.breakoutDirection == InternalDirectionState.BEARISH) {
        bearishEvidence.add("Confirmed downside breakdown")
      }
    }

    if (paContext.reversalState.classification == ReversalClassification.STRONG_REVERSAL_EVIDENCE) {
      if (paContext.reversalState.reversalDirection == InternalDirectionState.BULLISH) {
        bullishEvidence.add("Multi-factor bullish reversal alignment")
      } else if (paContext.reversalState.reversalDirection == InternalDirectionState.BEARISH) {
        bearishEvidence.add("Multi-factor bearish reversal alignment")
      }
    }

    if (momentumState.strongBullishMomentum) {
      bullishEvidence.add("Bullish momentum")
    } else if (momentumState.strongBearishMomentum) {
      bearishEvidence.add("Bearish momentum")
    }

    // 4. Context-Aware Candlestick Patterns (only count patterns that do NOT conflict with context)
    for (pattern in patterns.patterns) {
      if (pattern.conflictsWithCurrentTrend && !pattern.occursNearSupportOrResistance) continue
      when (pattern.direction) {
        InternalDirectionState.BULLISH ->
          bullishEvidence.add("${pattern.patternName} (${pattern.location})")
        InternalDirectionState.BEARISH ->
          bearishEvidence.add("${pattern.patternName} (${pattern.location})")
        else -> {}
      }
    }

    // 5. Evaluate Conflicts via ConflictEngine (Requirement 11)
    val conflictAnalysis = conflictEngine.evaluateConflicts(
      indicators = indicators,
      supportResistance = supportResistance,
      priceActionContext = paContext,
      bullishEvidenceCount = bullishEvidence.size,
      bearishEvidenceCount = bearishEvidence.size
    )

    // 6. Evaluate Setup Quality Grade (A+, A, B, C, NO_SETUP) via SetupQualityEngine (Requirement 12)
    val setupQualityEval = setupQualityEngine.evaluateSetupQuality(
      candleCount = candles.size,
      dataQuality = dataQuality,
      isChartRegionValid = isChartRegionValid,
      indicators = indicators,
      supportResistance = supportResistance,
      priceActionContext = paContext,
      conflictAnalysis = conflictAnalysis,
      bullishEvidenceCount = bullishEvidence.size,
      bearishEvidenceCount = bearishEvidence.size
    )

    // 7. Build Part 5 MarketStateSnapshot, PatternFingerprint, and Pro Trader BrainAssessment
    val marketStateSnapshot = proTraderBrainEngine.buildMarketStateSnapshot(
      candles = candles,
      priceAction = priceAction,
      priceActionContext = paContext,
      supportResistance = supportResistance,
      indicators = indicators,
      sequenceFeatures = sequenceFeatures,
      marketRegime = marketRegime,
      setupQualityEvaluation = setupQualityEval,
      dataQuality = dataQuality,
      isScreenDetectionStable = isScreenDetectionStable
    )
    val brainAssessment = proTraderBrainEngine.assessMarketState(
      snapshot = marketStateSnapshot,
      conflictAnalysis = conflictAnalysis,
      outcomeMemoryRecords = outcomeMemoryRecords
    )

    val isUnstableVolatility = indicators.atrState.classification == VolatilityClassification.HIGH_VOLATILITY &&
      indicators.atrState.unusuallyLargeCandles

    val safetyReason = when {
      isUnstableVolatility -> NoTradeSafetyReason.UNSTABLE_MARKET
      conflictAnalysis.hasConflict -> NoTradeSafetyReason.CONFLICTING_SIGNALS
      candles.size < 30 -> NoTradeSafetyReason.INSUFFICIENT_DATA
      else -> NoTradeSafetyReason.READY_FOR_EVALUATION
    }

    val analysisStrength = when {
      conflictAnalysis.hasConflict -> InternalStrengthLabel.CONFLICTING
      setupQualityEval.grade == SetupQualityGrade.A_PLUS || setupQualityEval.grade == SetupQualityGrade.A ->
        InternalStrengthLabel.STRONG
      setupQualityEval.grade == SetupQualityGrade.B -> InternalStrengthLabel.MODERATE
      else -> InternalStrengthLabel.WEAK
    }

    val overallTrendDir = when {
      conflictAnalysis.hasConflict -> InternalDirectionState.REVERSAL_RISK
      brainAssessment.dominantDirection != InternalDirectionState.NEUTRAL ->
        brainAssessment.dominantDirection
      bullishEvidence.size > bearishEvidence.size + 1 -> InternalDirectionState.BULLISH
      bearishEvidence.size > bullishEvidence.size + 1 -> InternalDirectionState.BEARISH
      paState.isConsolidation -> InternalDirectionState.CONSOLIDATING
      else -> sequenceFeatures.multiTimeframe.overallDirection
    }

    // Build concise beginner-friendly "Why?" explanations (Part 4 Requirement 17)
    val simpleWhyList = buildSimpleWhyExplanations(
      overallTrendDir = overallTrendDir,
      supportResistance = supportResistance,
      paContext = paContext,
      conflictAnalysis = conflictAnalysis,
      bullishEvidence = bullishEvidence,
      bearishEvidence = bearishEvidence
    )

    val contextSummary = when (marketRegime) {
      MarketRegime.TRENDING_UP -> "Uptrend structure"
      MarketRegime.TRENDING_DOWN -> "Downtrend structure"
      MarketRegime.SIDEWAYS -> "Sideways range"
      MarketRegime.BREAKOUT -> "Testing upside breakout"
      MarketRegime.BREAKDOWN -> "Testing downside breakdown"
      MarketRegime.HIGH_VOLATILITY -> "High volatility conditions"
      MarketRegime.LOW_VOLATILITY -> "Low volatility compression"
      MarketRegime.REVERSAL_RISK -> "Potential reversal zone"
      MarketRegime.UNCERTAIN -> "Mixed market structure"
    }

    return MarketAnalysis(
      marketRegime = marketRegime,
      trendDirection = overallTrendDir,
      trendStrength = analysisStrength,
      momentumState = momentumState.direction,
      volatilityState = indicators.atrState.classification,
      emaSignal = indicators.emaSignal,
      smaSignal = indicators.smaSignal,
      rsiSignal = indicators.rsiSignal,
      macdSignal = indicators.macdSignal,
      bollingerSignal = indicators.bollingerSignal,
      stochasticSignal = indicators.stochasticSignal,
      atrState = indicators.atrState,
      adxSignal = indicators.adxSignal,
      cciSignal = indicators.cciSignal,
      williamsSignal = indicators.williamsSignal,
      supportZones = supportResistance.supportZones,
      resistanceZones = supportResistance.resistanceZones,
      supportResistanceAnalysis = supportResistance,
      priceActionState = paState,
      candlestickPatterns = patterns.patterns,
      candleSequenceFeatures = sequenceFeatures,
      priceActionContext = paContext,
      conflictAnalysis = conflictAnalysis,
      setupQualityEvaluation = setupQualityEval,
      setupQualityGrade = setupQualityEval.grade,
      featureVector = featureVector,
      marketStateSnapshot = marketStateSnapshot,
      patternFingerprint = brainAssessment.patternFingerprint,
      brainAssessment = brainAssessment,
      bullishEvidence = bullishEvidence,
      bearishEvidence = bearishEvidence,
      conflicts = conflictAnalysis.conflictDescriptions,
      marketContext = contextSummary,
      dataQuality = dataQuality,
      analysisState = analysisStrength,
      safetyReason = safetyReason,
      // Part 5 strict rule: do NOT implement final next-candle UI prediction yet (Part 6 will do that)
      recommendedSignal = NextCandleSignal.WAIT,
      simpleWhyExplanations = simpleWhyList
    )
  }

  private fun buildSimpleWhyExplanations(
    overallTrendDir: InternalDirectionState,
    supportResistance: SupportResistanceAnalysis,
    paContext: PriceActionContext,
    conflictAnalysis: ConflictAnalysisResult,
    bullishEvidence: List<String>,
    bearishEvidence: List<String>
  ): List<String> {
    val explanations = ArrayList<String>()

    if (conflictAnalysis.hasConflict) {
      if (supportResistance.resistanceRejection || supportResistance.isNearResistance) {
        explanations.add("WAIT — resistance rejection and conflicting signals")
      } else if (supportResistance.supportRejection || supportResistance.isNearSupport) {
        explanations.add("WAIT — support rejection and conflicting signals")
      } else {
        explanations.add("WAIT — signals are conflicting across structure and momentum")
      }
      explanations.addAll(conflictAnalysis.conflictDescriptions.take(2))
      return explanations.distinct().take(4)
    }

    if (overallTrendDir == InternalDirectionState.BULLISH) {
      val parts = ArrayList<String>()
      parts.add("Strong bullish structure")
      if (supportResistance.supportRejection || supportResistance.isNearSupport) {
        parts.add("support rejection")
      }
      if (paContext.momentumState.direction == InternalDirectionState.BULLISH) {
        parts.add("bullish momentum")
      }
      explanations.add(parts.joinToString(" + "))
      explanations.addAll(bullishEvidence.take(2))
    } else if (overallTrendDir == InternalDirectionState.BEARISH) {
      val parts = ArrayList<String>()
      parts.add("Strong bearish structure")
      if (supportResistance.resistanceRejection || supportResistance.isNearResistance) {
        parts.add("resistance rejection")
      }
      if (paContext.momentumState.direction == InternalDirectionState.BEARISH) {
        parts.add("bearish momentum")
      }
      explanations.add(parts.joinToString(" + "))
      explanations.addAll(bearishEvidence.take(2))
    } else {
      explanations.add("WAIT — market is consolidating in a range")
    }

    return explanations.distinct().take(4)
  }
}

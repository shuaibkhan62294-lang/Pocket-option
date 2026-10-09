package com.example.engine.weighting

import com.example.engine.regime.RegimeReasoningProfile
import com.example.model.BosClassification
import com.example.model.BreakoutClassification
import com.example.model.ChochClassification
import com.example.model.ConflictAnalysisResult
import com.example.model.ContextualPatternEvaluation
import com.example.model.ExtractionQuality
import com.example.model.HistoricalPatternEvidence
import com.example.model.HistoricalSampleSufficiency
import com.example.model.InternalDirectionState
import com.example.model.LiquiditySweepClassification
import com.example.model.MarketStateSnapshot
import com.example.model.ReversalClassification
import com.example.model.WeightedEvidenceItem
import com.example.model.ZoneStrength

/**
 * Configurable, testable weights for the Evidence Weighting Engine (Part 5 Requirement 6).
 *
 * Uses transparent structural baseline weights (never inventing fake performance-based weights
 * without measured historical data).
 */
data class EvidenceWeightConfig(
  val priceActionStructureBaseWeight: Double = 1.40,
  val bosBaseWeight: Double = 1.25,
  val chochBaseWeight: Double = 1.30,
  val supportResistanceRejectionBaseWeight: Double = 1.45,
  val confirmedBreakoutBaseWeight: Double = 1.50,
  val liquiditySweepRejectionBaseWeight: Double = 1.35,
  val strongReversalBaseWeight: Double = 1.40,
  val momentumConfirmationBaseWeight: Double = 1.10,
  val contextualPatternBaseWeight: Double = 1.20,
  val trendIndicatorBaseWeight: Double = 0.85,
  val oscillatorIndicatorBaseWeight: Double = 0.65,
  val historicalEvidenceBaseWeight: Double = 1.15,
  val highDataQualityMultiplier: Double = 1.00,
  val mediumDataQualityMultiplier: Double = 0.75,
  val lowDataQualityMultiplier: Double = 0.00,
  val conflictPenaltyPerConflict: Double = 0.85
)

/**
 * Output of the Evidence Weighting Engine (Part 5 Requirement 6).
 */
data class WeightedEvidenceEvaluation(
  val bullishItems: List<WeightedEvidenceItem> = emptyList(),
  val bearishItems: List<WeightedEvidenceItem> = emptyList(),
  val rawBullishWeight: Double = 0.0,
  val rawBearishWeight: Double = 0.0,
  val conflictPenalty: Double = 0.0,
  val dataQualityFactor: Double = 0.0,
  val totalBullishWeight: Double = 0.0,
  val totalBearishWeight: Double = 0.0,
  val netDirectionalWeight: Double = 0.0,
  val dominantDirection: InternalDirectionState = InternalDirectionState.NEUTRAL
)

/**
 * 6. Evidence Weighting Engine (Part 5 Requirement 6).
 *
 * Weights evidence across:
 * - Data quality
 * - Market regime
 * - Historical relevance (only when sample is sufficient)
 * - Price-action confirmation
 * - Support/resistance location
 * - Momentum confirmation
 * - Pattern quality (context-adjusted)
 * - Conflicting evidence
 */
interface EvidenceWeightingEngine {
  val config: EvidenceWeightConfig

  fun weightEvidence(
    snapshot: MarketStateSnapshot,
    contextualPatterns: List<ContextualPatternEvaluation>,
    regimeProfile: RegimeReasoningProfile,
    conflictAnalysis: ConflictAnalysisResult,
    historicalEvidence: HistoricalPatternEvidence
  ): WeightedEvidenceEvaluation
}

class DefaultEvidenceWeightingEngine(
  override val config: EvidenceWeightConfig = EvidenceWeightConfig()
) : EvidenceWeightingEngine {

  override fun weightEvidence(
    snapshot: MarketStateSnapshot,
    contextualPatterns: List<ContextualPatternEvaluation>,
    regimeProfile: RegimeReasoningProfile,
    conflictAnalysis: ConflictAnalysisResult,
    historicalEvidence: HistoricalPatternEvidence
  ): WeightedEvidenceEvaluation {
    val dataQualityFactor = when (snapshot.dataQuality) {
      ExtractionQuality.HIGH -> config.highDataQualityMultiplier
      ExtractionQuality.MEDIUM -> config.mediumDataQualityMultiplier
      ExtractionQuality.LOW, ExtractionQuality.UNREADABLE -> config.lowDataQualityMultiplier
    }

    if (dataQualityFactor <= 0.0) {
      return WeightedEvidenceEvaluation(dataQualityFactor = 0.0)
    }

    val bullishItems = ArrayList<WeightedEvidenceItem>()
    val bearishItems = ArrayList<WeightedEvidenceItem>()

    fun addEvidence(
      category: String,
      description: String,
      direction: InternalDirectionState,
      baseWeight: Double,
      contextMultiplier: Double
    ) {
      val finalW = (baseWeight * contextMultiplier * dataQualityFactor).coerceAtLeast(0.0)
      if (finalW <= 0.0) return
      val item = WeightedEvidenceItem(
        sourceCategory = category,
        description = description,
        direction = direction,
        baseWeight = baseWeight,
        contextMultiplier = contextMultiplier,
        finalWeight = finalW
      )
      if (direction == InternalDirectionState.BULLISH) {
        bullishItems.add(item)
      } else if (direction == InternalDirectionState.BEARISH) {
        bearishItems.add(item)
      }
    }

    // 1. Price-Action Structure (HH/HL vs LH/LL)
    if (snapshot.priceAction.isUptrend || (snapshot.higherHigh && snapshot.higherLow)) {
      addEvidence(
        category = "PRICE_ACTION_STRUCTURE",
        description = "Higher Highs & Higher Lows uptrend structure",
        direction = InternalDirectionState.BULLISH,
        baseWeight = config.priceActionStructureBaseWeight,
        contextMultiplier = regimeProfile.trendIndicatorMultiplier
      )
    } else if (snapshot.priceAction.isDowntrend || (snapshot.lowerHigh && snapshot.lowerLow)) {
      addEvidence(
        category = "PRICE_ACTION_STRUCTURE",
        description = "Lower Highs & Lower Lows downtrend structure",
        direction = InternalDirectionState.BEARISH,
        baseWeight = config.priceActionStructureBaseWeight,
        contextMultiplier = regimeProfile.trendIndicatorMultiplier
      )
    }

    // 2. Break of Structure (BOS) & Change of Character (CHoCH)
    if (snapshot.bosState == BosClassification.BULLISH_BOS) {
      addEvidence(
        category = "MARKET_STRUCTURE_BOS",
        description = "Confirmed Bullish Break of Structure (BOS)",
        direction = InternalDirectionState.BULLISH,
        baseWeight = config.bosBaseWeight,
        contextMultiplier = regimeProfile.breakoutStructureMultiplier
      )
    } else if (snapshot.bosState == BosClassification.BEARISH_BOS) {
      addEvidence(
        category = "MARKET_STRUCTURE_BOS",
        description = "Confirmed Bearish Break of Structure (BOS)",
        direction = InternalDirectionState.BEARISH,
        baseWeight = config.bosBaseWeight,
        contextMultiplier = regimeProfile.breakoutStructureMultiplier
      )
    }

    if (snapshot.chochState == ChochClassification.BULLISH_CHOCH) {
      addEvidence(
        category = "MARKET_STRUCTURE_CHOCH",
        description = "Bullish Change of Character (CHoCH)",
        direction = InternalDirectionState.BULLISH,
        baseWeight = config.chochBaseWeight,
        contextMultiplier = regimeProfile.reversalEvidenceMultiplier
      )
    } else if (snapshot.chochState == ChochClassification.BEARISH_CHOCH) {
      addEvidence(
        category = "MARKET_STRUCTURE_CHOCH",
        description = "Bearish Change of Character (CHoCH)",
        direction = InternalDirectionState.BEARISH,
        baseWeight = config.chochBaseWeight,
        contextMultiplier = regimeProfile.reversalEvidenceMultiplier
      )
    }

    // 3. Support / Resistance Location & Rejection
    val sr = snapshot.supportResistance
    val supStrengthBoost = when (sr.nearestSupport?.strength) {
      ZoneStrength.VERY_STRONG -> 1.30
      ZoneStrength.STRONG -> 1.15
      ZoneStrength.MEDIUM -> 1.00
      else -> 0.80
    }
    val resStrengthBoost = when (sr.nearestResistance?.strength) {
      ZoneStrength.VERY_STRONG -> 1.30
      ZoneStrength.STRONG -> 1.15
      ZoneStrength.MEDIUM -> 1.00
      else -> 0.80
    }

    if (sr.supportRejection) {
      addEvidence(
        category = "SUPPORT_RESISTANCE",
        description = "Support zone rejection (${sr.nearestSupport?.strength ?: ZoneStrength.MEDIUM})",
        direction = InternalDirectionState.BULLISH,
        baseWeight = config.supportResistanceRejectionBaseWeight,
        contextMultiplier = regimeProfile.supportResistanceBoundaryMultiplier * supStrengthBoost
      )
    }
    if (sr.resistanceRejection) {
      addEvidence(
        category = "SUPPORT_RESISTANCE",
        description = "Resistance zone rejection (${sr.nearestResistance?.strength ?: ZoneStrength.MEDIUM})",
        direction = InternalDirectionState.BEARISH,
        baseWeight = config.supportResistanceRejectionBaseWeight,
        contextMultiplier = regimeProfile.supportResistanceBoundaryMultiplier * resStrengthBoost
      )
    }

    // 4. Breakout / Retest Confirmation
    if (snapshot.breakoutRetestState.classification == BreakoutClassification.BREAKOUT_CONFIRMED) {
      if (snapshot.breakoutRetestState.breakoutDirection == InternalDirectionState.BULLISH) {
        addEvidence(
          category = "BREAKOUT_INTELLIGENCE",
          description = "Confirmed upside breakout with body expansion",
          direction = InternalDirectionState.BULLISH,
          baseWeight = config.confirmedBreakoutBaseWeight,
          contextMultiplier = regimeProfile.breakoutStructureMultiplier
        )
      } else if (snapshot.breakoutRetestState.breakoutDirection == InternalDirectionState.BEARISH) {
        addEvidence(
          category = "BREAKOUT_INTELLIGENCE",
          description = "Confirmed downside breakdown with body expansion",
          direction = InternalDirectionState.BEARISH,
          baseWeight = config.confirmedBreakoutBaseWeight,
          contextMultiplier = regimeProfile.breakoutStructureMultiplier
        )
      }
    }

    // 5. Liquidity Sweep + Rejection
    if (snapshot.liquiditySweepState.classification == LiquiditySweepClassification.CONFIRMED_REJECTION_AFTER_SWEEP) {
      if (snapshot.liquiditySweepState.sweepDirectionBias == InternalDirectionState.BULLISH) {
        addEvidence(
          category = "LIQUIDITY_SWEEP",
          description = "Confirmed bullish rejection after lower liquidity sweep",
          direction = InternalDirectionState.BULLISH,
          baseWeight = config.liquiditySweepRejectionBaseWeight,
          contextMultiplier = regimeProfile.reversalEvidenceMultiplier
        )
      } else if (snapshot.liquiditySweepState.sweepDirectionBias == InternalDirectionState.BEARISH) {
        addEvidence(
          category = "LIQUIDITY_SWEEP",
          description = "Confirmed bearish rejection after upper liquidity sweep",
          direction = InternalDirectionState.BEARISH,
          baseWeight = config.liquiditySweepRejectionBaseWeight,
          contextMultiplier = regimeProfile.reversalEvidenceMultiplier
        )
      }
    }

    // 6. Multi-factor Reversal Intelligence
    if (snapshot.reversalState.classification == ReversalClassification.STRONG_REVERSAL_EVIDENCE) {
      addEvidence(
        category = "REVERSAL_INTELLIGENCE",
        description = "Strong multi-factor ${snapshot.reversalState.reversalDirection.name.lowercase()} reversal alignment",
        direction = snapshot.reversalState.reversalDirection,
        baseWeight = config.strongReversalBaseWeight,
        contextMultiplier = regimeProfile.reversalEvidenceMultiplier
      )
    }

    // 7. Momentum Confirmation
    if (snapshot.momentum.strongBullishMomentum ||
      (snapshot.momentum.direction == InternalDirectionState.BULLISH && !snapshot.momentum.weakeningMomentum)
    ) {
      addEvidence(
        category = "MOMENTUM",
        description = "Constructive bullish candle momentum",
        direction = InternalDirectionState.BULLISH,
        baseWeight = config.momentumConfirmationBaseWeight,
        contextMultiplier = regimeProfile.momentumMultiplier
      )
    } else if (snapshot.momentum.strongBearishMomentum ||
      (snapshot.momentum.direction == InternalDirectionState.BEARISH && !snapshot.momentum.weakeningMomentum)
    ) {
      addEvidence(
        category = "MOMENTUM",
        description = "Constructive bearish candle momentum",
        direction = InternalDirectionState.BEARISH,
        baseWeight = config.momentumConfirmationBaseWeight,
        contextMultiplier = regimeProfile.momentumMultiplier
      )
    }

    // 8. Context-Aware Candlestick Patterns (Part 5 Requirement 5)
    for (cp in contextualPatterns) {
      if (cp.direction == InternalDirectionState.BULLISH || cp.direction == InternalDirectionState.BEARISH) {
        addEvidence(
          category = "CONTEXTUAL_PATTERN",
          description = cp.contextReasoning,
          direction = cp.direction,
          baseWeight = config.contextualPatternBaseWeight,
          contextMultiplier = cp.contextWeightMultiplier
        )
      }
    }

    // 9. Trend & Oscillator Indicators (weighted by regime profile, never simply counted equally)
    val ind = snapshot.indicatorStates
    if (ind.emaSignal.direction == InternalDirectionState.BULLISH) {
      addEvidence(
        category = "TREND_INDICATOR",
        description = "EMA 9/21/50 bullish alignment",
        direction = InternalDirectionState.BULLISH,
        baseWeight = config.trendIndicatorBaseWeight,
        contextMultiplier = regimeProfile.trendIndicatorMultiplier
      )
    } else if (ind.emaSignal.direction == InternalDirectionState.BEARISH) {
      addEvidence(
        category = "TREND_INDICATOR",
        description = "EMA 9/21/50 bearish alignment",
        direction = InternalDirectionState.BEARISH,
        baseWeight = config.trendIndicatorBaseWeight,
        contextMultiplier = regimeProfile.trendIndicatorMultiplier
      )
    }

    if (ind.macdSignal.direction == InternalDirectionState.BULLISH) {
      addEvidence(
        category = "TREND_INDICATOR",
        description = "MACD bullish impulse",
        direction = InternalDirectionState.BULLISH,
        baseWeight = config.trendIndicatorBaseWeight,
        contextMultiplier = regimeProfile.trendIndicatorMultiplier
      )
    } else if (ind.macdSignal.direction == InternalDirectionState.BEARISH) {
      addEvidence(
        category = "TREND_INDICATOR",
        description = "MACD bearish impulse",
        direction = InternalDirectionState.BEARISH,
        baseWeight = config.trendIndicatorBaseWeight,
        contextMultiplier = regimeProfile.trendIndicatorMultiplier
      )
    }

    if (ind.rsiSignal.directionWithPriceAction == InternalDirectionState.BULLISH) {
      addEvidence(
        category = "OSCILLATOR_INDICATOR",
        description = "RSI aligned with bullish price action",
        direction = InternalDirectionState.BULLISH,
        baseWeight = config.oscillatorIndicatorBaseWeight,
        contextMultiplier = regimeProfile.oscillatorMeanReversionMultiplier
      )
    } else if (ind.rsiSignal.directionWithPriceAction == InternalDirectionState.BEARISH) {
      addEvidence(
        category = "OSCILLATOR_INDICATOR",
        description = "RSI aligned with bearish price action",
        direction = InternalDirectionState.BEARISH,
        baseWeight = config.oscillatorIndicatorBaseWeight,
        contextMultiplier = regimeProfile.oscillatorMeanReversionMultiplier
      )
    }

    // 10. Historical Pattern Relevance (ONLY if measured historical sample is sufficient!)
    if (historicalEvidence.isSampleSufficient &&
      (historicalEvidence.sampleSufficiency == HistoricalSampleSufficiency.MODERATE_EVIDENCE ||
        historicalEvidence.sampleSufficiency == HistoricalSampleSufficiency.POTENTIALLY_USEFUL)
    ) {
      val dist = historicalEvidence.outcomeDistribution
      val sufficiencyMult = if (historicalEvidence.sampleSufficiency == HistoricalSampleSufficiency.POTENTIALLY_USEFUL) {
        1.25
      } else {
        0.90
      }
      if (dist.bullishRatio >= 0.60) {
        addEvidence(
          category = "HISTORICAL_PATTERN_MATCH",
          description = "Historical pattern matches favor bullish outcome (${historicalEvidence.historicalMatchesCount} matches)",
          direction = InternalDirectionState.BULLISH,
          baseWeight = config.historicalEvidenceBaseWeight,
          contextMultiplier = sufficiencyMult * historicalEvidence.averageSimilarityScore
        )
      } else if (dist.bearishRatio >= 0.60) {
        addEvidence(
          category = "HISTORICAL_PATTERN_MATCH",
          description = "Historical pattern matches favor bearish outcome (${historicalEvidence.historicalMatchesCount} matches)",
          direction = InternalDirectionState.BEARISH,
          baseWeight = config.historicalEvidenceBaseWeight,
          contextMultiplier = sufficiencyMult * historicalEvidence.averageSimilarityScore
        )
      }
    }

    val rawBull = bullishItems.sumOf { it.finalWeight }
    val rawBear = bearishItems.sumOf { it.finalWeight }

    val conflictCount = conflictAnalysis.conflictDescriptions.size
    val conflictPenalty = conflictCount * config.conflictPenaltyPerConflict

    val adjustedBull = (rawBull - conflictPenalty * 0.5).coerceAtLeast(0.0)
    val adjustedBear = (rawBear - conflictPenalty * 0.5).coerceAtLeast(0.0)
    val netDelta = adjustedBull - adjustedBear

    val dominant = when {
      conflictAnalysis.priceActionConflict || conflictAnalysis.breakoutVsRejectionConflict ->
        InternalDirectionState.REVERSAL_RISK
      netDelta >= 1.25 -> InternalDirectionState.BULLISH
      netDelta <= -1.25 -> InternalDirectionState.BEARISH
      snapshot.priceAction.isConsolidation -> InternalDirectionState.CONSOLIDATING
      else -> InternalDirectionState.NEUTRAL
    }

    return WeightedEvidenceEvaluation(
      bullishItems = bullishItems,
      bearishItems = bearishItems,
      rawBullishWeight = rawBull,
      rawBearishWeight = rawBear,
      conflictPenalty = conflictPenalty,
      dataQualityFactor = dataQualityFactor,
      totalBullishWeight = adjustedBull,
      totalBearishWeight = adjustedBear,
      netDirectionalWeight = netDelta,
      dominantDirection = dominant
    )
  }
}

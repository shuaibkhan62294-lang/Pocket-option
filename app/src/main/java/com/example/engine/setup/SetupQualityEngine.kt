package com.example.engine.setup

import com.example.engine.indicator.IndicatorSnapshot
import com.example.model.BreakoutClassification
import com.example.model.ConflictAnalysisResult
import com.example.model.ExtractionQuality
import com.example.model.InternalDirectionState
import com.example.model.MarketRegime
import com.example.model.PriceActionContext
import com.example.model.ReversalClassification
import com.example.model.SetupQualityEvaluation
import com.example.model.SetupQualityGrade
import com.example.model.SupportResistanceAnalysis
import com.example.model.VolatilityClassification

/**
 * 12 & 15. Setup Quality Engine (Part 4 Requirements 12 & 15).
 *
 * Classifies setup quality internally as:
 * - A+ (only when multiple independent forms of evidence align and data quality is HIGH)
 * - A
 * - B
 * - C
 * - NO_SETUP (whenever data quality is poor, candles are missing, or market is unreadable)
 *
 * Downgrades setup quality when signals conflict, market is noisy, support/resistance
 * is unclear, breakout is unconfirmed, or reversal evidence is incomplete.
 */
interface SetupQualityEngine {
  fun evaluateSetupQuality(
    candleCount: Int,
    dataQuality: ExtractionQuality,
    isChartRegionValid: Boolean,
    indicators: IndicatorSnapshot,
    supportResistance: SupportResistanceAnalysis,
    priceActionContext: PriceActionContext,
    conflictAnalysis: ConflictAnalysisResult,
    bullishEvidenceCount: Int,
    bearishEvidenceCount: Int
  ): SetupQualityEvaluation
}

class DefaultSetupQualityEngine : SetupQualityEngine {

  override fun evaluateSetupQuality(
    candleCount: Int,
    dataQuality: ExtractionQuality,
    isChartRegionValid: Boolean,
    indicators: IndicatorSnapshot,
    supportResistance: SupportResistanceAnalysis,
    priceActionContext: PriceActionContext,
    conflictAnalysis: ConflictAnalysisResult,
    bullishEvidenceCount: Int,
    bearishEvidenceCount: Int
  ): SetupQualityEvaluation {
    val downgrades = ArrayList<String>()

    // 15. Data Quality Safety Gate -> immediate NO_SETUP
    if (!isChartRegionValid) {
      return SetupQualityEvaluation(
        grade = SetupQualityGrade.NO_SETUP,
        alignedFactorsCount = 0,
        downgradeReasons = listOf("Chart region is not selected or invalid"),
        summaryReason = "NO_SETUP — Please select the chart area"
      )
    }

    if (dataQuality == ExtractionQuality.LOW || dataQuality == ExtractionQuality.UNREADABLE) {
      return SetupQualityEvaluation(
        grade = SetupQualityGrade.NO_SETUP,
        alignedFactorsCount = 0,
        downgradeReasons = listOf("Chart data quality is $dataQuality"),
        summaryReason = "NO_SETUP — Waiting for clear chart data"
      )
    }

    if (candleCount < 20 || !indicators.isReady) {
      return SetupQualityEvaluation(
        grade = SetupQualityGrade.NO_SETUP,
        alignedFactorsCount = 0,
        downgradeReasons = listOf("Collecting minimum candle history ($candleCount candles)"),
        summaryReason = "NO_SETUP — Collecting candle data..."
      )
    }

    // Count independent aligned dimensions
    var alignedFactors = 0
    val dominantBullish = bullishEvidenceCount > bearishEvidenceCount
    val dominantCount = maxOf(bullishEvidenceCount, bearishEvidenceCount)

    if (dominantCount >= 3) alignedFactors++
    if (dominantCount >= 5) alignedFactors++

    val structureAligned = (dominantBullish && priceActionContext.trendStructure == InternalDirectionState.BULLISH) ||
      (!dominantBullish && priceActionContext.trendStructure == InternalDirectionState.BEARISH)
    if (structureAligned) alignedFactors++

    val srAligned = (dominantBullish && (supportResistance.supportRejection || supportResistance.resistanceBreakout)) ||
      (!dominantBullish && (supportResistance.resistanceRejection || supportResistance.supportBreakout))
    if (srAligned) alignedFactors++

    val momentumAligned = (dominantBullish && priceActionContext.momentumState.direction == InternalDirectionState.BULLISH) ||
      (!dominantBullish && priceActionContext.momentumState.direction == InternalDirectionState.BEARISH)
    if (momentumAligned) alignedFactors++

    val patternAligned = priceActionContext.candlePatternState.any {
      (dominantBullish && it.direction == InternalDirectionState.BULLISH && !it.conflictsWithCurrentTrend) ||
        (!dominantBullish && it.direction == InternalDirectionState.BEARISH && !it.conflictsWithCurrentTrend)
    }
    if (patternAligned) alignedFactors++

    // Evaluate Downgrade Conditions (Requirement 12)
    var penaltySteps = 0

    if (conflictAnalysis.hasConflict) {
      penaltySteps += if (conflictAnalysis.priceActionConflict || conflictAnalysis.breakoutVsRejectionConflict) 2 else 1
      downgrades.add("Signals conflict")
    }

    val isExtremelyNoisy = indicators.atrState.classification == VolatilityClassification.HIGH_VOLATILITY &&
      indicators.atrState.unusuallyLargeCandles ||
      priceActionContext.candleSequenceState.hasAlternatingCandles
    if (isExtremelyNoisy) {
      penaltySteps++
      downgrades.add("Market is noisy or highly volatile")
    }

    if (dataQuality != ExtractionQuality.HIGH) {
      penaltySteps++
      downgrades.add("Data quality is medium")
    }

    if (supportResistance.supportZones.isEmpty() && supportResistance.resistanceZones.isEmpty()) {
      penaltySteps++
      downgrades.add("Support/resistance context is unclear")
    }

    if (priceActionContext.marketRegime == MarketRegime.UNCERTAIN) {
      penaltySteps++
      downgrades.add("Market structure is unclear")
    }

    if (priceActionContext.breakoutState.classification == BreakoutClassification.BREAKOUT_ATTEMPT ||
      priceActionContext.breakoutState.classification == BreakoutClassification.FAKE_BREAKOUT
    ) {
      penaltySteps++
      downgrades.add("Breakout is unconfirmed")
    }

    if (priceActionContext.reversalState.classification == ReversalClassification.EARLY_REVERSAL_WARNING ||
      priceActionContext.reversalState.classification == ReversalClassification.UNCLEAR
    ) {
      penaltySteps++
      downgrades.add("Reversal evidence is incomplete")
    }

    val netScore = (alignedFactors - penaltySteps).coerceAtLeast(0)

    // A+ is ONLY possible when multiple independent forms of evidence align, zero conflicts exist, and dataQuality == HIGH
    val grade = when {
      dataQuality == ExtractionQuality.HIGH && !conflictAnalysis.hasConflict &&
        alignedFactors >= 5 && penaltySteps == 0 -> SetupQualityGrade.A_PLUS
      netScore >= 4 && !conflictAnalysis.priceActionConflict -> SetupQualityGrade.A
      netScore >= 2 -> SetupQualityGrade.B
      netScore >= 1 || dominantCount >= 2 -> SetupQualityGrade.C
      else -> SetupQualityGrade.NO_SETUP
    }

    val summary = when (grade) {
      SetupQualityGrade.A_PLUS -> "A+ Setup — Strong multi-factor alignment"
      SetupQualityGrade.A -> "A Setup — Solid structural alignment"
      SetupQualityGrade.B -> "B Setup — Moderate evidence with minor caution"
      SetupQualityGrade.C -> "C Setup — Weak or conflicting conditions"
      SetupQualityGrade.NO_SETUP -> "NO_SETUP — Waiting for clear setup"
    }

    return SetupQualityEvaluation(
      grade = grade,
      alignedFactorsCount = alignedFactors,
      downgradeReasons = downgrades,
      summaryReason = summary
    )
  }
}

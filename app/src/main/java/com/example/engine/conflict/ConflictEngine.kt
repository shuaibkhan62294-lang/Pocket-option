package com.example.engine.conflict

import com.example.engine.indicator.IndicatorSnapshot
import com.example.model.BreakoutClassification
import com.example.model.ConflictAnalysisResult
import com.example.model.ExtractionQuality
import com.example.model.InternalDirectionState
import com.example.model.PriceActionContext
import com.example.model.ReversalClassification
import com.example.model.SupportResistanceAnalysis
import com.example.model.ZoneStrength

/**
 * 11. Conflict Detection Engine (Part 4 Requirement 11).
 *
 * Detects:
 * - Indicator conflict (e.g. EMA/MACD vs RSI/Stochastic)
 * - Price-action conflict (e.g. bullish indicators vs bearish market structure or resistance rejection)
 * - Pattern conflict (e.g. bullish and bearish patterns appearing simultaneously or against structure)
 * - Trend vs reversal conflict
 * - Breakout vs rejection conflict
 * - Momentum conflict
 * - Low-quality data conflict
 */
interface ConflictEngine {
  fun evaluateConflicts(
    indicators: IndicatorSnapshot,
    supportResistance: SupportResistanceAnalysis,
    priceActionContext: PriceActionContext,
    bullishEvidenceCount: Int,
    bearishEvidenceCount: Int
  ): ConflictAnalysisResult
}

class DefaultConflictEngine : ConflictEngine {

  override fun evaluateConflicts(
    indicators: IndicatorSnapshot,
    supportResistance: SupportResistanceAnalysis,
    priceActionContext: PriceActionContext,
    bullishEvidenceCount: Int,
    bearishEvidenceCount: Int
  ): ConflictAnalysisResult {
    val descriptions = ArrayList<String>()

    // 1. Low-quality data conflict
    val lowDataQuality = priceActionContext.dataQuality == ExtractionQuality.LOW ||
      priceActionContext.dataQuality == ExtractionQuality.UNREADABLE
    if (lowDataQuality) {
      descriptions.add("Low chart data quality prevents reliable confirmation")
    }

    // 2. Indicator conflict (trend indicators vs oscillators disagree)
    val emaDir = indicators.emaSignal.direction
    val macdDir = indicators.macdSignal.direction
    val rsiDir = indicators.rsiSignal.directionWithPriceAction
    val indicatorConflict = (emaDir == InternalDirectionState.BULLISH && macdDir == InternalDirectionState.BEARISH) ||
      (emaDir == InternalDirectionState.BEARISH && macdDir == InternalDirectionState.BULLISH) ||
      (emaDir == InternalDirectionState.BULLISH && rsiDir == InternalDirectionState.BEARISH) ||
      (emaDir == InternalDirectionState.BEARISH && rsiDir == InternalDirectionState.BULLISH)
    if (indicatorConflict) {
      descriptions.add("Trend and momentum indicators are pointing in opposite directions")
    }

    // 3. Price-action conflict (indicators suggest UP/DOWN while price hits strong opposing level or structure)
    val strongRes = supportResistance.nearestResistance?.strength == ZoneStrength.STRONG ||
      supportResistance.nearestResistance?.strength == ZoneStrength.VERY_STRONG
    val strongSup = supportResistance.nearestSupport?.strength == ZoneStrength.STRONG ||
      supportResistance.nearestSupport?.strength == ZoneStrength.VERY_STRONG

    val bullVsResistance = bullishEvidenceCount >= 2 &&
      (supportResistance.resistanceRejection || (supportResistance.isNearResistance && strongRes))
    val bearVsSupport = bearishEvidenceCount >= 2 &&
      (supportResistance.supportRejection || (supportResistance.isNearSupport && strongSup))
    val indVsStructure = (emaDir == InternalDirectionState.BULLISH &&
      priceActionContext.trendStructure == InternalDirectionState.BEARISH) ||
      (emaDir == InternalDirectionState.BEARISH &&
        priceActionContext.trendStructure == InternalDirectionState.BULLISH)

    val priceActionConflict = bullVsResistance || bearVsSupport || indVsStructure
    if (bullVsResistance) {
      descriptions.add("Bullish indicators conflict with strong overhead resistance")
    }
    if (bearVsSupport) {
      descriptions.add("Bearish indicators conflict with strong underlying support")
    }
    if (indVsStructure) {
      descriptions.add("Technical indicators conflict with current swing price structure")
    }

    // 4. Pattern conflict
    val hasBullPattern = priceActionContext.candlePatternState.any {
      it.direction == InternalDirectionState.BULLISH
    }
    val hasBearPattern = priceActionContext.candlePatternState.any {
      it.direction == InternalDirectionState.BEARISH
    }
    val hasTrendConflictingPattern = priceActionContext.candlePatternState.any {
      it.conflictsWithCurrentTrend && !it.occursNearSupportOrResistance
    }
    val patternConflict = (hasBullPattern && hasBearPattern) || hasTrendConflictingPattern
    if (patternConflict) {
      descriptions.add("Candlestick pattern conflicts with broader market context")
    }

    // 5. Trend vs reversal conflict
    val revClass = priceActionContext.reversalState.classification
    val trendVsReversal = (revClass == ReversalClassification.EARLY_REVERSAL_WARNING ||
      revClass == ReversalClassification.MODERATE_REVERSAL_EVIDENCE) &&
      (priceActionContext.trendStructure == InternalDirectionState.BULLISH ||
        priceActionContext.trendStructure == InternalDirectionState.BEARISH)
    if (trendVsReversal) {
      descriptions.add("Active trend faces early reversal warning signs")
    }

    // 6. Breakout vs rejection conflict
    val boClass = priceActionContext.breakoutState.classification
    val breakoutVsRejection = boClass == BreakoutClassification.FAKE_BREAKOUT ||
      (boClass == BreakoutClassification.BREAKOUT_ATTEMPT &&
        (supportResistance.resistanceRejection || supportResistance.supportRejection))
    if (breakoutVsRejection) {
      descriptions.add("Breakout attempt is encountering immediate wick rejection")
    }

    // 7. Momentum conflict
    val mom = priceActionContext.momentumState
    val seq = priceActionContext.candleSequenceState
    val momentumConflict = mom.isMomentumExhausted || seq.hasExhaustion ||
      (bullishEvidenceCount >= 2 && mom.direction == InternalDirectionState.BEARISH) ||
      (bearishEvidenceCount >= 2 && mom.direction == InternalDirectionState.BULLISH)
    if (momentumConflict) {
      descriptions.add(
        seq.exhaustionWarningDescription ?: "Candle momentum is weakening or exhausted"
      )
    }

    val hasAnyConflict = lowDataQuality || indicatorConflict || priceActionConflict ||
      patternConflict || trendVsReversal || breakoutVsRejection || momentumConflict

    val summary = when {
      lowDataQuality -> "LOW_DATA_QUALITY_CONFLICT"
      priceActionConflict || breakoutVsRejection -> "CONFLICT — WAIT / LOW QUALITY SETUP"
      hasAnyConflict -> "SIGNALS_CONFLICTING"
      else -> "ALIGNED"
    }

    return ConflictAnalysisResult(
      hasConflict = hasAnyConflict,
      indicatorConflict = indicatorConflict,
      priceActionConflict = priceActionConflict,
      patternConflict = patternConflict,
      trendVsReversalConflict = trendVsReversal,
      breakoutVsRejectionConflict = breakoutVsRejection,
      momentumConflict = momentumConflict,
      lowQualityDataConflict = lowDataQuality,
      conflictDescriptions = descriptions.distinct(),
      summaryStatus = summary
    )
  }
}

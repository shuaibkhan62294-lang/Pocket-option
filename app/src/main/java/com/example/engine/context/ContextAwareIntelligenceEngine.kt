package com.example.engine.context

import com.example.model.ContextualPatternEvaluation
import com.example.model.DetectedCandlestickPattern
import com.example.model.InternalDirectionState
import com.example.model.InternalStrengthLabel
import com.example.model.LiquiditySweepClassification
import com.example.model.MarketStateSnapshot
import com.example.model.ZoneStrength

/**
 * 5. Context-Aware Intelligence Engine (Part 5 Requirement 5).
 *
 * Evaluates how market context changes the meaning and strength of candlestick patterns:
 * - Example 1: Bullish Engulfing at strong support + bearish exhaustion = stronger bullish evidence.
 * - Example 2: Bullish Engulfing directly below strong resistance + weak momentum = weaker evidence.
 * - Example 3: Bearish Engulfing / Shooting Star at strong resistance + bullish exhaustion = stronger bearish evidence.
 * - Example 4: Bearish Engulfing directly above strong support + weak momentum = weaker evidence.
 */
interface ContextAwareIntelligenceEngine {
  fun evaluatePatternsInContext(snapshot: MarketStateSnapshot): List<ContextualPatternEvaluation>

  fun evaluateSinglePatternInContext(
    pattern: DetectedCandlestickPattern,
    snapshot: MarketStateSnapshot
  ): ContextualPatternEvaluation
}

class DefaultContextAwareIntelligenceEngine : ContextAwareIntelligenceEngine {

  override fun evaluatePatternsInContext(
    snapshot: MarketStateSnapshot
  ): List<ContextualPatternEvaluation> {
    if (snapshot.candlestickPatterns.isEmpty()) return emptyList()
    return snapshot.candlestickPatterns.map { pattern ->
      evaluateSinglePatternInContext(pattern, snapshot)
    }
  }

  override fun evaluateSinglePatternInContext(
    pattern: DetectedCandlestickPattern,
    snapshot: MarketStateSnapshot
  ): ContextualPatternEvaluation {
    val sr = snapshot.supportResistance
    val mom = snapshot.momentum
    val seq = snapshot.candleSequenceFeatures
    val liq = snapshot.liquiditySweepState

    val strongSupport = (sr.isNearSupport || sr.supportRejection) &&
      (sr.nearestSupport?.strength == ZoneStrength.STRONG ||
        sr.nearestSupport?.strength == ZoneStrength.VERY_STRONG ||
        sr.supportRejection)

    val strongResistance = (sr.isNearResistance || sr.resistanceRejection) &&
      (sr.nearestResistance?.strength == ZoneStrength.STRONG ||
        sr.nearestResistance?.strength == ZoneStrength.VERY_STRONG ||
        sr.resistanceRejection)

    val bearishExhaustion = (seq.consecutiveBearishCandles >= 3 && (seq.hasExhaustion || mom.isMomentumExhausted || mom.isDecelerating)) ||
      snapshot.indicatorStates.rsiSignal.isOversold ||
      (liq.sweepBelowPreviousLow && liq.classification == LiquiditySweepClassification.CONFIRMED_REJECTION_AFTER_SWEEP)

    val bullishExhaustion = (seq.consecutiveBullishCandles >= 3 && (seq.hasExhaustion || mom.isMomentumExhausted || mom.isDecelerating)) ||
      snapshot.indicatorStates.rsiSignal.isOverbought ||
      (liq.sweepAbovePreviousHigh && liq.classification == LiquiditySweepClassification.CONFIRMED_REJECTION_AFTER_SWEEP)

    val weakBullishMomentum = mom.direction != InternalDirectionState.BULLISH &&
      !mom.strongBullishMomentum &&
      (mom.weakeningMomentum || mom.isDecelerating || mom.direction == InternalDirectionState.NEUTRAL)

    val weakBearishMomentum = mom.direction != InternalDirectionState.BEARISH &&
      !mom.strongBearishMomentum &&
      (mom.weakeningMomentum || mom.isDecelerating || mom.direction == InternalDirectionState.NEUTRAL)

    return when (pattern.direction) {
      InternalDirectionState.BULLISH -> {
        val atFavorableSupportWithExhaustion = (strongSupport || sr.isNearSupport) && bearishExhaustion
        val atFavorableSupport = strongSupport || sr.supportRejection
        val blockedByOverheadResistance = (strongResistance || sr.isNearResistance) &&
          (weakBullishMomentum || sr.resistanceRejection)
        val midRangeAgainstDowntrend = pattern.conflictsWithCurrentTrend && !sr.isNearSupport && !sr.supportRejection

        when {
          atFavorableSupportWithExhaustion -> ContextualPatternEvaluation(
            patternName = pattern.patternName,
            direction = pattern.direction,
            rawStrength = pattern.strength,
            adjustedStrength = InternalStrengthLabel.STRONG,
            contextWeightMultiplier = 1.45,
            isBoostedByContext = true,
            isWeakenedByContext = false,
            contextReasoning = "${pattern.patternName} at strong support + bearish exhaustion (stronger bullish evidence)"
          )
          blockedByOverheadResistance -> ContextualPatternEvaluation(
            patternName = pattern.patternName,
            direction = pattern.direction,
            rawStrength = pattern.strength,
            adjustedStrength = InternalStrengthLabel.WEAK,
            contextWeightMultiplier = 0.40,
            isBoostedByContext = false,
            isWeakenedByContext = true,
            contextReasoning = "${pattern.patternName} directly below strong resistance + weak momentum (weaker evidence)"
          )
          atFavorableSupport -> ContextualPatternEvaluation(
            patternName = pattern.patternName,
            direction = pattern.direction,
            rawStrength = pattern.strength,
            adjustedStrength = InternalStrengthLabel.STRONG,
            contextWeightMultiplier = 1.25,
            isBoostedByContext = true,
            isWeakenedByContext = false,
            contextReasoning = "${pattern.patternName} confirmed at support zone"
          )
          midRangeAgainstDowntrend -> ContextualPatternEvaluation(
            patternName = pattern.patternName,
            direction = pattern.direction,
            rawStrength = pattern.strength,
            adjustedStrength = InternalStrengthLabel.WEAK,
            contextWeightMultiplier = 0.55,
            isBoostedByContext = false,
            isWeakenedByContext = true,
            contextReasoning = "${pattern.patternName} in mid-range against active downtrend without support"
          )
          else -> ContextualPatternEvaluation(
            patternName = pattern.patternName,
            direction = pattern.direction,
            rawStrength = pattern.strength,
            adjustedStrength = pattern.strength,
            contextWeightMultiplier = 1.0,
            isBoostedByContext = false,
            isWeakenedByContext = false,
            contextReasoning = "${pattern.patternName} in neutral/trend context (${pattern.location})"
          )
        }
      }

      InternalDirectionState.BEARISH -> {
        val atFavorableResistanceWithExhaustion = (strongResistance || sr.isNearResistance) && bullishExhaustion
        val atFavorableResistance = strongResistance || sr.resistanceRejection
        val blockedByUnderlyingSupport = (strongSupport || sr.isNearSupport) &&
          (weakBearishMomentum || sr.supportRejection)
        val midRangeAgainstUptrend = pattern.conflictsWithCurrentTrend && !sr.isNearResistance && !sr.resistanceRejection

        when {
          atFavorableResistanceWithExhaustion -> ContextualPatternEvaluation(
            patternName = pattern.patternName,
            direction = pattern.direction,
            rawStrength = pattern.strength,
            adjustedStrength = InternalStrengthLabel.STRONG,
            contextWeightMultiplier = 1.45,
            isBoostedByContext = true,
            isWeakenedByContext = false,
            contextReasoning = "${pattern.patternName} at strong resistance + bullish exhaustion (stronger bearish evidence)"
          )
          blockedByUnderlyingSupport -> ContextualPatternEvaluation(
            patternName = pattern.patternName,
            direction = pattern.direction,
            rawStrength = pattern.strength,
            adjustedStrength = InternalStrengthLabel.WEAK,
            contextWeightMultiplier = 0.40,
            isBoostedByContext = false,
            isWeakenedByContext = true,
            contextReasoning = "${pattern.patternName} directly above strong support + weak momentum (weaker evidence)"
          )
          atFavorableResistance -> ContextualPatternEvaluation(
            patternName = pattern.patternName,
            direction = pattern.direction,
            rawStrength = pattern.strength,
            adjustedStrength = InternalStrengthLabel.STRONG,
            contextWeightMultiplier = 1.25,
            isBoostedByContext = true,
            isWeakenedByContext = false,
            contextReasoning = "${pattern.patternName} confirmed at resistance zone"
          )
          midRangeAgainstUptrend -> ContextualPatternEvaluation(
            patternName = pattern.patternName,
            direction = pattern.direction,
            rawStrength = pattern.strength,
            adjustedStrength = InternalStrengthLabel.WEAK,
            contextWeightMultiplier = 0.55,
            isBoostedByContext = false,
            isWeakenedByContext = true,
            contextReasoning = "${pattern.patternName} in mid-range against active uptrend without resistance"
          )
          else -> ContextualPatternEvaluation(
            patternName = pattern.patternName,
            direction = pattern.direction,
            rawStrength = pattern.strength,
            adjustedStrength = pattern.strength,
            contextWeightMultiplier = 1.0,
            isBoostedByContext = false,
            isWeakenedByContext = false,
            contextReasoning = "${pattern.patternName} in neutral/trend context (${pattern.location})"
          )
        }
      }

      else -> {
        val atBoundary = strongSupport || strongResistance
        ContextualPatternEvaluation(
          patternName = pattern.patternName,
          direction = pattern.direction,
          rawStrength = pattern.strength,
          adjustedStrength = if (atBoundary) InternalStrengthLabel.MODERATE else InternalStrengthLabel.WEAK,
          contextWeightMultiplier = if (atBoundary) 1.0 else 0.65,
          isBoostedByContext = atBoundary,
          isWeakenedByContext = !atBoundary,
          contextReasoning = "${pattern.patternName} indecision candle at ${pattern.location}"
        )
      }
    }
  }
}

package com.example.engine.regime

import com.example.model.BreakoutClassification
import com.example.model.InternalDirectionState
import com.example.model.MarketRegime
import com.example.model.MarketStateSnapshot
import com.example.model.ReversalClassification

/**
 * Regime-specific weighting multipliers and evaluation guidance (Part 5 Requirement 7).
 */
data class RegimeReasoningProfile(
  val regime: MarketRegime,
  val trendIndicatorMultiplier: Double,
  val oscillatorMeanReversionMultiplier: Double,
  val supportResistanceBoundaryMultiplier: Double,
  val breakoutStructureMultiplier: Double,
  val reversalEvidenceMultiplier: Double,
  val momentumMultiplier: Double,
  val minimumNetWeightDeltaForCandidate: Double,
  val allowCounterTrendCandidate: Boolean,
  val reasoningSummary: String
)

/**
 * 7. Regime-Specific Reasoning Engine (Part 5 Requirement 7).
 *
 * Applies distinct analytical rules and category weights for each of the 9 market regimes:
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
interface RegimeReasoningEngine {
  fun evaluateRegimeProfile(snapshot: MarketStateSnapshot): RegimeReasoningProfile

  fun isCandidatePermittedByRegime(
    direction: InternalDirectionState,
    snapshot: MarketStateSnapshot,
    profile: RegimeReasoningProfile
  ): Boolean
}

class DefaultRegimeReasoningEngine : RegimeReasoningEngine {

  override fun evaluateRegimeProfile(snapshot: MarketStateSnapshot): RegimeReasoningProfile {
    return when (snapshot.marketRegime) {
      MarketRegime.TRENDING_UP -> RegimeReasoningProfile(
        regime = MarketRegime.TRENDING_UP,
        trendIndicatorMultiplier = 1.35,
        oscillatorMeanReversionMultiplier = 0.65,
        supportResistanceBoundaryMultiplier = 1.15,
        breakoutStructureMultiplier = 1.20,
        reversalEvidenceMultiplier = 0.70,
        momentumMultiplier = 1.25,
        minimumNetWeightDeltaForCandidate = 2.2,
        allowCounterTrendCandidate = false,
        reasoningSummary = "TRENDING_UP regime: prioritizes higher-low pullbacks, bullish BOS, and trend continuation"
      )

      MarketRegime.TRENDING_DOWN -> RegimeReasoningProfile(
        regime = MarketRegime.TRENDING_DOWN,
        trendIndicatorMultiplier = 1.35,
        oscillatorMeanReversionMultiplier = 0.65,
        supportResistanceBoundaryMultiplier = 1.15,
        breakoutStructureMultiplier = 1.20,
        reversalEvidenceMultiplier = 0.70,
        momentumMultiplier = 1.25,
        minimumNetWeightDeltaForCandidate = 2.2,
        allowCounterTrendCandidate = false,
        reasoningSummary = "TRENDING_DOWN regime: prioritizes lower-high pullbacks, bearish BOS, and downward continuation"
      )

      MarketRegime.SIDEWAYS -> RegimeReasoningProfile(
        regime = MarketRegime.SIDEWAYS,
        trendIndicatorMultiplier = 0.60,
        oscillatorMeanReversionMultiplier = 1.30,
        supportResistanceBoundaryMultiplier = 1.45,
        breakoutStructureMultiplier = 0.85,
        reversalEvidenceMultiplier = 1.25,
        momentumMultiplier = 0.90,
        minimumNetWeightDeltaForCandidate = 2.4,
        allowCounterTrendCandidate = true,
        reasoningSummary = "SIDEWAYS regime: prioritizes support/resistance boundary rejections and range extremes"
      )

      MarketRegime.BREAKOUT -> RegimeReasoningProfile(
        regime = MarketRegime.BREAKOUT,
        trendIndicatorMultiplier = 1.20,
        oscillatorMeanReversionMultiplier = 0.50,
        supportResistanceBoundaryMultiplier = 1.25,
        breakoutStructureMultiplier = 1.50,
        reversalEvidenceMultiplier = 0.60,
        momentumMultiplier = 1.35,
        minimumNetWeightDeltaForCandidate = 2.5,
        allowCounterTrendCandidate = false,
        reasoningSummary = "BREAKOUT regime: requires confirmed body close above resistance with range expansion"
      )

      MarketRegime.BREAKDOWN -> RegimeReasoningProfile(
        regime = MarketRegime.BREAKDOWN,
        trendIndicatorMultiplier = 1.20,
        oscillatorMeanReversionMultiplier = 0.50,
        supportResistanceBoundaryMultiplier = 1.25,
        breakoutStructureMultiplier = 1.50,
        reversalEvidenceMultiplier = 0.60,
        momentumMultiplier = 1.35,
        minimumNetWeightDeltaForCandidate = 2.5,
        allowCounterTrendCandidate = false,
        reasoningSummary = "BREAKDOWN regime: requires confirmed body close below support with range expansion"
      )

      MarketRegime.HIGH_VOLATILITY -> RegimeReasoningProfile(
        regime = MarketRegime.HIGH_VOLATILITY,
        trendIndicatorMultiplier = 0.90,
        oscillatorMeanReversionMultiplier = 0.80,
        supportResistanceBoundaryMultiplier = 1.30,
        breakoutStructureMultiplier = 1.00,
        reversalEvidenceMultiplier = 1.05,
        momentumMultiplier = 0.95,
        minimumNetWeightDeltaForCandidate = 3.2,
        allowCounterTrendCandidate = false,
        reasoningSummary = "HIGH_VOLATILITY regime: enforces stricter confirmation threshold due to expanded candle ranges"
      )

      MarketRegime.LOW_VOLATILITY -> RegimeReasoningProfile(
        regime = MarketRegime.LOW_VOLATILITY,
        trendIndicatorMultiplier = 0.75,
        oscillatorMeanReversionMultiplier = 1.05,
        supportResistanceBoundaryMultiplier = 1.20,
        breakoutStructureMultiplier = 1.15,
        reversalEvidenceMultiplier = 1.00,
        momentumMultiplier = 0.80,
        minimumNetWeightDeltaForCandidate = 2.8,
        allowCounterTrendCandidate = true,
        reasoningSummary = "LOW_VOLATILITY regime: compression state waiting for clean expansion or level test"
      )

      MarketRegime.REVERSAL_RISK -> RegimeReasoningProfile(
        regime = MarketRegime.REVERSAL_RISK,
        trendIndicatorMultiplier = 0.65,
        oscillatorMeanReversionMultiplier = 1.25,
        supportResistanceBoundaryMultiplier = 1.40,
        breakoutStructureMultiplier = 0.80,
        reversalEvidenceMultiplier = 1.50,
        momentumMultiplier = 1.10,
        minimumNetWeightDeltaForCandidate = 2.6,
        allowCounterTrendCandidate = true,
        reasoningSummary = "REVERSAL_RISK regime: prioritizes multi-factor reversal confirmation (CHoCH, sweep, exhaustion at S/R)"
      )

      MarketRegime.UNCERTAIN -> RegimeReasoningProfile(
        regime = MarketRegime.UNCERTAIN,
        trendIndicatorMultiplier = 0.70,
        oscillatorMeanReversionMultiplier = 0.70,
        supportResistanceBoundaryMultiplier = 0.90,
        breakoutStructureMultiplier = 0.75,
        reversalEvidenceMultiplier = 0.75,
        momentumMultiplier = 0.75,
        minimumNetWeightDeltaForCandidate = 4.0,
        allowCounterTrendCandidate = false,
        reasoningSummary = "UNCERTAIN regime: mixed market structure — defaults to WAIT until clarity emerges"
      )
    }
  }

  override fun isCandidatePermittedByRegime(
    direction: InternalDirectionState,
    snapshot: MarketStateSnapshot,
    profile: RegimeReasoningProfile
  ): Boolean {
    return when (profile.regime) {
      MarketRegime.UNCERTAIN -> false

      MarketRegime.TRENDING_UP -> {
        if (direction == InternalDirectionState.BULLISH) {
          !snapshot.supportResistance.resistanceRejection
        } else {
          // Only allow bearish candidate against an uptrend if strong multi-factor reversal is confirmed
          snapshot.reversalState.classification == ReversalClassification.STRONG_REVERSAL_EVIDENCE
        }
      }

      MarketRegime.TRENDING_DOWN -> {
        if (direction == InternalDirectionState.BEARISH) {
          !snapshot.supportResistance.supportRejection
        } else {
          snapshot.reversalState.classification == ReversalClassification.STRONG_REVERSAL_EVIDENCE
        }
      }

      MarketRegime.SIDEWAYS -> {
        // In sideways range, require proximity or reaction at support/resistance rather than mid-range guessing
        if (direction == InternalDirectionState.BULLISH) {
          snapshot.supportResistance.isNearSupport || snapshot.supportResistance.supportRejection
        } else if (direction == InternalDirectionState.BEARISH) {
          snapshot.supportResistance.isNearResistance || snapshot.supportResistance.resistanceRejection
        } else {
          false
        }
      }

      MarketRegime.BREAKOUT -> {
        direction == InternalDirectionState.BULLISH &&
          snapshot.breakoutRetestState.classification == BreakoutClassification.BREAKOUT_CONFIRMED
      }

      MarketRegime.BREAKDOWN -> {
        direction == InternalDirectionState.BEARISH &&
          snapshot.breakoutRetestState.classification == BreakoutClassification.BREAKOUT_CONFIRMED
      }

      MarketRegime.REVERSAL_RISK -> {
        val rev = snapshot.reversalState
        (rev.classification == ReversalClassification.STRONG_REVERSAL_EVIDENCE ||
          rev.classification == ReversalClassification.MODERATE_REVERSAL_EVIDENCE) &&
          rev.reversalDirection == direction
      }

      MarketRegime.HIGH_VOLATILITY -> {
        !snapshot.volatility.unusuallyLargeCandles
      }

      MarketRegime.LOW_VOLATILITY -> {
        snapshot.supportResistance.supportRejection ||
          snapshot.supportResistance.resistanceRejection ||
          snapshot.breakoutRetestState.classification == BreakoutClassification.BREAKOUT_CONFIRMED
      }
    }
  }
}

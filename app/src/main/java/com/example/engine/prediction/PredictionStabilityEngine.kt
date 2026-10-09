package com.example.engine.prediction

import com.example.model.BreakoutClassification
import com.example.model.BrainAssessment
import com.example.model.CurrentCandleMonitoringState
import com.example.model.ExtractionQuality
import com.example.model.InternalDirectionState
import com.example.model.PredictionDirection
import com.example.model.PredictionInvalidationReason
import com.example.model.PredictionStabilitySnapshot
import com.example.model.PriceDirection
import kotlin.math.abs

/**
 * Configurable parameters for prediction stability & anti-flipping hysteresis (Part 6 Requirement 6).
 */
data class PredictionStabilityConfig(
  val minEvidenceDeltaToFlipDirectly: Double = 3.6,
  val minFlipCooldownMs: Long = 2_500L
)

/**
 * 6 & 8. Prediction Stability & Invalidation Engine (Part 6 Requirements 6 & 8).
 *
 * - Prevents rapid UP -> DOWN -> UP -> DOWN flipping caused by minor price movements.
 * - Detects invalidation conditions (market structure change, strong opposite momentum,
 *   failed breakout, support/resistance rejection change, data quality drop, or unreliable
 *   chart detection) and forces WAIT rather than keeping an invalid prediction active.
 */
interface PredictionStabilityEngine {
  fun evaluateInvalidation(
    activeDirection: PredictionDirection,
    assessment: BrainAssessment,
    currentCandleMonitor: CurrentCandleMonitoringState,
    isChartRegionValid: Boolean
  ): List<PredictionInvalidationReason>

  fun applyStabilityControl(
    candidateDirection: PredictionDirection,
    netEvidenceScore: Double,
    hasStrongAgreement: Boolean,
    nowMillis: Long = System.currentTimeMillis()
  ): PredictionStabilitySnapshot

  fun reset()
}

class DefaultPredictionStabilityEngine(
  private val config: PredictionStabilityConfig = PredictionStabilityConfig()
) : PredictionStabilityEngine {

  private var committedDirection: PredictionDirection = PredictionDirection.NONE
  private var lastDirectionChangeTimestamp: Long = 0L
  private var directionChangesCount: Int = 0
  private var consecutiveConsistentCount: Int = 0
  private var previousNetEvidenceScore: Double = 0.0

  @Synchronized
  override fun evaluateInvalidation(
    activeDirection: PredictionDirection,
    assessment: BrainAssessment,
    currentCandleMonitor: CurrentCandleMonitoringState,
    isChartRegionValid: Boolean
  ): List<PredictionInvalidationReason> {
    if (activeDirection == PredictionDirection.NONE) {
      return emptyList()
    }

    val reasons = ArrayList<PredictionInvalidationReason>()
    val snap = assessment.marketState

    // 1. Data quality drops
    if (snap.dataQuality == ExtractionQuality.LOW || snap.dataQuality == ExtractionQuality.UNREADABLE) {
      reasons.add(PredictionInvalidationReason.DATA_QUALITY_DROPPED)
    }

    // 2. Chart detection becomes unreliable
    if (!snap.isScreenDetectionStable || !isChartRegionValid) {
      reasons.add(PredictionInvalidationReason.CHART_DETECTION_UNRELIABLE)
    }

    // 3. Breakout fails (FAKE_BREAKOUT or failed retest)
    if (snap.breakoutRetestState.classification == BreakoutClassification.FAKE_BREAKOUT || snap.isFailedRetest) {
      reasons.add(PredictionInvalidationReason.BREAKOUT_FAILED)
    }

    if (activeDirection == PredictionDirection.UP) {
      // 4. Market structure changes against UP
      if (snap.priceAction.isDowntrend ||
        snap.chochState == com.example.model.ChochClassification.BEARISH_CHOCH ||
        snap.bosState == com.example.model.BosClassification.BEARISH_BOS
      ) {
        reasons.add(PredictionInvalidationReason.MARKET_STRUCTURE_CHANGED)
      }

      // 5. Strong opposite momentum appears against UP
      val strongBearishCandleFlip = currentCandleMonitor.direction == PriceDirection.DOWN &&
        currentCandleMonitor.bodyDevelopmentRatio >= 1.20 &&
        currentCandleMonitor.isAccelerating
      if (snap.momentum.strongBearishMomentum || snap.momentum.suddenReversal || strongBearishCandleFlip) {
        reasons.add(PredictionInvalidationReason.STRONG_OPPOSITE_MOMENTUM)
      }

      // 6. Support/resistance rejection changes against UP (overhead resistance rejection)
      if (snap.supportResistance.resistanceRejection ||
        (currentCandleMonitor.hasStrongOpposingWick && currentCandleMonitor.upperWickRatio >= 0.50)
      ) {
        reasons.add(PredictionInvalidationReason.SUPPORT_RESISTANCE_REJECTION_CHANGED)
      }
    } else if (activeDirection == PredictionDirection.DOWN) {
      // 4. Market structure changes against DOWN
      if (snap.priceAction.isUptrend ||
        snap.chochState == com.example.model.ChochClassification.BULLISH_CHOCH ||
        snap.bosState == com.example.model.BosClassification.BULLISH_BOS
      ) {
        reasons.add(PredictionInvalidationReason.MARKET_STRUCTURE_CHANGED)
      }

      // 5. Strong opposite momentum appears against DOWN
      val strongBullishCandleFlip = currentCandleMonitor.direction == PriceDirection.UP &&
        currentCandleMonitor.bodyDevelopmentRatio >= 1.20 &&
        currentCandleMonitor.isAccelerating
      if (snap.momentum.strongBullishMomentum || snap.momentum.suddenReversal || strongBullishCandleFlip) {
        reasons.add(PredictionInvalidationReason.STRONG_OPPOSITE_MOMENTUM)
      }

      // 6. Support/resistance rejection changes against DOWN (underlying support rejection)
      if (snap.supportResistance.supportRejection ||
        (currentCandleMonitor.hasStrongOpposingWick && currentCandleMonitor.lowerWickRatio >= 0.50)
      ) {
        reasons.add(PredictionInvalidationReason.SUPPORT_RESISTANCE_REJECTION_CHANGED)
      }
    }

    return reasons.distinct()
  }

  @Synchronized
  override fun applyStabilityControl(
    candidateDirection: PredictionDirection,
    netEvidenceScore: Double,
    hasStrongAgreement: Boolean,
    nowMillis: Long
  ): PredictionStabilitySnapshot {
    val previousDir = committedDirection
    val evidenceDelta = netEvidenceScore - previousNetEvidenceScore
    previousNetEvidenceScore = netEvidenceScore

    if (lastDirectionChangeTimestamp == 0L) {
      lastDirectionChangeTimestamp = nowMillis
    }

    val elapsedSinceChange = (nowMillis - lastDirectionChangeTimestamp).coerceAtLeast(0L)

    // Check if attempting a direct opposite flip (UP -> DOWN or DOWN -> UP)
    val isOppositeFlipAttempt = (previousDir == PredictionDirection.UP && candidateDirection == PredictionDirection.DOWN) ||
      (previousDir == PredictionDirection.DOWN && candidateDirection == PredictionDirection.UP)

    var rapidFlipPrevented = false
    val resolvedDirection: PredictionDirection = if (isOppositeFlipAttempt) {
      val hasDecisiveEvidence = hasStrongAgreement &&
        abs(netEvidenceScore) >= config.minEvidenceDeltaToFlipDirectly &&
        elapsedSinceChange >= config.minFlipCooldownMs

      if (hasDecisiveEvidence) {
        candidateDirection
      } else {
        // Prevent rapid UP <-> DOWN flip; step down to NONE (WAIT) first
        rapidFlipPrevented = true
        PredictionDirection.NONE
      }
    } else {
      candidateDirection
    }

    if (resolvedDirection != previousDir) {
      committedDirection = resolvedDirection
      lastDirectionChangeTimestamp = nowMillis
      if (previousDir != PredictionDirection.NONE && resolvedDirection != PredictionDirection.NONE) {
        directionChangesCount++
      } else if (resolvedDirection != PredictionDirection.NONE) {
        directionChangesCount++
      }
      consecutiveConsistentCount = 1
    } else {
      consecutiveConsistentCount++
    }

    val currentStabilityDuration = (nowMillis - lastDirectionChangeTimestamp).coerceAtLeast(0L)

    return PredictionStabilitySnapshot(
      previousPrediction = previousDir,
      currentPrediction = committedDirection,
      directionChangesCount = directionChangesCount,
      stabilityDurationMs = currentStabilityDuration,
      consecutiveConsistentEvaluations = consecutiveConsistentCount,
      evidenceChangeDelta = evidenceDelta,
      wasRapidFlipPrevented = rapidFlipPrevented
    )
  }

  @Synchronized
  override fun reset() {
    committedDirection = PredictionDirection.NONE
    lastDirectionChangeTimestamp = 0L
    directionChangesCount = 0
    consecutiveConsistentCount = 0
    previousNetEvidenceScore = 0.0
  }
}

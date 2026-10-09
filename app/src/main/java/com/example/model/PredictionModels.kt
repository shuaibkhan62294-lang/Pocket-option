package com.example.model

/**
 * Part 6 Requirement 3: Direction of the next-candle forecast.
 */
enum class PredictionDirection {
  UP,
  DOWN,
  NONE
}

/**
 * Part 6 Requirement 3: Lifecycle state of the next-candle forecast.
 */
enum class PredictionLifecycleState {
  ACTIVE,
  WAIT,
  INVALIDATED,
  EXPIRED
}

/**
 * Part 6 Requirement 4: Agreement classification across multi-dimensional evidence.
 * Never uses simple majority voting.
 */
enum class PredictionAgreementLevel {
  STRONG_AGREEMENT,
  MODERATE_AGREEMENT,
  CONFLICTING_EVIDENCE,
  INSUFFICIENT_EVIDENCE
}

/**
 * Part 6 Requirement 8: Reasons why an active prediction was invalidated and forced to WAIT.
 */
enum class PredictionInvalidationReason {
  MARKET_STRUCTURE_CHANGED,
  STRONG_OPPOSITE_MOMENTUM,
  BREAKOUT_FAILED,
  SUPPORT_RESISTANCE_REJECTION_CHANGED,
  DATA_QUALITY_DROPPED,
  CHART_DETECTION_UNRELIABLE
}

/**
 * Part 6 Requirement 10: Outcome classification for a finalized prediction record.
 */
enum class PredictionOutcomeStatus {
  CORRECT,
  INCORRECT,
  NO_VALID_RESULT
}

/**
 * 2. Current Candle Monitoring State (Part 6 Requirement 2 & 7).
 *
 * Continuously monitors the current forming candle:
 * - Current price position
 * - Body development
 * - Wick development
 * - Momentum
 * - Acceleration & deceleration
 * - Distance from support/resistance
 * - Trend structure alignment
 * - Pattern formation
 */
data class CurrentCandleMonitoringState(
  val currentPrice: Double = 0.0,
  val pricePositionInRange: Double = 0.5, // 0.0 = at low, 1.0 = at high
  val bodyDevelopmentRatio: Double = 0.0, // current body / average completed body
  val bodyToRangeRatio: Double = 0.0,
  val upperWickRatio: Double = 0.0,
  val lowerWickRatio: Double = 0.0,
  val direction: PriceDirection = PriceDirection.FLAT,
  val intraCandleMomentum: Double = 0.0,
  val isAccelerating: Boolean = false,
  val isDecelerating: Boolean = false,
  val hasStrongOpposingWick: Boolean = false,
  val distanceToSupportNormalized: Double? = null,
  val distanceToResistanceNormalized: Double? = null,
  val isAlignedWithTrendStructure: Boolean = false,
  val developingPatternHint: String? = null,
  val isBehaviorStable: Boolean = true,
  val isEarlyForecastReady: Boolean = false
)

/**
 * 6. Prediction Stability Tracking Snapshot (Part 6 Requirement 6).
 *
 * Prevents rapid UP -> DOWN -> UP -> DOWN flipping due to tiny price movements.
 */
data class PredictionStabilitySnapshot(
  val previousPrediction: PredictionDirection = PredictionDirection.NONE,
  val currentPrediction: PredictionDirection = PredictionDirection.NONE,
  val directionChangesCount: Int = 0,
  val stabilityDurationMs: Long = 0L,
  val consecutiveConsistentEvaluations: Int = 0,
  val evidenceChangeDelta: Double = 0.0,
  val wasRapidFlipPrevented: Boolean = false
)

/**
 * 3. Next-Candle Forecast (`NextCandlePrediction`) (Part 6 Requirement 3).
 *
 * Complete output of the Part 6 Final Smart Prediction Engine.
 * Note: Never claims guaranteed predictions or certainty.
 */
data class NextCandlePrediction(
  val direction: PredictionDirection = PredictionDirection.NONE,
  val state: PredictionLifecycleState = PredictionLifecycleState.WAIT,
  val evidence: List<String> = emptyList(),
  val agreementLevel: PredictionAgreementLevel = PredictionAgreementLevel.INSUFFICIENT_EVIDENCE,
  val setupQuality: SetupQualityGrade = SetupQualityGrade.NO_SETUP,
  val dataQuality: ExtractionQuality = ExtractionQuality.UNREADABLE,
  val stability: MarketStabilityState = MarketStabilityState.UNSTABLE,
  val stabilitySnapshot: PredictionStabilitySnapshot = PredictionStabilitySnapshot(),
  val currentCandleMonitoring: CurrentCandleMonitoringState = CurrentCandleMonitoringState(),
  val timestamp: Long = 0L,
  val predictionHorizon: String = "NEXT_1_CANDLE",
  val reasons: List<String> = listOf("Waiting for chart data..."),
  val invalidationReasons: List<PredictionInvalidationReason> = emptyList(),
  val isEarlyWarning: Boolean = false
) {
  val uiSignal: NextCandleSignal
    get() = when {
      state != PredictionLifecycleState.ACTIVE -> NextCandleSignal.WAIT
      direction == PredictionDirection.UP -> NextCandleSignal.UP
      direction == PredictionDirection.DOWN -> NextCandleSignal.DOWN
      else -> NextCandleSignal.WAIT
    }

  val setupDisplayBadgeText: String
    get() = when (setupQuality) {
      SetupQualityGrade.A_PLUS -> "Setup: A+"
      SetupQualityGrade.A -> "Setup: A"
      SetupQualityGrade.B -> "Setup: B"
      SetupQualityGrade.C -> "Setup: C"
      SetupQualityGrade.NO_SETUP -> "Setup: NO SETUP"
    }

  val primaryWhyLine: String
    get() = reasons.firstOrNull() ?: "WAIT — waiting for clear market evidence"
}

/**
 * 10. Immutable Finalized Prediction History Record (Part 6 Requirement 10).
 *
 * Stored once a predicted candle period finalizes. Never modified after creation.
 */
data class FinalizedPredictionRecord(
  val id: Long,
  val timestamp: Long,
  val direction: PredictionDirection,
  val setupQuality: SetupQualityGrade,
  val dataQuality: ExtractionQuality,
  val marketRegime: MarketRegime,
  val evidence: List<String>,
  val actualNextCandleResult: PriceDirection?,
  val outcomeStatus: PredictionOutcomeStatus
)

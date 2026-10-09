package com.example.engine.prediction

import com.example.engine.indicator.IndicatorSnapshot
import com.example.engine.movement.PriceMicroStructureSnapshot
import com.example.engine.pattern.CandlestickPatternResult
import com.example.engine.priceaction.PriceActionSnapshot
import com.example.model.BreakoutClassification
import com.example.model.Candle
import com.example.model.ExtractionQuality
import com.example.model.InternalDirectionState
import com.example.model.MarketAnalysis
import com.example.model.MarketRegime
import com.example.model.MarketStabilityState
import com.example.model.NextCandlePrediction
import com.example.model.NextCandleSignal
import com.example.model.PredictionAgreementLevel
import com.example.model.PredictionDirection
import com.example.model.PredictionLifecycleState
import com.example.model.RecommendationState
import com.example.model.ReversalClassification
import com.example.model.SetupQualityGrade
import kotlin.math.abs

/**
 * High-level output for the NEXT CANDLE status card (compatible with Parts 1–6).
 */
data class NextCandleAnalysisResult(
  val signal: NextCandleSignal = NextCandleSignal.WAITING,
  val summaryMessage: String = "Live candle analysis will appear here.",
  val prediction: NextCandlePrediction = NextCandlePrediction()
)

/**
 * Part 6 — Final Smart Prediction Engine.
 *
 * Combines:
 * - Pro Trader Brain ([com.example.model.BrainAssessment])
 * - Price Action & Candlestick Intelligence
 * - Market Structure (HH/HL/LH/LL, BOS, CHoCH)
 * - Support / Resistance
 * - Momentum & Volatility
 * - Multi-window Indicators & Candle Sequence
 * - Historical Pattern Matching
 * - Current Forming Candle Behavior ([CurrentCandleMonitor])
 * - Data Quality, Invalidation & Stability ([PredictionStabilityEngine])
 *
 * Produces:
 * - UP (🟢 UP)
 * - DOWN (🔴 DOWN)
 * - WAIT (⚪ WAIT)
 * for the NEXT candle without ever forcing signals or claiming guaranteed results.
 */
interface PredictionEngine {
  fun evaluateNextCandle(
    candles: List<Candle>,
    indicators: IndicatorSnapshot,
    priceAction: PriceActionSnapshot,
    patterns: CandlestickPatternResult
  ): NextCandleAnalysisResult

  fun generateNextCandlePrediction(
    candles: List<Candle>,
    marketAnalysis: MarketAnalysis,
    microStructure: PriceMicroStructureSnapshot = PriceMicroStructureSnapshot(),
    isChartRegionValid: Boolean = true,
    isScreenDetectionStable: Boolean = true,
    nowMillis: Long = System.currentTimeMillis()
  ): NextCandlePrediction

  fun resetStability()
}

class DefaultPredictionEngine(
  private val currentCandleMonitor: CurrentCandleMonitor = DefaultCurrentCandleMonitor(),
  private val stabilityEngine: PredictionStabilityEngine = DefaultPredictionStabilityEngine()
) : PredictionEngine {

  private var lastActiveDirection: PredictionDirection = PredictionDirection.NONE

  override fun evaluateNextCandle(
    candles: List<Candle>,
    indicators: IndicatorSnapshot,
    priceAction: PriceActionSnapshot,
    patterns: CandlestickPatternResult
  ): NextCandleAnalysisResult {
    return NextCandleAnalysisResult(
      signal = NextCandleSignal.WAITING,
      summaryMessage = "Live candle analysis will appear here."
    )
  }

  @Synchronized
  override fun generateNextCandlePrediction(
    candles: List<Candle>,
    marketAnalysis: MarketAnalysis,
    microStructure: PriceMicroStructureSnapshot,
    isChartRegionValid: Boolean,
    isScreenDetectionStable: Boolean,
    nowMillis: Long
  ): NextCandlePrediction {
    val assessment = marketAnalysis.brainAssessment
    val paContext = marketAnalysis.priceActionContext
    val sr = marketAnalysis.supportResistanceAnalysis
    val conflict = marketAnalysis.conflictAnalysis
    val setupGrade = marketAnalysis.setupQualityGrade
    val dataQuality = marketAnalysis.dataQuality

    // 1. Monitor Current Forming Candle (Requirement 2 & 7)
    val currentMonitor = currentCandleMonitor.monitorCurrentCandle(
      candles = candles,
      supportResistance = sr,
      marketStructure = paContext.swingStructure,
      microStructure = microStructure
    )

    val latestCandle = candles.lastOrNull()
    val isCurrentForming = latestCandle != null && !latestCandle.isComplete
    val isEarlyWarning = isCurrentForming && currentMonitor.isEarlyForecastReady
    val horizon = if (isEarlyWarning) "EARLY_FORECAST_NEXT_CANDLE" else "NEXT_1_CANDLE"

    // 2. Check Invalidation against previously active direction (Requirement 8)
    val invalidations = stabilityEngine.evaluateInvalidation(
      activeDirection = lastActiveDirection,
      assessment = assessment,
      currentCandleMonitor = currentMonitor,
      isChartRegionValid = isChartRegionValid
    )

    if (invalidations.isNotEmpty()) {
      lastActiveDirection = PredictionDirection.NONE
      val stabilitySnap = stabilityEngine.applyStabilityControl(
        candidateDirection = PredictionDirection.NONE,
        netEvidenceScore = 0.0,
        hasStrongAgreement = false,
        nowMillis = nowMillis
      )
      val invReasonText = formatInvalidationWaitMessage(invalidations.first(), sr, conflict.conflictDescriptions)
      return NextCandlePrediction(
        direction = PredictionDirection.NONE,
        state = PredictionLifecycleState.INVALIDATED,
        evidence = emptyList(),
        agreementLevel = PredictionAgreementLevel.CONFLICTING_EVIDENCE,
        setupQuality = setupGrade,
        dataQuality = dataQuality,
        stability = assessment.stability,
        stabilitySnapshot = stabilitySnap,
        currentCandleMonitoring = currentMonitor,
        timestamp = nowMillis,
        predictionHorizon = horizon,
        reasons = listOf(invReasonText) + conflict.conflictDescriptions.take(2),
        invalidationReasons = invalidations,
        isEarlyWarning = false
      )
    }

    // 3. Evaluate Agreement Level across dimensions (Requirement 4 — never simple majority voting)
    val bullWeight = assessment.totalBullishWeight
    val bearWeight = assessment.totalBearishWeight
    val netWeight = bullWeight - bearWeight
    val absNet = abs(netWeight)

    val agreementLevel = when {
      !isChartRegionValid ||
        dataQuality == ExtractionQuality.LOW ||
        dataQuality == ExtractionQuality.UNREADABLE ||
        candles.size < 20 ->
        PredictionAgreementLevel.INSUFFICIENT_EVIDENCE
      conflict.priceActionConflict ||
        conflict.breakoutVsRejectionConflict ||
        (conflict.hasConflict && absNet < 2.4) ->
        PredictionAgreementLevel.CONFLICTING_EVIDENCE
      (setupGrade == SetupQualityGrade.A_PLUS || setupGrade == SetupQualityGrade.A) &&
        !conflict.hasConflict && absNet >= 3.2 ->
        PredictionAgreementLevel.STRONG_AGREEMENT
      (setupGrade == SetupQualityGrade.A_PLUS ||
        setupGrade == SetupQualityGrade.A ||
        setupGrade == SetupQualityGrade.B) && absNet >= 2.2 ->
        PredictionAgreementLevel.MODERATE_AGREEMENT
      conflict.hasConflict ->
        PredictionAgreementLevel.CONFLICTING_EVIDENCE
      else ->
        PredictionAgreementLevel.INSUFFICIENT_EVIDENCE
    }

    // 4. First-Class WAIT System Checks (Requirement 5)
    val breakoutUnconfirmed = paContext.breakoutState.classification == BreakoutClassification.BREAKOUT_ATTEMPT ||
      paContext.breakoutState.classification == BreakoutClassification.FAKE_BREAKOUT
    val reversalIncomplete = marketAnalysis.marketRegime == MarketRegime.REVERSAL_RISK &&
      (paContext.reversalState.classification == ReversalClassification.EARLY_REVERSAL_WARNING ||
        paContext.reversalState.classification == ReversalClassification.UNCLEAR)

    val histEvidence = assessment.historicalPatternEvidence
    val historicalSampleGateFailed = histEvidence.totalEvaluatedRecords >= 10 && !histEvidence.isSampleSufficient

    val mustWait = !isChartRegionValid ||
      dataQuality == ExtractionQuality.LOW ||
      dataQuality == ExtractionQuality.UNREADABLE ||
      !isScreenDetectionStable ||
      candles.size < 20 ||
      !currentMonitor.isBehaviorStable ||
      assessment.stability == MarketStabilityState.UNSTABLE ||
      marketAnalysis.marketRegime == MarketRegime.UNCERTAIN ||
      setupGrade == SetupQualityGrade.NO_SETUP ||
      setupGrade == SetupQualityGrade.C ||
      agreementLevel == PredictionAgreementLevel.CONFLICTING_EVIDENCE ||
      agreementLevel == PredictionAgreementLevel.INSUFFICIENT_EVIDENCE ||
      breakoutUnconfirmed ||
      reversalIncomplete ||
      historicalSampleGateFailed ||
      assessment.recommendationState == RecommendationState.WAIT

    if (mustWait) {
      lastActiveDirection = PredictionDirection.NONE
      val stabilitySnap = stabilityEngine.applyStabilityControl(
        candidateDirection = PredictionDirection.NONE,
        netEvidenceScore = netWeight,
        hasStrongAgreement = false,
        nowMillis = nowMillis
      )
      val waitReasons = buildWaitReasons(
        isChartRegionValid = isChartRegionValid,
        dataQuality = dataQuality,
        isScreenDetectionStable = isScreenDetectionStable,
        candleCount = candles.size,
        currentMonitor = currentMonitor,
        breakoutUnconfirmed = breakoutUnconfirmed,
        reversalIncomplete = reversalIncomplete,
        historicalSampleGateFailed = historicalSampleGateFailed,
        sr = sr,
        conflict = conflict
      )
      return NextCandlePrediction(
        direction = PredictionDirection.NONE,
        state = PredictionLifecycleState.WAIT,
        evidence = emptyList(),
        agreementLevel = agreementLevel,
        setupQuality = setupGrade,
        dataQuality = dataQuality,
        stability = assessment.stability,
        stabilitySnapshot = stabilitySnap,
        currentCandleMonitoring = currentMonitor,
        timestamp = nowMillis,
        predictionHorizon = horizon,
        reasons = waitReasons,
        invalidationReasons = emptyList(),
        isEarlyWarning = false
      )
    }

    // 5. Candidate Direction from BrainAssessment & Current Candle Alignment
    val candidateDirection = when (assessment.recommendationState) {
      RecommendationState.UP_CANDIDATE -> {
        if (currentMonitor.hasStrongOpposingWick && currentMonitor.upperWickRatio >= 0.48) {
          PredictionDirection.NONE
        } else {
          PredictionDirection.UP
        }
      }
      RecommendationState.DOWN_CANDIDATE -> {
        if (currentMonitor.hasStrongOpposingWick && currentMonitor.lowerWickRatio >= 0.48) {
          PredictionDirection.NONE
        } else {
          PredictionDirection.DOWN
        }
      }
      RecommendationState.WAIT -> PredictionDirection.NONE
    }

    // 6. Apply Prediction Stability & Anti-Flipping Hysteresis (Requirement 6)
    val stabilitySnap = stabilityEngine.applyStabilityControl(
      candidateDirection = candidateDirection,
      netEvidenceScore = netWeight,
      hasStrongAgreement = (agreementLevel == PredictionAgreementLevel.STRONG_AGREEMENT),
      nowMillis = nowMillis
    )

    val finalDir = stabilitySnap.currentPrediction
    lastActiveDirection = finalDir

    if (finalDir == PredictionDirection.NONE) {
      val flipReason = if (stabilitySnap.wasRapidFlipPrevented) {
        "WAIT — preventing rapid direction flip until stronger evidence confirms"
      } else {
        "WAIT — current candle rejection conflicts with setup"
      }
      return NextCandlePrediction(
        direction = PredictionDirection.NONE,
        state = PredictionLifecycleState.WAIT,
        evidence = emptyList(),
        agreementLevel = agreementLevel,
        setupQuality = setupGrade,
        dataQuality = dataQuality,
        stability = assessment.stability,
        stabilitySnapshot = stabilitySnap,
        currentCandleMonitoring = currentMonitor,
        timestamp = nowMillis,
        predictionHorizon = horizon,
        reasons = listOf(flipReason),
        invalidationReasons = emptyList(),
        isEarlyWarning = false
      )
    }

    val activeEvidence = if (finalDir == PredictionDirection.UP) {
      assessment.bullishEvidenceDescriptions
    } else {
      assessment.bearishEvidenceDescriptions
    }

    val whyLines = buildActivePredictionReasons(
      direction = finalDir,
      sr = sr,
      paContext = paContext,
      currentMonitor = currentMonitor,
      evidenceDescriptions = activeEvidence
    )

    return NextCandlePrediction(
      direction = finalDir,
      state = PredictionLifecycleState.ACTIVE,
      evidence = activeEvidence,
      agreementLevel = agreementLevel,
      setupQuality = setupGrade,
      dataQuality = dataQuality,
      stability = assessment.stability,
      stabilitySnapshot = stabilitySnap,
      currentCandleMonitoring = currentMonitor,
      timestamp = nowMillis,
      predictionHorizon = horizon,
      reasons = whyLines,
      invalidationReasons = emptyList(),
      isEarlyWarning = isEarlyWarning
    )
  }

  @Synchronized
  override fun resetStability() {
    lastActiveDirection = PredictionDirection.NONE
    stabilityEngine.reset()
  }

  private fun formatInvalidationWaitMessage(
    primaryReason: com.example.model.PredictionInvalidationReason,
    sr: com.example.model.SupportResistanceAnalysis,
    conflicts: List<String>
  ): String {
    return when (primaryReason) {
      com.example.model.PredictionInvalidationReason.MARKET_STRUCTURE_CHANGED ->
        "WAIT — market structure shifted against previous setup"
      com.example.model.PredictionInvalidationReason.STRONG_OPPOSITE_MOMENTUM ->
        "WAIT — strong opposite momentum invalidated setup"
      com.example.model.PredictionInvalidationReason.BREAKOUT_FAILED ->
        "WAIT — breakout attempt failed and rejected back inside range"
      com.example.model.PredictionInvalidationReason.SUPPORT_RESISTANCE_REJECTION_CHANGED -> {
        if (sr.resistanceRejection) {
          "WAIT — resistance rejection + conflicting momentum"
        } else {
          "WAIT — support rejection + conflicting momentum"
        }
      }
      com.example.model.PredictionInvalidationReason.DATA_QUALITY_DROPPED ->
        "WAIT — chart data quality dropped below safe threshold"
      com.example.model.PredictionInvalidationReason.CHART_DETECTION_UNRELIABLE ->
        "WAIT — chart detection unstable"
    }
  }

  private fun buildWaitReasons(
    isChartRegionValid: Boolean,
    dataQuality: ExtractionQuality,
    isScreenDetectionStable: Boolean,
    candleCount: Int,
    currentMonitor: com.example.model.CurrentCandleMonitoringState,
    breakoutUnconfirmed: Boolean,
    reversalIncomplete: Boolean,
    historicalSampleGateFailed: Boolean,
    sr: com.example.model.SupportResistanceAnalysis,
    conflict: com.example.model.ConflictAnalysisResult
  ): List<String> {
    val reasons = ArrayList<String>()
    when {
      !isChartRegionValid ->
        reasons.add("WAIT — please select the chart area")
      dataQuality == ExtractionQuality.LOW || dataQuality == ExtractionQuality.UNREADABLE ->
        reasons.add("WAIT — chart data unclear")
      !isScreenDetectionStable ->
        reasons.add("WAIT — candle detection is stabilizing")
      candleCount < 20 ->
        reasons.add("WAIT — collecting candle history ($candleCount candles)")
      !currentMonitor.isBehaviorStable ->
        reasons.add("WAIT — current forming candle behavior is unstable")
      breakoutUnconfirmed ->
        reasons.add("WAIT — breakout is unconfirmed")
      reversalIncomplete ->
        reasons.add("WAIT — reversal evidence is incomplete")
      historicalSampleGateFailed ->
        reasons.add("WAIT — historical pattern sample is insufficient")
      sr.resistanceRejection && conflict.hasConflict ->
        reasons.add("WAIT — resistance rejection + conflicting momentum")
      sr.supportRejection && conflict.hasConflict ->
        reasons.add("WAIT — support rejection + conflicting momentum")
      conflict.hasConflict ->
        reasons.add("WAIT — conflicting evidence across structure and momentum")
      else ->
        reasons.add("WAIT — waiting for high-quality setup alignment")
    }
    reasons.addAll(conflict.conflictDescriptions.take(2))
    return reasons.distinct().take(4)
  }

  private fun buildActivePredictionReasons(
    direction: PredictionDirection,
    sr: com.example.model.SupportResistanceAnalysis,
    paContext: com.example.model.PriceActionContext,
    currentMonitor: com.example.model.CurrentCandleMonitoringState,
    evidenceDescriptions: List<String>
  ): List<String> {
    val lines = ArrayList<String>()
    if (direction == PredictionDirection.UP) {
      val tags = ArrayList<String>()
      if (sr.supportRejection || sr.isNearSupport) tags.add("support rejection")
      if (paContext.trendStructure == InternalDirectionState.BULLISH || paContext.swingStructure.higherLow) {
        tags.add("bullish structure")
      }
      if (paContext.momentumState.direction == InternalDirectionState.BULLISH || currentMonitor.isAccelerating) {
        tags.add("momentum confirmation")
      }
      if (tags.isEmpty()) {
        tags.add("bullish structure + momentum confirmation")
      }
      lines.add("UP — ${tags.joinToString(" + ")}")
    } else if (direction == PredictionDirection.DOWN) {
      val tags = ArrayList<String>()
      if (sr.resistanceRejection || sr.isNearResistance) tags.add("resistance rejection")
      if (paContext.trendStructure == InternalDirectionState.BEARISH || paContext.swingStructure.lowerHigh) {
        tags.add("bearish structure")
      }
      if (paContext.momentumState.direction == InternalDirectionState.BEARISH || currentMonitor.isAccelerating) {
        tags.add("momentum confirmation")
      }
      if (tags.isEmpty()) {
        tags.add("bearish structure + momentum confirmation")
      }
      lines.add("DOWN — ${tags.joinToString(" + ")}")
    }

    lines.addAll(evidenceDescriptions.take(3))
    return lines.distinct().take(4)
  }
}

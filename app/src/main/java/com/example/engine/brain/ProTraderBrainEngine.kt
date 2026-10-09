package com.example.engine.brain

import com.example.engine.context.ContextAwareIntelligenceEngine
import com.example.engine.context.DefaultContextAwareIntelligenceEngine
import com.example.engine.fingerprint.DefaultPatternFingerprintEngine
import com.example.engine.fingerprint.PatternFingerprintEngine
import com.example.engine.indicator.IndicatorSnapshot
import com.example.engine.matching.DefaultHistoricalPatternMatcher
import com.example.engine.matching.HistoricalPatternMatcher
import com.example.engine.priceaction.PriceActionSnapshot
import com.example.engine.regime.DefaultRegimeReasoningEngine
import com.example.engine.regime.RegimeReasoningEngine
import com.example.engine.weighting.DefaultEvidenceWeightingEngine
import com.example.engine.weighting.EvidenceWeightingEngine
import com.example.model.BrainAssessment
import com.example.model.Candle
import com.example.model.CandleSequenceFeatures
import com.example.model.ConflictAnalysisResult
import com.example.model.ExtractionQuality
import com.example.model.InternalDirectionState
import com.example.model.MarketRegime
import com.example.model.MarketStabilityState
import com.example.model.MarketStateSnapshot
import com.example.model.OutcomeMemoryRecord
import com.example.model.PriceActionContext
import com.example.model.RecommendationState
import com.example.model.SetupQualityEvaluation
import com.example.model.SetupQualityGrade
import com.example.model.SupportResistanceAnalysis
import com.example.model.VolatilityClassification
import kotlin.math.abs

/**
 * Part 5 — Pro Trader Brain Engine.
 *
 * Combines Parts 1–4 outputs into a complete [MarketStateSnapshot], generates a normalized
 * [com.example.model.PatternFingerprint], matches against real stored [OutcomeMemoryRecord]
 * history, applies context-aware pattern intelligence, regime-specific reasoning, and
 * configurable evidence weighting to produce a [BrainAssessment].
 */
interface ProTraderBrainEngine {
  fun buildMarketStateSnapshot(
    candles: List<Candle>,
    priceAction: PriceActionSnapshot,
    priceActionContext: PriceActionContext,
    supportResistance: SupportResistanceAnalysis,
    indicators: IndicatorSnapshot,
    sequenceFeatures: CandleSequenceFeatures,
    marketRegime: MarketRegime,
    setupQualityEvaluation: SetupQualityEvaluation,
    dataQuality: ExtractionQuality,
    isScreenDetectionStable: Boolean = true,
    timestamp: Long = candles.lastOrNull()?.timestamp ?: System.currentTimeMillis()
  ): MarketStateSnapshot

  fun assessMarketState(
    snapshot: MarketStateSnapshot,
    conflictAnalysis: ConflictAnalysisResult,
    outcomeMemoryRecords: List<OutcomeMemoryRecord> = emptyList()
  ): BrainAssessment
}

class DefaultProTraderBrainEngine(
  val fingerprintEngine: PatternFingerprintEngine = DefaultPatternFingerprintEngine(),
  val historicalPatternMatcher: HistoricalPatternMatcher = DefaultHistoricalPatternMatcher(),
  val contextAwareEngine: ContextAwareIntelligenceEngine = DefaultContextAwareIntelligenceEngine(),
  val regimeReasoningEngine: RegimeReasoningEngine = DefaultRegimeReasoningEngine(),
  val evidenceWeightingEngine: EvidenceWeightingEngine = DefaultEvidenceWeightingEngine()
) : ProTraderBrainEngine {

  override fun buildMarketStateSnapshot(
    candles: List<Candle>,
    priceAction: PriceActionSnapshot,
    priceActionContext: PriceActionContext,
    supportResistance: SupportResistanceAnalysis,
    indicators: IndicatorSnapshot,
    sequenceFeatures: CandleSequenceFeatures,
    marketRegime: MarketRegime,
    setupQualityEvaluation: SetupQualityEvaluation,
    dataQuality: ExtractionQuality,
    isScreenDetectionStable: Boolean,
    timestamp: Long
  ): MarketStateSnapshot {
    val swing = priceActionContext.swingStructure
    val paState = priceAction.priceActionState

    return MarketStateSnapshot(
      timestamp = timestamp,
      recentCandleSequence = candles.takeLast(40),
      candleStructure = priceActionContext.recentCandleStructures,
      latestCandleStructure = priceActionContext.latestCandleStructure,
      priceAction = paState,
      higherHigh = swing.higherHigh || paState.higherHigh,
      higherLow = swing.higherLow || paState.higherLow,
      lowerHigh = swing.lowerHigh || paState.lowerHigh,
      lowerLow = swing.lowerLow || paState.lowerLow,
      bosState = priceActionContext.bosState,
      chochState = priceActionContext.chochState,
      supportResistance = supportResistance,
      breakoutRetestState = priceActionContext.breakoutState,
      isRetest = supportResistance.isRetest || priceActionContext.breakoutState.postBreakoutRetest,
      isFailedRetest = supportResistance.isFailedRetest || priceActionContext.breakoutState.postBreakoutRejection,
      reversalState = priceActionContext.reversalState,
      liquiditySweepState = priceActionContext.liquiditySweepState,
      momentum = priceActionContext.momentumState,
      volatility = indicators.atrState,
      marketRegime = marketRegime,
      indicatorStates = indicators,
      candlestickPatterns = priceActionContext.candlePatternState,
      candleSequenceFeatures = sequenceFeatures,
      setupQuality = setupQualityEvaluation.grade,
      setupQualityEvaluation = setupQualityEvaluation,
      dataQuality = dataQuality,
      isScreenDetectionStable = isScreenDetectionStable
    )
  }

  override fun assessMarketState(
    snapshot: MarketStateSnapshot,
    conflictAnalysis: ConflictAnalysisResult,
    outcomeMemoryRecords: List<OutcomeMemoryRecord>
  ): BrainAssessment {
    // 1. Create normalized PatternFingerprint
    val fingerprint = fingerprintEngine.createFingerprint(snapshot)

    // 2. Compare against stored REAL market states in OutcomeMemory
    val historicalEvidence = historicalPatternMatcher.evaluateHistoricalPatternEvidence(
      currentFingerprint = fingerprint,
      outcomeRecords = outcomeMemoryRecords
    )

    // 3. Context-Aware Candlestick Intelligence
    val contextualPatterns = contextAwareEngine.evaluatePatternsInContext(snapshot)

    // 4. Regime-Specific Reasoning Profile
    val regimeProfile = regimeReasoningEngine.evaluateRegimeProfile(snapshot)

    // 5. Evidence Weighting Engine
    val weightedEvaluation = evidenceWeightingEngine.weightEvidence(
      snapshot = snapshot,
      contextualPatterns = contextualPatterns,
      regimeProfile = regimeProfile,
      conflictAnalysis = conflictAnalysis,
      historicalEvidence = historicalEvidence
    )

    // 6. Evaluate Market Stability State
    val isErraticVolatility = snapshot.volatility.classification == VolatilityClassification.HIGH_VOLATILITY &&
      snapshot.volatility.unusuallyLargeCandles
    val stability = when {
      !snapshot.isScreenDetectionStable ||
        snapshot.dataQuality == ExtractionQuality.LOW ||
        snapshot.dataQuality == ExtractionQuality.UNREADABLE ||
        isErraticVolatility -> MarketStabilityState.UNSTABLE
      snapshot.dataQuality == ExtractionQuality.MEDIUM ||
        conflictAnalysis.hasConflict ||
        snapshot.candleSequenceFeatures.hasAlternatingCandles -> MarketStabilityState.MODERATE
      else -> MarketStabilityState.STABLE
    }

    // 7. Determine Pre-Prediction RecommendationState (UP_CANDIDATE, DOWN_CANDIDATE, or WAIT)
    val dominantDir = weightedEvaluation.dominantDirection
    val netWeightAbs = abs(weightedEvaluation.netDirectionalWeight)
    val permittedByRegime = regimeReasoningEngine.isCandidatePermittedByRegime(
      direction = dominantDir,
      snapshot = snapshot,
      profile = regimeProfile
    )

    val canConsiderCandidate = snapshot.isScreenDetectionStable &&
      (snapshot.dataQuality == ExtractionQuality.HIGH || snapshot.dataQuality == ExtractionQuality.MEDIUM) &&
      snapshot.recentCandleSequence.size >= 20 &&
      (snapshot.setupQuality == SetupQualityGrade.A_PLUS ||
        snapshot.setupQuality == SetupQualityGrade.A ||
        snapshot.setupQuality == SetupQualityGrade.B) &&
      !conflictAnalysis.priceActionConflict &&
      !conflictAnalysis.breakoutVsRejectionConflict &&
      stability != MarketStabilityState.UNSTABLE &&
      permittedByRegime &&
      netWeightAbs >= regimeProfile.minimumNetWeightDeltaForCandidate

    val recommendation = when {
      !canConsiderCandidate -> RecommendationState.WAIT
      dominantDir == InternalDirectionState.BULLISH -> RecommendationState.UP_CANDIDATE
      dominantDir == InternalDirectionState.BEARISH -> RecommendationState.DOWN_CANDIDATE
      else -> RecommendationState.WAIT
    }

    return BrainAssessment(
      marketState = snapshot,
      patternFingerprint = fingerprint,
      dominantDirection = dominantDir,
      bullishEvidence = weightedEvaluation.bullishItems,
      bearishEvidence = weightedEvaluation.bearishItems,
      bullishEvidenceDescriptions = weightedEvaluation.bullishItems.map { it.description },
      bearishEvidenceDescriptions = weightedEvaluation.bearishItems.map { it.description },
      totalBullishWeight = weightedEvaluation.totalBullishWeight,
      totalBearishWeight = weightedEvaluation.totalBearishWeight,
      conflicts = conflictAnalysis.conflictDescriptions,
      contextualPatterns = contextualPatterns,
      historicalPatternEvidence = historicalEvidence,
      setupQuality = snapshot.setupQuality,
      dataQuality = snapshot.dataQuality,
      stability = stability,
      regimeReasoningSummary = regimeProfile.reasoningSummary,
      recommendationState = recommendation
    )
  }
}

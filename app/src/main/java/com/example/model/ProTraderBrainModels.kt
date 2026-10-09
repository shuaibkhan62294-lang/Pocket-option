package com.example.model

import com.example.engine.indicator.IndicatorSnapshot

/**
 * Part 5 Requirement 8: Pre-prediction candidate recommendation state produced by
 * the Pro Trader Brain.
 *
 * NOTE: This is NOT the final UP/DOWN UI prediction signal (which remains WAIT in Parts 1–5
 * until Part 6 consumes [BrainAssessment]).
 */
enum class RecommendationState {
  UP_CANDIDATE,
  DOWN_CANDIDATE,
  WAIT
}

/**
 * Stability classification for the current market and capture stream (Part 5 Requirement 8).
 */
enum class MarketStabilityState {
  STABLE,
  MODERATE,
  UNSTABLE
}

/**
 * Similarity quality tier for historical pattern matching (Part 5 Requirement 3).
 */
enum class SimilarityQualityLevel {
  NONE,
  LOW,
  MODERATE,
  HIGH
}

/**
 * Sample sufficiency classification for historical pattern matches (Part 5 Requirement 3).
 *
 * Never treats a tiny number of matches (e.g. 2 matches) as reliable evidence:
 * - < weakEvidenceMinMatches (default 10) -> INSUFFICIENT_EVIDENCE
 * - 10..24 -> WEAK_EVIDENCE
 * - 25..49 -> MODERATE_EVIDENCE
 * - 50+ quality matches -> POTENTIALLY_USEFUL
 */
enum class HistoricalSampleSufficiency {
  INSUFFICIENT_EVIDENCE,
  WEAK_EVIDENCE,
  MODERATE_EVIDENCE,
  POTENTIALLY_USEFUL
}

/**
 * 1. Complete Market State Snapshot (Part 5 Requirement 1).
 *
 * Serves as the primary structured input to the Pro Trader Brain, combining:
 * - Recent candle sequence
 * - Candle structure
 * - Price action
 * - HH / HL / LH / LL
 * - BOS & CHoCH
 * - Support / resistance
 * - Breakout / retest state
 * - Reversal state
 * - Liquidity sweep state
 * - Momentum
 * - Volatility
 * - Market regime
 * - Indicator states
 * - Candlestick patterns
 * - Setup quality
 * - Data quality
 */
data class MarketStateSnapshot(
  val timestamp: Long = 0L,
  val recentCandleSequence: List<Candle> = emptyList(),
  val candleStructure: List<CandleStructureMetrics> = emptyList(),
  val latestCandleStructure: CandleStructureMetrics? = null,
  val priceAction: PriceActionAnalysisState = PriceActionAnalysisState(),
  val higherHigh: Boolean = false,
  val higherLow: Boolean = false,
  val lowerHigh: Boolean = false,
  val lowerLow: Boolean = false,
  val bosState: BosClassification = BosClassification.NONE,
  val chochState: ChochClassification = ChochClassification.NONE,
  val supportResistance: SupportResistanceAnalysis = SupportResistanceAnalysis(),
  val breakoutRetestState: BreakoutAnalysisState = BreakoutAnalysisState(),
  val isRetest: Boolean = false,
  val isFailedRetest: Boolean = false,
  val reversalState: ReversalAnalysisState = ReversalAnalysisState(),
  val liquiditySweepState: LiquiditySweepState = LiquiditySweepState(),
  val momentum: MomentumAnalysisState = MomentumAnalysisState(),
  val volatility: AtrVolatilityState = AtrVolatilityState(),
  val marketRegime: MarketRegime = MarketRegime.UNCERTAIN,
  val indicatorStates: IndicatorSnapshot = IndicatorSnapshot(),
  val candlestickPatterns: List<DetectedCandlestickPattern> = emptyList(),
  val candleSequenceFeatures: CandleSequenceFeatures = CandleSequenceFeatures(),
  val setupQuality: SetupQualityGrade = SetupQualityGrade.NO_SETUP,
  val setupQualityEvaluation: SetupQualityEvaluation = SetupQualityEvaluation(),
  val dataQuality: ExtractionQuality = ExtractionQuality.UNREADABLE,
  val isScreenDetectionStable: Boolean = true
)

/**
 * Normalized indicator states used inside [PatternFingerprint] so setups at different
 * price levels can be compared fairly (Part 5 Requirement 2).
 */
data class NormalizedIndicatorFingerprint(
  val emaDirection: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val smaDirection: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val rsiZone: Int = 0, // -1 oversold, 0 neutral, +1 overbought
  val rsiMomentum: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val macdDirection: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val bollingerPosition: Int = 0, // -1 near lower, 0 middle, +1 near upper
  val stochasticDirection: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val adxStrongTrend: Boolean = false,
  val cciMomentum: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val williamsDirection: InternalDirectionState = InternalDirectionState.NEUTRAL
)

/**
 * 2. Normalized Pattern Fingerprint (Part 5 Requirement 2).
 *
 * Represents a setup in scale-invariant, normalized form so different price levels
 * and sessions can be compared accurately.
 */
data class PatternFingerprint(
  val candleDirectionSequence: List<Int> = emptyList(), // +1 bullish, -1 bearish, 0 flat
  val relativeCandleBodySizes: List<Double> = emptyList(), // normalized to average body (0.0..4.0)
  val wickRatios: List<Double> = emptyList(), // total wick / total range (0.0..1.0)
  val upperWickRatios: List<Double> = emptyList(),
  val lowerWickRatios: List<Double> = emptyList(),
  val momentumBehavior: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val isMomentumAccelerating: Boolean = false,
  val isMomentumExhausted: Boolean = false,
  val volatilityBehavior: VolatilityClassification = VolatilityClassification.NORMAL_VOLATILITY,
  val isVolatilityExpanding: Boolean = false,
  val isVolatilityContracting: Boolean = false,
  val trendStructure: MarketStructureType = MarketStructureType.RANGE_STRUCTURE,
  val bosState: BosClassification = BosClassification.NONE,
  val chochState: ChochClassification = ChochClassification.NONE,
  val supportResistanceRelationship: CandleSrPosition = CandleSrPosition.MID_RANGE,
  val indicatorStates: NormalizedIndicatorFingerprint = NormalizedIndicatorFingerprint(),
  val candlestickPatterns: List<String> = emptyList(),
  val breakoutState: BreakoutClassification = BreakoutClassification.BREAKOUT_UNCLEAR,
  val reversalState: ReversalClassification = ReversalClassification.NO_REVERSAL,
  val liquiditySweepState: LiquiditySweepClassification = LiquiditySweepClassification.UNCLEAR,
  val marketRegime: MarketRegime = MarketRegime.UNCERTAIN
)

/**
 * Distribution of actual next-candle outcomes among matched historical setups (Part 5 Requirement 3).
 */
data class HistoricalOutcomeDistribution(
  val bullishOutcomes: Int = 0,
  val bearishOutcomes: Int = 0,
  val flatOutcomes: Int = 0,
  val bullishRatio: Double = 0.0,
  val bearishRatio: Double = 0.0
) {
  val totalOutcomes: Int
    get() = bullishOutcomes + bearishOutcomes + flatOutcomes
}

/**
 * 3. Historical Pattern Matching Evidence (Part 5 Requirement 3).
 */
data class HistoricalPatternEvidence(
  val historicalMatchesCount: Int = 0,
  val totalEvaluatedRecords: Int = 0,
  val similarityQuality: SimilarityQualityLevel = SimilarityQualityLevel.NONE,
  val averageSimilarityScore: Double = 0.0,
  val topSimilarityScore: Double = 0.0,
  val outcomeDistribution: HistoricalOutcomeDistribution = HistoricalOutcomeDistribution(),
  val sampleSufficiency: HistoricalSampleSufficiency = HistoricalSampleSufficiency.INSUFFICIENT_EVIDENCE,
  val isSampleSufficient: Boolean = false,
  val summaryDescription: String = "Insufficient historical matches"
)

/**
 * 4. Immutable Outcome Memory Record (Part 5 Requirement 4).
 *
 * Recorded ONLY once the subsequent candle completes so that the actual outcome is known.
 * Never modified after creation.
 */
data class OutcomeMemoryRecord(
  val id: Long,
  val timestamp: Long,
  val setupSnapshot: MarketStateSnapshot,
  val patternFingerprint: PatternFingerprint,
  val predictedDirection: RecommendationState?,
  val actualNextCandleDirection: PriceDirection,
  val actualCandleBodySize: Double,
  val actualCandleRange: Double,
  val wasPredictionCorrect: Boolean?,
  val marketRegime: MarketRegime,
  val dataQuality: ExtractionQuality,
  val setupQuality: SetupQualityGrade
)

/**
 * 5. Context-Aware Pattern Evaluation (Part 5 Requirement 5).
 *
 * Captures how market context (support/resistance, exhaustion, momentum, trend)
 * strengthens or weakens a raw candlestick pattern.
 */
data class ContextualPatternEvaluation(
  val patternName: String,
  val direction: InternalDirectionState,
  val rawStrength: InternalStrengthLabel,
  val adjustedStrength: InternalStrengthLabel,
  val contextWeightMultiplier: Double,
  val isBoostedByContext: Boolean,
  val isWeakenedByContext: Boolean,
  val contextReasoning: String
)

/**
 * 6. Weighted Evidence Item (Part 5 Requirement 6).
 */
data class WeightedEvidenceItem(
  val sourceCategory: String,
  val description: String,
  val direction: InternalDirectionState,
  val baseWeight: Double,
  val contextMultiplier: Double,
  val finalWeight: Double
)

/**
 * 9. Memory Safety Validation Result (Part 5 Requirement 9).
 */
enum class MemoryRejectionReason {
  MISSING_CANDLES,
  UNREADABLE_CANDLES,
  DUPLICATE_CANDLES,
  IMPOSSIBLE_OHLC_RELATIONSHIPS,
  UNSTABLE_SCREEN_DETECTION,
  INCORRECT_TIMESTAMPS
}

data class MemorySafetyValidationResult(
  val isValidForMemory: Boolean,
  val rejectionReasons: List<MemoryRejectionReason> = emptyList(),
  val diagnosticSummary: String = "VALID"
)

/**
 * 8. Pro Trader Brain Output (`BrainAssessment`) (Part 5 Requirement 8).
 *
 * Contains the complete synthesized assessment of the current market state,
 * weighted bullish/bearish evidence, conflicts, historical pattern evidence,
 * setup/data quality, stability, and pre-prediction [RecommendationState].
 */
data class BrainAssessment(
  val marketState: MarketStateSnapshot = MarketStateSnapshot(),
  val patternFingerprint: PatternFingerprint = PatternFingerprint(),
  val dominantDirection: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val bullishEvidence: List<WeightedEvidenceItem> = emptyList(),
  val bearishEvidence: List<WeightedEvidenceItem> = emptyList(),
  val bullishEvidenceDescriptions: List<String> = emptyList(),
  val bearishEvidenceDescriptions: List<String> = emptyList(),
  val totalBullishWeight: Double = 0.0,
  val totalBearishWeight: Double = 0.0,
  val conflicts: List<String> = emptyList(),
  val contextualPatterns: List<ContextualPatternEvaluation> = emptyList(),
  val historicalPatternEvidence: HistoricalPatternEvidence = HistoricalPatternEvidence(),
  val setupQuality: SetupQualityGrade = SetupQualityGrade.NO_SETUP,
  val dataQuality: ExtractionQuality = ExtractionQuality.UNREADABLE,
  val stability: MarketStabilityState = MarketStabilityState.UNSTABLE,
  val regimeReasoningSummary: String = "Waiting for reliable chart data",
  val recommendationState: RecommendationState = RecommendationState.WAIT
)

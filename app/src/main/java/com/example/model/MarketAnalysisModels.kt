package com.example.model

/**
 * Internal directional classification used across Multi-Timeframe, Indicator,
 * Price Action, and Pattern engines (Parts 3 & 4).
 */
enum class InternalDirectionState {
  BULLISH,
  BEARISH,
  NEUTRAL,
  REVERSAL_RISK,
  CONSOLIDATING
}

/**
 * Honest internal strength labels (Part 3 Requirement 19).
 * Never uses arbitrary fake confidence percentages like "87% confidence".
 */
enum class InternalStrengthLabel {
  WEAK,
  MODERATE,
  STRONG,
  CONFLICTING
}

/**
 * Volatility classification from the ATR / Volatility Engine.
 */
enum class VolatilityClassification {
  LOW_VOLATILITY,
  NORMAL_VOLATILITY,
  HIGH_VOLATILITY
}

/**
 * Support / Resistance zone strength classification (Part 3 + Part 4 Requirement 4).
 */
enum class ZoneStrength {
  WEAK,
  MEDIUM,
  STRONG,
  VERY_STRONG
}

/**
 * Market regime classification (Part 3 Requirement 17).
 */
enum class MarketRegime {
  TRENDING_UP,
  TRENDING_DOWN,
  SIDEWAYS,
  BREAKOUT,
  BREAKDOWN,
  HIGH_VOLATILITY,
  LOW_VOLATILITY,
  REVERSAL_RISK,
  UNCERTAIN
}

/**
 * Internal safety reasons when returning WAIT.
 */
enum class NoTradeSafetyReason {
  INSUFFICIENT_DATA,
  CONFLICTING_SIGNALS,
  UNSTABLE_MARKET,
  LOW_DATA_QUALITY,
  READY_FOR_EVALUATION
}

// ============================================================================
// Part 4 Enums & Classifications
// ============================================================================

/**
 * 1. Advanced Candle Structure Classifications (Part 4 Requirement 1).
 */
enum class CandleBehaviorClassification {
  STRONG_BULLISH,
  WEAK_BULLISH,
  STRONG_BEARISH,
  WEAK_BEARISH,
  DOJI_INDECISION,
  REJECTION,
  EXHAUSTION,
  MOMENTUM,
  BREAKOUT_ATTEMPT,
  FAKE_BREAKOUT,
  CONSOLIDATION
}

enum class WickDominance {
  UPPER_WICK_DOMINANT,
  LOWER_WICK_DOMINANT,
  BALANCED_WICKS,
  MINIMAL_WICKS
}

enum class CandleRangeExpansionState {
  EXPANDING,
  CONTRACTING,
  NORMAL
}

enum class RelativeCandlePosition {
  HIGHER_CLOSE,
  LOWER_CLOSE,
  INSIDE_BAR,
  OUTSIDE_ENGULFING,
  NEUTRAL_OVERLAP
}

enum class CandleSrPosition {
  AT_SUPPORT,
  AT_RESISTANCE,
  BREAKING_SUPPORT,
  BREAKING_RESISTANCE,
  MID_RANGE
}

/**
 * 3. Market Structure, BOS, and CHoCH States (Part 4 Requirement 3).
 */
enum class MarketStructureType {
  UPTREND_STRUCTURE,
  DOWNTREND_STRUCTURE,
  RANGE_STRUCTURE,
  STRUCTURE_WEAKENING,
  POSSIBLE_REVERSAL,
  STRUCTURE_BREAK
}

enum class BosClassification {
  NONE,
  BULLISH_BOS,
  BEARISH_BOS
}

enum class ChochClassification {
  NONE,
  BULLISH_CHOCH,
  BEARISH_CHOCH
}

/**
 * 5. Liquidity / Stop-Run Style Price Action Classification (Part 4 Requirement 5).
 */
enum class LiquiditySweepClassification {
  POSSIBLE_LIQUIDITY_SWEEP,
  CONFIRMED_REJECTION_AFTER_SWEEP,
  POSSIBLE_CONTINUATION,
  UNCLEAR
}

/**
 * 6. Breakout Intelligence Classification (Part 4 Requirement 6).
 */
enum class BreakoutClassification {
  BREAKOUT_CONFIRMED,
  BREAKOUT_ATTEMPT,
  FAKE_BREAKOUT,
  BREAKOUT_UNCLEAR
}

/**
 * 7. Reversal Intelligence Classification (Part 4 Requirement 7).
 */
enum class ReversalClassification {
  STRONG_REVERSAL_EVIDENCE,
  MODERATE_REVERSAL_EVIDENCE,
  EARLY_REVERSAL_WARNING,
  NO_REVERSAL,
  UNCLEAR
}

/**
 * 12. Setup Quality Grade (Part 4 Requirement 12).
 */
enum class SetupQualityGrade(val displayLabel: String) {
  A_PLUS("A+"),
  A("A"),
  B("B"),
  C("C"),
  NO_SETUP("NO_SETUP")
}

// ============================================================================
// Structured Sub-Signals for MarketAnalysis (Parts 3 & 4)
// ============================================================================

data class MultiTimeframeState(
  val window5Direction: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val window10Direction: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val window20Direction: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val window30Direction: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val window40Direction: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val shortTermDirection: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val mediumTermDirection: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val overallDirection: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val trendStrength: InternalStrengthLabel = InternalStrengthLabel.WEAK,
  val momentum: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val isConsolidating: Boolean = false,
  val hasReversalPossibility: Boolean = false
)

data class EmaSignal(
  val ema9: Double? = null,
  val ema21: Double? = null,
  val ema50: Double? = null,
  val bullishCrossover: Boolean = false,
  val bearishCrossover: Boolean = false,
  val priceAboveEma: Boolean = false,
  val priceBelowEma: Boolean = false,
  val emaAlignment: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val emaSeparation: Double = 0.0,
  val emaCompression: Boolean = false,
  val direction: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val strength: InternalStrengthLabel = InternalStrengthLabel.WEAK
)

data class SmaSignal(
  val sma20: Double? = null,
  val sma50: Double? = null,
  val priceAboveSma20: Boolean = false,
  val priceAboveSma50: Boolean = false,
  val bullishCrossover: Boolean = false,
  val bearishCrossover: Boolean = false,
  val trendConfirmation: Boolean = false,
  val possibleReversal: Boolean = false,
  val direction: InternalDirectionState = InternalDirectionState.NEUTRAL
)

data class RsiSignal(
  val rsiValue: Double? = null,
  val isOverbought: Boolean = false,
  val isOversold: Boolean = false,
  val isNeutral: Boolean = true,
  val momentumIncrease: Boolean = false,
  val momentumDecrease: Boolean = false,
  val bullishDivergence: Boolean = false,
  val bearishDivergence: Boolean = false,
  val directionWithPriceAction: InternalDirectionState = InternalDirectionState.NEUTRAL
)

data class MacdSignal(
  val macdLine: Double? = null,
  val signalLine: Double? = null,
  val histogram: Double? = null,
  val bullishCrossover: Boolean = false,
  val bearishCrossover: Boolean = false,
  val histogramExpansion: Boolean = false,
  val histogramContraction: Boolean = false,
  val momentumTransition: Boolean = false,
  val bullishDivergence: Boolean = false,
  val bearishDivergence: Boolean = false,
  val direction: InternalDirectionState = InternalDirectionState.NEUTRAL
)

data class BollingerSignal(
  val middleBand: Double? = null,
  val upperBand: Double? = null,
  val lowerBand: Double? = null,
  val bandWidth: Double = 0.0,
  val priceNearUpperBand: Boolean = false,
  val priceNearLowerBand: Boolean = false,
  val bandExpansion: Boolean = false,
  val bandContraction: Boolean = false,
  val possibleBreakout: Boolean = false,
  val possibleMeanReversion: Boolean = false,
  val directionContext: InternalDirectionState = InternalDirectionState.NEUTRAL
)

data class StochasticSignal(
  val percentK: Double? = null,
  val percentD: Double? = null,
  val bullishCrossover: Boolean = false,
  val bearishCrossover: Boolean = false,
  val isOverbought: Boolean = false,
  val isOversold: Boolean = false,
  val momentumTransition: Boolean = false,
  val directionWithTrend: InternalDirectionState = InternalDirectionState.NEUTRAL
)

data class AtrVolatilityState(
  val atrValue: Double? = null,
  val classification: VolatilityClassification = VolatilityClassification.NORMAL_VOLATILITY,
  val volatilityExpansion: Boolean = false,
  val volatilityContraction: Boolean = false,
  val unusuallyLargeCandles: Boolean = false,
  val unusuallySmallCandles: Boolean = false
)

data class AdxSignal(
  val adxValue: Double? = null,
  val plusDi: Double? = null,
  val minusDi: Double? = null,
  val isTrendingMarket: Boolean = false,
  val isWeakTrend: Boolean = true,
  val isStrongTrend: Boolean = false,
  val possibleTrendTransition: Boolean = false,
  val direction: InternalDirectionState = InternalDirectionState.NEUTRAL
)

data class CciSignal(
  val cciValue: Double? = null,
  val momentum: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val isExtremeReading: Boolean = false,
  val momentumReversal: Boolean = false,
  val zeroLineTransition: Boolean = false
)

data class WilliamsSignal(
  val williamsR: Double? = null,
  val isOverbought: Boolean = false,
  val isOversold: Boolean = false,
  val momentumReversal: Boolean = false,
  val confirmedWithPriceAction: Boolean = false,
  val direction: InternalDirectionState = InternalDirectionState.NEUTRAL
)

data class SupportResistanceZone(
  val levelPrice: Double,
  val lowerBound: Double,
  val upperBound: Double,
  val touchCount: Int,
  val rejectionCount: Int,
  val strength: ZoneStrength,
  val isSupport: Boolean
)

data class SupportResistanceAnalysis(
  val supportZones: List<SupportResistanceZone> = emptyList(),
  val resistanceZones: List<SupportResistanceZone> = emptyList(),
  val nearestSupport: SupportResistanceZone? = null,
  val nearestResistance: SupportResistanceZone? = null,
  val isNearSupport: Boolean = false,
  val isNearResistance: Boolean = false,
  val supportRejection: Boolean = false,
  val resistanceRejection: Boolean = false,
  val supportBreakout: Boolean = false,
  val resistanceBreakout: Boolean = false,
  val possibleFakeBreakout: Boolean = false,
  val fakeBreakdown: Boolean = false,
  val isRetest: Boolean = false,
  val isFailedRetest: Boolean = false,
  val consolidationZonesCount: Int = 0
)

data class MomentumAnalysisState(
  val averageBodySize: Double = 0.0,
  val averageWickSize: Double = 0.0,
  val consecutiveBullishCandles: Int = 0,
  val consecutiveBearishCandles: Int = 0,
  val isAccelerating: Boolean = false,
  val isDecelerating: Boolean = false,
  val isMomentumExhausted: Boolean = false,
  val strongBullishMomentum: Boolean = false,
  val strongBearishMomentum: Boolean = false,
  val weakeningMomentum: Boolean = false,
  val suddenReversal: Boolean = false,
  val direction: InternalDirectionState = InternalDirectionState.NEUTRAL
)

data class PriceActionAnalysisState(
  val higherHigh: Boolean = false,
  val higherLow: Boolean = false,
  val lowerHigh: Boolean = false,
  val lowerLow: Boolean = false,
  val isUptrend: Boolean = false,
  val isDowntrend: Boolean = false,
  val isRange: Boolean = false,
  val isBreakout: Boolean = false,
  val isBreakdown: Boolean = false,
  val isReversalStructure: Boolean = false,
  val isConsolidation: Boolean = true,
  val structuralChangeDetected: Boolean = false,
  val direction: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val momentum: MomentumAnalysisState = MomentumAnalysisState()
)

/**
 * 1. Advanced Candle Structure metrics for a single candle (Part 4 Requirement 1).
 */
data class CandleStructureMetrics(
  val index: Int,
  val open: Double,
  val high: Double,
  val low: Double,
  val close: Double,
  val bodySize: Double,
  val upperWick: Double,
  val lowerWick: Double,
  val totalRange: Double,
  val bodyToRangeRatio: Double,
  val direction: PriceDirection,
  val relativeBodyStrength: Double,
  val wickDominance: WickDominance,
  val expansionState: CandleRangeExpansionState,
  val positionRelativeToPrevious: RelativeCandlePosition,
  val positionRelativeToSr: CandleSrPosition,
  val positionInRecentRange: Double,
  val candleSpeedPerSecond: Double,
  val primaryClassification: CandleBehaviorClassification,
  val secondaryClassifications: List<CandleBehaviorClassification> = emptyList()
)

/**
 * 2. Context-aware Candlestick Pattern record (Part 3 + Part 4 Requirement 2).
 */
data class DetectedCandlestickPattern(
  val patternName: String,
  val direction: InternalDirectionState,
  val strength: InternalStrengthLabel,
  val candlePositions: List<Int>,
  val context: String,
  val location: String = "Mid-Range",
  val previousMarketContext: String = "Neutral",
  val occursNearSupportOrResistance: Boolean = false,
  val momentumConfirms: Boolean = false,
  val indicatorsConfirm: Boolean = false,
  val conflictsWithCurrentTrend: Boolean = false
)

/**
 * 3. Market Structure Engine Output (Part 4 Requirement 3).
 */
data class MarketStructureAnalysis(
  val higherHigh: Boolean = false,
  val higherLow: Boolean = false,
  val lowerHigh: Boolean = false,
  val lowerLow: Boolean = false,
  val structureType: MarketStructureType = MarketStructureType.RANGE_STRUCTURE,
  val bosState: BosClassification = BosClassification.NONE,
  val chochState: ChochClassification = ChochClassification.NONE,
  val isStructureWeakening: Boolean = false,
  val isPossibleReversal: Boolean = false,
  val confirmedSwingHighsCount: Int = 0,
  val confirmedSwingLowsCount: Int = 0,
  val lastSwingHighPrice: Double? = null,
  val lastSwingLowPrice: Double? = null
)

/**
 * 5. Liquidity / Stop-Run Style Price Action Output (Part 4 Requirement 5).
 */
data class LiquiditySweepState(
  val equalHighs: Boolean = false,
  val equalLows: Boolean = false,
  val previousSwingLiquidityAreas: List<Double> = emptyList(),
  val sweepAbovePreviousHigh: Boolean = false,
  val sweepBelowPreviousLow: Boolean = false,
  val rejectionAfterSweep: Boolean = false,
  val failedBreakout: Boolean = false,
  val failedBreakdown: Boolean = false,
  val classification: LiquiditySweepClassification = LiquiditySweepClassification.UNCLEAR,
  val sweepDirectionBias: InternalDirectionState = InternalDirectionState.NEUTRAL
)

/**
 * 6. Breakout Intelligence Output (Part 4 Requirement 6).
 */
data class BreakoutAnalysisState(
  val preBreakoutCompression: Boolean = false,
  val narrowCandleRanges: Boolean = false,
  val repeatedLevelTests: Boolean = false,
  val momentumBuildup: Boolean = false,
  val volatilityContraction: Boolean = false,
  val strongBreakoutBody: Boolean = false,
  val cleanWickBehavior: Boolean = false,
  val rangeExpansion: Boolean = false,
  val breakoutDirection: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val levelPenetration: Boolean = false,
  val postBreakoutContinuation: Boolean = false,
  val postBreakoutRetest: Boolean = false,
  val postBreakoutRejection: Boolean = false,
  val classification: BreakoutClassification = BreakoutClassification.BREAKOUT_UNCLEAR
)

/**
 * 7. Reversal Intelligence Output (Part 4 Requirement 7).
 */
data class ReversalAnalysisState(
  val trendExhaustion: Boolean = false,
  val largeOppositeCandle: Boolean = false,
  val longRejectionWick: Boolean = false,
  val engulfingPresent: Boolean = false,
  val momentumWeakening: Boolean = false,
  val rsiMacdContextSupports: Boolean = false,
  val atSupportOrResistance: Boolean = false,
  val structureSupportsReversal: Boolean = false,
  val bosOrChochPresent: Boolean = false,
  val reversalSequencePresent: Boolean = false,
  val confirmingFactorsCount: Int = 0,
  val reversalDirection: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val classification: ReversalClassification = ReversalClassification.NO_REVERSAL
)

/**
 * 8. Candle Sequence Intelligence Output (Part 3 + Part 4 Requirement 8).
 */
data class CandleSequenceFeatures(
  val multiTimeframe: MultiTimeframeState = MultiTimeframeState(),
  val consecutiveBullishCandles: Int = 0,
  val consecutiveBearishCandles: Int = 0,
  val hasRepeatedSequences: Boolean = false,
  val hasAlternatingCandles: Boolean = false,
  val increasingBodySize: Boolean = false,
  val decreasingBodySize: Boolean = false,
  val increasingWickSize: Boolean = false,
  val momentumAcceleration: Boolean = false,
  val momentumDeceleration: Boolean = false,
  val hasExhaustion: Boolean = false,
  val hasCompression: Boolean = false,
  val hasExpansion: Boolean = false,
  val hasMomentumSequence: Boolean = false,
  val hasReversalSequence: Boolean = false,
  val hasCompressionBeforeExpansion: Boolean = false,
  val hasRepeatedRejection: Boolean = false,
  val hasRepeatedContinuation: Boolean = false,
  val hasFailedReversalAttempts: Boolean = false,
  val hasTrendContinuationStructure: Boolean = false,
  val exhaustionWarningDescription: String? = null
)

/**
 * 9. Price Action Context Object (Part 4 Requirement 9).
 */
data class PriceActionContext(
  val trendStructure: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val swingStructure: MarketStructureAnalysis = MarketStructureAnalysis(),
  val bosState: BosClassification = BosClassification.NONE,
  val chochState: ChochClassification = ChochClassification.NONE,
  val supportState: SupportResistanceZone? = null,
  val resistanceState: SupportResistanceZone? = null,
  val breakoutState: BreakoutAnalysisState = BreakoutAnalysisState(),
  val liquiditySweepState: LiquiditySweepState = LiquiditySweepState(),
  val reversalState: ReversalAnalysisState = ReversalAnalysisState(),
  val momentumState: MomentumAnalysisState = MomentumAnalysisState(),
  val candlePatternState: List<DetectedCandlestickPattern> = emptyList(),
  val candleSequenceState: CandleSequenceFeatures = CandleSequenceFeatures(),
  val latestCandleStructure: CandleStructureMetrics? = null,
  val recentCandleStructures: List<CandleStructureMetrics> = emptyList(),
  val marketRegime: MarketRegime = MarketRegime.UNCERTAIN,
  val dataQuality: ExtractionQuality = ExtractionQuality.UNREADABLE
)

/**
 * 11. Conflict Detection Engine Output (Part 4 Requirement 11).
 */
data class ConflictAnalysisResult(
  val hasConflict: Boolean = false,
  val indicatorConflict: Boolean = false,
  val priceActionConflict: Boolean = false,
  val patternConflict: Boolean = false,
  val trendVsReversalConflict: Boolean = false,
  val breakoutVsRejectionConflict: Boolean = false,
  val momentumConflict: Boolean = false,
  val lowQualityDataConflict: Boolean = false,
  val conflictDescriptions: List<String> = emptyList(),
  val summaryStatus: String = "NO_CONFLICT"
)

/**
 * 12. Setup Quality Engine Evaluation (Part 4 Requirement 12).
 */
data class SetupQualityEvaluation(
  val grade: SetupQualityGrade = SetupQualityGrade.NO_SETUP,
  val alignedFactorsCount: Int = 0,
  val downgradeReasons: List<String> = emptyList(),
  val summaryReason: String = "Waiting for reliable chart data"
)

/**
 * 14. Market State Feature Vector for Historical Pattern Matching (Part 4 Requirement 14).
 */
data class MarketStateFeatureVector(
  val candleDirections: List<Int> = emptyList(), // +1 bullish, -1 bearish, 0 flat
  val relativeBodySizes: List<Double> = emptyList(),
  val wickRatios: List<Double> = emptyList(),
  val priceStructure: MarketStructureType = MarketStructureType.RANGE_STRUCTURE,
  val momentumState: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val volatilityState: VolatilityClassification = VolatilityClassification.NORMAL_VOLATILITY,
  val srRelationship: CandleSrPosition = CandleSrPosition.MID_RANGE,
  val indicatorBias: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val primaryPatternName: String? = null,
  val marketRegime: MarketRegime = MarketRegime.UNCERTAIN
)

/**
 * Complete Pro Trader Brain Analysis Object (`MarketAnalysis`) combining Parts 3 & 4.
 */
data class MarketAnalysis(
  val marketRegime: MarketRegime = MarketRegime.UNCERTAIN,
  val trendDirection: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val trendStrength: InternalStrengthLabel = InternalStrengthLabel.WEAK,
  val momentumState: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val volatilityState: VolatilityClassification = VolatilityClassification.NORMAL_VOLATILITY,

  val emaSignal: EmaSignal = EmaSignal(),
  val smaSignal: SmaSignal = SmaSignal(),
  val rsiSignal: RsiSignal = RsiSignal(),
  val macdSignal: MacdSignal = MacdSignal(),
  val bollingerSignal: BollingerSignal = BollingerSignal(),
  val stochasticSignal: StochasticSignal = StochasticSignal(),
  val atrState: AtrVolatilityState = AtrVolatilityState(),
  val adxSignal: AdxSignal = AdxSignal(),
  val cciSignal: CciSignal = CciSignal(),
  val williamsSignal: WilliamsSignal = WilliamsSignal(),

  val supportZones: List<SupportResistanceZone> = emptyList(),
  val resistanceZones: List<SupportResistanceZone> = emptyList(),
  val supportResistanceAnalysis: SupportResistanceAnalysis = SupportResistanceAnalysis(),

  val priceActionState: PriceActionAnalysisState = PriceActionAnalysisState(),
  val candlestickPatterns: List<DetectedCandlestickPattern> = emptyList(),
  val candleSequenceFeatures: CandleSequenceFeatures = CandleSequenceFeatures(),

  // Part 4 Advanced Price Action & Candlestick Intelligence Extensions
  val priceActionContext: PriceActionContext = PriceActionContext(),
  val conflictAnalysis: ConflictAnalysisResult = ConflictAnalysisResult(),
  val setupQualityEvaluation: SetupQualityEvaluation = SetupQualityEvaluation(),
  val setupQualityGrade: SetupQualityGrade = SetupQualityGrade.NO_SETUP,
  val featureVector: MarketStateFeatureVector = MarketStateFeatureVector(),

  // Part 5 Pro Trader Brain Extensions
  val marketStateSnapshot: MarketStateSnapshot = MarketStateSnapshot(),
  val patternFingerprint: PatternFingerprint = PatternFingerprint(),
  val brainAssessment: BrainAssessment = BrainAssessment(),

  val bullishEvidence: List<String> = emptyList(),
  val bearishEvidence: List<String> = emptyList(),
  val conflicts: List<String> = emptyList(),
  val marketContext: String = "Waiting for reliable chart data",

  val dataQuality: ExtractionQuality = ExtractionQuality.UNREADABLE,
  val analysisState: InternalStrengthLabel = InternalStrengthLabel.WEAK,
  val safetyReason: NoTradeSafetyReason = NoTradeSafetyReason.INSUFFICIENT_DATA,
  val recommendedSignal: NextCandleSignal = NextCandleSignal.WAIT,
  val simpleWhyExplanations: List<String> = listOf("Waiting for chart data...")
)

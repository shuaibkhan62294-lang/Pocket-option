package com.example.engine.memory

import com.example.engine.safety.DefaultMemorySafetyValidator
import com.example.engine.safety.MemorySafetyValidator
import com.example.model.Candle
import com.example.model.ExtractionQuality
import com.example.model.InternalDirectionState
import com.example.model.MarketRegime
import com.example.model.MarketStateFeatureVector
import com.example.model.MarketStateSnapshot
import com.example.model.MemorySafetyValidationResult
import com.example.model.NextCandleSignal
import com.example.model.OutcomeMemoryRecord
import com.example.model.PatternFingerprint
import com.example.model.PriceActionContext
import com.example.model.PriceDirection
import com.example.model.RecommendationState
import com.example.model.SetupQualityGrade
import com.example.model.VolatilityClassification

/**
 * 13 (Part 4) & 4 (Part 5). Pro Trader Brain Observation Record.
 */
data class BrainObservationRecord(
  val id: Long,
  val timestamp: Long,
  val recentCandleSequence: List<Candle>,
  val marketState: String,
  val marketRegime: MarketRegime = MarketRegime.UNCERTAIN,
  val priceActionContext: PriceActionContext = PriceActionContext(),
  val detectedPatterns: List<String> = emptyList(),
  val indicatorValues: Map<String, Double> = emptyMap(),
  val supportResistanceContext: String = "MID_RANGE",
  val momentumState: InternalDirectionState = InternalDirectionState.NEUTRAL,
  val volatilityState: VolatilityClassification = VolatilityClassification.NORMAL_VOLATILITY,
  val predictionContextPlaceholder: String = "WAIT_FOUNDATION",
  val futureOutcome: String? = null,
  val actualNextCandleBullish: Boolean? = null,
  val predictionResult: NextCandleSignal = NextCandleSignal.WAIT,
  val predictionAccuracy: Float? = null,
  val setupQuality: SetupQualityGrade = SetupQualityGrade.NO_SETUP,
  val dataQuality: ExtractionQuality = ExtractionQuality.HIGH,
  val featureVector: MarketStateFeatureVector = MarketStateFeatureVector(),
  val patternFingerprint: PatternFingerprint = PatternFingerprint()
)

/**
 * Internal staging slot for a completed setup awaiting the next completed candle
 * so that an immutable [OutcomeMemoryRecord] can be sealed once the outcome is known.
 */
private data class PendingOutcomeSetup(
  val timestamp: Long,
  val lastCandleTimestamp: Long,
  val setupSnapshot: MarketStateSnapshot,
  val patternFingerprint: PatternFingerprint,
  val predictedDirection: RecommendationState?
)

/**
 * 9 (Part 2), 13 (Part 4), & 4 + 9 (Part 5). Pro Trader Brain Memory.
 *
 * Local rolling observation and immutable outcome memory:
 * - Guards all writes with [MemorySafetyValidator] so corrupted, unreadable, duplicate,
 *   or impossible-OHLC data never contaminates historical memory.
 * - Seals immutable [OutcomeMemoryRecord] entries once the subsequent candle completes
 *   and NEVER modifies an [OutcomeMemoryRecord] after recording it.
 */
interface ProTraderBrainMemory {
  fun recordCompletedSequence(
    recentCandleSequence: List<Candle>,
    marketState: String = "OBSERVING",
    detectedPatterns: List<String> = emptyList(),
    indicatorValues: Map<String, Double> = emptyMap(),
    predictionResult: NextCandleSignal = NextCandleSignal.WAIT,
    timestamp: Long = System.currentTimeMillis(),
    marketRegime: MarketRegime = MarketRegime.UNCERTAIN,
    priceActionContext: PriceActionContext = PriceActionContext(),
    supportResistanceContext: String = "MID_RANGE",
    momentumState: InternalDirectionState = InternalDirectionState.NEUTRAL,
    volatilityState: VolatilityClassification = VolatilityClassification.NORMAL_VOLATILITY,
    setupQuality: SetupQualityGrade = SetupQualityGrade.NO_SETUP,
    dataQuality: ExtractionQuality = ExtractionQuality.HIGH,
    featureVector: MarketStateFeatureVector = MarketStateFeatureVector(),
    setupSnapshot: MarketStateSnapshot? = null,
    patternFingerprint: PatternFingerprint = PatternFingerprint(),
    predictedDirection: RecommendationState? = null,
    isScreenDetectionStable: Boolean = true
  ): MemorySafetyValidationResult

  fun getRecentObservations(limit: Int = 50): List<BrainObservationRecord>
  fun getObservationCount(): Int
  fun getOutcomeMemoryRecords(limit: Int = 200): List<OutcomeMemoryRecord>
  fun getOutcomeCount(): Int
  fun getRejectedWriteCount(): Int
  fun getLastValidationResult(): MemorySafetyValidationResult
  fun clear()
}

class DefaultProTraderBrainMemory(
  private val maxStoredObservations: Int = 200,
  private val maxOutcomeRecords: Int = 250,
  private val safetyValidator: MemorySafetyValidator = DefaultMemorySafetyValidator()
) : ProTraderBrainMemory {

  private val records = ArrayDeque<BrainObservationRecord>(maxStoredObservations)
  private val outcomeRecords = ArrayDeque<OutcomeMemoryRecord>(maxOutcomeRecords)
  private var pendingSetup: PendingOutcomeSetup? = null
  private var nextObservationId = 1L
  private var nextOutcomeId = 1L
  private var rejectedWrites = 0
  private var lastValidation = MemorySafetyValidationResult(isValidForMemory = true)

  @Synchronized
  override fun recordCompletedSequence(
    recentCandleSequence: List<Candle>,
    marketState: String,
    detectedPatterns: List<String>,
    indicatorValues: Map<String, Double>,
    predictionResult: NextCandleSignal,
    timestamp: Long,
    marketRegime: MarketRegime,
    priceActionContext: PriceActionContext,
    supportResistanceContext: String,
    momentumState: InternalDirectionState,
    volatilityState: VolatilityClassification,
    setupQuality: SetupQualityGrade,
    dataQuality: ExtractionQuality,
    featureVector: MarketStateFeatureVector,
    setupSnapshot: MarketStateSnapshot?,
    patternFingerprint: PatternFingerprint,
    predictedDirection: RecommendationState?,
    isScreenDetectionStable: Boolean
  ): MemorySafetyValidationResult {
    // 9. Memory Safety Check: reject corrupted, unreadable, duplicate, or invalid-timestamp sequences
    val validation = safetyValidator.validateSequence(
      candles = recentCandleSequence,
      dataQuality = dataQuality,
      isScreenDetectionStable = isScreenDetectionStable
    )
    lastValidation = validation
    if (!validation.isValidForMemory) {
      rejectedWrites++
      // Invalidate any pending setup so a gap or corrupted candle never links to a previous setup
      pendingSetup = null
      return validation
    }

    val newestCompleted = recentCandleSequence.last()

    // 4. Resolve pending setup into an IMMUTABLE OutcomeMemoryRecord (never modified after creation)
    val staged = pendingSetup
    if (staged != null &&
      newestCompleted.timestamp > staged.lastCandleTimestamp &&
      safetyValidator.isValidSingleCandle(newestCompleted)
    ) {
      val actualDir = when {
        newestCompleted.close > newestCompleted.open -> PriceDirection.UP
        newestCompleted.close < newestCompleted.open -> PriceDirection.DOWN
        else -> PriceDirection.FLAT
      }

      val wasCorrect: Boolean? = when (staged.predictedDirection) {
        RecommendationState.UP_CANDIDATE -> (actualDir == PriceDirection.UP)
        RecommendationState.DOWN_CANDIDATE -> (actualDir == PriceDirection.DOWN)
        RecommendationState.WAIT, null -> null
      }

      if (outcomeRecords.size >= maxOutcomeRecords) {
        outcomeRecords.removeFirst()
      }

      outcomeRecords.addLast(
        OutcomeMemoryRecord(
          id = nextOutcomeId++,
          timestamp = timestamp,
          setupSnapshot = staged.setupSnapshot,
          patternFingerprint = staged.patternFingerprint,
          predictedDirection = staged.predictedDirection,
          actualNextCandleDirection = actualDir,
          actualCandleBodySize = newestCompleted.bodySize,
          actualCandleRange = newestCompleted.totalRange,
          wasPredictionCorrect = wasCorrect,
          marketRegime = staged.setupSnapshot.marketRegime,
          dataQuality = staged.setupSnapshot.dataQuality,
          setupQuality = staged.setupSnapshot.setupQuality
        )
      )
    }

    // Update the previous BrainObservationRecord's outcome for Part 4 compatibility
    val previousRecord = records.lastOrNull()
    if (previousRecord != null && previousRecord.futureOutcome == null) {
      val outcomeLabel = if (newestCompleted.isBullish) "UP" else "DOWN"
      records[records.lastIndex] = previousRecord.copy(
        futureOutcome = outcomeLabel,
        actualNextCandleBullish = newestCompleted.isBullish
      )
    }

    if (records.size >= maxStoredObservations) {
      records.removeFirst()
    }

    val effectiveSnapshot = setupSnapshot ?: MarketStateSnapshot(
      timestamp = timestamp,
      recentCandleSequence = recentCandleSequence.takeLast(40),
      marketRegime = marketRegime,
      setupQuality = setupQuality,
      dataQuality = dataQuality,
      isScreenDetectionStable = isScreenDetectionStable
    )

    records.addLast(
      BrainObservationRecord(
        id = nextObservationId++,
        timestamp = timestamp,
        recentCandleSequence = recentCandleSequence.takeLast(40),
        marketState = marketState,
        marketRegime = marketRegime,
        priceActionContext = priceActionContext,
        detectedPatterns = detectedPatterns,
        indicatorValues = indicatorValues,
        supportResistanceContext = supportResistanceContext,
        momentumState = momentumState,
        volatilityState = volatilityState,
        predictionContextPlaceholder = "PART_5_BRAIN_READY",
        futureOutcome = null,
        actualNextCandleBullish = null,
        predictionResult = predictionResult,
        predictionAccuracy = null,
        setupQuality = setupQuality,
        dataQuality = dataQuality,
        featureVector = featureVector,
        patternFingerprint = patternFingerprint
      )
    )

    // Stage the current setup so its actual outcome will be sealed when the NEXT candle completes
    pendingSetup = PendingOutcomeSetup(
      timestamp = timestamp,
      lastCandleTimestamp = newestCompleted.timestamp,
      setupSnapshot = effectiveSnapshot,
      patternFingerprint = patternFingerprint,
      predictedDirection = predictedDirection
    )

    return validation
  }

  @Synchronized
  override fun getRecentObservations(limit: Int): List<BrainObservationRecord> {
    return records.takeLast(limit.coerceAtLeast(1))
  }

  @Synchronized
  override fun getObservationCount(): Int = records.size

  @Synchronized
  override fun getOutcomeMemoryRecords(limit: Int): List<OutcomeMemoryRecord> {
    return outcomeRecords.takeLast(limit.coerceAtLeast(1))
  }

  @Synchronized
  override fun getOutcomeCount(): Int = outcomeRecords.size

  @Synchronized
  override fun getRejectedWriteCount(): Int = rejectedWrites

  @Synchronized
  override fun getLastValidationResult(): MemorySafetyValidationResult = lastValidation

  @Synchronized
  override fun clear() {
    records.clear()
    outcomeRecords.clear()
    pendingSetup = null
    rejectedWrites = 0
    lastValidation = MemorySafetyValidationResult(isValidForMemory = true)
  }
}

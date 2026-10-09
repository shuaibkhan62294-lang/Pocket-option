package com.example.engine.prediction

import com.example.model.Candle
import com.example.model.ExtractionQuality
import com.example.model.FinalizedPredictionRecord
import com.example.model.MarketRegime
import com.example.model.NextCandlePrediction
import com.example.model.PredictionDirection
import com.example.model.PredictionLifecycleState
import com.example.model.PredictionOutcomeStatus
import com.example.model.PriceDirection
import com.example.model.SetupQualityGrade

/**
 * Internal staged forecast waiting for the predicted candle to close so that
 * an immutable [FinalizedPredictionRecord] can be sealed.
 */
private data class StagedPredictionEntry(
  val timestamp: Long,
  val baseCompletedCandleTimestamp: Long,
  val prediction: NextCandlePrediction,
  val marketRegime: MarketRegime
)

/**
 * 10. Prediction History Repository (Part 6 Requirement 10).
 *
 * Stores every finalized prediction with:
 * - Timestamp
 * - Direction
 * - Setup quality
 * - Data quality
 * - Market regime
 * - Evidence
 * - Actual next candle result
 * - Correct / Incorrect / No-valid-result
 *
 * Completed records are immutable and NEVER modified after recording.
 */
interface PredictionHistoryRepository {
  fun stageActivePrediction(
    prediction: NextCandlePrediction,
    marketRegime: MarketRegime,
    lastCompletedCandleTimestamp: Long
  )

  fun onCandleCompleted(completedCandle: Candle): FinalizedPredictionRecord?

  fun recordFinalizedPredictionDirect(
    timestamp: Long,
    direction: PredictionDirection,
    setupQuality: SetupQualityGrade,
    dataQuality: ExtractionQuality,
    marketRegime: MarketRegime,
    evidence: List<String>,
    actualNextCandleResult: PriceDirection?
  ): FinalizedPredictionRecord

  fun getFinalizedHistory(limit: Int = 100): List<FinalizedPredictionRecord>
  fun getTotalFinalizedCount(): Int
  fun clear()
}

class DefaultPredictionHistoryRepository(
  private val maxHistoryCapacity: Int = 250
) : PredictionHistoryRepository {

  private val records = ArrayDeque<FinalizedPredictionRecord>(maxHistoryCapacity)
  private var stagedEntry: StagedPredictionEntry? = null
  private var nextId = 1L

  @Synchronized
  override fun stageActivePrediction(
    prediction: NextCandlePrediction,
    marketRegime: MarketRegime,
    lastCompletedCandleTimestamp: Long
  ) {
    if (lastCompletedCandleTimestamp <= 0L) return
    val existing = stagedEntry
    // Update or stage for the current candle period
    if (existing == null || existing.baseCompletedCandleTimestamp == lastCompletedCandleTimestamp) {
      stagedEntry = StagedPredictionEntry(
        timestamp = prediction.timestamp,
        baseCompletedCandleTimestamp = lastCompletedCandleTimestamp,
        prediction = prediction,
        marketRegime = marketRegime
      )
    }
  }

  @Synchronized
  override fun onCandleCompleted(completedCandle: Candle): FinalizedPredictionRecord? {
    val staged = stagedEntry ?: return null
    if (completedCandle.timestamp <= staged.baseCompletedCandleTimestamp) {
      return null
    }
    stagedEntry = null

    val isCandleValid = completedCandle.qualityScore >= 0.45f &&
      completedCandle.open > 0.0 &&
      completedCandle.high >= maxOf(completedCandle.open, completedCandle.close) - 1e-9 &&
      completedCandle.low <= minOf(completedCandle.open, completedCandle.close) + 1e-9

    val actualResult = if (isCandleValid) completedCandle.direction else null

    return recordFinalizedPredictionDirect(
      timestamp = staged.timestamp,
      direction = if (staged.prediction.state == PredictionLifecycleState.ACTIVE) {
        staged.prediction.direction
      } else {
        PredictionDirection.NONE
      },
      setupQuality = staged.prediction.setupQuality,
      dataQuality = staged.prediction.dataQuality,
      marketRegime = staged.marketRegime,
      evidence = staged.prediction.evidence,
      actualNextCandleResult = actualResult
    )
  }

  @Synchronized
  override fun recordFinalizedPredictionDirect(
    timestamp: Long,
    direction: PredictionDirection,
    setupQuality: SetupQualityGrade,
    dataQuality: ExtractionQuality,
    marketRegime: MarketRegime,
    evidence: List<String>,
    actualNextCandleResult: PriceDirection?
  ): FinalizedPredictionRecord {
    val outcomeStatus = when {
      actualNextCandleResult == null ||
        dataQuality == ExtractionQuality.LOW ||
        dataQuality == ExtractionQuality.UNREADABLE ||
        direction == PredictionDirection.NONE ||
        actualNextCandleResult == PriceDirection.FLAT ->
        PredictionOutcomeStatus.NO_VALID_RESULT
      direction == PredictionDirection.UP && actualNextCandleResult == PriceDirection.UP ->
        PredictionOutcomeStatus.CORRECT
      direction == PredictionDirection.DOWN && actualNextCandleResult == PriceDirection.DOWN ->
        PredictionOutcomeStatus.CORRECT
      else ->
        PredictionOutcomeStatus.INCORRECT
    }

    if (records.size >= maxHistoryCapacity) {
      records.removeFirst()
    }

    val record = FinalizedPredictionRecord(
      id = nextId++,
      timestamp = timestamp,
      direction = direction,
      setupQuality = setupQuality,
      dataQuality = dataQuality,
      marketRegime = marketRegime,
      evidence = evidence.toList(),
      actualNextCandleResult = actualNextCandleResult,
      outcomeStatus = outcomeStatus
    )
    records.addLast(record)
    return record
  }

  @Synchronized
  override fun getFinalizedHistory(limit: Int): List<FinalizedPredictionRecord> {
    return records.takeLast(limit.coerceAtLeast(1))
  }

  @Synchronized
  override fun getTotalFinalizedCount(): Int = records.size

  @Synchronized
  override fun clear() {
    records.clear()
    stagedEntry = null
  }
}

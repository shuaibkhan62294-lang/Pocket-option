package com.example.engine.quality

import com.example.model.Candle
import com.example.model.ExtractionQuality
import com.example.model.NextCandleSignal

/**
 * Input parameters checked by [DataQualityEngine] before any future prediction (Requirement 8).
 */
data class DataQualityCheckInput(
  val isScreenCaptureActive: Boolean,
  val isChartRegionValid: Boolean,
  val isChartVisible: Boolean,
  val extractionQuality: ExtractionQuality,
  val isCandleDetectionStable: Boolean,
  val candles: List<Candle>,
  val currentCandle: Candle?,
  val isPriceMovementUpdating: Boolean,
  val minRequiredCandles: Int = 5,
  val wasCaptureRecentlyStopped: Boolean = false
)

/**
 * Result of the [DataQualityEngine] readiness audit.
 */
data class DataQualityEvaluation(
  val isReliableForAnalysis: Boolean,
  val qualityLevel: ExtractionQuality,
  val recommendedSignal: NextCandleSignal,
  val primaryReasonMessage: String,
  val dataQualityWord: String,
  val chartStatusBannerText: String
)

/**
 * 8. Data Quality Engine
 *
 * Verifies all 7 readiness conditions before any future prediction:
 * 1. chart is visible
 * 2. candle detection is stable
 * 3. enough candles exist
 * 4. current candle is readable
 * 5. price movement is updating
 * 6. screen capture is active
 * 7. timestamps are valid
 *
 * If requirements are not satisfied, enforces Prediction = WAIT with a clear beginner-friendly reason.
 */
interface DataQualityEngine {
  fun evaluateQuality(input: DataQualityCheckInput): DataQualityEvaluation
}

class DefaultDataQualityEngine : DataQualityEngine {

  override fun evaluateQuality(input: DataQualityCheckInput): DataQualityEvaluation {
    // 1. Check if screen capture stopped
    if (input.wasCaptureRecentlyStopped && !input.isScreenCaptureActive) {
      return DataQualityEvaluation(
        isReliableForAnalysis = false,
        qualityLevel = ExtractionQuality.UNREADABLE,
        recommendedSignal = NextCandleSignal.WAIT,
        primaryReasonMessage = "Screen capture stopped",
        dataQualityWord = "Stopped",
        chartStatusBannerText = "○ Screen Capture: OFF"
      )
    }

    // 2. Check if chart region is selected and valid
    if (!input.isChartRegionValid) {
      return DataQualityEvaluation(
        isReliableForAnalysis = false,
        qualityLevel = ExtractionQuality.UNREADABLE,
        recommendedSignal = NextCandleSignal.WAIT,
        primaryReasonMessage = "Please select the chart area again",
        dataQualityWord = "Waiting",
        chartStatusBannerText = "○ Chart Area Not Selected"
      )
    }

    // 3. Check if screen capture is active
    if (!input.isScreenCaptureActive) {
      return DataQualityEvaluation(
        isReliableForAnalysis = false,
        qualityLevel = ExtractionQuality.UNREADABLE,
        recommendedSignal = NextCandleSignal.WAIT,
        primaryReasonMessage = "Not enough reliable chart data",
        dataQualityWord = "Waiting",
        chartStatusBannerText = "○ Waiting for Screen Capture"
      )
    }

    // 4. Check if chart is visible and readable
    if (!input.isChartVisible || input.extractionQuality == ExtractionQuality.UNREADABLE) {
      return DataQualityEvaluation(
        isReliableForAnalysis = false,
        qualityLevel = ExtractionQuality.UNREADABLE,
        recommendedSignal = NextCandleSignal.WAIT,
        primaryReasonMessage = "Chart not readable",
        dataQualityWord = "Unreadable",
        chartStatusBannerText = "● Data Quality: Unreadable"
      )
    }

    // 5. If extraction quality is LOW, show "WAIT — Chart data unclear"
    if (input.extractionQuality == ExtractionQuality.LOW) {
      return DataQualityEvaluation(
        isReliableForAnalysis = false,
        qualityLevel = ExtractionQuality.LOW,
        recommendedSignal = NextCandleSignal.WAIT,
        primaryReasonMessage = "WAIT — Chart data unclear",
        dataQualityWord = "Low",
        chartStatusBannerText = "● Data Quality: Low"
      )
    }

    // 6. Verify enough candles exist
    if (input.candles.size < input.minRequiredCandles) {
      return DataQualityEvaluation(
        isReliableForAnalysis = false,
        qualityLevel = input.extractionQuality,
        recommendedSignal = NextCandleSignal.WAIT,
        primaryReasonMessage = "Collecting candle data...",
        dataQualityWord = "Calibrating",
        chartStatusBannerText = "● Collecting candle data..."
      )
    }

    // 7. Verify current candle readability, detection stability, price updates, and monotonic timestamps
    val currentReadable = input.currentCandle != null && input.currentCandle.qualityScore >= 0.50f
    val timestampsValid = areCandleTimestampsValid(input.candles)

    if (!input.isCandleDetectionStable || !currentReadable || !input.isPriceMovementUpdating || !timestampsValid) {
      return DataQualityEvaluation(
        isReliableForAnalysis = false,
        qualityLevel = ExtractionQuality.MEDIUM,
        recommendedSignal = NextCandleSignal.WAIT,
        primaryReasonMessage = "Not enough reliable chart data",
        dataQualityWord = "Syncing",
        chartStatusBannerText = "● Data Quality: Syncing"
      )
    }

    val qualityWord = if (input.extractionQuality == ExtractionQuality.HIGH) "Good" else "Medium"
    return DataQualityEvaluation(
      isReliableForAnalysis = true,
      qualityLevel = input.extractionQuality,
      recommendedSignal = NextCandleSignal.WAIT, // Part 2 never generates UP/DOWN signals yet
      primaryReasonMessage = "Live candle intelligence active — waiting for Part 3+ signal engine",
      dataQualityWord = qualityWord,
      chartStatusBannerText = "● Data Quality: $qualityWord"
    )
  }

  private fun areCandleTimestampsValid(candles: List<Candle>): Boolean {
    if (candles.isEmpty()) return false
    var previousTimestamp = -1L
    for (candle in candles) {
      if (candle.timestamp <= 0L || candle.timestamp < previousTimestamp) {
        return false
      }
      previousTimestamp = candle.timestamp
    }
    return true
  }
}

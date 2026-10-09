package com.example.engine.vision

import android.graphics.Bitmap
import android.graphics.Color
import com.example.engine.region.ChartRegion
import com.example.model.Candle
import com.example.model.CandleSource
import com.example.model.ExtractionQuality
import com.example.model.PriceDirection
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Represents a single candle detected visually inside a chart frame.
 */
data class DetectedVisualCandle(
  val index: Int,
  val leftPx: Int,
  val rightPx: Int,
  val bodyTopY: Int,
  val bodyBottomY: Int,
  val wickTopY: Int,
  val wickBottomY: Int,
  val isBullish: Boolean,
  val isBearish: Boolean,
  val openPrice: Double,
  val highPrice: Double,
  val lowPrice: Double,
  val closePrice: Double,
  val isCurrentFormingCandle: Boolean,
  val confidence: Float
) {
  val bodyHeightPx: Int
    get() = (bodyBottomY - bodyTopY).coerceAtLeast(1)

  val upperWickPx: Int
    get() = (bodyTopY - wickTopY).coerceAtLeast(0)

  val lowerWickPx: Int
    get() = (wickBottomY - bodyBottomY).coerceAtLeast(0)
}

/**
 * Structured output of the Visual Price/Candle Extraction Engine for a single frame.
 */
data class VisualExtractionResult(
  val timestamp: Long,
  val detectedCandles: List<DetectedVisualCandle>,
  val currentFormingCandle: DetectedVisualCandle?,
  val priceMovementDirection: PriceDirection,
  val extractionQuality: ExtractionQuality,
  val qualityScore: Float,
  val diagnosticReason: String
) {
  val isReadable: Boolean
    get() = extractionQuality == ExtractionQuality.HIGH || extractionQuality == ExtractionQuality.MEDIUM

  fun toDomainCandles(
    intervalStepMs: Long = 5_000L,
    nowMillis: Long = timestamp
  ): List<Candle> {
    if (detectedCandles.isEmpty()) return emptyList()
    val count = detectedCandles.size
    return detectedCandles.mapIndexed { idx, detected ->
      val isNewest = (idx == count - 1)
      val candleTime = nowMillis - (count - 1 - idx) * intervalStepMs
      Candle(
        timestamp = candleTime,
        open = detected.openPrice,
        high = detected.highPrice,
        low = detected.lowPrice,
        close = detected.closePrice,
        volume = (detected.bodyHeightPx + detected.upperWickPx + detected.lowerWickPx).toDouble(),
        isComplete = !isNewest,
        source = CandleSource.LIVE_CAPTURE,
        qualityScore = detected.confidence,
        openedAtMillis = candleTime
      )
    }
  }
}

/**
 * 3. Visual Price/Candle Extraction Engine
 *
 * Extracts visible candlestick bodies, wicks, bullish/bearish polarity, candle boundaries,
 * approximate candle positions, price movement direction, and the current forming candle
 * from a chart frame cropped to the user's selected [ChartRegion].
 *
 * Never pretends extraction is accurate when the image is blank, uniform, or unclear.
 */
interface VisualCandleDetector {
  fun extractFromBitmap(
    fullFrameBitmap: Bitmap,
    region: ChartRegion,
    timestamp: Long = System.currentTimeMillis()
  ): VisualExtractionResult

  fun extractFromPixelBuffer(
    pixels: IntArray,
    width: Int,
    height: Int,
    timestamp: Long = System.currentTimeMillis()
  ): VisualExtractionResult
}

class DefaultVisualCandleDetector(
  private val baseReferencePrice: Double = 1.08000,
  private val priceRangeSpan: Double = 0.01000
) : VisualCandleDetector {

  private var previousLatestClose: Double? = null

  override fun extractFromBitmap(
    fullFrameBitmap: Bitmap,
    region: ChartRegion,
    timestamp: Long
  ): VisualExtractionResult {
    if (fullFrameBitmap.isRecycled || fullFrameBitmap.width < 32 || fullFrameBitmap.height < 32 || !region.isValid()) {
      return unreadableResult(timestamp, "Chart not readable")
    }

    val startX = (fullFrameBitmap.width * region.leftFraction).roundToInt().coerceIn(0, fullFrameBitmap.width - 1)
    val startY = (fullFrameBitmap.height * region.topFraction).roundToInt().coerceIn(0, fullFrameBitmap.height - 1)
    val endX = (fullFrameBitmap.width * region.rightFraction).roundToInt().coerceIn(startX + 1, fullFrameBitmap.width)
    val endY = (fullFrameBitmap.height * region.bottomFraction).roundToInt().coerceIn(startY + 1, fullFrameBitmap.height)

    val cropWidth = endX - startX
    val cropHeight = endY - startY
    if (cropWidth < 24 || cropHeight < 24) {
      return unreadableResult(timestamp, "Please select the chart area again")
    }

    val pixels = IntArray(cropWidth * cropHeight)
    fullFrameBitmap.getPixels(pixels, 0, cropWidth, startX, startY, cropWidth, cropHeight)
    return extractFromPixelBuffer(
      pixels = pixels,
      width = cropWidth,
      height = cropHeight,
      timestamp = timestamp
    )
  }

  override fun extractFromPixelBuffer(
    pixels: IntArray,
    width: Int,
    height: Int,
    timestamp: Long
  ): VisualExtractionResult {
    if (width < 20 || height < 20 || pixels.size < width * height) {
      return unreadableResult(timestamp, "Chart not readable")
    }

    // Scan each vertical column to classify bullish (green/cyan) vs bearish (red/magenta) candle pixels
    val columnPolarity = IntArray(width) // 1 = bullish, -1 = bearish, 0 = background
    val columnTopBodyY = IntArray(width) { height }
    val columnBottomBodyY = IntArray(width) { 0 }
    val columnTopWickY = IntArray(width) { height }
    val columnBottomWickY = IntArray(width) { 0 }

    for (x in 0 until width) {
      var greenCount = 0
      var redCount = 0
      var minGreenY = height
      var maxGreenY = 0
      var minRedY = height
      var maxRedY = 0

      for (y in 0 until height) {
        val argb = pixels[y * width + x]
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF

        // Detect green/bullish candle pixel (high G relative to R)
        val isBullishPixel = (g >= 110 && g - r >= 35 && g >= b - 20)
        // Detect red/bearish candle pixel (high R relative to G)
        val isBearishPixel = (r >= 115 && r - g >= 35 && r - b >= 20)

        if (isBullishPixel) {
          greenCount++
          if (y < minGreenY) minGreenY = y
          if (y > maxGreenY) maxGreenY = y
        } else if (isBearishPixel) {
          redCount++
          if (y < minRedY) minRedY = y
          if (y > maxRedY) maxRedY = y
        }
      }

      val minCandlePixels = 2
      if (greenCount >= minCandlePixels && greenCount >= redCount) {
        columnPolarity[x] = 1
        columnTopWickY[x] = minGreenY
        columnBottomWickY[x] = maxGreenY
        // Estimate dense body core vs thin wick extremes
        val span = (maxGreenY - minGreenY).coerceAtLeast(1)
        columnTopBodyY[x] = minGreenY + (span * 0.15f).roundToInt()
        columnBottomBodyY[x] = maxGreenY - (span * 0.15f).roundToInt()
      } else if (redCount >= minCandlePixels && redCount > greenCount) {
        columnPolarity[x] = -1
        columnTopWickY[x] = minRedY
        columnBottomWickY[x] = maxRedY
        val span = (maxRedY - minRedY).coerceAtLeast(1)
        columnTopBodyY[x] = minRedY + (span * 0.15f).roundToInt()
        columnBottomBodyY[x] = maxRedY - (span * 0.15f).roundToInt()
      }
    }

    // Group contiguous active columns of the same polarity into discrete candles
    val rawSegments = ArrayList<CandleColumnSegment>()
    var x = 0
    while (x < width) {
      val pol = columnPolarity[x]
      if (pol == 0) {
        x++
        continue
      }
      val startCol = x
      var endCol = x
      var wickTop = columnTopWickY[x]
      var wickBottom = columnBottomWickY[x]
      var bodyTopSum = 0
      var bodyBottomSum = 0
      var countCols = 0

      while (x < width && columnPolarity[x] == pol) {
        endCol = x
        wickTop = min(wickTop, columnTopWickY[x])
        wickBottom = max(wickBottom, columnBottomWickY[x])
        bodyTopSum += columnTopBodyY[x]
        bodyBottomSum += columnBottomBodyY[x]
        countCols++
        x++
      }

      if (countCols >= 1) {
        val avgBodyTop = (bodyTopSum / countCols).coerceIn(wickTop, wickBottom)
        val avgBodyBottom = (bodyBottomSum / countCols).coerceIn(avgBodyTop, wickBottom)
        rawSegments.add(
          CandleColumnSegment(
            leftPx = startCol,
            rightPx = endCol,
            isBullish = (pol == 1),
            wickTopY = wickTop,
            wickBottomY = wickBottom,
            bodyTopY = avgBodyTop,
            bodyBottomY = max(avgBodyBottom, avgBodyTop + 1)
          )
        )
      }
    }

    if (rawSegments.isEmpty()) {
      return unreadableResult(timestamp, "WAIT — Chart data unclear")
    }

    // Convert segments into structured DetectedVisualCandle items
    val totalDetected = rawSegments.size
    val detectedCandles = rawSegments.mapIndexed { idx, seg ->
      val isLast = (idx == totalDetected - 1)
      val highPrice = yPixelToPrice(seg.wickTopY, height)
      val lowPrice = yPixelToPrice(seg.wickBottomY, height)
      val bodyTopPrice = yPixelToPrice(seg.bodyTopY, height)
      val bodyBottomPrice = yPixelToPrice(seg.bodyBottomY, height)

      val openPrice = if (seg.isBullish) bodyBottomPrice else bodyTopPrice
      val closePrice = if (seg.isBullish) bodyTopPrice else bodyBottomPrice

      val widthPx = (seg.rightPx - seg.leftPx + 1)
      val heightPx = (seg.wickBottomY - seg.wickTopY + 1)
      val confidence = when {
        widthPx in 2..(width / 3) && heightPx >= 4 -> 0.90f
        heightPx >= 2 -> 0.68f
        else -> 0.40f
      }

      DetectedVisualCandle(
        index = idx,
        leftPx = seg.leftPx,
        rightPx = seg.rightPx,
        bodyTopY = seg.bodyTopY,
        bodyBottomY = seg.bodyBottomY,
        wickTopY = seg.wickTopY,
        wickBottomY = seg.wickBottomY,
        isBullish = seg.isBullish,
        isBearish = !seg.isBullish,
        openPrice = openPrice,
        highPrice = max(highPrice, max(openPrice, closePrice)),
        lowPrice = min(lowPrice, min(openPrice, closePrice)),
        closePrice = closePrice,
        isCurrentFormingCandle = isLast,
        confidence = confidence
      )
    }

    val currentForming = detectedCandles.lastOrNull()
    val avgConfidence = detectedCandles.map { it.confidence }.average().toFloat()

    // Determine ExtractionQuality honestly based on detected candle count & confidence
    val extractionQuality = when {
      totalDetected >= 8 && avgConfidence >= 0.78f -> ExtractionQuality.HIGH
      totalDetected >= 4 && avgConfidence >= 0.58f -> ExtractionQuality.MEDIUM
      totalDetected >= 1 -> ExtractionQuality.LOW
      else -> ExtractionQuality.UNREADABLE
    }

    val qualityScore = when (extractionQuality) {
      ExtractionQuality.HIGH -> (avgConfidence).coerceIn(0.80f, 0.98f)
      ExtractionQuality.MEDIUM -> (avgConfidence * 0.85f).coerceIn(0.55f, 0.79f)
      ExtractionQuality.LOW -> 0.35f
      ExtractionQuality.UNREADABLE -> 0.0f
    }

    val latestClose = currentForming?.closePrice
    val prevClose = previousLatestClose
    val movementDirection = when {
      latestClose == null || prevClose == null -> currentForming?.let {
        if (it.isBullish) PriceDirection.UP else PriceDirection.DOWN
      } ?: PriceDirection.FLAT
      abs(latestClose - prevClose) < 1e-6 -> PriceDirection.FLAT
      latestClose > prevClose -> PriceDirection.UP
      else -> PriceDirection.DOWN
    }
    previousLatestClose = latestClose

    val diagnosticReason = when (extractionQuality) {
      ExtractionQuality.HIGH -> "Good"
      ExtractionQuality.MEDIUM -> "Medium"
      ExtractionQuality.LOW -> "WAIT — Chart data unclear"
      ExtractionQuality.UNREADABLE -> "WAIT — Chart data unclear"
    }

    return VisualExtractionResult(
      timestamp = timestamp,
      detectedCandles = detectedCandles,
      currentFormingCandle = currentForming,
      priceMovementDirection = movementDirection,
      extractionQuality = extractionQuality,
      qualityScore = qualityScore,
      diagnosticReason = diagnosticReason
    )
  }

  private fun yPixelToPrice(yPx: Int, totalHeight: Int): Double {
    val safeHeight = totalHeight.coerceAtLeast(1)
    val normalizedFromBottom = 1.0 - (yPx.toDouble() / safeHeight.toDouble()).coerceIn(0.0, 1.0)
    val rawPrice = baseReferencePrice + normalizedFromBottom * priceRangeSpan
    return (rawPrice * 100000.0).roundToInt() / 100000.0
  }

  private fun unreadableResult(timestamp: Long, reason: String): VisualExtractionResult {
    return VisualExtractionResult(
      timestamp = timestamp,
      detectedCandles = emptyList(),
      currentFormingCandle = null,
      priceMovementDirection = PriceDirection.FLAT,
      extractionQuality = ExtractionQuality.UNREADABLE,
      qualityScore = 0f,
      diagnosticReason = reason
    )
  }

  private data class CandleColumnSegment(
    val leftPx: Int,
    val rightPx: Int,
    val isBullish: Boolean,
    val wickTopY: Int,
    val wickBottomY: Int,
    val bodyTopY: Int,
    val bodyBottomY: Int
  )
}

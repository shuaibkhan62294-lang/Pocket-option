package com.example.engine.chart

import com.example.model.Candle
import com.example.model.ChartDataSourceMode
import com.example.model.LiveChartSyncState
import kotlin.math.max
import kotlin.math.min

/**
 * Immutable viewport calculation snapshot computed by [LiveChartEngine] for rendering
 * the live mirrored candlestick chart component.
 */
data class ChartViewportMetrics(
  val visibleCandles: List<Candle>,
  val startIndex: Int,
  val endIndexExclusive: Int,
  val minPrice: Double,
  val maxPrice: Double,
  val priceSpan: Double,
  val priceGridLevels: List<Double>,
  val latestPrice: Double?,
  val isLatestCandleBullish: Boolean
) {
  /**
   * Maps a price value to a normalized vertical ratio in [0f..1f],
   * where 0f is the top of the chart canvas and 1f is the bottom.
   */
  fun priceToNormalizedY(price: Double): Float {
    if (priceSpan <= 1e-9) return 0.5f
    val ratio = (maxPrice - price) / priceSpan
    return ratio.toFloat().coerceIn(0f, 1f)
  }
}

/**
 * 1. Chart Engine & 10. LiveChartEngine
 *
 * Responsible for computing price scale ranges, wick/body coordinates, grid levels,
 * visible window slicing (30–40 latest candles), and live synchronization status
 * for the mirrored candlestick chart.
 */
interface ChartEngine {
  fun computeViewportMetrics(
    allCandles: List<Candle>,
    visibleCapacity: Int = DEFAULT_VISIBLE_CANDLES,
    scrollOffsetFromLatest: Int = 0,
    priceLevelsCount: Int = 5
  ): ChartViewportMetrics

  fun formatPrice(price: Double): String

  companion object {
    const val DEFAULT_VISIBLE_CANDLES = 32
  }
}

interface LiveChartEngine : ChartEngine {
  fun resolveSyncState(
    dataSourceMode: ChartDataSourceMode,
    isScreenCaptureActive: Boolean,
    isDataReliable: Boolean,
    candleCount: Int
  ): LiveChartSyncState
}

class DefaultChartEngine : LiveChartEngine {

  override fun computeViewportMetrics(
    allCandles: List<Candle>,
    visibleCapacity: Int,
    scrollOffsetFromLatest: Int,
    priceLevelsCount: Int
  ): ChartViewportMetrics {
    if (allCandles.isEmpty()) {
      return ChartViewportMetrics(
        visibleCandles = emptyList(),
        startIndex = 0,
        endIndexExclusive = 0,
        minPrice = 1.08000,
        maxPrice = 1.09000,
        priceSpan = 0.01000,
        priceGridLevels = listOf(1.09000, 1.08750, 1.08500, 1.08250, 1.08000),
        latestPrice = null,
        isLatestCandleBullish = true
      )
    }

    val safeCapacity = visibleCapacity.coerceIn(10, 40)
    val safeOffset = scrollOffsetFromLatest.coerceIn(0, max(0, allCandles.size - 1))
    val endExclusive = (allCandles.size - safeOffset).coerceAtLeast(1)
    val start = max(0, endExclusive - safeCapacity)
    val visibleSlice = allCandles.subList(start, endExclusive)

    var rawMin = Double.MAX_VALUE
    var rawMax = -Double.MAX_VALUE
    for (candle in visibleSlice) {
      rawMin = min(rawMin, candle.low)
      rawMax = max(rawMax, candle.high)
    }

    val rawSpan = (rawMax - rawMin).coerceAtLeast(0.00020)
    val verticalPadding = rawSpan * 0.14
    val minPrice = rawMin - verticalPadding
    val maxPrice = rawMax + verticalPadding
    val totalSpan = (maxPrice - minPrice).coerceAtLeast(0.00025)

    val levels = ArrayList<Double>(priceLevelsCount)
    val steps = (priceLevelsCount - 1).coerceAtLeast(1)
    for (i in 0 until priceLevelsCount) {
      val fraction = i.toDouble() / steps.toDouble()
      levels.add(maxPrice - fraction * totalSpan)
    }

    val latestCandle = allCandles.lastOrNull()
    return ChartViewportMetrics(
      visibleCandles = visibleSlice,
      startIndex = start,
      endIndexExclusive = endExclusive,
      minPrice = minPrice,
      maxPrice = maxPrice,
      priceSpan = totalSpan,
      priceGridLevels = levels,
      latestPrice = latestCandle?.close,
      isLatestCandleBullish = latestCandle?.isBullish ?: true
    )
  }

  override fun resolveSyncState(
    dataSourceMode: ChartDataSourceMode,
    isScreenCaptureActive: Boolean,
    isDataReliable: Boolean,
    candleCount: Int
  ): LiveChartSyncState {
    return when {
      dataSourceMode == ChartDataSourceMode.DEMO_PLACEHOLDER && candleCount > 0 -> {
        LiveChartSyncState.DEMO_PREVIEW
      }
      isScreenCaptureActive && isDataReliable && candleCount >= 5 -> {
        LiveChartSyncState.LIVE
      }
      isScreenCaptureActive -> {
        LiveChartSyncState.SYNCING
      }
      else -> {
        LiveChartSyncState.WAITING
      }
    }
  }

  override fun formatPrice(price: Double): String {
    return String.format(java.util.Locale.US, "%.5f", price)
  }
}

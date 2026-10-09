package com.example.engine.region

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Normalized rectangular region (0.0f..1.0f) defining the area containing only
 * the visible chart on screen.
 *
 * Processing exclusively this region protects user privacy (ignores top status bars,
 * account balances, payment headers, or bottom trade execution panels) and minimizes CPU/battery usage.
 */
data class ChartRegion(
  val leftFraction: Float,
  val topFraction: Float,
  val rightFraction: Float,
  val bottomFraction: Float,
  val label: String = "Chart Area"
) {
  val widthFraction: Float
    get() = (rightFraction - leftFraction).coerceAtLeast(0f)

  val heightFraction: Float
    get() = (bottomFraction - topFraction).coerceAtLeast(0f)

  fun isValid(): Boolean {
    return leftFraction in 0f..0.85f &&
      topFraction in 0f..0.85f &&
      rightFraction in 0.15f..1f &&
      bottomFraction in 0.15f..1f &&
      widthFraction >= 0.20f &&
      heightFraction >= 0.18f
  }

  companion object {
    /**
     * Beginner-friendly default chart area covering the central candlestick chart region
     * while excluding top account bars and bottom control panels.
     */
    val DEFAULT_CENTER_CHART_REGION = ChartRegion(
      leftFraction = 0.05f,
      topFraction = 0.18f,
      rightFraction = 0.92f,
      bottomFraction = 0.72f,
      label = "Standard Chart Area"
    )

    val UPPER_SPLIT_CHART_REGION = ChartRegion(
      leftFraction = 0.04f,
      topFraction = 0.12f,
      rightFraction = 0.94f,
      bottomFraction = 0.52f,
      label = "Top Half Chart Area"
    )

    val WIDE_LANDSCAPE_CHART_REGION = ChartRegion(
      leftFraction = 0.08f,
      topFraction = 0.14f,
      rightFraction = 0.86f,
      bottomFraction = 0.82f,
      label = "Full Chart Canvas"
    )
  }
}

/**
 * 2. Chart Region Manager
 *
 * Stores and validates the user-selected chart region locally for the current session.
 * Supports Select Chart Area, Re-select Chart Area, and Clear Selection.
 */
interface ChartRegionManager {
  val selectedRegion: StateFlow<ChartRegion?>
  val isRegionValid: StateFlow<Boolean>

  fun selectChartRegion(region: ChartRegion = ChartRegion.DEFAULT_CENTER_CHART_REGION)
  fun reselectChartRegion(region: ChartRegion)
  fun clearSelection()
  fun hasValidSelection(): Boolean
}

class DefaultChartRegionManager : ChartRegionManager {
  private val _selectedRegion = MutableStateFlow<ChartRegion?>(null)
  override val selectedRegion: StateFlow<ChartRegion?> = _selectedRegion.asStateFlow()

  private val _isRegionValid = MutableStateFlow(false)
  override val isRegionValid: StateFlow<Boolean> = _isRegionValid.asStateFlow()

  override fun selectChartRegion(region: ChartRegion) {
    _selectedRegion.value = region
    _isRegionValid.value = region.isValid()
  }

  override fun reselectChartRegion(region: ChartRegion) {
    _selectedRegion.value = region
    _isRegionValid.value = region.isValid()
  }

  override fun clearSelection() {
    _selectedRegion.value = null
    _isRegionValid.value = false
  }

  override fun hasValidSelection(): Boolean {
    return _selectedRegion.value?.isValid() == true
  }
}

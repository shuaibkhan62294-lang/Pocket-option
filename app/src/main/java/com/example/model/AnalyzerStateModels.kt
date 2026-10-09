package com.example.model

/**
 * Represents the user-controlled state of the OTC Vision AI analyzer.
 */
enum class AnalyzerRunState {
  OFF,
  ACTIVE,
  PAUSED
}

/**
 * High-level beginner-friendly signal states for the NEXT CANDLE card.
 * Note: In Part 1 and Part 2, no UP or DOWN predictions are generated;
 * the card remains in WAIT / WAITING state.
 */
enum class NextCandleSignal {
  WAITING,
  UP,
  DOWN,
  WAIT
}

/**
 * Indicates the source of chart data.
 */
enum class ChartDataSourceMode {
  EMPTY_WAITING,
  LIVE_CAPTURE,
  DEMO_PLACEHOLDER
}

/**
 * Data quality state returned by the Visual Candle Extraction & Data Quality engines.
 */
enum class ExtractionQuality {
  HIGH,
  MEDIUM,
  LOW,
  UNREADABLE
}

/**
 * Live mirrored chart synchronization status.
 */
enum class LiveChartSyncState {
  WAITING,
  SYNCING,
  LIVE,
  DEMO_PREVIEW
}

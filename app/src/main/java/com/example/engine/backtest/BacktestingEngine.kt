package com.example.engine.backtest

import com.example.model.Candle

/**
 * Internal summary for historical rolling-window evaluation in Part 7+.
 */
data class BacktestReport(
  val evaluatedCandlesCount: Int = 0,
  val isReady: Boolean = false
)

/**
 * 8. Backtesting Engine
 *
 * Foundation module for Part 7+ rolling-candle historical evaluation.
 */
interface BacktestingEngine {
  fun runRollingEvaluation(candles: List<Candle>): BacktestReport
}

class DefaultBacktestingEngine : BacktestingEngine {
  override fun runRollingEvaluation(candles: List<Candle>): BacktestReport {
    return BacktestReport(
      evaluatedCandlesCount = candles.size,
      isReady = false
    )
  }
}

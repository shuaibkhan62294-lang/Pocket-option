package com.example.engine.prediction

import com.example.engine.movement.PriceMicroStructureSnapshot
import com.example.model.Candle
import com.example.model.CurrentCandleMonitoringState
import com.example.model.InternalDirectionState
import com.example.model.MarketStructureAnalysis
import com.example.model.MarketStructureType
import com.example.model.PriceDirection
import com.example.model.SupportResistanceAnalysis
import kotlin.math.abs
import kotlin.math.min

/**
 * 2 & 7. Current Candle Behavior & Early Warning Monitor (Part 6 Requirements 2 & 7).
 *
 * Continuously monitors the active forming candle:
 * - Current price position in range
 * - Body & wick development
 * - Intra-candle momentum, acceleration, and deceleration
 * - Normalized distance to support and resistance
 * - Alignment with trend structure
 * - Developing candlestick pattern formation
 * - Early forecast window readiness before candle completion
 */
interface CurrentCandleMonitor {
  fun monitorCurrentCandle(
    candles: List<Candle>,
    supportResistance: SupportResistanceAnalysis,
    marketStructure: MarketStructureAnalysis,
    microStructure: PriceMicroStructureSnapshot = PriceMicroStructureSnapshot()
  ): CurrentCandleMonitoringState
}

class DefaultCurrentCandleMonitor : CurrentCandleMonitor {

  override fun monitorCurrentCandle(
    candles: List<Candle>,
    supportResistance: SupportResistanceAnalysis,
    marketStructure: MarketStructureAnalysis,
    microStructure: PriceMicroStructureSnapshot
  ): CurrentCandleMonitoringState {
    if (candles.isEmpty()) {
      return CurrentCandleMonitoringState(isBehaviorStable = false, isEarlyForecastReady = false)
    }

    val current = candles.last()
    val completedPool = candles.dropLast(1).takeLast(min(25, (candles.size - 1).coerceAtLeast(1)))
    val avgBody = if (completedPool.isNotEmpty()) {
      completedPool.map { it.bodySize }.average().coerceAtLeast(1e-7)
    } else {
      current.bodySize.coerceAtLeast(1e-7)
    }
    val avgRange = if (completedPool.isNotEmpty()) {
      completedPool.map { it.totalRange }.average().coerceAtLeast(1e-7)
    } else {
      current.totalRange.coerceAtLeast(1e-7)
    }

    val range = current.totalRange.coerceAtLeast(1e-7)
    val pricePosInRange = ((current.close - current.low) / range).coerceIn(0.0, 1.0)
    val bodyDevRatio = (current.bodySize / avgBody).coerceIn(0.0, 5.0)
    val bodyToRange = (current.bodySize / range).coerceIn(0.0, 1.0)
    val upperWickRatio = (current.upperWick / range).coerceIn(0.0, 1.0)
    val lowerWickRatio = (current.lowerWick / range).coerceIn(0.0, 1.0)

    val dir = current.direction
    val hasStrongOpposingWick = when (dir) {
      PriceDirection.UP -> upperWickRatio >= 0.48 && current.upperWick > current.bodySize * 1.15
      PriceDirection.DOWN -> lowerWickRatio >= 0.48 && current.lowerWick > current.bodySize * 1.15
      PriceDirection.FLAT -> upperWickRatio >= 0.45 && lowerWickRatio >= 0.45
    }

    val distToSupport = supportResistance.nearestSupport?.let { sup ->
      abs(current.close - sup.levelPrice) / avgRange
    }
    val distToResistance = supportResistance.nearestResistance?.let { res ->
      abs(res.levelPrice - current.close) / avgRange
    }

    val alignedWithStructure = when (marketStructure.structureType) {
      MarketStructureType.UPTREND_STRUCTURE -> dir == PriceDirection.UP
      MarketStructureType.DOWNTREND_STRUCTURE -> dir == PriceDirection.DOWN
      else -> dir != PriceDirection.FLAT && bodyToRange >= 0.40
    }

    val prev = if (candles.size >= 2) candles[candles.size - 2] else null
    val developingPatternHint = when {
      prev != null && prev.isBearish && current.isBullish &&
        current.close >= prev.open && current.open <= prev.close -> "Forming Bullish Engulfing"
      prev != null && prev.isBullish && current.isBearish &&
        current.open >= prev.close && current.close <= prev.open -> "Forming Bearish Engulfing"
      lowerWickRatio >= 0.58 && upperWickRatio <= 0.20 && current.totalRange >= avgRange * 0.6 ->
        "Forming Lower Rejection / Hammer"
      upperWickRatio >= 0.58 && lowerWickRatio <= 0.20 && current.totalRange >= avgRange * 0.6 ->
        "Forming Upper Rejection / Shooting Star"
      bodyToRange >= 0.80 && bodyDevRatio >= 1.25 ->
        "Forming Marubozu Impulse"
      bodyToRange <= 0.14 && current.totalRange >= avgRange * 0.45 ->
        "Forming Doji Indecision"
      else -> null
    }

    val intraMomentum = if (microStructure.recentTicksCount >= 2) {
      microStructure.momentum
    } else {
      current.close - current.open
    }

    val isAccelerating = microStructure.isAccelerating || (bodyDevRatio >= 1.25 && bodyToRange >= 0.65)
    val isDecelerating = microStructure.isDecelerating || hasStrongOpposingWick

    // Behavior is unstable if there is a violent micro-reversal, extreme whip-saw wicks on both sides,
    // or an abnormally huge spike (> 3.2x average range)
    val isWhipSawCandle = upperWickRatio >= 0.38 && lowerWickRatio >= 0.38 && bodyToRange <= 0.24
    val isExtremeSpike = current.totalRange > avgRange * 3.2
    val isBehaviorStable = !microStructure.hasRapidReversal && !isWhipSawCandle && !isExtremeSpike

    // Early forecast readiness (Requirement 7): sufficient information exists before candle closes
    // when at least 20 candles exist, current candle behavior is stable, and body or clear rejection has developed
    val isEarlyReady = candles.size >= 20 &&
      isBehaviorStable &&
      (bodyDevRatio >= 0.45 || developingPatternHint != null) &&
      !hasStrongOpposingWick

    return CurrentCandleMonitoringState(
      currentPrice = current.close,
      pricePositionInRange = pricePosInRange,
      bodyDevelopmentRatio = bodyDevRatio,
      bodyToRangeRatio = bodyToRange,
      upperWickRatio = upperWickRatio,
      lowerWickRatio = lowerWickRatio,
      direction = dir,
      intraCandleMomentum = intraMomentum,
      isAccelerating = isAccelerating,
      isDecelerating = isDecelerating,
      hasStrongOpposingWick = hasStrongOpposingWick,
      distanceToSupportNormalized = distToSupport,
      distanceToResistanceNormalized = distToResistance,
      isAlignedWithTrendStructure = alignedWithStructure,
      developingPatternHint = developingPatternHint,
      isBehaviorStable = isBehaviorStable,
      isEarlyForecastReady = isEarlyReady
    )
  }
}

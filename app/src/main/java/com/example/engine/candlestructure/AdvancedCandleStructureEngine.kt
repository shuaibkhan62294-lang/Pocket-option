package com.example.engine.candlestructure

import com.example.engine.movement.PriceMicroStructureSnapshot
import com.example.model.Candle
import com.example.model.CandleBehaviorClassification
import com.example.model.CandleRangeExpansionState
import com.example.model.CandleSrPosition
import com.example.model.CandleStructureMetrics
import com.example.model.PriceDirection
import com.example.model.RelativeCandlePosition
import com.example.model.SupportResistanceAnalysis
import com.example.model.WickDominance
import kotlin.math.abs
import kotlin.math.min

/**
 * 1. Advanced Candle Structure Engine (Part 4 Requirement 1).
 *
 * Calculates comprehensive structural geometry and behavioral classifications
 * for every detected candle in the latest 30–40 candle window.
 */
interface AdvancedCandleStructureEngine {
  fun analyzeCandles(
    candles: List<Candle>,
    supportResistance: SupportResistanceAnalysis = SupportResistanceAnalysis(),
    microStructure: PriceMicroStructureSnapshot = PriceMicroStructureSnapshot()
  ): List<CandleStructureMetrics>
}

class DefaultAdvancedCandleStructureEngine : AdvancedCandleStructureEngine {

  override fun analyzeCandles(
    candles: List<Candle>,
    supportResistance: SupportResistanceAnalysis,
    microStructure: PriceMicroStructureSnapshot
  ): List<CandleStructureMetrics> {
    if (candles.isEmpty()) return emptyList()

    val window = candles.takeLast(40)
    val avgBody = window.map { it.bodySize }.average().coerceAtLeast(1e-7)
    val avgRange = window.map { it.totalRange }.average().coerceAtLeast(1e-7)
    val recent20 = window.takeLast(min(20, window.size))
    val recentHigh = recent20.maxOf { it.high }
    val recentLow = recent20.minOf { it.low }
    val recentSpan = (recentHigh - recentLow).coerceAtLeast(1e-7)

    return window.mapIndexed { idx, candle ->
      val prev = if (idx > 0) window[idx - 1] else null
      val isLatest = (idx == window.lastIndex)

      val range = candle.totalRange.coerceAtLeast(1e-7)
      val bodyRatio = (candle.bodySize / range).coerceIn(0.0, 1.0)
      val relativeBodyStrength = candle.bodySize / avgBody

      val wickDom = when {
        candle.upperWick <= range * 0.12 && candle.lowerWick <= range * 0.12 ->
          WickDominance.MINIMAL_WICKS
        candle.upperWick > candle.lowerWick * 1.65 && candle.upperWick > range * 0.25 ->
          WickDominance.UPPER_WICK_DOMINANT
        candle.lowerWick > candle.upperWick * 1.65 && candle.lowerWick > range * 0.25 ->
          WickDominance.LOWER_WICK_DOMINANT
        else -> WickDominance.BALANCED_WICKS
      }

      val expansionState = when {
        candle.totalRange > avgRange * 1.35 -> CandleRangeExpansionState.EXPANDING
        candle.totalRange < avgRange * 0.65 -> CandleRangeExpansionState.CONTRACTING
        else -> CandleRangeExpansionState.NORMAL
      }

      val relToPrev = if (prev == null) {
        RelativeCandlePosition.NEUTRAL_OVERLAP
      } else {
        when {
          candle.high <= prev.high && candle.low >= prev.low -> RelativeCandlePosition.INSIDE_BAR
          candle.bodyTop >= prev.bodyTop && candle.bodyBottom <= prev.bodyBottom &&
            candle.bodySize > prev.bodySize -> RelativeCandlePosition.OUTSIDE_ENGULFING
          candle.close > prev.close -> RelativeCandlePosition.HIGHER_CLOSE
          candle.close < prev.close -> RelativeCandlePosition.LOWER_CLOSE
          else -> RelativeCandlePosition.NEUTRAL_OVERLAP
        }
      }

      val srPos = resolveSrPosition(candle, supportResistance, avgRange)
      val posInRecentRange = ((candle.close - recentLow) / recentSpan).coerceIn(0.0, 1.0)

      val durationSec = (candle.elapsedTimeMs() / 1000.0).coerceAtLeast(1.0)
      val speedPerSec = if (isLatest && microStructure.recentTicksCount > 0) {
        abs(microStructure.momentum) / durationSec
      } else {
        candle.totalRange / 5.0
      }

      val classifications = classifyBehavior(
        candle = candle,
        bodyRatio = bodyRatio,
        relativeBodyStrength = relativeBodyStrength,
        wickDom = wickDom,
        expansionState = expansionState,
        srPos = srPos,
        supportResistance = supportResistance,
        window = window,
        idx = idx
      )

      CandleStructureMetrics(
        index = idx,
        open = candle.open,
        high = candle.high,
        low = candle.low,
        close = candle.close,
        bodySize = candle.bodySize,
        upperWick = candle.upperWick,
        lowerWick = candle.lowerWick,
        totalRange = candle.totalRange,
        bodyToRangeRatio = bodyRatio,
        direction = candle.direction,
        relativeBodyStrength = relativeBodyStrength,
        wickDominance = wickDom,
        expansionState = expansionState,
        positionRelativeToPrevious = relToPrev,
        positionRelativeToSr = srPos,
        positionInRecentRange = posInRecentRange,
        candleSpeedPerSecond = speedPerSec,
        primaryClassification = classifications.first(),
        secondaryClassifications = classifications.drop(1)
      )
    }
  }

  private fun resolveSrPosition(
    candle: Candle,
    sr: SupportResistanceAnalysis,
    avgRange: Double
  ): CandleSrPosition {
    val tol = avgRange * 0.45
    val res = sr.nearestResistance
    val sup = sr.nearestSupport

    if (res != null) {
      if (candle.close > res.upperBound + tol * 0.25 && candle.open <= res.upperBound) {
        return CandleSrPosition.BREAKING_RESISTANCE
      }
      if (abs(candle.high - res.levelPrice) <= tol || abs(candle.close - res.levelPrice) <= tol) {
        return CandleSrPosition.AT_RESISTANCE
      }
    }

    if (sup != null) {
      if (candle.close < sup.lowerBound - tol * 0.25 && candle.open >= sup.lowerBound) {
        return CandleSrPosition.BREAKING_SUPPORT
      }
      if (abs(candle.low - sup.levelPrice) <= tol || abs(candle.close - sup.levelPrice) <= tol) {
        return CandleSrPosition.AT_SUPPORT
      }
    }

    return CandleSrPosition.MID_RANGE
  }

  private fun classifyBehavior(
    candle: Candle,
    bodyRatio: Double,
    relativeBodyStrength: Double,
    wickDom: WickDominance,
    expansionState: CandleRangeExpansionState,
    srPos: CandleSrPosition,
    supportResistance: SupportResistanceAnalysis,
    window: List<Candle>,
    idx: Int
  ): List<CandleBehaviorClassification> {
    val tags = ArrayList<CandleBehaviorClassification>()

    // 1. Check Fake Breakout
    if (idx == window.lastIndex && (supportResistance.possibleFakeBreakout || supportResistance.fakeBreakdown)) {
      tags.add(CandleBehaviorClassification.FAKE_BREAKOUT)
    }

    // 2. Check Breakout Attempt
    if (srPos == CandleSrPosition.BREAKING_RESISTANCE || srPos == CandleSrPosition.BREAKING_SUPPORT) {
      tags.add(CandleBehaviorClassification.BREAKOUT_ATTEMPT)
    }

    // 3. Check Doji / Indecision
    if (bodyRatio <= 0.16) {
      tags.add(CandleBehaviorClassification.DOJI_INDECISION)
    }

    // 4. Check Wick Rejection
    if ((wickDom == WickDominance.UPPER_WICK_DOMINANT || wickDom == WickDominance.LOWER_WICK_DOMINANT) &&
      bodyRatio < 0.45
    ) {
      tags.add(CandleBehaviorClassification.REJECTION)
    }

    // 5. Check Exhaustion (after 3+ same-direction candles)
    if (idx >= 3) {
      val prior3 = window.subList(idx - 3, idx)
      val bullRun = prior3.all { it.isBullish }
      val bearRun = prior3.all { it.isBearish }
      if ((bullRun && (wickDom == WickDominance.UPPER_WICK_DOMINANT || relativeBodyStrength < 0.65)) ||
        (bearRun && (wickDom == WickDominance.LOWER_WICK_DOMINANT || relativeBodyStrength < 0.65))
      ) {
        tags.add(CandleBehaviorClassification.EXHAUSTION)
      }
    }

    // 6. Check Momentum Impulse
    if (relativeBodyStrength >= 1.35 && bodyRatio >= 0.68) {
      tags.add(CandleBehaviorClassification.MOMENTUM)
    }

    // 7. Check Consolidation
    if (expansionState == CandleRangeExpansionState.CONTRACTING && relativeBodyStrength < 0.70) {
      tags.add(CandleBehaviorClassification.CONSOLIDATION)
    }

    // 8. Directional Bullish / Bearish strength
    if (candle.direction == PriceDirection.UP) {
      if (relativeBodyStrength >= 1.15 && bodyRatio >= 0.58) {
        tags.add(CandleBehaviorClassification.STRONG_BULLISH)
      } else {
        tags.add(CandleBehaviorClassification.WEAK_BULLISH)
      }
    } else if (candle.direction == PriceDirection.DOWN) {
      if (relativeBodyStrength >= 1.15 && bodyRatio >= 0.58) {
        tags.add(CandleBehaviorClassification.STRONG_BEARISH)
      } else {
        tags.add(CandleBehaviorClassification.WEAK_BEARISH)
      }
    } else {
      tags.add(CandleBehaviorClassification.DOJI_INDECISION)
    }

    return tags.distinct()
  }
}

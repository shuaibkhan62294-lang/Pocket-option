package com.example.engine.sequence

import com.example.model.Candle
import com.example.model.CandleSequenceFeatures
import com.example.model.InternalDirectionState
import com.example.model.InternalStrengthLabel
import com.example.model.MultiTimeframeState
import kotlin.math.abs
import kotlin.math.min

/**
 * 1 & 8. Multi-Timeframe & Candle Sequence Intelligence Engine (Parts 2, 3, & 4).
 *
 * Analyzes sequences of 5, 10, 20, 30, 40 (and up to 100) candles to detect:
 * - Consecutive bullish / bearish candles
 * - Alternating candles
 * - Increasing / decreasing body size
 * - Increasing wick size
 * - Momentum acceleration / deceleration
 * - Exhaustion (e.g., consecutive bullish candles + shrinking bodies + increasing upper wicks)
 * - Compression & expansion
 * - Repeated rejection & repeated continuation
 * - Failed reversal attempts
 */
interface CandleSequenceEngine {
  fun updateSequence(candles: List<Candle>)
  fun getWindow(windowSize: Int): List<Candle>
  fun getAllSupportedWindows(): Map<Int, List<Candle>>
  fun analyzeSequenceFeatures(
    candles: List<Candle> = emptyList(),
    isNearResistance: Boolean = false,
    isNearSupport: Boolean = false
  ): CandleSequenceFeatures
  fun getSequenceStatusWord(): String
  fun clear()

  companion object {
    val SUPPORTED_WINDOWS = listOf(5, 10, 20, 30, 40, 50, 100)
    val ANALYSIS_WINDOWS = listOf(5, 10, 20, 30, 40)
  }
}

class DefaultCandleSequenceEngine : CandleSequenceEngine {

  private var latestCandles: List<Candle> = emptyList()
  private var lastCacheKey: String = ""
  private var cachedFeatures: CandleSequenceFeatures = CandleSequenceFeatures()

  @Synchronized
  override fun updateSequence(candles: List<Candle>) {
    latestCandles = candles.takeLast(CandleSequenceEngine.SUPPORTED_WINDOWS.last())
  }

  @Synchronized
  override fun getWindow(windowSize: Int): List<Candle> {
    val safeWindow = windowSize.coerceAtLeast(1)
    return latestCandles.takeLast(safeWindow)
  }

  @Synchronized
  override fun getAllSupportedWindows(): Map<Int, List<Candle>> {
    val current = latestCandles
    return CandleSequenceEngine.SUPPORTED_WINDOWS.associateWith { window ->
      current.takeLast(window)
    }
  }

  @Synchronized
  override fun analyzeSequenceFeatures(
    candles: List<Candle>,
    isNearResistance: Boolean,
    isNearSupport: Boolean
  ): CandleSequenceFeatures {
    val source = if (candles.isNotEmpty()) candles else latestCandles
    if (source.size < 5) {
      return CandleSequenceFeatures()
    }

    val latest = source.last()
    val cacheKey = "${source.size}:${latest.timestamp}:${latest.close}:${latest.high}:${latest.low}:$isNearResistance:$isNearSupport"
    if (cacheKey == lastCacheKey) {
      return cachedFeatures
    }

    val avgRange = source.takeLast(min(40, source.size)).map { it.totalRange }.average().coerceAtLeast(1e-6)

    // Multi-Timeframe Analysis across 5, 10, 20, 30, 40 candle windows
    val dir5 = evaluateWindowDirection(source.takeLast(5), avgRange)
    val dir10 = evaluateWindowDirection(source.takeLast(min(10, source.size)), avgRange)
    val dir20 = evaluateWindowDirection(source.takeLast(min(20, source.size)), avgRange)
    val dir30 = evaluateWindowDirection(source.takeLast(min(30, source.size)), avgRange)
    val dir40 = evaluateWindowDirection(source.takeLast(min(40, source.size)), avgRange)

    val shortTerm = dir5
    val mediumTerm = if (dir10 == dir20) dir10 else dir20
    val overall = if (dir30 == dir40) dir40 else dir30

    val allDirs = listOf(dir5, dir10, dir20, dir30, dir40)
    val bullishCount = allDirs.count { it == InternalDirectionState.BULLISH }
    val bearishCount = allDirs.count { it == InternalDirectionState.BEARISH }

    val hasReversalPossibility = (overall == InternalDirectionState.BULLISH && shortTerm == InternalDirectionState.BEARISH) ||
      (overall == InternalDirectionState.BEARISH && shortTerm == InternalDirectionState.BULLISH) ||
      shortTerm == InternalDirectionState.REVERSAL_RISK

    val isConsolidating = allDirs.count {
      it == InternalDirectionState.CONSOLIDATING || it == InternalDirectionState.NEUTRAL
    } >= 3

    val trendStrength = when {
      bullishCount >= 4 || bearishCount >= 4 -> InternalStrengthLabel.STRONG
      bullishCount == 3 || bearishCount == 3 -> InternalStrengthLabel.MODERATE
      bullishCount >= 2 && bearishCount >= 2 -> InternalStrengthLabel.CONFLICTING
      else -> InternalStrengthLabel.WEAK
    }

    // Count consecutive bullish / bearish candles at tail
    var consecutiveBull = 0
    var consecutiveBear = 0
    for (i in source.indices.reversed()) {
      val c = source[i]
      if (c.isBullish) {
        if (consecutiveBear > 0) break
        consecutiveBull++
      } else {
        if (consecutiveBull > 0) break
        consecutiveBear++
      }
    }

    val r5 = source.takeLast(5)
    val r3 = source.takeLast(3)

    // Increasing / decreasing body size over recent 3 candles
    val increasingBodies = r3.size == 3 &&
      r3[2].bodySize > r3[1].bodySize * 1.05 &&
      r3[1].bodySize >= r3[0].bodySize * 0.95
    val decreasingBodies = r3.size == 3 &&
      r3[2].bodySize < r3[1].bodySize * 0.90 &&
      r3[1].bodySize <= r3[0].bodySize * 1.05

    // Increasing wick size over recent 3 candles
    val wicks3 = r3.map { it.upperWick + it.lowerWick }
    val increasingWicks = wicks3.size == 3 && wicks3[2] > wicks3[1] && wicks3[1] >= wicks3[0]
    val increasingUpperWicks = r3.size == 3 && r3[2].upperWick > r3[1].upperWick && r3[2].upperWick > avgRange * 0.25
    val increasingLowerWicks = r3.size == 3 && r3[2].lowerWick > r3[1].lowerWick && r3[2].lowerWick > avgRange * 0.25

    val recent3AvgBody = r3.map { it.bodySize }.average()
    val prior5AvgBody = source.dropLast(3).takeLast(5).map { it.bodySize }.average().coerceAtLeast(1e-7)
    val momAccel = increasingBodies || recent3AvgBody > prior5AvgBody * 1.25
    val momDecel = decreasingBodies || recent3AvgBody < prior5AvgBody * 0.75

    // Part 4 Requirement 8 specific exhaustion pattern:
    // e.g. consecutive bullish candles + shrinking bodies + increasing upper wicks (+ resistance nearby)
    val bullishExhaustionSeq = consecutiveBull >= 3 && (decreasingBodies || momDecel) &&
      (increasingUpperWicks || increasingWicks || isNearResistance)
    val bearishExhaustionSeq = consecutiveBear >= 3 && (decreasingBodies || momDecel) &&
      (increasingLowerWicks || increasingWicks || isNearSupport)
    val hasExhaustion = bullishExhaustionSeq || bearishExhaustionSeq

    val exhaustionWarningText = when {
      bullishExhaustionSeq && isNearResistance ->
        "Bullish exhaustion near resistance (shrinking bodies and upper wicks)"
      bullishExhaustionSeq ->
        "Bullish momentum slowing after consecutive up candles"
      bearishExhaustionSeq && isNearSupport ->
        "Bearish exhaustion near support (shrinking bodies and lower wicks)"
      bearishExhaustionSeq ->
        "Bearish momentum slowing after consecutive down candles"
      else -> null
    }

    val multiTimeframe = MultiTimeframeState(
      window5Direction = dir5,
      window10Direction = dir10,
      window20Direction = dir20,
      window30Direction = dir30,
      window40Direction = dir40,
      shortTermDirection = if (hasExhaustion) InternalDirectionState.REVERSAL_RISK else shortTerm,
      mediumTermDirection = mediumTerm,
      overallDirection = overall,
      trendStrength = trendStrength,
      momentum = if (hasExhaustion) InternalDirectionState.REVERSAL_RISK else dir5,
      isConsolidating = isConsolidating,
      hasReversalPossibility = hasReversalPossibility || hasExhaustion
    )

    val recent10 = source.takeLast(min(10, source.size))
    val polarities = recent10.map { it.isBullish }

    val recent5Pol = polarities.takeLast(5)
    var transitions = 0
    for (i in 1 until recent5Pol.size) {
      if (recent5Pol[i] != recent5Pol[i - 1]) transitions++
    }
    val hasAlternating = transitions >= 3

    var hasRepeatedSeq = false
    if (polarities.size >= 6) {
      val tailPattern = polarities.takeLast(3)
      for (start in 0..(polarities.size - 6)) {
        if (polarities.subList(start, start + 3) == tailPattern) {
          hasRepeatedSeq = true
          break
        }
      }
    }

    val hasMomentumSeq = r3.size == 3 &&
      ((r3.all { it.isBullish } || r3.all { it.isBearish }) && increasingBodies)

    val hasReversalSeq = r5.size == 5 && (
      (r5.take(3).all { it.isBullish } && r5.takeLast(2).all { it.isBearish }) ||
        (r5.take(3).all { it.isBearish } && r5.takeLast(2).all { it.isBullish })
      )

    val recent5AvgRange = r5.map { it.totalRange }.average()
    val hasCompression = recent5AvgRange < avgRange * 0.75
    val hasExpansion = r3.last().totalRange > avgRange * 1.30

    val hasCompressionBeforeExpansion = if (r5.size >= 4) {
      val prior3Avg = r5.dropLast(1).takeLast(3).map { it.totalRange }.average()
      val lastRange = r5.last().totalRange
      prior3Avg < avgRange * 0.72 && lastRange > avgRange * 1.32
    } else {
      false
    }

    val upperRejections = r5.count { it.upperWick > it.bodySize * 1.15 && it.upperWick > avgRange * 0.3 }
    val lowerRejections = r5.count { it.lowerWick > it.bodySize * 1.15 && it.lowerWick > avgRange * 0.3 }
    val hasRepeatedRejection = upperRejections >= 2 || lowerRejections >= 2

    val hasTrendContinuation = (overall == InternalDirectionState.BULLISH && shortTerm == InternalDirectionState.BULLISH) ||
      (overall == InternalDirectionState.BEARISH && shortTerm == InternalDirectionState.BEARISH)

    // Repeated continuation: trend resumes after single counter-trend pause candle
    val hasRepeatedContinuation = r5.size == 5 && (
      (r5.count { it.isBullish } == 4 && r5.last().isBullish) ||
        (r5.count { it.isBearish } == 4 && r5.last().isBearish)
      )

    // Failed reversal attempt: single opposite candle immediately engulfed/invalidated by trend continuation
    val hasFailedReversal = r3.size == 3 && (
      (r3[0].isBullish && r3[1].isBearish && r3[2].isBullish && r3[2].close > r3[0].high) ||
        (r3[0].isBearish && r3[1].isBullish && r3[2].isBearish && r3[2].close < r3[0].low)
      )

    val features = CandleSequenceFeatures(
      multiTimeframe = multiTimeframe,
      consecutiveBullishCandles = consecutiveBull,
      consecutiveBearishCandles = consecutiveBear,
      hasRepeatedSequences = hasRepeatedSeq,
      hasAlternatingCandles = hasAlternating,
      increasingBodySize = increasingBodies,
      decreasingBodySize = decreasingBodies,
      increasingWickSize = increasingWicks,
      momentumAcceleration = momAccel,
      momentumDeceleration = momDecel,
      hasExhaustion = hasExhaustion,
      hasCompression = hasCompression,
      hasExpansion = hasExpansion,
      hasMomentumSequence = hasMomentumSeq,
      hasReversalSequence = hasReversalSeq,
      hasCompressionBeforeExpansion = hasCompressionBeforeExpansion,
      hasRepeatedRejection = hasRepeatedRejection,
      hasRepeatedContinuation = hasRepeatedContinuation,
      hasFailedReversalAttempts = hasFailedReversal,
      hasTrendContinuationStructure = hasTrendContinuation,
      exhaustionWarningDescription = exhaustionWarningText
    )

    lastCacheKey = cacheKey
    cachedFeatures = features
    return features
  }

  private fun evaluateWindowDirection(window: List<Candle>, avgRange: Double): InternalDirectionState {
    if (window.isEmpty()) return InternalDirectionState.NEUTRAL
    val netChange = window.last().close - window.first().open
    val bullRatio = window.count { it.isBullish }.toDouble() / window.size.toDouble()
    val threshold = avgRange * (0.35 + window.size * 0.04)

    return when {
      abs(netChange) < threshold * 0.45 -> InternalDirectionState.CONSOLIDATING
      netChange >= threshold && bullRatio >= 0.55 -> InternalDirectionState.BULLISH
      netChange <= -threshold && bullRatio <= 0.45 -> InternalDirectionState.BEARISH
      (netChange > 0 && window.last().isBearish && window.last().bodySize > avgRange * 0.8) ||
        (netChange < 0 && window.last().isBullish && window.last().bodySize > avgRange * 0.8) ->
        InternalDirectionState.REVERSAL_RISK
      else -> InternalDirectionState.NEUTRAL
    }
  }

  @Synchronized
  override fun getSequenceStatusWord(): String {
    val count = latestCandles.size
    return when {
      count == 0 -> "Waiting"
      count < 5 -> "Collecting candle data..."
      count < 30 -> "Building sequence"
      count < 100 -> "Ready"
      else -> "Full memory active"
    }
  }

  @Synchronized
  override fun clear() {
    latestCandles = emptyList()
    lastCacheKey = ""
    cachedFeatures = CandleSequenceFeatures()
  }
}

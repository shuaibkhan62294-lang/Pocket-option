package com.example.engine.sr

import com.example.model.Candle
import com.example.model.SupportResistanceAnalysis
import com.example.model.SupportResistanceZone
import com.example.model.ZoneStrength
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 12 (Part 3) & 4 (Part 4). Intelligent Support & Resistance Price Action Engine
 *
 * Analyzes:
 * - Repeated touches & rejections
 * - Swing highs & swing lows
 * - Consolidation zones
 * - Previous breakout & breakdown levels
 * - Failed breakouts & failed breakdowns
 * - Retest & failed retest behavior
 *
 * Classifies zones internally as WEAK, MEDIUM, STRONG, or VERY_STRONG.
 * Never treats a single wick crossing a level as a confirmed breakout.
 */
interface SupportResistanceEngine {
  fun analyzeSupportResistance(candles: List<Candle>): SupportResistanceAnalysis
}

class DefaultSupportResistanceEngine : SupportResistanceEngine {

  private var lastCacheKey: String = ""
  private var cachedAnalysis: SupportResistanceAnalysis = SupportResistanceAnalysis()

  @Synchronized
  override fun analyzeSupportResistance(candles: List<Candle>): SupportResistanceAnalysis {
    if (candles.size < 8) {
      return SupportResistanceAnalysis()
    }

    val window = candles.takeLast(60)
    val latest = window.last()
    val cacheKey = "${window.size}:${latest.timestamp}:${latest.close}:${latest.high}:${latest.low}"
    if (cacheKey == lastCacheKey) {
      return cachedAnalysis
    }

    val avgRange = window.map { it.totalRange }.average().coerceAtLeast(0.00010)
    val zoneTolerance = avgRange * 0.45

    val candidateHighs = ArrayList<Pair<Double, Boolean>>()
    val candidateLows = ArrayList<Pair<Double, Boolean>>()
    var consolidationClusters = 0

    for (i in 1 until (window.size - 1)) {
      val prev = window[i - 1]
      val curr = window[i]
      val next = window[i + 1]

      val isSwingHigh = curr.high >= prev.high && curr.high >= next.high
      val hasUpperRejection = curr.upperWick > curr.bodySize * 1.1 && curr.upperWick > avgRange * 0.3
      if (isSwingHigh || hasUpperRejection) {
        candidateHighs.add(curr.high to hasUpperRejection)
      }

      val isSwingLow = curr.low <= prev.low && curr.low <= next.low
      val hasLowerRejection = curr.lowerWick > curr.bodySize * 1.1 && curr.lowerWick > avgRange * 0.3
      if (isSwingLow || hasLowerRejection) {
        candidateLows.add(curr.low to hasLowerRejection)
      }

      // Check local 3-candle tight consolidation
      val threeHigh = maxOf(prev.high, curr.high, next.high)
      val threeLow = minOf(prev.low, curr.low, next.low)
      if (threeHigh - threeLow < avgRange * 1.25) {
        consolidationClusters++
      }
    }

    val resistanceZones = clusterZones(
      candidates = candidateHighs,
      allCandles = window,
      tolerance = zoneTolerance,
      isSupport = false
    )
    val supportZones = clusterZones(
      candidates = candidateLows,
      allCandles = window,
      tolerance = zoneTolerance,
      isSupport = true
    )

    val currentPrice = latest.close
    val prevCandle = window[window.size - 2]
    val prev2Candle = if (window.size >= 3) window[window.size - 3] else prevCandle

    val nearestSupport = supportZones
      .filter { it.upperBound <= currentPrice + zoneTolerance }
      .maxByOrNull { it.levelPrice }
      ?: supportZones.minByOrNull { abs(it.levelPrice - currentPrice) }

    val nearestResistance = resistanceZones
      .filter { it.lowerBound >= currentPrice - zoneTolerance }
      .minByOrNull { it.levelPrice }
      ?: resistanceZones.minByOrNull { abs(it.levelPrice - currentPrice) }

    val isNearSupport = nearestSupport != null &&
      abs(currentPrice - nearestSupport.levelPrice) <= zoneTolerance * 1.35 &&
      nearestSupport.strength != ZoneStrength.WEAK

    val isNearResistance = nearestResistance != null &&
      abs(currentPrice - nearestResistance.levelPrice) <= zoneTolerance * 1.35 &&
      nearestResistance.strength != ZoneStrength.WEAK

    val supportRejection = nearestSupport != null &&
      latest.low <= nearestSupport.upperBound + zoneTolerance * 0.5 &&
      latest.close > nearestSupport.levelPrice &&
      (latest.isBullish || latest.lowerWick >= latest.bodySize * 0.8)

    val resistanceRejection = nearestResistance != null &&
      latest.high >= nearestResistance.lowerBound - zoneTolerance * 0.5 &&
      latest.close < nearestResistance.levelPrice &&
      (latest.isBearish || latest.upperWick >= latest.bodySize * 0.8)

    // Confirmed breakout requires meaningful candle body close beyond the level (never just a wick)
    val resistanceBreakout = nearestResistance != null &&
      prevCandle.close <= nearestResistance.upperBound &&
      latest.close > nearestResistance.upperBound + zoneTolerance * 0.25 &&
      latest.bodySize > avgRange * 0.55 &&
      latest.upperWick < latest.bodySize * 0.9

    val supportBreakout = nearestSupport != null &&
      prevCandle.close >= nearestSupport.lowerBound &&
      latest.close < nearestSupport.lowerBound - zoneTolerance * 0.25 &&
      latest.bodySize > avgRange * 0.55 &&
      latest.lowerWick < latest.bodySize * 0.9

    // Fake breakout / Fake breakdown (wick sweeps level or prior candle poked out, then closes back inside)
    val fakeUpBreakout = nearestResistance != null && (
      (latest.high > nearestResistance.upperBound + zoneTolerance * 0.2 && latest.close < nearestResistance.upperBound) ||
        (prevCandle.close > nearestResistance.upperBound && latest.close < nearestResistance.levelPrice)
      )

    val fakeDownBreakout = nearestSupport != null && (
      (latest.low < nearestSupport.lowerBound - zoneTolerance * 0.2 && latest.close > nearestSupport.lowerBound) ||
        (prevCandle.close < nearestSupport.lowerBound && latest.close > nearestSupport.levelPrice)
      )

    // Retest detection: prior candle broke level, current candle pulls back to test the broken level and holds
    val bullishRetest = nearestSupport != null &&
      prev2Candle.close < nearestSupport.upperBound &&
      prevCandle.close > nearestSupport.upperBound &&
      latest.low <= nearestSupport.upperBound + zoneTolerance * 0.3 &&
      latest.close > nearestSupport.upperBound

    val bearishRetest = nearestResistance != null &&
      prev2Candle.close > nearestResistance.lowerBound &&
      prevCandle.close < nearestResistance.lowerBound &&
      latest.high >= nearestResistance.lowerBound - zoneTolerance * 0.3 &&
      latest.close < nearestResistance.lowerBound

    val isRetest = bullishRetest || bearishRetest
    val isFailedRetest = (fakeUpBreakout && prevCandle.close > (nearestResistance?.upperBound ?: Double.MAX_VALUE)) ||
      (fakeDownBreakout && prevCandle.close < (nearestSupport?.lowerBound ?: -Double.MAX_VALUE))

    val analysis = SupportResistanceAnalysis(
      supportZones = supportZones,
      resistanceZones = resistanceZones,
      nearestSupport = nearestSupport,
      nearestResistance = nearestResistance,
      isNearSupport = isNearSupport,
      isNearResistance = isNearResistance,
      supportRejection = supportRejection,
      resistanceRejection = resistanceRejection,
      supportBreakout = supportBreakout,
      resistanceBreakout = resistanceBreakout,
      possibleFakeBreakout = fakeUpBreakout || fakeDownBreakout,
      fakeBreakdown = fakeDownBreakout,
      isRetest = isRetest,
      isFailedRetest = isFailedRetest,
      consolidationZonesCount = (consolidationClusters / 3).coerceAtLeast(0)
    )

    lastCacheKey = cacheKey
    cachedAnalysis = analysis
    return analysis
  }

  private fun clusterZones(
    candidates: List<Pair<Double, Boolean>>,
    allCandles: List<Candle>,
    tolerance: Double,
    isSupport: Boolean
  ): List<SupportResistanceZone> {
    if (candidates.isEmpty()) return emptyList()
    val sorted = candidates.sortedBy { it.first }
    val clusters = ArrayList<MutableList<Pair<Double, Boolean>>>()

    for (item in sorted) {
      val lastCluster = clusters.lastOrNull()
      if (lastCluster == null) {
        clusters.add(mutableListOf(item))
      } else {
        val clusterAvg = lastCluster.map { it.first }.average()
        if (abs(item.first - clusterAvg) <= tolerance) {
          lastCluster.add(item)
        } else {
          clusters.add(mutableListOf(item))
        }
      }
    }

    return clusters.map { cluster ->
      val avgPrice = cluster.map { it.first }.average()
      val lower = avgPrice - tolerance * 0.5
      val upper = avgPrice + tolerance * 0.5

      var touches = 0
      var rejections = cluster.count { it.second }
      for (c in allCandles) {
        val touched = if (isSupport) {
          c.low <= upper && c.high >= lower
        } else {
          c.high >= lower && c.low <= upper
        }
        if (touched) {
          touches++
          if (isSupport && c.close > upper && c.lowerWick > c.bodySize * 0.7) {
            rejections++
          } else if (!isSupport && c.close < lower && c.upperWick > c.bodySize * 0.7) {
            rejections++
          }
        }
      }

      // Classify as WEAK, MEDIUM, STRONG, or VERY_STRONG (never STRONG from 1 random candle)
      val strength = when {
        cluster.size >= 4 || (touches >= 5 && rejections >= 3) -> ZoneStrength.VERY_STRONG
        cluster.size == 3 || (touches >= 3 && rejections >= 2) -> ZoneStrength.STRONG
        cluster.size == 2 || touches >= 2 -> ZoneStrength.MEDIUM
        else -> ZoneStrength.WEAK
      }

      SupportResistanceZone(
        levelPrice = avgPrice,
        lowerBound = min(lower, upper),
        upperBound = max(lower, upper),
        touchCount = touches,
        rejectionCount = rejections,
        strength = strength,
        isSupport = isSupport
      )
    }.sortedByDescending { it.touchCount + it.rejectionCount * 2 }
  }
}

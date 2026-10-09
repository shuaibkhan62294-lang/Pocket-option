package com.example.engine.structure

import com.example.model.BosClassification
import com.example.model.Candle
import com.example.model.ChochClassification
import com.example.model.MarketStructureAnalysis
import com.example.model.MarketStructureType

/**
 * 3. Market Structure Engine (Part 4 Requirement 3).
 *
 * Continuously analyzes confirmed swing structure:
 * - Higher High (HH), Higher Low (HL), Lower High (LH), Lower Low (LL)
 * - Uptrend, Downtrend, Range, Structure Weakening, Possible Reversal, Structure Break
 * - BOS (Break of Structure) & CHoCH (Change of Character)
 *
 * Uses confirmed multi-candle swing pivots and minimum ATR-scaled displacement
 * to avoid false BOS/CHoCH triggers from insignificant single-candle noise.
 */
interface MarketStructureEngine {
  fun analyzeStructure(candles: List<Candle>): MarketStructureAnalysis
}

class DefaultMarketStructureEngine : MarketStructureEngine {

  private data class SwingPivot(val index: Int, val price: Double)

  override fun analyzeStructure(candles: List<Candle>): MarketStructureAnalysis {
    if (candles.size < 10) {
      return MarketStructureAnalysis()
    }

    val window = candles.takeLast(45)
    val avgRange = window.map { it.totalRange }.average().coerceAtLeast(0.00008)
    val minSignificance = avgRange * 0.28

    val swingHighs = ArrayList<SwingPivot>()
    val swingLows = ArrayList<SwingPivot>()

    // Confirm swing pivots using 2-bar left and 1-bar right confirmation to filter tiny noise
    for (i in 2 until (window.size - 1)) {
      val curr = window[i]
      val left1 = window[i - 1]
      val left2 = window[i - 2]
      val right1 = window[i + 1]

      if (curr.high >= left1.high && curr.high >= left2.high && curr.high >= right1.high) {
        val lastH = swingHighs.lastOrNull()
        if (lastH == null || i - lastH.index >= 2) {
          swingHighs.add(SwingPivot(i, curr.high))
        } else if (curr.high > lastH.price) {
          swingHighs[swingHighs.lastIndex] = SwingPivot(i, curr.high)
        }
      }

      if (curr.low <= left1.low && curr.low <= left2.low && curr.low <= right1.low) {
        val lastL = swingLows.lastOrNull()
        if (lastL == null || i - lastL.index >= 2) {
          swingLows.add(SwingPivot(i, curr.low))
        } else if (curr.low < lastL.price) {
          swingLows[swingLows.lastIndex] = SwingPivot(i, curr.low)
        }
      }
    }

    val lastHigh = swingHighs.lastOrNull()?.price ?: window.dropLast(1).maxOf { it.high }
    val prevHigh = if (swingHighs.size >= 2) swingHighs[swingHighs.size - 2].price else lastHigh
    val lastLow = swingLows.lastOrNull()?.price ?: window.dropLast(1).minOf { it.low }
    val prevLow = if (swingLows.size >= 2) swingLows[swingLows.size - 2].price else lastLow

    val higherHigh = lastHigh > prevHigh + minSignificance
    val lowerHigh = lastHigh < prevHigh - minSignificance
    val higherLow = lastLow > prevLow + minSignificance
    val lowerLow = lastLow < prevLow - minSignificance

    val latest = window.last()
    val brokeAboveLastSwingHigh = latest.close > lastHigh + minSignificance * 0.4 &&
      latest.bodySize >= avgRange * 0.45
    val brokeBelowLastSwingLow = latest.close < lastLow - minSignificance * 0.4 &&
      latest.bodySize >= avgRange * 0.45

    val priorWasUptrend = higherHigh && higherLow
    val priorWasDowntrend = lowerHigh && lowerLow

    // BOS = Break of Structure in the direction of the prevailing trend
    val bosState = when {
      priorWasUptrend && brokeAboveLastSwingHigh -> BosClassification.BULLISH_BOS
      priorWasDowntrend && brokeBelowLastSwingLow -> BosClassification.BEARISH_BOS
      else -> BosClassification.NONE
    }

    // CHoCH = Change of Character (first break of an opposing swing level against the prior trend)
    val chochState = when {
      priorWasDowntrend && (brokeAboveLastSwingHigh || (higherHigh && higherLow)) ->
        ChochClassification.BULLISH_CHOCH
      priorWasUptrend && (brokeBelowLastSwingLow || (lowerLow && lowerHigh)) ->
        ChochClassification.BEARISH_CHOCH
      else -> ChochClassification.NONE
    }

    val isWeakening = (higherHigh && lowerLow) || (lowerHigh && higherLow) ||
      (priorWasUptrend && lowerHigh) || (priorWasDowntrend && higherLow)

    val isPossibleReversal = chochState != ChochClassification.NONE ||
      (priorWasUptrend && lowerLow) || (priorWasDowntrend && higherHigh)

    val structureType = when {
      bosState != BosClassification.NONE || chochState != ChochClassification.NONE ->
        MarketStructureType.STRUCTURE_BREAK
      isPossibleReversal -> MarketStructureType.POSSIBLE_REVERSAL
      isWeakening -> MarketStructureType.STRUCTURE_WEAKENING
      priorWasUptrend -> MarketStructureType.UPTREND_STRUCTURE
      priorWasDowntrend -> MarketStructureType.DOWNTREND_STRUCTURE
      else -> MarketStructureType.RANGE_STRUCTURE
    }

    return MarketStructureAnalysis(
      higherHigh = higherHigh,
      higherLow = higherLow,
      lowerHigh = lowerHigh,
      lowerLow = lowerLow,
      structureType = structureType,
      bosState = bosState,
      chochState = chochState,
      isStructureWeakening = isWeakening,
      isPossibleReversal = isPossibleReversal,
      confirmedSwingHighsCount = swingHighs.size,
      confirmedSwingLowsCount = swingLows.size,
      lastSwingHighPrice = lastHigh,
      lastSwingLowPrice = lastLow
    )
  }
}

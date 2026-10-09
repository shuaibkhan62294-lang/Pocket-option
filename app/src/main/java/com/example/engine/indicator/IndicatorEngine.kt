package com.example.engine.indicator

import com.example.model.AdxSignal
import com.example.model.AtrVolatilityState
import com.example.model.BollingerSignal
import com.example.model.Candle
import com.example.model.CciSignal
import com.example.model.EmaSignal
import com.example.model.InternalDirectionState
import com.example.model.InternalStrengthLabel
import com.example.model.MacdSignal
import com.example.model.RsiSignal
import com.example.model.SmaSignal
import com.example.model.StochasticSignal
import com.example.model.VolatilityClassification
import com.example.model.WilliamsSignal
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Full background snapshot of all 10 technical indicator engines (Part 3 Requirements 2–11).
 * Kept strictly internal and hidden from the beginner-friendly main screen.
 */
data class IndicatorSnapshot(
  val fastEma: Double? = null,
  val slowEma: Double? = null,
  val rsi14: Double? = null,
  val atr14: Double? = null,
  val emaSignal: EmaSignal = EmaSignal(),
  val smaSignal: SmaSignal = SmaSignal(),
  val rsiSignal: RsiSignal = RsiSignal(),
  val macdSignal: MacdSignal = MacdSignal(),
  val bollingerSignal: BollingerSignal = BollingerSignal(),
  val stochasticSignal: StochasticSignal = StochasticSignal(),
  val atrState: AtrVolatilityState = AtrVolatilityState(),
  val adxSignal: AdxSignal = AdxSignal(),
  val cciSignal: CciSignal = CciSignal(),
  val williamsSignal: WilliamsSignal = WilliamsSignal(),
  val isReady: Boolean = false
)

/**
 * 2–11. Indicator Engine
 *
 * Calculates EMA (9, 21, 50), SMA (20, 50), RSI (14), MACD (12, 26, 9),
 * Bollinger Bands (20, 2), Stochastic (14, 3), ATR (14), ADX (14), CCI (20),
 * and Williams %R (14) with built-in caching for Android battery/CPU efficiency.
 */
interface IndicatorEngine {
  fun evaluateIndicators(candles: List<Candle>): IndicatorSnapshot
}

class DefaultIndicatorEngine : IndicatorEngine {

  private var lastCacheKey: String = ""
  private var cachedSnapshot: IndicatorSnapshot = IndicatorSnapshot(isReady = false)

  @Synchronized
  override fun evaluateIndicators(candles: List<Candle>): IndicatorSnapshot {
    if (candles.size < 10) {
      return IndicatorSnapshot(isReady = false)
    }

    val window = candles.takeLast(80)
    val latest = window.last()
    val cacheKey = "${window.size}:${latest.timestamp}:${latest.close}:${latest.high}:${latest.low}"
    if (cacheKey == lastCacheKey) {
      return cachedSnapshot
    }

    val closes = window.map { it.close }
    val atrState = computeAtrState(window)
    val avgRange = (atrState.atrValue ?: latest.totalRange).coerceAtLeast(0.00008)

    val emaSignal = computeEmaSignal(closes, avgRange)
    val smaSignal = computeSmaSignal(closes, avgRange)
    val rsiSignal = computeRsiSignal(window, closes)
    val macdSignal = computeMacdSignal(window, closes, avgRange)
    val bollingerSignal = computeBollingerSignal(window, closes)
    val stochasticSignal = computeStochasticSignal(window, emaSignal.direction)
    val adxSignal = computeAdxSignal(window)
    val cciSignal = computeCciSignal(window)
    val williamsSignal = computeWilliamsSignal(window)

    val snapshot = IndicatorSnapshot(
      fastEma = emaSignal.ema9,
      slowEma = emaSignal.ema21,
      rsi14 = rsiSignal.rsiValue,
      atr14 = atrState.atrValue,
      emaSignal = emaSignal,
      smaSignal = smaSignal,
      rsiSignal = rsiSignal,
      macdSignal = macdSignal,
      bollingerSignal = bollingerSignal,
      stochasticSignal = stochasticSignal,
      atrState = atrState,
      adxSignal = adxSignal,
      cciSignal = cciSignal,
      williamsSignal = williamsSignal,
      isReady = true
    )

    lastCacheKey = cacheKey
    cachedSnapshot = snapshot
    return snapshot
  }

  // ==========================================================================
  // 2. EMA Engine (EMA 9, EMA 21, EMA 50)
  // ==========================================================================
  private fun computeEmaSignal(closes: List<Double>, avgRange: Double): EmaSignal {
    val ema9Series = computeEmaSeries(closes, 9)
    val ema21Series = computeEmaSeries(closes, 21)
    val ema50Series = computeEmaSeries(closes, min(50, closes.size))

    val ema9 = ema9Series.lastOrNull() ?: return EmaSignal()
    val ema21 = ema21Series.lastOrNull() ?: ema9
    val ema50 = ema50Series.lastOrNull() ?: ema21

    val prevEma9 = if (ema9Series.size >= 2) ema9Series[ema9Series.size - 2] else ema9
    val prevEma21 = if (ema21Series.size >= 2) ema21Series[ema21Series.size - 2] else ema21

    val latestClose = closes.last()
    val bullishCrossover = prevEma9 <= prevEma21 && ema9 > ema21
    val bearishCrossover = prevEma9 >= prevEma21 && ema9 < ema21

    val priceAboveAll = latestClose > ema9 && latestClose > ema21 && latestClose > ema50
    val priceBelowAll = latestClose < ema9 && latestClose < ema21 && latestClose < ema50

    val separation = abs(ema9 - ema21) + abs(ema21 - ema50)
    val isCompressed = separation < avgRange * 0.45

    val alignment = when {
      isCompressed -> InternalDirectionState.CONSOLIDATING
      ema9 > ema21 && ema21 >= ema50 -> InternalDirectionState.BULLISH
      ema9 < ema21 && ema21 <= ema50 -> InternalDirectionState.BEARISH
      else -> InternalDirectionState.NEUTRAL
    }

    val direction = when {
      bullishCrossover || (alignment == InternalDirectionState.BULLISH && priceAboveAll) ->
        InternalDirectionState.BULLISH
      bearishCrossover || (alignment == InternalDirectionState.BEARISH && priceBelowAll) ->
        InternalDirectionState.BEARISH
      isCompressed -> InternalDirectionState.CONSOLIDATING
      else -> alignment
    }

    val strength = when {
      (alignment == InternalDirectionState.BULLISH && priceAboveAll && separation > avgRange * 0.9) ||
        (alignment == InternalDirectionState.BEARISH && priceBelowAll && separation > avgRange * 0.9) ->
        InternalStrengthLabel.STRONG
      direction == InternalDirectionState.BULLISH || direction == InternalDirectionState.BEARISH ->
        InternalStrengthLabel.MODERATE
      else -> InternalStrengthLabel.WEAK
    }

    return EmaSignal(
      ema9 = ema9,
      ema21 = ema21,
      ema50 = ema50,
      bullishCrossover = bullishCrossover,
      bearishCrossover = bearishCrossover,
      priceAboveEma = priceAboveAll,
      priceBelowEma = priceBelowAll,
      emaAlignment = alignment,
      emaSeparation = separation,
      emaCompression = isCompressed,
      direction = direction,
      strength = strength
    )
  }

  // ==========================================================================
  // 3. SMA Engine (SMA 20, SMA 50)
  // ==========================================================================
  private fun computeSmaSignal(closes: List<Double>, avgRange: Double): SmaSignal {
    val sma20 = computeSma(closes, min(20, closes.size))
    val sma50 = computeSma(closes, min(50, closes.size))
    val prevCloses = if (closes.size > 1) closes.dropLast(1) else closes
    val prevSma20 = computeSma(prevCloses, min(20, prevCloses.size))
    val prevSma50 = computeSma(prevCloses, min(50, prevCloses.size))

    val latestPrice = closes.last()
    val above20 = latestPrice > sma20
    val above50 = latestPrice > sma50

    val bullishCross = prevSma20 <= prevSma50 && sma20 > sma50
    val bearishCross = prevSma20 >= prevSma50 && sma20 < sma50

    val trendConfirmed = (above20 && above50 && sma20 > sma50) || (!above20 && !above50 && sma20 < sma50)
    // Possible reversal if price crosses SMA20 against SMA50 slope or overextends far from SMA20
    val distFrom20 = abs(latestPrice - sma20)
    val possibleReversal = (above20 != above50) || (distFrom20 > avgRange * 3.0)

    val direction = when {
      above20 && above50 && sma20 >= sma50 -> InternalDirectionState.BULLISH
      !above20 && !above50 && sma20 <= sma50 -> InternalDirectionState.BEARISH
      possibleReversal -> InternalDirectionState.REVERSAL_RISK
      else -> InternalDirectionState.NEUTRAL
    }

    return SmaSignal(
      sma20 = sma20,
      sma50 = sma50,
      priceAboveSma20 = above20,
      priceAboveSma50 = above50,
      bullishCrossover = bullishCross,
      bearishCrossover = bearishCross,
      trendConfirmation = trendConfirmed,
      possibleReversal = possibleReversal,
      direction = direction
    )
  }

  // ==========================================================================
  // 4. RSI Engine (Wilder's 14-period + Price Action & Divergence)
  // ==========================================================================
  private fun computeRsiSignal(candles: List<Candle>, closes: List<Double>): RsiSignal {
    val rsiSeries = computeRsiSeries(closes, period = 14)
    val currentRsi = rsiSeries.lastOrNull() ?: 50.0
    val prevRsi = if (rsiSeries.size >= 2) rsiSeries[rsiSeries.size - 2] else currentRsi
    val prev3Rsi = if (rsiSeries.size >= 4) rsiSeries[rsiSeries.size - 4] else prevRsi

    val isOverbought = currentRsi >= 70.0
    val isOversold = currentRsi <= 30.0
    val isNeutral = currentRsi in 42.0..58.0

    val momentumIncrease = currentRsi > prevRsi && currentRsi > prev3Rsi + 1.5
    val momentumDecrease = currentRsi < prevRsi && currentRsi < prev3Rsi - 1.5

    // Detect bullish/bearish divergence across recent 10 candles
    var bullishDiv = false
    var bearishDiv = false
    if (closes.size >= 10 && rsiSeries.size >= 10) {
      val olderWindowPrices = closes.subList(closes.size - 10, closes.size - 4)
      val recentWindowPrices = closes.takeLast(4)
      val olderWindowRsi = rsiSeries.subList(rsiSeries.size - 10, rsiSeries.size - 4)
      val recentWindowRsi = rsiSeries.takeLast(4)

      val olderLow = olderWindowPrices.minOrNull() ?: closes.last()
      val recentLow = recentWindowPrices.minOrNull() ?: closes.last()
      val olderRsiLow = olderWindowRsi.minOrNull() ?: 50.0
      val recentRsiLow = recentWindowRsi.minOrNull() ?: 50.0

      if (recentLow < olderLow && recentRsiLow > olderRsiLow + 2.0) {
        bullishDiv = true
      }

      val olderHigh = olderWindowPrices.maxOrNull() ?: closes.last()
      val recentHigh = recentWindowPrices.maxOrNull() ?: closes.last()
      val olderRsiHigh = olderWindowRsi.maxOrNull() ?: 50.0
      val recentRsiHigh = recentWindowRsi.maxOrNull() ?: 50.0

      if (recentHigh > olderHigh && recentRsiHigh < olderRsiHigh - 2.0) {
        bearishDiv = true
      }
    }

    // IMPORTANT: Do NOT treat RSI overbought as automatically DOWN, nor oversold as automatically UP.
    // Combine RSI with price action (candle polarity / rejection).
    val latestCandle = candles.last()
    val directionWithPriceAction = when {
      isOverbought && (bearishDiv || (latestCandle.isBearish && momentumDecrease)) ->
        InternalDirectionState.REVERSAL_RISK
      isOversold && (bullishDiv || (latestCandle.isBullish && momentumIncrease)) ->
        InternalDirectionState.REVERSAL_RISK
      currentRsi in 53.0..75.0 && momentumIncrease && latestCandle.isBullish ->
        InternalDirectionState.BULLISH
      currentRsi in 25.0..47.0 && momentumDecrease && latestCandle.isBearish ->
        InternalDirectionState.BEARISH
      else -> InternalDirectionState.NEUTRAL
    }

    return RsiSignal(
      rsiValue = currentRsi,
      isOverbought = isOverbought,
      isOversold = isOversold,
      isNeutral = isNeutral,
      momentumIncrease = momentumIncrease,
      momentumDecrease = momentumDecrease,
      bullishDivergence = bullishDiv,
      bearishDivergence = bearishDiv,
      directionWithPriceAction = directionWithPriceAction
    )
  }

  // ==========================================================================
  // 5. MACD Engine (12, 26, 9)
  // ==========================================================================
  private fun computeMacdSignal(
    candles: List<Candle>,
    closes: List<Double>,
    avgRange: Double
  ): MacdSignal {
    val ema12 = computeEmaSeries(closes, 12)
    val ema26 = computeEmaSeries(closes, min(26, closes.size))
    val size = min(ema12.size, ema26.size)
    if (size < 3) return MacdSignal()

    val macdSeries = List(size) { i -> ema12[i] - ema26[i] }
    val signalSeries = computeEmaSeries(macdSeries, min(9, size))
    val histSeries = List(size) { i -> macdSeries[i] - signalSeries[i] }

    val macd = macdSeries.last()
    val sig = signalSeries.last()
    val hist = histSeries.last()
    val prevMacd = macdSeries[size - 2]
    val prevSig = signalSeries[size - 2]
    val prevHist = histSeries[size - 2]

    val bullishCross = prevMacd <= prevSig && macd > sig
    val bearishCross = prevMacd >= prevSig && macd < sig

    val histExpansion = abs(hist) > abs(prevHist) * 1.08
    val histContraction = abs(hist) < abs(prevHist) * 0.92
    val momentumTransition = (prevHist < 0 && hist >= 0) || (prevHist > 0 && hist <= 0) || histContraction

    var bullishDiv = false
    var bearishDiv = false
    if (size >= 10 && candles.size >= 10) {
      val olderPriceLow = closes.subList(closes.size - 10, closes.size - 4).minOrNull() ?: closes.last()
      val recentPriceLow = closes.takeLast(4).minOrNull() ?: closes.last()
      val olderHistLow = histSeries.subList(size - 10, size - 4).minOrNull() ?: hist
      val recentHistLow = histSeries.takeLast(4).minOrNull() ?: hist
      if (recentPriceLow < olderPriceLow && recentHistLow > olderHistLow) {
        bullishDiv = true
      }

      val olderPriceHigh = closes.subList(closes.size - 10, closes.size - 4).maxOrNull() ?: closes.last()
      val recentPriceHigh = closes.takeLast(4).maxOrNull() ?: closes.last()
      val olderHistHigh = histSeries.subList(size - 10, size - 4).maxOrNull() ?: hist
      val recentHistHigh = histSeries.takeLast(4).maxOrNull() ?: hist
      if (recentPriceHigh > olderPriceHigh && recentHistHigh < olderHistHigh) {
        bearishDiv = true
      }
    }

    val direction = when {
      bullishCross || (hist > 0 && histExpansion && macd > sig) -> InternalDirectionState.BULLISH
      bearishCross || (hist < 0 && histExpansion && macd < sig) -> InternalDirectionState.BEARISH
      bullishDiv || bearishDiv -> InternalDirectionState.REVERSAL_RISK
      abs(hist) < avgRange * 0.02 -> InternalDirectionState.CONSOLIDATING
      else -> InternalDirectionState.NEUTRAL
    }

    return MacdSignal(
      macdLine = macd,
      signalLine = sig,
      histogram = hist,
      bullishCrossover = bullishCross,
      bearishCrossover = bearishCross,
      histogramExpansion = histExpansion,
      histogramContraction = histContraction,
      momentumTransition = momentumTransition,
      bullishDivergence = bullishDiv,
      bearishDivergence = bearishDiv,
      direction = direction
    )
  }

  // ==========================================================================
  // 6. Bollinger Bands Engine (20, 2.0)
  // ==========================================================================
  private fun computeBollingerSignal(candles: List<Candle>, closes: List<Double>): BollingerSignal {
    val period = min(20, closes.size)
    val recent = closes.takeLast(period)
    val middle = recent.average()
    val variance = recent.map { (it - middle) * (it - middle) }.average()
    val stdDev = sqrt(variance).coerceAtLeast(1e-6)

    val upper = middle + 2.0 * stdDev
    val lower = middle - 2.0 * stdDev
    val width = (upper - lower) / middle.coerceAtLeast(1e-6)

    val prevCloses = if (closes.size > 2) closes.dropLast(2).takeLast(period) else recent
    val prevMiddle = prevCloses.average()
    val prevStd = sqrt(prevCloses.map { (it - prevMiddle) * (it - prevMiddle) }.average()).coerceAtLeast(1e-6)
    val prevWidth = (4.0 * prevStd) / prevMiddle.coerceAtLeast(1e-6)

    val latestCandle = candles.last()
    val bandSpan = (upper - lower).coerceAtLeast(1e-6)
    val nearUpper = latestCandle.high >= upper - bandSpan * 0.15
    val nearLower = latestCandle.low <= lower + bandSpan * 0.15

    val expanding = width > prevWidth * 1.10
    val contracting = width < prevWidth * 0.90

    val possibleBreakout = expanding && (latestCandle.close > upper || latestCandle.close < lower)
    val possibleMeanReversion = !expanding && (
      (nearUpper && latestCandle.isBearish && latestCandle.upperWick > latestCandle.bodySize) ||
        (nearLower && latestCandle.isBullish && latestCandle.lowerWick > latestCandle.bodySize)
      )

    val directionContext = when {
      possibleMeanReversion -> InternalDirectionState.REVERSAL_RISK
      contracting -> InternalDirectionState.CONSOLIDATING
      possibleBreakout && latestCandle.close > upper -> InternalDirectionState.BULLISH
      possibleBreakout && latestCandle.close < lower -> InternalDirectionState.BEARISH
      else -> InternalDirectionState.NEUTRAL
    }

    return BollingerSignal(
      middleBand = middle,
      upperBand = upper,
      lowerBand = lower,
      bandWidth = width,
      priceNearUpperBand = nearUpper,
      priceNearLowerBand = nearLower,
      bandExpansion = expanding,
      bandContraction = contracting,
      possibleBreakout = possibleBreakout,
      possibleMeanReversion = possibleMeanReversion,
      directionContext = directionContext
    )
  }

  // ==========================================================================
  // 7. Stochastic Engine (14, 3)
  // ==========================================================================
  private fun computeStochasticSignal(
    candles: List<Candle>,
    trendContext: InternalDirectionState
  ): StochasticSignal {
    val kPeriod = min(14, candles.size)
    if (kPeriod < 4) return StochasticSignal()

    val kValues = ArrayList<Double>()
    val startIdx = max(0, candles.size - 6)
    for (endIdx in startIdx until candles.size) {
      val sliceStart = max(0, endIdx - kPeriod + 1)
      val slice = candles.subList(sliceStart, endIdx + 1)
      val highest = slice.maxOf { it.high }
      val lowest = slice.minOf { it.low }
      val span = (highest - lowest).coerceAtLeast(1e-7)
      val k = ((candles[endIdx].close - lowest) / span) * 100.0
      kValues.add(k.coerceIn(0.0, 100.0))
    }

    val currentK = kValues.last()
    val currentD = kValues.takeLast(min(3, kValues.size)).average()
    val prevK = if (kValues.size >= 2) kValues[kValues.size - 2] else currentK
    val prevD = if (kValues.size >= 2) kValues.dropLast(1).takeLast(min(3, kValues.size - 1)).average() else currentD

    val bullishCross = prevK <= prevD && currentK > currentD
    val bearishCross = prevK >= prevD && currentK < currentD
    val overbought = currentK >= 80.0
    val oversold = currentK <= 20.0
    val momentumTransition = bullishCross || bearishCross

    // Combine with trend context
    val directionWithTrend = when {
      bullishCross && (trendContext == InternalDirectionState.BULLISH || oversold) ->
        InternalDirectionState.BULLISH
      bearishCross && (trendContext == InternalDirectionState.BEARISH || overbought) ->
        InternalDirectionState.BEARISH
      (overbought && bearishCross) || (oversold && bullishCross) ->
        InternalDirectionState.REVERSAL_RISK
      else -> InternalDirectionState.NEUTRAL
    }

    return StochasticSignal(
      percentK = currentK,
      percentD = currentD,
      bullishCrossover = bullishCross,
      bearishCrossover = bearishCross,
      isOverbought = overbought,
      isOversold = oversold,
      momentumTransition = momentumTransition,
      directionWithTrend = directionWithTrend
    )
  }

  // ==========================================================================
  // 8. ATR / Volatility Engine (14-period True Range)
  // ==========================================================================
  private fun computeAtrState(candles: List<Candle>): AtrVolatilityState {
    if (candles.size < 3) return AtrVolatilityState()

    val trueRanges = ArrayList<Double>(candles.size)
    for (i in candles.indices) {
      val c = candles[i]
      val tr = if (i == 0) {
        c.totalRange
      } else {
        val prevClose = candles[i - 1].close
        maxOf(
          c.high - c.low,
          abs(c.high - prevClose),
          abs(c.low - prevClose)
        )
      }
      trueRanges.add(tr)
    }

    val recentAtr = trueRanges.takeLast(min(14, trueRanges.size)).average()
    val baselineAtr = trueRanges.average().coerceAtLeast(1e-7)
    val latestRange = trueRanges.last()

    val expanding = recentAtr > baselineAtr * 1.22
    val contracting = recentAtr < baselineAtr * 0.78
    val unusuallyLarge = latestRange > baselineAtr * 2.1
    val unusuallySmall = latestRange < baselineAtr * 0.35

    val classification = when {
      unusuallyLarge || recentAtr > baselineAtr * 1.40 -> VolatilityClassification.HIGH_VOLATILITY
      unusuallySmall || recentAtr < baselineAtr * 0.65 -> VolatilityClassification.LOW_VOLATILITY
      else -> VolatilityClassification.NORMAL_VOLATILITY
    }

    return AtrVolatilityState(
      atrValue = recentAtr,
      classification = classification,
      volatilityExpansion = expanding,
      volatilityContraction = contracting,
      unusuallyLargeCandles = unusuallyLarge,
      unusuallySmallCandles = unusuallySmall
    )
  }

  // ==========================================================================
  // 9. ADX Engine (14-period Directional Movement)
  // ==========================================================================
  private fun computeAdxSignal(candles: List<Candle>): AdxSignal {
    if (candles.size < 10) return AdxSignal()

    val trList = ArrayList<Double>()
    val plusDmList = ArrayList<Double>()
    val minusDmList = ArrayList<Double>()

    for (i in 1 until candles.size) {
      val curr = candles[i]
      val prev = candles[i - 1]

      val upMove = curr.high - prev.high
      val downMove = prev.low - curr.low

      val plusDm = if (upMove > downMove && upMove > 0) upMove else 0.0
      val minusDm = if (downMove > upMove && downMove > 0) downMove else 0.0
      val tr = maxOf(
        curr.high - curr.low,
        abs(curr.high - prev.close),
        abs(curr.low - prev.close)
      )

      trList.add(tr)
      plusDmList.add(plusDm)
      minusDmList.add(minusDm)
    }

    val period = min(14, trList.size)
    val dxValues = ArrayList<Double>()
    var latestPlusDi = 0.0
    var latestMinusDi = 0.0

    for (endIdx in (period - 1) until trList.size) {
      val startIdx = endIdx - period + 1
      val trSum = trList.subList(startIdx, endIdx + 1).sum().coerceAtLeast(1e-7)
      val pDmSum = plusDmList.subList(startIdx, endIdx + 1).sum()
      val mDmSum = minusDmList.subList(startIdx, endIdx + 1).sum()

      val pDi = (pDmSum / trSum) * 100.0
      val mDi = (mDmSum / trSum) * 100.0
      val diSum = (pDi + mDi).coerceAtLeast(1e-7)
      val dx = (abs(pDi - mDi) / diSum) * 100.0
      dxValues.add(dx)

      if (endIdx == trList.lastIndex) {
        latestPlusDi = pDi
        latestMinusDi = mDi
      }
    }

    val adx = if (dxValues.isNotEmpty()) dxValues.takeLast(min(14, dxValues.size)).average() else 15.0
    val prevAdx = if (dxValues.size >= 2) dxValues.dropLast(1).takeLast(min(14, dxValues.size - 1)).average() else adx

    val isStrong = adx >= 28.0
    val isTrending = adx >= 20.0
    val isWeak = adx < 20.0
    val transition = (prevAdx < 20.0 && adx >= 20.0) || (prevAdx >= 28.0 && adx < prevAdx - 2.5)

    val direction = when {
      isTrending && latestPlusDi > latestMinusDi + 3.0 -> InternalDirectionState.BULLISH
      isTrending && latestMinusDi > latestPlusDi + 3.0 -> InternalDirectionState.BEARISH
      isWeak -> InternalDirectionState.CONSOLIDATING
      else -> InternalDirectionState.NEUTRAL
    }

    return AdxSignal(
      adxValue = adx,
      plusDi = latestPlusDi,
      minusDi = latestMinusDi,
      isTrendingMarket = isTrending,
      isWeakTrend = isWeak,
      isStrongTrend = isStrong,
      possibleTrendTransition = transition,
      direction = direction
    )
  }

  // ==========================================================================
  // 10. CCI Engine (20-period Commodity Channel Index)
  // ==========================================================================
  private fun computeCciSignal(candles: List<Candle>): CciSignal {
    val period = min(20, candles.size)
    if (period < 5) return CciSignal()

    val typicalPrices = candles.map { (it.high + it.low + it.close) / 3.0 }
    val recentTp = typicalPrices.takeLast(period)
    val smaTp = recentTp.average()
    val meanDeviation = recentTp.map { abs(it - smaTp) }.average().coerceAtLeast(1e-7)
    val cci = (typicalPrices.last() - smaTp) / (0.015 * meanDeviation)

    val prevTp = typicalPrices.dropLast(1).takeLast(period)
    val prevSmaTp = prevTp.average()
    val prevMeanDev = prevTp.map { abs(it - prevSmaTp) }.average().coerceAtLeast(1e-7)
    val prevCci = ( prevTp.last() - prevSmaTp ) / (0.015 * prevMeanDev)

    val isExtreme = abs(cci) >= 100.0
    val zeroTransition = (prevCci <= 0.0 && cci > 0.0) || (prevCci >= 0.0 && cci < 0.0)
    val reversal = (prevCci > 100.0 && cci < prevCci - 15.0) || (prevCci < -100.0 && cci > prevCci + 15.0)

    val momentum = when {
      reversal -> InternalDirectionState.REVERSAL_RISK
      cci > 50.0 -> InternalDirectionState.BULLISH
      cci < -50.0 -> InternalDirectionState.BEARISH
      else -> InternalDirectionState.NEUTRAL
    }

    return CciSignal(
      cciValue = cci,
      momentum = momentum,
      isExtremeReading = isExtreme,
      momentumReversal = reversal,
      zeroLineTransition = zeroTransition
    )
  }

  // ==========================================================================
  // 11. Williams %R Engine (14-period)
  // ==========================================================================
  private fun computeWilliamsSignal(candles: List<Candle>): WilliamsSignal {
    val period = min(14, candles.size)
    if (period < 4) return WilliamsSignal()

    val recent = candles.takeLast(period)
    val highest = recent.maxOf { it.high }
    val lowest = recent.minOf { it.low }
    val span = (highest - lowest).coerceAtLeast(1e-7)
    val latest = candles.last()
    val wr = ((highest - latest.close) / span) * -100.0

    val prevSlice = candles.dropLast(1).takeLast(period)
    val prevHigh = prevSlice.maxOf { it.high }
    val prevLow = prevSlice.minOf { it.low }
    val prevSpan = (prevHigh - prevLow).coerceAtLeast(1e-7)
    val prevWr = ((prevHigh - prevSlice.last().close) / prevSpan) * -100.0

    val overbought = wr >= -20.0
    val oversold = wr <= -80.0
    val reversal = (prevWr <= -80.0 && wr > -75.0) || (prevWr >= -20.0 && wr < -25.0)
    val confirmedWithPa = (oversold && latest.isBullish) || (overbought && latest.isBearish) ||
      (wr > -50.0 && latest.isBullish) || (wr < -50.0 && latest.isBearish)

    val direction = when {
      reversal && confirmedWithPa -> InternalDirectionState.REVERSAL_RISK
      wr > -45.0 && latest.isBullish -> InternalDirectionState.BULLISH
      wr < -55.0 && latest.isBearish -> InternalDirectionState.BEARISH
      else -> InternalDirectionState.NEUTRAL
    }

    return WilliamsSignal(
      williamsR = wr,
      isOverbought = overbought,
      isOversold = oversold,
      momentumReversal = reversal,
      confirmedWithPriceAction = confirmedWithPa,
      direction = direction
    )
  }

  // ==========================================================================
  // Helper Math Functions
  // ==========================================================================
  private fun computeSma(values: List<Double>, period: Int): Double {
    if (values.isEmpty()) return 0.0
    return values.takeLast(period.coerceAtLeast(1)).average()
  }

  private fun computeEmaSeries(values: List<Double>, period: Int): List<Double> {
    if (values.isEmpty()) return emptyList()
    val safePeriod = period.coerceAtLeast(1)
    val multiplier = 2.0 / (safePeriod + 1.0)
    val result = ArrayList<Double>(values.size)
    var currentEma = values.first()
    result.add(currentEma)
    for (i in 1 until values.size) {
      currentEma = (values[i] - currentEma) * multiplier + currentEma
      result.add(currentEma)
    }
    return result
  }

  private fun computeRsiSeries(closes: List<Double>, period: Int = 14): List<Double> {
    if (closes.size < 2) return listOf(50.0)
    val safePeriod = min(period, closes.size - 1).coerceAtLeast(2)
    val result = ArrayList<Double>(closes.size)
    var avgGain = 0.0
    var avgLoss = 0.0

    for (i in 1..safePeriod) {
      val diff = closes[i] - closes[i - 1]
      if (diff > 0) avgGain += diff else avgLoss -= diff
      result.add(50.0)
    }
    avgGain /= safePeriod
    avgLoss /= safePeriod

    val firstRs = if (avgLoss <= 1e-9) 100.0 else avgGain / avgLoss
    val firstRsi = if (avgLoss <= 1e-9) 100.0 else 100.0 - (100.0 / (1.0 + firstRs))
    result[result.lastIndex] = firstRsi

    for (i in (safePeriod + 1) until closes.size) {
      val diff = closes[i] - closes[i - 1]
      val gain = if (diff > 0) diff else 0.0
      val loss = if (diff < 0) -diff else 0.0
      avgGain = (avgGain * (safePeriod - 1) + gain) / safePeriod
      avgLoss = (avgLoss * (safePeriod - 1) + loss) / safePeriod
      val rsi = if (avgLoss <= 1e-9) {
        100.0
      } else {
        val rs = avgGain / avgLoss
        100.0 - (100.0 / (1.0 + rs))
      }
      result.add(rsi.coerceIn(0.0, 100.0))
    }
    return result
  }
}

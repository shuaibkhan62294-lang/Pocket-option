package com.example.engine.pattern

import com.example.engine.indicator.IndicatorSnapshot
import com.example.model.Candle
import com.example.model.DetectedCandlestickPattern
import com.example.model.InternalDirectionState
import com.example.model.InternalStrengthLabel
import com.example.model.MomentumAnalysisState
import com.example.model.SupportResistanceAnalysis
import kotlin.math.abs
import kotlin.math.min

/**
 * Result of the Candlestick Pattern Engine (Parts 2, 3, & 4).
 */
data class CandlestickPatternResult(
  val detectedPatternName: String? = null,
  val patterns: List<DetectedCandlestickPattern> = emptyList(),
  val isReady: Boolean = false
)

/**
 * 2. Complete Context-Aware Candlestick Pattern Engine (Part 4 Requirement 2).
 *
 * Detects single, double, and three+ candle patterns with full context awareness:
 * Single candle:
 * - Doji, Dragonfly Doji, Gravestone Doji, Hammer, Inverted Hammer,
 *   Shooting Star, Hanging Man, Marubozu, Spinning Top
 * Two candle:
 * - Bullish Engulfing, Bearish Engulfing, Bullish Harami, Bearish Harami,
 *   Piercing Pattern, Dark Cloud Cover, Tweezer Bottom, Tweezer Top
 * Three+ candle:
 * - Morning Star, Evening Star, Three White Soldiers, Three Black Crows,
 *   Three Inside Up, Three Inside Down, Three Outside Up, Three Outside Down
 *
 * Evaluates whether each pattern occurs near support/resistance, whether momentum
 * and indicators confirm it, and whether it conflicts with the prevailing trend.
 */
interface CandlestickPatternEngine {
  fun detectPatterns(
    candles: List<Candle>,
    supportResistance: SupportResistanceAnalysis = SupportResistanceAnalysis(),
    momentum: MomentumAnalysisState = MomentumAnalysisState(),
    indicators: IndicatorSnapshot = IndicatorSnapshot()
  ): CandlestickPatternResult
}

class DefaultCandlestickPatternEngine : CandlestickPatternEngine {

  override fun detectPatterns(
    candles: List<Candle>,
    supportResistance: SupportResistanceAnalysis,
    momentum: MomentumAnalysisState,
    indicators: IndicatorSnapshot
  ): CandlestickPatternResult {
    if (candles.size < 4) {
      return CandlestickPatternResult(isReady = false)
    }

    val rawPatterns = ArrayList<DetectedCandlestickPattern>()
    val avgBody = candles.takeLast(min(25, candles.size)).map { it.bodySize }.average().coerceAtLeast(1e-6)
    val avgRange = candles.takeLast(min(25, candles.size)).map { it.totalRange }.average().coerceAtLeast(1e-6)

    val lastIdx = candles.lastIndex
    val c0 = candles[lastIdx]
    val c1 = candles[lastIdx - 1]
    val c2 = candles[lastIdx - 2]

    val trendSlice = candles.subList(maxOf(0, lastIdx - 6), lastIdx)
    val priorDelta = if (trendSlice.isNotEmpty()) {
      trendSlice.last().close - trendSlice.first().open
    } else {
      0.0
    }
    val isPriorUptrend = priorDelta > avgRange * 0.65
    val isPriorDowntrend = priorDelta < -avgRange * 0.65
    val prevMarketContext = when {
      isPriorUptrend -> "Uptrend"
      isPriorDowntrend -> "Downtrend"
      else -> "Sideways Consolidation"
    }

    val locationLabel = when {
      supportResistance.isNearResistance || supportResistance.resistanceRejection -> "At Resistance"
      supportResistance.isNearSupport || supportResistance.supportRejection -> "At Support"
      isPriorUptrend -> "Upper Swing"
      isPriorDowntrend -> "Lower Swing"
      else -> "Mid-Range"
    }

    fun addContextualPattern(
      name: String,
      dir: InternalDirectionState,
      baseStrength: InternalStrengthLabel,
      positions: List<Int>,
      description: String
    ) {
      val nearSr = when (dir) {
        InternalDirectionState.BULLISH ->
          supportResistance.isNearSupport || supportResistance.supportRejection
        InternalDirectionState.BEARISH ->
          supportResistance.isNearResistance || supportResistance.resistanceRejection
        else -> supportResistance.isNearSupport || supportResistance.isNearResistance
      }

      val momConfirms = when (dir) {
        InternalDirectionState.BULLISH ->
          momentum.direction == InternalDirectionState.BULLISH || momentum.strongBullishMomentum
        InternalDirectionState.BEARISH ->
          momentum.direction == InternalDirectionState.BEARISH || momentum.strongBearishMomentum
        else -> false
      }

      val indConfirms = when (dir) {
        InternalDirectionState.BULLISH ->
          indicators.emaSignal.direction == InternalDirectionState.BULLISH ||
            indicators.macdSignal.direction == InternalDirectionState.BULLISH ||
            indicators.rsiSignal.directionWithPriceAction == InternalDirectionState.BULLISH
        InternalDirectionState.BEARISH ->
          indicators.emaSignal.direction == InternalDirectionState.BEARISH ||
            indicators.macdSignal.direction == InternalDirectionState.BEARISH ||
            indicators.rsiSignal.directionWithPriceAction == InternalDirectionState.BEARISH
        else -> false
      }

      val conflictsWithTrend = (dir == InternalDirectionState.BULLISH && isPriorDowntrend && !nearSr) ||
        (dir == InternalDirectionState.BEARISH && isPriorUptrend && !nearSr)

      // Context-aware strength adjustment (Requirement 2)
      val contextualStrength = when {
        nearSr && (momConfirms || indConfirms) -> InternalStrengthLabel.STRONG
        conflictsWithTrend && !nearSr && !momConfirms -> InternalStrengthLabel.WEAK
        else -> baseStrength
      }

      rawPatterns.add(
        DetectedCandlestickPattern(
          patternName = name,
          direction = dir,
          strength = contextualStrength,
          candlePositions = positions,
          context = description,
          location = locationLabel,
          previousMarketContext = prevMarketContext,
          occursNearSupportOrResistance = nearSr,
          momentumConfirms = momConfirms,
          indicatorsConfirm = indConfirms,
          conflictsWithCurrentTrend = conflictsWithTrend
        )
      )
    }

    // ========================================================================
    // Single-Candle Patterns
    // ========================================================================
    val isDojiBody = c0.totalRange >= avgRange * 0.45 && c0.bodySize <= c0.totalRange * 0.14
    if (isDojiBody) {
      when {
        c0.lowerWick >= c0.totalRange * 0.65 && c0.upperWick <= c0.totalRange * 0.15 -> {
          addContextualPattern(
            name = "Dragonfly Doji",
            dir = InternalDirectionState.BULLISH,
            baseStrength = InternalStrengthLabel.MODERATE,
            positions = listOf(lastIdx),
            description = "Long lower shadow doji showing buyer rejection at $locationLabel"
          )
        }
        c0.upperWick >= c0.totalRange * 0.65 && c0.lowerWick <= c0.totalRange * 0.15 -> {
          addContextualPattern(
            name = "Gravestone Doji",
            dir = InternalDirectionState.BEARISH,
            baseStrength = InternalStrengthLabel.MODERATE,
            positions = listOf(lastIdx),
            description = "Long upper shadow doji showing seller rejection at $locationLabel"
          )
        }
        else -> {
          addContextualPattern(
            name = "Doji",
            dir = InternalDirectionState.NEUTRAL,
            baseStrength = InternalStrengthLabel.MODERATE,
            positions = listOf(lastIdx),
            description = "Indecision candle in $prevMarketContext"
          )
        }
      }
    }

    // Spinning Top (small real body centered between balanced upper and lower wicks)
    if (!isDojiBody && c0.totalRange >= avgRange * 0.55 &&
      c0.bodySize in (c0.totalRange * 0.15)..(c0.totalRange * 0.35) &&
      c0.upperWick >= c0.bodySize * 0.75 &&
      c0.lowerWick >= c0.bodySize * 0.75
    ) {
      addContextualPattern(
        name = "Spinning Top",
        dir = InternalDirectionState.NEUTRAL,
        baseStrength = InternalStrengthLabel.WEAK,
        positions = listOf(lastIdx),
        description = "Balanced upper and lower wicks showing market hesitation"
      )
    }

    // Marubozu
    if (c0.bodySize >= avgBody * 1.45 && c0.upperWick <= c0.bodySize * 0.12 && c0.lowerWick <= c0.bodySize * 0.12) {
      addContextualPattern(
        name = "Marubozu",
        dir = if (c0.isBullish) InternalDirectionState.BULLISH else InternalDirectionState.BEARISH,
        baseStrength = InternalStrengthLabel.STRONG,
        positions = listOf(lastIdx),
        description = "${if (c0.isBullish) "Bullish" else "Bearish"} full-body impulse candle"
      )
    }

    // Hammer & Hanging Man
    val isLowerPin = !isDojiBody && c0.totalRange >= avgRange * 0.6 &&
      c0.lowerWick >= maxOf(c0.bodySize * 1.9, c0.totalRange * 0.55) &&
      c0.upperWick <= c0.totalRange * 0.22

    if (isLowerPin) {
      if (isPriorDowntrend || !isPriorUptrend) {
        addContextualPattern(
          name = "Hammer",
          dir = InternalDirectionState.BULLISH,
          baseStrength = if (isPriorDowntrend) InternalStrengthLabel.STRONG else InternalStrengthLabel.MODERATE,
          positions = listOf(lastIdx),
          description = "Bullish lower-wick rejection at $locationLabel"
        )
      } else {
        addContextualPattern(
          name = "Hanging Man",
          dir = InternalDirectionState.BEARISH,
          baseStrength = InternalStrengthLabel.MODERATE,
          positions = listOf(lastIdx),
          description = "Lower-wick warning candle after $prevMarketContext"
        )
      }
    }

    // Shooting Star & Inverted Hammer
    val isUpperPin = !isDojiBody && c0.totalRange >= avgRange * 0.6 &&
      c0.upperWick >= maxOf(c0.bodySize * 1.9, c0.totalRange * 0.55) &&
      c0.lowerWick <= c0.totalRange * 0.22

    if (isUpperPin) {
      if (isPriorUptrend || !isPriorDowntrend) {
        addContextualPattern(
          name = "Shooting Star",
          dir = InternalDirectionState.BEARISH,
          baseStrength = if (isPriorUptrend) InternalStrengthLabel.STRONG else InternalStrengthLabel.MODERATE,
          positions = listOf(lastIdx),
          description = "Bearish upper-wick rejection at $locationLabel"
        )
      } else {
        addContextualPattern(
          name = "Inverted Hammer",
          dir = InternalDirectionState.BULLISH,
          baseStrength = InternalStrengthLabel.MODERATE,
          positions = listOf(lastIdx),
          description = "Upside test candle after $prevMarketContext"
        )
      }
    }

    // ========================================================================
    // Two-Candle Patterns
    // ========================================================================
    if (c1.isBearish && c0.isBullish &&
      c0.close >= c1.open && c0.open <= c1.close &&
      c0.bodySize > c1.bodySize * 1.08
    ) {
      addContextualPattern(
        name = "Bullish Engulfing",
        dir = InternalDirectionState.BULLISH,
        baseStrength = if (isPriorDowntrend) InternalStrengthLabel.STRONG else InternalStrengthLabel.MODERATE,
        positions = listOf(lastIdx - 1, lastIdx),
        description = "Bullish body engulfs prior bearish candle at $locationLabel"
      )
    }

    if (c1.isBullish && c0.isBearish &&
      c0.open >= c1.close && c0.close <= c1.open &&
      c0.bodySize > c1.bodySize * 1.08
    ) {
      addContextualPattern(
        name = "Bearish Engulfing",
        dir = InternalDirectionState.BEARISH,
        baseStrength = if (isPriorUptrend) InternalStrengthLabel.STRONG else InternalStrengthLabel.MODERATE,
        positions = listOf(lastIdx - 1, lastIdx),
        description = "Bearish body engulfs prior bullish candle at $locationLabel"
      )
    }

    if (c1.isBearish && c0.isBullish &&
      c1.bodySize >= avgBody * 0.9 &&
      c0.bodyTop <= c1.bodyTop && c0.bodyBottom >= c1.bodyBottom &&
      c0.bodySize <= c1.bodySize * 0.65
    ) {
      addContextualPattern(
        name = "Bullish Harami",
        dir = InternalDirectionState.BULLISH,
        baseStrength = InternalStrengthLabel.MODERATE,
        positions = listOf(lastIdx - 1, lastIdx),
        description = "Inside bullish candle within prior bearish body"
      )
    }

    if (c1.isBullish && c0.isBearish &&
      c1.bodySize >= avgBody * 0.9 &&
      c0.bodyTop <= c1.bodyTop && c0.bodyBottom >= c1.bodyBottom &&
      c0.bodySize <= c1.bodySize * 0.65
    ) {
      addContextualPattern(
        name = "Bearish Harami",
        dir = InternalDirectionState.BEARISH,
        baseStrength = InternalStrengthLabel.MODERATE,
        positions = listOf(lastIdx - 1, lastIdx),
        description = "Inside bearish candle within prior bullish body"
      )
    }

    val c1Midpoint = (c1.open + c1.close) / 2.0
    if (c1.isBearish && c0.isBullish &&
      c1.bodySize >= avgBody * 0.8 &&
      c0.open <= c1.close &&
      c0.close > c1Midpoint && c0.close < c1.open
    ) {
      addContextualPattern(
        name = "Piercing Pattern",
        dir = InternalDirectionState.BULLISH,
        baseStrength = InternalStrengthLabel.MODERATE,
        positions = listOf(lastIdx - 1, lastIdx),
        description = "Bullish recovery closing above prior bearish midpoint"
      )
    }

    if (c1.isBullish && c0.isBearish &&
      c1.bodySize >= avgBody * 0.8 &&
      c0.open >= c1.close &&
      c0.close < c1Midpoint && c0.close > c1.open
    ) {
      addContextualPattern(
        name = "Dark Cloud Cover",
        dir = InternalDirectionState.BEARISH,
        baseStrength = InternalStrengthLabel.MODERATE,
        positions = listOf(lastIdx - 1, lastIdx),
        description = "Bearish reversal closing below prior bullish midpoint"
      )
    }

    if (c1.isBullish && c0.isBearish &&
      abs(c1.high - c0.high) <= avgRange * 0.12 &&
      (c1.upperWick > 0 || c0.upperWick > 0)
    ) {
      addContextualPattern(
        name = "Tweezer Top",
        dir = InternalDirectionState.BEARISH,
        baseStrength = InternalStrengthLabel.MODERATE,
        positions = listOf(lastIdx - 1, lastIdx),
        description = "Double high rejection at $locationLabel"
      )
    }

    if (c1.isBearish && c0.isBullish &&
      abs(c1.low - c0.low) <= avgRange * 0.12 &&
      (c1.lowerWick > 0 || c0.lowerWick > 0)
    ) {
      addContextualPattern(
        name = "Tweezer Bottom",
        dir = InternalDirectionState.BULLISH,
        baseStrength = InternalStrengthLabel.MODERATE,
        positions = listOf(lastIdx - 1, lastIdx),
        description = "Double low rejection at $locationLabel"
      )
    }

    // ========================================================================
    // Three+ Candle Patterns
    // ========================================================================
    val c2Mid = (c2.open + c2.close) / 2.0
    if (c2.isBearish && c2.bodySize >= avgBody * 0.85 &&
      c1.bodySize <= c2.bodySize * 0.45 &&
      c0.isBullish && c0.close >= c2Mid
    ) {
      addContextualPattern(
        name = "Morning Star",
        dir = InternalDirectionState.BULLISH,
        baseStrength = InternalStrengthLabel.STRONG,
        positions = listOf(lastIdx - 2, lastIdx - 1, lastIdx),
        description = "Three-candle bullish reversal at $locationLabel"
      )
    }

    if (c2.isBullish && c2.bodySize >= avgBody * 0.85 &&
      c1.bodySize <= c2.bodySize * 0.45 &&
      c0.isBearish && c0.close <= c2Mid
    ) {
      addContextualPattern(
        name = "Evening Star",
        dir = InternalDirectionState.BEARISH,
        baseStrength = InternalStrengthLabel.STRONG,
        positions = listOf(lastIdx - 2, lastIdx - 1, lastIdx),
        description = "Three-candle bearish reversal at $locationLabel"
      )
    }

    if (c2.isBullish && c1.isBullish && c0.isBullish &&
      c1.close > c2.close && c0.close > c1.close &&
      c2.bodySize >= avgBody * 0.6 && c1.bodySize >= avgBody * 0.6 && c0.bodySize >= avgBody * 0.6
    ) {
      addContextualPattern(
        name = "Three White Soldiers",
        dir = InternalDirectionState.BULLISH,
        baseStrength = InternalStrengthLabel.STRONG,
        positions = listOf(lastIdx - 2, lastIdx - 1, lastIdx),
        description = "Three consecutive strong bullish candles"
      )
    }

    if (c2.isBearish && c1.isBearish && c0.isBearish &&
      c1.close < c2.close && c0.close < c1.close &&
      c2.bodySize >= avgBody * 0.6 && c1.bodySize >= avgBody * 0.6 && c0.bodySize >= avgBody * 0.6
    ) {
      addContextualPattern(
        name = "Three Black Crows",
        dir = InternalDirectionState.BEARISH,
        baseStrength = InternalStrengthLabel.STRONG,
        positions = listOf(lastIdx - 2, lastIdx - 1, lastIdx),
        description = "Three consecutive strong bearish candles"
      )
    }

    // Three Inside Up (Bearish c2 + Bullish Harami c1 + Bullish confirmation c0 closing above c2 open)
    if (c2.isBearish && c1.isBullish &&
      c1.bodyTop <= c2.bodyTop && c1.bodyBottom >= c2.bodyBottom &&
      c0.isBullish && c0.close > c2.open
    ) {
      addContextualPattern(
        name = "Three Inside Up",
        dir = InternalDirectionState.BULLISH,
        baseStrength = InternalStrengthLabel.STRONG,
        positions = listOf(lastIdx - 2, lastIdx - 1, lastIdx),
        description = "Confirmed bullish harami breakout at $locationLabel"
      )
    }

    // Three Inside Down (Bullish c2 + Bearish Harami c1 + Bearish confirmation c0 closing below c2 open)
    if (c2.isBullish && c1.isBearish &&
      c1.bodyTop <= c2.bodyTop && c1.bodyBottom >= c2.bodyBottom &&
      c0.isBearish && c0.close < c2.open
    ) {
      addContextualPattern(
        name = "Three Inside Down",
        dir = InternalDirectionState.BEARISH,
        baseStrength = InternalStrengthLabel.STRONG,
        positions = listOf(lastIdx - 2, lastIdx - 1, lastIdx),
        description = "Confirmed bearish harami breakdown at $locationLabel"
      )
    }

    // Three Outside Up (Bearish c2 + Bullish Engulfing c1 + Bullish continuation c0 closing higher)
    if (c2.isBearish && c1.isBullish &&
      c1.close >= c2.open && c1.open <= c2.close &&
      c0.isBullish && c0.close > c1.close
    ) {
      addContextualPattern(
        name = "Three Outside Up",
        dir = InternalDirectionState.BULLISH,
        baseStrength = InternalStrengthLabel.STRONG,
        positions = listOf(lastIdx - 2, lastIdx - 1, lastIdx),
        description = "Confirmed bullish engulfing follow-through"
      )
    }

    // Three Outside Down (Bullish c2 + Bearish Engulfing c1 + Bearish continuation c0 closing lower)
    if (c2.isBullish && c1.isBearish &&
      c1.open >= c2.close && c1.close <= c2.open &&
      c0.isBearish && c0.close < c1.close
    ) {
      addContextualPattern(
        name = "Three Outside Down",
        dir = InternalDirectionState.BEARISH,
        baseStrength = InternalStrengthLabel.STRONG,
        positions = listOf(lastIdx - 2, lastIdx - 1, lastIdx),
        description = "Confirmed bearish engulfing follow-through"
      )
    }

    return CandlestickPatternResult(
      detectedPatternName = rawPatterns.firstOrNull()?.patternName,
      patterns = rawPatterns,
      isReady = true
    )
  }
}

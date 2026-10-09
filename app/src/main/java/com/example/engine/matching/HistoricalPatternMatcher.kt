package com.example.engine.matching

import com.example.engine.fingerprint.DefaultPatternFingerprintEngine
import com.example.engine.fingerprint.PatternFingerprintEngine
import com.example.engine.indicator.IndicatorSnapshot
import com.example.engine.memory.BrainObservationRecord
import com.example.model.Candle
import com.example.model.CandleSrPosition
import com.example.model.HistoricalOutcomeDistribution
import com.example.model.HistoricalPatternEvidence
import com.example.model.HistoricalSampleSufficiency
import com.example.model.MarketRegime
import com.example.model.MarketStateFeatureVector
import com.example.model.OutcomeMemoryRecord
import com.example.model.PatternFingerprint
import com.example.model.PriceActionContext
import com.example.model.PriceDirection
import com.example.model.SimilarityQualityLevel
import kotlin.math.abs
import kotlin.math.min

/**
 * Configurable thresholds for Historical Pattern Matching (Part 5 Requirement 3).
 *
 * Ensures tiny sample sizes are never treated as reliable evidence:
 * - < weakEvidenceMinMatches (e.g. 2 matches) -> INSUFFICIENT_EVIDENCE
 * - >= weakEvidenceMinMatches (default 10) -> WEAK_EVIDENCE
 * - >= moderateEvidenceMinMatches (default 25) -> MODERATE_EVIDENCE
 * - >= potentiallyUsefulMinMatches (default 50) -> POTENTIALLY_USEFUL
 */
data class PatternMatchingConfig(
  val similarityThreshold: Double = 0.72,
  val highQualitySimilarityThreshold: Double = 0.84,
  val weakEvidenceMinMatches: Int = 10,
  val moderateEvidenceMinMatches: Int = 25,
  val potentiallyUsefulMinMatches: Int = 50
)

/**
 * Result of comparing the current market state against genuinely stored historical setups (Part 4).
 */
data class HistoricalPatternMatchSummary(
  val totalStoredObservations: Int = 0,
  val matchingHistoricalSetupsCount: Int = 0,
  val topSimilarityScore: Double = 0.0,
  val isHistorySufficient: Boolean = false
)

/**
 * 14 (Part 4) & 3 (Part 5). Historical Pattern Matching Engine.
 *
 * Compares the current normalized [PatternFingerprint] (and [MarketStateFeatureVector])
 * against previously stored REAL market states in [com.example.engine.memory.ProTraderBrainMemory].
 *
 * Searches for:
 * - Similar candle sequences
 * - Similar price-action structures
 * - Similar support/resistance situations
 * - Similar momentum conditions
 * - Similar market regimes
 */
interface HistoricalPatternMatcher {
  val config: PatternMatchingConfig

  fun buildFeatureVector(
    candles: List<Candle>,
    indicators: IndicatorSnapshot,
    priceActionContext: PriceActionContext,
    marketRegime: MarketRegime
  ): MarketStateFeatureVector

  fun computeSimilarity(
    current: MarketStateFeatureVector,
    historical: MarketStateFeatureVector
  ): Double

  fun computeFingerprintSimilarity(
    current: PatternFingerprint,
    historical: PatternFingerprint
  ): Double

  fun findSimilarHistoricalSetups(
    currentVector: MarketStateFeatureVector,
    storedObservations: List<BrainObservationRecord>,
    similarityThreshold: Double = config.similarityThreshold
  ): HistoricalPatternMatchSummary

  fun evaluateHistoricalPatternEvidence(
    currentFingerprint: PatternFingerprint,
    outcomeRecords: List<OutcomeMemoryRecord>,
    matchingConfig: PatternMatchingConfig = config
  ): HistoricalPatternEvidence
}

class DefaultHistoricalPatternMatcher(
  override val config: PatternMatchingConfig = PatternMatchingConfig(),
  private val fingerprintEngine: PatternFingerprintEngine = DefaultPatternFingerprintEngine()
) : HistoricalPatternMatcher {

  override fun buildFeatureVector(
    candles: List<Candle>,
    indicators: IndicatorSnapshot,
    priceActionContext: PriceActionContext,
    marketRegime: MarketRegime
  ): MarketStateFeatureVector {
    val recent = candles.takeLast(min(8, candles.size))
    if (recent.isEmpty()) return MarketStateFeatureVector()

    val avgBody = candles.takeLast(min(25, candles.size)).map { it.bodySize }.average().coerceAtLeast(1e-7)

    val directions = recent.map {
      when {
        it.close > it.open -> 1
        it.close < it.open -> -1
        else -> 0
      }
    }

    val relBodies = recent.map { (it.bodySize / avgBody).coerceIn(0.0, 4.0) }
    val wickRatios = recent.map {
      val range = it.totalRange.coerceAtLeast(1e-7)
      ((it.upperWick + it.lowerWick) / range).coerceIn(0.0, 1.0)
    }

    val srPos = priceActionContext.latestCandleStructure?.positionRelativeToSr
      ?: CandleSrPosition.MID_RANGE

    return MarketStateFeatureVector(
      candleDirections = directions,
      relativeBodySizes = relBodies,
      wickRatios = wickRatios,
      priceStructure = priceActionContext.swingStructure.structureType,
      momentumState = priceActionContext.momentumState.direction,
      volatilityState = indicators.atrState.classification,
      srRelationship = srPos,
      indicatorBias = indicators.emaSignal.direction,
      primaryPatternName = priceActionContext.candlePatternState.firstOrNull()?.patternName,
      marketRegime = marketRegime
    )
  }

  override fun computeSimilarity(
    current: MarketStateFeatureVector,
    historical: MarketStateFeatureVector
  ): Double {
    if (current.candleDirections.isEmpty() || historical.candleDirections.isEmpty()) {
      return 0.0
    }

    var score = 0.0
    var totalWeight = 0.0

    // 1. Candle direction sequence match (weight 0.25)
    val len = min(current.candleDirections.size, historical.candleDirections.size)
    if (len > 0) {
      val cDirs = current.candleDirections.takeLast(len)
      val hDirs = historical.candleDirections.takeLast(len)
      val matches = cDirs.indices.count { cDirs[it] == hDirs[it] }
      score += (matches.toDouble() / len.toDouble()) * 0.25
      totalWeight += 0.25
    }

    // 2. Relative body size similarity (weight 0.15)
    val bLen = min(current.relativeBodySizes.size, historical.relativeBodySizes.size)
    if (bLen > 0) {
      val cBodies = current.relativeBodySizes.takeLast(bLen)
      val hBodies = historical.relativeBodySizes.takeLast(bLen)
      val avgDiff = cBodies.indices.map { abs(cBodies[it] - hBodies[it]) }.average()
      val bodySim = (1.0 - (avgDiff / 2.0)).coerceIn(0.0, 1.0)
      score += bodySim * 0.15
      totalWeight += 0.15
    }

    // 3. Price structure match (weight 0.15)
    if (current.priceStructure == historical.priceStructure) {
      score += 0.15
    }
    totalWeight += 0.15

    // 4. Support / Resistance relationship match (weight 0.15)
    if (current.srRelationship == historical.srRelationship) {
      score += 0.15
    }
    totalWeight += 0.15

    // 5. Momentum & Indicator bias match (weight 0.15)
    if (current.momentumState == historical.momentumState) score += 0.08
    if (current.indicatorBias == historical.indicatorBias) score += 0.07
    totalWeight += 0.15

    // 6. Market regime & pattern match (weight 0.15)
    if (current.marketRegime == historical.marketRegime) score += 0.10
    if (current.primaryPatternName != null && current.primaryPatternName == historical.primaryPatternName) {
      score += 0.05
    }
    totalWeight += 0.15

    return if (totalWeight > 0) (score / totalWeight).coerceIn(0.0, 1.0) else 0.0
  }

  override fun computeFingerprintSimilarity(
    current: PatternFingerprint,
    historical: PatternFingerprint
  ): Double {
    if (current.candleDirectionSequence.isEmpty() || historical.candleDirectionSequence.isEmpty()) {
      return 0.0
    }

    var score = 0.0
    var totalWeight = 0.0

    // 1. Candle sequence similarity (directions + body sizes + wick ratios: weight 0.30)
    val dLen = min(current.candleDirectionSequence.size, historical.candleDirectionSequence.size)
    if (dLen > 0) {
      val cDirs = current.candleDirectionSequence.takeLast(dLen)
      val hDirs = historical.candleDirectionSequence.takeLast(dLen)
      val dirMatches = cDirs.indices.count { cDirs[it] == hDirs[it] }
      score += (dirMatches.toDouble() / dLen.toDouble()) * 0.16
      totalWeight += 0.16
    }

    val bLen = min(current.relativeCandleBodySizes.size, historical.relativeCandleBodySizes.size)
    if (bLen > 0) {
      val cBodies = current.relativeCandleBodySizes.takeLast(bLen)
      val hBodies = historical.relativeCandleBodySizes.takeLast(bLen)
      val avgBodyDiff = cBodies.indices.map { abs(cBodies[it] - hBodies[it]) }.average()
      val bodySim = (1.0 - (avgBodyDiff / 2.0)).coerceIn(0.0, 1.0)
      score += bodySim * 0.08
      totalWeight += 0.08
    }

    val wLen = min(current.wickRatios.size, historical.wickRatios.size)
    if (wLen > 0) {
      val cWicks = current.wickRatios.takeLast(wLen)
      val hWicks = historical.wickRatios.takeLast(wLen)
      val avgWickDiff = cWicks.indices.map { abs(cWicks[it] - hWicks[it]) }.average()
      val wickSim = (1.0 - avgWickDiff).coerceIn(0.0, 1.0)
      score += wickSim * 0.06
      totalWeight += 0.06
    }

    // 2. Price-action structure, BOS/CHoCH, breakout & reversal similarity (weight 0.22)
    if (current.trendStructure == historical.trendStructure) score += 0.10
    if (current.bosState == historical.bosState) score += 0.04
    if (current.chochState == historical.chochState) score += 0.04
    if (current.breakoutState == historical.breakoutState) score += 0.02
    if (current.reversalState == historical.reversalState) score += 0.02
    totalWeight += 0.22

    // 3. Support / Resistance relationship (weight 0.16)
    if (current.supportResistanceRelationship == historical.supportResistanceRelationship) {
      score += 0.16
    }
    totalWeight += 0.16

    // 4. Momentum & Volatility behavior (weight 0.14)
    if (current.momentumBehavior == historical.momentumBehavior) score += 0.07
    if (current.isMomentumExhausted == historical.isMomentumExhausted) score += 0.03
    if (current.volatilityBehavior == historical.volatilityBehavior) score += 0.04
    totalWeight += 0.14

    // 5. Market Regime & Indicator / Pattern alignment (weight 0.18)
    if (current.marketRegime == historical.marketRegime) score += 0.10
    if (current.indicatorStates.emaDirection == historical.indicatorStates.emaDirection) score += 0.03
    if (current.indicatorStates.rsiZone == historical.indicatorStates.rsiZone) score += 0.02
    val sharedPatterns = current.candlestickPatterns.intersect(historical.candlestickPatterns.toSet())
    if (sharedPatterns.isNotEmpty() ||
      (current.candlestickPatterns.isEmpty() && historical.candlestickPatterns.isEmpty())
    ) {
      score += 0.03
    }
    totalWeight += 0.18

    return if (totalWeight > 0.0) (score / totalWeight).coerceIn(0.0, 1.0) else 0.0
  }

  override fun findSimilarHistoricalSetups(
    currentVector: MarketStateFeatureVector,
    storedObservations: List<BrainObservationRecord>,
    similarityThreshold: Double
  ): HistoricalPatternMatchSummary {
    if (storedObservations.isEmpty()) {
      return HistoricalPatternMatchSummary()
    }

    val completedObservations = storedObservations.filter { it.futureOutcome != null }
    val similarities = completedObservations.map { obs ->
      computeSimilarity(currentVector, obs.featureVector)
    }

    val matches = similarities.count { it >= similarityThreshold }
    val topSim = similarities.maxOrNull() ?: 0.0

    return HistoricalPatternMatchSummary(
      totalStoredObservations = storedObservations.size,
      matchingHistoricalSetupsCount = matches,
      topSimilarityScore = topSim,
      isHistorySufficient = matches >= config.weakEvidenceMinMatches
    )
  }

  override fun evaluateHistoricalPatternEvidence(
    currentFingerprint: PatternFingerprint,
    outcomeRecords: List<OutcomeMemoryRecord>,
    matchingConfig: PatternMatchingConfig
  ): HistoricalPatternEvidence {
    if (outcomeRecords.isEmpty() || currentFingerprint.candleDirectionSequence.isEmpty()) {
      return HistoricalPatternEvidence(
        totalEvaluatedRecords = outcomeRecords.size,
        sampleSufficiency = HistoricalSampleSufficiency.INSUFFICIENT_EVIDENCE,
        isSampleSufficient = false,
        summaryDescription = "No completed historical setups recorded yet"
      )
    }

    val scoredMatches = ArrayList<Pair<OutcomeMemoryRecord, Double>>()
    var topSim = 0.0

    for (record in outcomeRecords) {
      val sim = computeFingerprintSimilarity(currentFingerprint, record.patternFingerprint)
      if (sim > topSim) topSim = sim
      if (sim >= matchingConfig.similarityThreshold) {
        scoredMatches.add(record to sim)
      }
    }

    val matchCount = scoredMatches.size
    val avgSim = if (scoredMatches.isNotEmpty()) {
      scoredMatches.map { it.second }.average()
    } else {
      0.0
    }

    var bullCount = 0
    var bearCount = 0
    var flatCount = 0
    for ((rec, _) in scoredMatches) {
      when (rec.actualNextCandleDirection) {
        PriceDirection.UP -> bullCount++
        PriceDirection.DOWN -> bearCount++
        PriceDirection.FLAT -> flatCount++
      }
    }

    val total = (bullCount + bearCount + flatCount).coerceAtLeast(1)
    val distribution = HistoricalOutcomeDistribution(
      bullishOutcomes = bullCount,
      bearishOutcomes = bearCount,
      flatOutcomes = flatCount,
      bullishRatio = if (matchCount > 0) bullCount.toDouble() / total.toDouble() else 0.0,
      bearishRatio = if (matchCount > 0) bearCount.toDouble() / total.toDouble() else 0.0
    )

    val similarityQuality = when {
      matchCount == 0 -> SimilarityQualityLevel.NONE
      avgSim >= matchingConfig.highQualitySimilarityThreshold -> SimilarityQualityLevel.HIGH
      avgSim >= matchingConfig.similarityThreshold -> SimilarityQualityLevel.MODERATE
      else -> SimilarityQualityLevel.LOW
    }

    // Enforce Part 5 Requirement 3: never treat a tiny number of matches (e.g. 2 matches) as reliable evidence
    val sufficiency = when {
      matchCount >= matchingConfig.potentiallyUsefulMinMatches &&
        (similarityQuality == SimilarityQualityLevel.HIGH || similarityQuality == SimilarityQualityLevel.MODERATE) ->
        HistoricalSampleSufficiency.POTENTIALLY_USEFUL
      matchCount >= matchingConfig.moderateEvidenceMinMatches ->
        HistoricalSampleSufficiency.MODERATE_EVIDENCE
      matchCount >= matchingConfig.weakEvidenceMinMatches ->
        HistoricalSampleSufficiency.WEAK_EVIDENCE
      else ->
        HistoricalSampleSufficiency.INSUFFICIENT_EVIDENCE
    }

    val isSufficient = sufficiency == HistoricalSampleSufficiency.MODERATE_EVIDENCE ||
      sufficiency == HistoricalSampleSufficiency.POTENTIALLY_USEFUL

    val summary = when (sufficiency) {
      HistoricalSampleSufficiency.INSUFFICIENT_EVIDENCE ->
        "$matchCount historical matches (insufficient sample — minimum ${matchingConfig.weakEvidenceMinMatches} required)"
      HistoricalSampleSufficiency.WEAK_EVIDENCE ->
        "$matchCount historical matches (weak sample)"
      HistoricalSampleSufficiency.MODERATE_EVIDENCE ->
        "$matchCount historical matches (moderate sample)"
      HistoricalSampleSufficiency.POTENTIALLY_USEFUL ->
        "$matchCount quality historical matches (statistically meaningful sample)"
    }

    return HistoricalPatternEvidence(
      historicalMatchesCount = matchCount,
      totalEvaluatedRecords = outcomeRecords.size,
      similarityQuality = similarityQuality,
      averageSimilarityScore = avgSim,
      topSimilarityScore = topSim,
      outcomeDistribution = distribution,
      sampleSufficiency = sufficiency,
      isSampleSufficient = isSufficient,
      summaryDescription = summary
    )
  }
}

package com.example

import android.graphics.Color
import com.example.data.candle.CandleDataManager
import com.example.data.candle.DemoPlaceholderCandleSeeder
import com.example.data.candle.RollingCandleRepository
import com.example.engine.memory.DefaultProTraderBrainMemory
import com.example.engine.movement.DefaultPriceMovementDetector
import com.example.engine.quality.DataQualityCheckInput
import com.example.engine.quality.DefaultDataQualityEngine
import com.example.engine.region.ChartRegion
import com.example.engine.region.DefaultChartRegionManager
import com.example.engine.sequence.CandleSequenceEngine
import com.example.engine.sequence.DefaultCandleSequenceEngine
import com.example.engine.vision.DefaultVisualCandleDetector
import com.example.model.AnalyzerRunState
import com.example.model.ExtractionQuality
import com.example.model.NextCandleSignal
import com.example.model.PriceDirection
import com.example.ui.OtcVisionViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun rollingCandleRepository_supportsAtLeast100Candles_andCurrentCandleTracking() = runTest {
    val repo = RollingCandleRepository(maxCapacity = CandleDataManager.DEFAULT_ROLLING_CAPACITY)
    val initial = DemoPlaceholderCandleSeeder.generateInitialDemoCandles(count = 105)
    repo.replaceAllCandles(initial)

    assertEquals(105, repo.candlesFlow.value.size)
    assertEquals(100, repo.getRecentCandles(100).size)
    assertTrue(repo.getRecentCandles(40).size >= 40)

    val activeBefore = repo.currentCandleFlow.value
    assertNotNull(activeBefore)
    assertFalse(activeBefore!!.isComplete)

    val spikedHighPrice = activeBefore.high + 0.00100
    repo.updateCurrentCandle(price = spikedHighPrice, volumeDelta = 5.0)
    val activeAfterTick = repo.currentCandleFlow.value!!
    assertEquals(spikedHighPrice, activeAfterTick.close, 1e-6)
    assertEquals(spikedHighPrice, activeAfterTick.high, 1e-6)
    assertFalse(activeAfterTick.isComplete)

    val tracker = repo.currentCandleTrackerFlow.value
    assertNotNull(tracker)
    assertEquals(spikedHighPrice, tracker!!.currentPrice, 1e-6)
    assertEquals(PriceDirection.UP, tracker.direction)

    repo.closeCurrentCandle()
    val completedCandle = repo.currentCandleFlow.value!!
    assertTrue(completedCandle.isComplete)
  }

  @Test
  fun chartRegionManager_selectReselectAndClearSelection() {
    val manager = DefaultChartRegionManager()
    assertFalse(manager.hasValidSelection())

    manager.selectChartRegion(ChartRegion.DEFAULT_CENTER_CHART_REGION)
    assertTrue(manager.hasValidSelection())
    assertEquals("Standard Chart Area", manager.selectedRegion.value?.label)

    manager.reselectChartRegion(ChartRegion.UPPER_SPLIT_CHART_REGION)
    assertTrue(manager.hasValidSelection())
    assertEquals("Top Half Chart Area", manager.selectedRegion.value?.label)

    manager.clearSelection()
    assertFalse(manager.hasValidSelection())
    assertNull(manager.selectedRegion.value)
  }

  @Test
  fun candleSequenceEngine_preservesMultiScaleWindowsUpTo100() {
    val engine = DefaultCandleSequenceEngine()
    val candles = DemoPlaceholderCandleSeeder.generateInitialDemoCandles(count = 110)
    engine.updateSequence(candles)

    val allWindows = engine.getAllSupportedWindows()
    for (windowSize in CandleSequenceEngine.SUPPORTED_WINDOWS) {
      assertEquals(windowSize, allWindows[windowSize]?.size)
    }
    assertEquals("Full memory active", engine.getSequenceStatusWord())
  }

  private fun packRgb(r: Int, g: Int, b: Int): Int {
    return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
  }

  @Test
  fun visualCandleDetector_returnsUnreadableForBlankImage_andDetectsCandlesInChartFrame() {
    val detector = DefaultVisualCandleDetector()
    val width = 120
    val height = 80
    val blankPixels = IntArray(width * height) { packRgb(10, 14, 24) }

    val blankResult = detector.extractFromPixelBuffer(blankPixels, width, height)
    assertEquals(ExtractionQuality.UNREADABLE, blankResult.extractionQuality)
    assertEquals("WAIT — Chart data unclear", blankResult.diagnosticReason)

    // Paint 10 synthetic alternating green bullish and red bearish candles into the buffer
    val chartPixels = IntArray(width * height) { packRgb(10, 14, 24) }
    for (candleIdx in 0 until 10) {
      val isBull = candleIdx % 2 == 0
      val color = if (isBull) packRgb(0, 230, 118) else packRgb(255, 59, 92)
      val startX = 6 + candleIdx * 11
      for (x in startX until (startX + 5)) {
        for (y in 20..55) {
          chartPixels[y * width + x] = color
        }
      }
    }

    val validResult = detector.extractFromPixelBuffer(chartPixels, width, height)
    assertEquals(ExtractionQuality.HIGH, validResult.extractionQuality)
    assertEquals(10, validResult.detectedCandles.size)
    assertNotNull(validResult.currentFormingCandle)
    assertTrue(validResult.currentFormingCandle!!.isCurrentFormingCandle)
  }

  @Test
  fun priceMovementDetector_tracksIntraCandleMicroStructure() {
    val detector = DefaultPriceMovementDetector()
    val baseTime = 1_700_000_000_000L
    detector.recordPriceUpdate(1.08400, baseTime)
    detector.recordPriceUpdate(1.08410, baseTime + 500)
    detector.recordPriceUpdate(1.08425, baseTime + 1000)
    val snap = detector.recordPriceUpdate(1.08460, baseTime + 1500)

    assertEquals(4, snap.recentTicksCount)
    assertEquals(PriceDirection.UP, snap.lastDirection)
    assertTrue(snap.momentum > 0.0)
    assertTrue(detector.isPriceUpdatingRecently(baseTime + 2000))
  }

  @Test
  fun dataQualityEngine_enforcesWaitAndErrorMessagesWhenRequirementsNotMet() {
    val qualityEngine = DefaultDataQualityEngine()
    val sampleCandles = DemoPlaceholderCandleSeeder.generateInitialDemoCandles(count = 20)

    // 1. Screen capture stopped
    val stoppedEval = qualityEngine.evaluateQuality(
      DataQualityCheckInput(
        isScreenCaptureActive = false,
        isChartRegionValid = true,
        isChartVisible = false,
        extractionQuality = ExtractionQuality.UNREADABLE,
        isCandleDetectionStable = false,
        candles = emptyList(),
        currentCandle = null,
        isPriceMovementUpdating = false,
        wasCaptureRecentlyStopped = true
      )
    )
    assertFalse(stoppedEval.isReliableForAnalysis)
    assertEquals(NextCandleSignal.WAIT, stoppedEval.recommendedSignal)
    assertEquals("Screen capture stopped", stoppedEval.primaryReasonMessage)

    // 2. Invalid chart region
    val invalidRegionEval = qualityEngine.evaluateQuality(
      DataQualityCheckInput(
        isScreenCaptureActive = true,
        isChartRegionValid = false,
        isChartVisible = false,
        extractionQuality = ExtractionQuality.UNREADABLE,
        isCandleDetectionStable = false,
        candles = sampleCandles,
        currentCandle = sampleCandles.last(),
        isPriceMovementUpdating = true
      )
    )
    assertEquals("Please select the chart area again", invalidRegionEval.primaryReasonMessage)

    // 3. Low extraction quality -> "WAIT — Chart data unclear"
    val lowQualityEval = qualityEngine.evaluateQuality(
      DataQualityCheckInput(
        isScreenCaptureActive = true,
        isChartRegionValid = true,
        isChartVisible = true,
        extractionQuality = ExtractionQuality.LOW,
        isCandleDetectionStable = true,
        candles = sampleCandles,
        currentCandle = sampleCandles.last(),
        isPriceMovementUpdating = true
      )
    )
    assertEquals("WAIT — Chart data unclear", lowQualityEval.primaryReasonMessage)

    // 4. Not enough candles -> "Collecting candle data..."
    val collectingEval = qualityEngine.evaluateQuality(
      DataQualityCheckInput(
        isScreenCaptureActive = true,
        isChartRegionValid = true,
        isChartVisible = true,
        extractionQuality = ExtractionQuality.HIGH,
        isCandleDetectionStable = true,
        candles = sampleCandles.take(2),
        currentCandle = sampleCandles.first(),
        isPriceMovementUpdating = true,
        minRequiredCandles = 5
      )
    )
    assertEquals("Collecting candle data...", collectingEval.primaryReasonMessage)
  }

  @Test
  fun proTraderBrainMemory_storesCompletedCandleObservations() {
    val memory = DefaultProTraderBrainMemory()
    val candles = DemoPlaceholderCandleSeeder.generateInitialDemoCandles(count = 45)
    memory.recordCompletedSequence(
      recentCandleSequence = candles,
      marketState = "CONSOLIDATION",
      detectedPatterns = listOf("INSIDE_BAR"),
      predictionResult = NextCandleSignal.WAIT
    )
    assertEquals(1, memory.getObservationCount())
    assertEquals(40, memory.getRecentObservations().first().recentCandleSequence.size)
  }

  @Test
  fun viewModel_controlsAnalyzerAndRegionStates_withoutFakePredictions() {
    val viewModel = OtcVisionViewModel()
    assertEquals(AnalyzerRunState.OFF, viewModel.uiState.value.analyzerState)
    assertEquals("Analyzer: OFF", viewModel.uiState.value.analyzerStatusHeaderText)
    assertEquals("Screen Capture: OFF", viewModel.uiState.value.screenCaptureHeaderText)
    assertEquals(NextCandleSignal.WAITING, viewModel.uiState.value.nextCandleSignal)

    viewModel.startAnalyzer()
    assertEquals(AnalyzerRunState.ACTIVE, viewModel.uiState.value.analyzerState)
    assertEquals("Analyzer: ACTIVE", viewModel.uiState.value.analyzerStatusHeaderText)

    viewModel.pauseAnalyzer()
    assertEquals(AnalyzerRunState.PAUSED, viewModel.uiState.value.analyzerState)

    viewModel.stopAnalyzer()
    assertEquals(AnalyzerRunState.OFF, viewModel.uiState.value.analyzerState)

    viewModel.clearChartRegionSelection()
    assertFalse(viewModel.uiState.value.isChartRegionValid)
    assertEquals("Please select the chart area again", viewModel.uiState.value.nextCandleSubtitle)
  }

  @Test
  fun part3MarketAnalysisEngines_computeIndicatorsPatternsRegimeAndConflictDetection() {
    val candles = DemoPlaceholderCandleSeeder.generateInitialDemoCandles(count = 60)
    val sequenceEngine = DefaultCandleSequenceEngine()
    sequenceEngine.updateSequence(candles)
    val sequenceFeatures = sequenceEngine.analyzeSequenceFeatures(candles)

    val indicatorEngine = com.example.engine.indicator.DefaultIndicatorEngine()
    val indicators = indicatorEngine.evaluateIndicators(candles)
    assertTrue(indicators.isReady)
    assertNotNull(indicators.emaSignal.ema9)
    assertNotNull(indicators.emaSignal.ema21)
    assertNotNull(indicators.emaSignal.ema50)
    assertNotNull(indicators.smaSignal.sma20)
    assertNotNull(indicators.smaSignal.sma50)
    assertNotNull(indicators.rsiSignal.rsiValue)
    assertNotNull(indicators.macdSignal.macdLine)
    assertNotNull(indicators.bollingerSignal.middleBand)
    assertNotNull(indicators.stochasticSignal.percentK)
    assertNotNull(indicators.atrState.atrValue)
    assertNotNull(indicators.adxSignal.adxValue)
    assertNotNull(indicators.cciSignal.cciValue)
    assertNotNull(indicators.williamsSignal.williamsR)

    val srEngine = com.example.engine.sr.DefaultSupportResistanceEngine()
    val srAnalysis = srEngine.analyzeSupportResistance(candles)
    assertTrue(srAnalysis.supportZones.isNotEmpty() || srAnalysis.resistanceZones.isNotEmpty())

    val paEngine = com.example.engine.priceaction.DefaultPriceActionEngine()
    val paSnapshot = paEngine.analyzePriceAction(candles)
    assertTrue(paSnapshot.isReady)

    val patternEngine = com.example.engine.pattern.DefaultCandlestickPatternEngine()
    val patternResult = patternEngine.detectPatterns(candles)
    assertTrue(patternResult.isReady)

    val regimeEngine = com.example.engine.regime.DefaultMarketRegimeEngine()
    val regime = regimeEngine.detectRegime(
      candles = candles,
      indicators = indicators,
      priceAction = paSnapshot.priceActionState,
      supportResistance = srAnalysis,
      sequenceFeatures = sequenceFeatures
    )
    assertNotNull(regime)

    val consensusEngine = com.example.engine.consensus.DefaultSignalConsensusEngine()
    val marketAnalysis = consensusEngine.buildMarketAnalysis(
      candles = candles,
      dataQuality = ExtractionQuality.HIGH,
      indicators = indicators,
      supportResistance = srAnalysis,
      priceAction = paSnapshot,
      patterns = patternResult,
      sequenceFeatures = sequenceFeatures,
      marketRegime = regime
    )

    // Part 3 strict rule: analysis only, always returns WAIT (never fake UP/DOWN)
    assertEquals(NextCandleSignal.WAIT, marketAnalysis.recommendedSignal)
    assertTrue(marketAnalysis.simpleWhyExplanations.isNotEmpty())
  }

  @Test
  fun part4AdvancedPriceActionAndCandlestickIntelligenceEngines_verifyAllModulesAndSafetyGates() {
    val candles = DemoPlaceholderCandleSeeder.generateInitialDemoCandles(count = 60)

    val srEngine = com.example.engine.sr.DefaultSupportResistanceEngine()
    val srAnalysis = srEngine.analyzeSupportResistance(candles)

    val candleStructEngine = com.example.engine.candlestructure.DefaultAdvancedCandleStructureEngine()
    val structMetrics = candleStructEngine.analyzeCandles(candles, srAnalysis)
    assertEquals(40, structMetrics.size)
    val latestMetric = structMetrics.last()
    assertTrue(latestMetric.totalRange >= 0.0)
    assertTrue(latestMetric.bodyToRangeRatio in 0.0..1.0)
    assertNotNull(latestMetric.primaryClassification)

    val marketStructureEngine = com.example.engine.structure.DefaultMarketStructureEngine()
    val marketStructure = marketStructureEngine.analyzeStructure(candles)
    assertNotNull(marketStructure.structureType)
    assertNotNull(marketStructure.bosState)
    assertNotNull(marketStructure.chochState)

    val liquidityEngine = com.example.engine.liquidity.DefaultLiquiditySweepEngine()
    val liquiditySweep = liquidityEngine.analyzeLiquiditySweeps(candles, srAnalysis)
    assertNotNull(liquiditySweep.classification)

    val paEngine = com.example.engine.priceaction.DefaultPriceActionEngine()
    val paSnapshot = paEngine.analyzePriceAction(candles)
    val indicatorEngine = com.example.engine.indicator.DefaultIndicatorEngine()
    val indicators = indicatorEngine.evaluateIndicators(candles)

    val breakoutEngine = com.example.engine.breakout.DefaultBreakoutEngine()
    val breakoutState = breakoutEngine.analyzeBreakout(
      candles = candles,
      supportResistance = srAnalysis,
      momentum = paSnapshot.priceActionState.momentum,
      indicators = indicators
    )
    assertNotNull(breakoutState.classification)

    val sequenceEngine = DefaultCandleSequenceEngine()
    sequenceEngine.updateSequence(candles)
    val seqFeatures = sequenceEngine.analyzeSequenceFeatures(
      candles = candles,
      isNearResistance = srAnalysis.isNearResistance,
      isNearSupport = srAnalysis.isNearSupport
    )

    val patternEngine = com.example.engine.pattern.DefaultCandlestickPatternEngine()
    val patterns = patternEngine.detectPatterns(
      candles = candles,
      supportResistance = srAnalysis,
      momentum = paSnapshot.priceActionState.momentum,
      indicators = indicators
    )
    assertTrue(patterns.isReady)

    val reversalEngine = com.example.engine.reversal.DefaultReversalEngine()
    val reversalState = reversalEngine.analyzeReversal(
      candles = candles,
      indicators = indicators,
      supportResistance = srAnalysis,
      marketStructure = marketStructure,
      momentum = paSnapshot.priceActionState.momentum,
      patterns = patterns,
      sequenceFeatures = seqFeatures
    )
    assertNotNull(reversalState.classification)

    val regimeEngine = com.example.engine.regime.DefaultMarketRegimeEngine()
    val regime = regimeEngine.detectRegime(
      candles = candles,
      indicators = indicators,
      priceAction = paSnapshot.priceActionState,
      supportResistance = srAnalysis,
      sequenceFeatures = seqFeatures
    )

    val consensusEngine = com.example.engine.consensus.DefaultSignalConsensusEngine()
    val highQualityAnalysis = consensusEngine.buildMarketAnalysis(
      candles = candles,
      dataQuality = ExtractionQuality.HIGH,
      indicators = indicators,
      supportResistance = srAnalysis,
      priceAction = paSnapshot,
      patterns = patterns,
      sequenceFeatures = seqFeatures,
      marketRegime = regime,
      isChartRegionValid = true
    )

    // Verify PriceActionContext, ConflictAnalysis, SetupQuality, FeatureVector, and WAIT gate
    assertNotNull(highQualityAnalysis.priceActionContext.latestCandleStructure)
    assertNotNull(highQualityAnalysis.conflictAnalysis)
    assertNotNull(highQualityAnalysis.setupQualityGrade)
    assertTrue(highQualityAnalysis.featureVector.candleDirections.isNotEmpty())
    assertEquals(NextCandleSignal.WAIT, highQualityAnalysis.recommendedSignal)

    // Verify Data Quality Safety Gate: LOW quality must force NO_SETUP and WAIT
    val lowQualityAnalysis = consensusEngine.buildMarketAnalysis(
      candles = candles,
      dataQuality = ExtractionQuality.LOW,
      indicators = indicators,
      supportResistance = srAnalysis,
      priceAction = paSnapshot,
      patterns = patterns,
      sequenceFeatures = seqFeatures,
      marketRegime = regime,
      isChartRegionValid = true
    )
    assertEquals(com.example.model.SetupQualityGrade.NO_SETUP, lowQualityAnalysis.setupQualityGrade)
    assertEquals(NextCandleSignal.WAIT, lowQualityAnalysis.recommendedSignal)

    // Verify ProTraderBrainMemory next-candle outcome resolution & HistoricalPatternMatcher
    val memory = DefaultProTraderBrainMemory()
    memory.recordCompletedSequence(
      recentCandleSequence = candles.take(45),
      marketState = regime.name,
      marketRegime = regime,
      priceActionContext = highQualityAnalysis.priceActionContext,
      featureVector = highQualityAnalysis.featureVector
    )
    memory.recordCompletedSequence(
      recentCandleSequence = candles.take(46),
      marketState = regime.name,
      marketRegime = regime,
      priceActionContext = highQualityAnalysis.priceActionContext,
      featureVector = highQualityAnalysis.featureVector
    )
    val observations = memory.getRecentObservations()
    assertEquals(2, observations.size)
    // First observation's actual next candle outcome is resolved once the second sequence completes
    assertNotNull(observations.first().futureOutcome)
    assertNotNull(observations.first().actualNextCandleBullish)

    val matcher = com.example.engine.matching.DefaultHistoricalPatternMatcher()
    val matchSummary = matcher.findSimilarHistoricalSetups(
      currentVector = highQualityAnalysis.featureVector,
      storedObservations = observations,
      similarityThreshold = 0.50
    )
    assertTrue(matchSummary.matchingHistoricalSetupsCount >= 1)
    assertTrue(matchSummary.topSimilarityScore >= 0.50)
  }

  @Test
  fun part5ProTraderBrain_snapshotFingerprintContextMatchingMemorySafetyAndBrainAssessment() {
    val candles = DemoPlaceholderCandleSeeder.generateInitialDemoCandles(count = 60)
    val viewModel = OtcVisionViewModel()

    // 1. Verify MemorySafetyValidator rejects corrupted / bad-quality data (Requirement 9)
    val safetyValidator = com.example.engine.safety.DefaultMemorySafetyValidator()
    val validCheck = safetyValidator.validateSequence(candles, ExtractionQuality.HIGH, true)
    assertTrue(validCheck.isValidForMemory)

    // Missing candles (< 5)
    val missingCheck = safetyValidator.validateSequence(candles.take(2), ExtractionQuality.HIGH, true)
    assertFalse(missingCheck.isValidForMemory)
    assertTrue(missingCheck.rejectionReasons.contains(com.example.model.MemoryRejectionReason.MISSING_CANDLES))

    // Unreadable data quality
    val unreadableCheck = safetyValidator.validateSequence(candles, ExtractionQuality.UNREADABLE, true)
    assertFalse(unreadableCheck.isValidForMemory)
    assertTrue(unreadableCheck.rejectionReasons.contains(com.example.model.MemoryRejectionReason.UNREADABLE_CANDLES))

    // Duplicate timestamps
    val duplicateCandles = candles.take(10).toMutableList()
    duplicateCandles[5] = duplicateCandles[4].copy(open = 1.0850)
    val duplicateCheck = safetyValidator.validateSequence(duplicateCandles, ExtractionQuality.HIGH, true)
    assertFalse(duplicateCheck.isValidForMemory)
    assertTrue(duplicateCheck.rejectionReasons.contains(com.example.model.MemoryRejectionReason.DUPLICATE_CANDLES))

    // Impossible OHLC relationship (high < open)
    val impossibleOhlcCandles = candles.take(10).toMutableList()
    val targetC = impossibleOhlcCandles[3]
    impossibleOhlcCandles[3] = targetC.copy(open = 1.0900, high = 1.0800, low = 1.0750, close = 1.0850)
    val impossibleCheck = safetyValidator.validateSequence(impossibleOhlcCandles, ExtractionQuality.HIGH, true)
    assertFalse(impossibleCheck.isValidForMemory)
    assertTrue(
      impossibleCheck.rejectionReasons.contains(
        com.example.model.MemoryRejectionReason.IMPOSSIBLE_OHLC_RELATIONSHIPS
      )
    )

    // Unstable screen detection
    val unstableCheck = safetyValidator.validateSequence(candles, ExtractionQuality.HIGH, isScreenDetectionStable = false)
    assertFalse(unstableCheck.isValidForMemory)
    assertTrue(
      unstableCheck.rejectionReasons.contains(
        com.example.model.MemoryRejectionReason.UNSTABLE_SCREEN_DETECTION
      )
    )

    // 2. Verify Context-Aware Intelligence for Bullish Engulfing in two contrasting contexts (Requirement 5)
    val contextEngine = com.example.engine.context.DefaultContextAwareIntelligenceEngine()
    val bullishEngulfing = com.example.model.DetectedCandlestickPattern(
      patternName = "Bullish Engulfing",
      direction = com.example.model.InternalDirectionState.BULLISH,
      strength = com.example.model.InternalStrengthLabel.MODERATE,
      candlePositions = listOf(38, 39),
      context = "Bullish body engulfs prior bearish candle"
    )

    val strongSupZone = com.example.model.SupportResistanceZone(
      levelPrice = 1.0800,
      lowerBound = 1.0795,
      upperBound = 1.0805,
      touchCount = 4,
      rejectionCount = 3,
      strength = com.example.model.ZoneStrength.STRONG,
      isSupport = true
    )
    val supportExhaustionSnapshot = com.example.model.MarketStateSnapshot(
      recentCandleSequence = candles.takeLast(40),
      supportResistance = com.example.model.SupportResistanceAnalysis(
        nearestSupport = strongSupZone,
        isNearSupport = true,
        supportRejection = true
      ),
      candleSequenceFeatures = com.example.model.CandleSequenceFeatures(
        consecutiveBearishCandles = 4,
        hasExhaustion = true
      ),
      momentum = com.example.model.MomentumAnalysisState(
        isMomentumExhausted = true,
        isDecelerating = true
      ),
      candlestickPatterns = listOf(bullishEngulfing),
      dataQuality = ExtractionQuality.HIGH
    )
    val boostedEval = contextEngine.evaluateSinglePatternInContext(bullishEngulfing, supportExhaustionSnapshot)
    assertTrue(boostedEval.isBoostedByContext)
    assertFalse(boostedEval.isWeakenedByContext)
    assertEquals(com.example.model.InternalStrengthLabel.STRONG, boostedEval.adjustedStrength)
    assertTrue(boostedEval.contextWeightMultiplier > 1.30)

    val strongResZone = com.example.model.SupportResistanceZone(
      levelPrice = 1.0900,
      lowerBound = 1.0895,
      upperBound = 1.0905,
      touchCount = 4,
      rejectionCount = 3,
      strength = com.example.model.ZoneStrength.STRONG,
      isSupport = false
    )
    val resistanceWeakMomSnapshot = com.example.model.MarketStateSnapshot(
      recentCandleSequence = candles.takeLast(40),
      supportResistance = com.example.model.SupportResistanceAnalysis(
        nearestResistance = strongResZone,
        isNearResistance = true,
        resistanceRejection = true
      ),
      momentum = com.example.model.MomentumAnalysisState(
        weakeningMomentum = true,
        direction = com.example.model.InternalDirectionState.NEUTRAL
      ),
      candlestickPatterns = listOf(bullishEngulfing),
      dataQuality = ExtractionQuality.HIGH
    )
    val weakenedEval = contextEngine.evaluateSinglePatternInContext(bullishEngulfing, resistanceWeakMomSnapshot)
    assertTrue(weakenedEval.isWeakenedByContext)
    assertFalse(weakenedEval.isBoostedByContext)
    assertEquals(com.example.model.InternalStrengthLabel.WEAK, weakenedEval.adjustedStrength)
    assertTrue(weakenedEval.contextWeightMultiplier < 0.55)

    // 3. Verify Regime-Specific Reasoning across all 9 MarketRegimes (Requirement 7)
    val regimeReasoningEngine = com.example.engine.regime.DefaultRegimeReasoningEngine()
    val allProfiles = com.example.model.MarketRegime.values().map { r ->
      regimeReasoningEngine.evaluateRegimeProfile(
        supportExhaustionSnapshot.copy(marketRegime = r)
      )
    }
    assertEquals(9, allProfiles.size)
    assertTrue(allProfiles.map { it.reasoningSummary }.distinct().size == 9)

    // 4. Verify Historical Pattern Matching sample sufficiency tiers: 2 vs 10 vs 50+ matches (Requirement 3)
    val fpEngine = com.example.engine.fingerprint.DefaultPatternFingerprintEngine()
    val sampleFp = fpEngine.createFingerprint(supportExhaustionSnapshot)
    assertTrue(sampleFp.candleDirectionSequence.isNotEmpty())

    val matcher = com.example.engine.matching.DefaultHistoricalPatternMatcher()
    fun buildOutcomeRecords(
      count: Int,
      fp: com.example.model.PatternFingerprint = sampleFp
    ): List<com.example.model.OutcomeMemoryRecord> {
      return (1..count).map { idx ->
        com.example.model.OutcomeMemoryRecord(
          id = idx.toLong(),
          timestamp = 1_700_000_000_000L + idx * 5000L,
          setupSnapshot = supportExhaustionSnapshot,
          patternFingerprint = fp,
          predictedDirection = com.example.model.RecommendationState.UP_CANDIDATE,
          actualNextCandleDirection = PriceDirection.UP,
          actualCandleBodySize = 0.00025,
          actualCandleRange = 0.00040,
          wasPredictionCorrect = true,
          marketRegime = fp.marketRegime,
          dataQuality = ExtractionQuality.HIGH,
          setupQuality = com.example.model.SetupQualityGrade.A
        )
      }
    }

    val twoMatchesEvidence = matcher.evaluateHistoricalPatternEvidence(sampleFp, buildOutcomeRecords(2))
    assertEquals(2, twoMatchesEvidence.historicalMatchesCount)
    assertEquals(
      com.example.model.HistoricalSampleSufficiency.INSUFFICIENT_EVIDENCE,
      twoMatchesEvidence.sampleSufficiency
    )
    assertFalse(twoMatchesEvidence.isSampleSufficient)

    val tenMatchesEvidence = matcher.evaluateHistoricalPatternEvidence(sampleFp, buildOutcomeRecords(10))
    assertEquals(10, tenMatchesEvidence.historicalMatchesCount)
    assertEquals(
      com.example.model.HistoricalSampleSufficiency.WEAK_EVIDENCE,
      tenMatchesEvidence.sampleSufficiency
    )
    assertFalse(tenMatchesEvidence.isSampleSufficient)

    val fiftyFiveMatchesEvidence = matcher.evaluateHistoricalPatternEvidence(sampleFp, buildOutcomeRecords(55))
    assertEquals(55, fiftyFiveMatchesEvidence.historicalMatchesCount)
    assertEquals(
      com.example.model.HistoricalSampleSufficiency.POTENTIALLY_USEFUL,
      fiftyFiveMatchesEvidence.sampleSufficiency
    )
    assertTrue(fiftyFiveMatchesEvidence.isSampleSufficient)
    assertEquals(1.0, fiftyFiveMatchesEvidence.outcomeDistribution.bullishRatio, 1e-6)

    // 5. Verify OutcomeMemory seals immutable OutcomeMemoryRecord and rejects bad writes (Requirements 4 & 9)
    val brainMemory = DefaultProTraderBrainMemory(safetyValidator = safetyValidator)
    brainMemory.recordCompletedSequence(
      recentCandleSequence = candles.take(45),
      dataQuality = ExtractionQuality.HIGH,
      setupSnapshot = supportExhaustionSnapshot,
      patternFingerprint = sampleFp,
      predictedDirection = com.example.model.RecommendationState.UP_CANDIDATE
    )
    assertEquals(0, brainMemory.getOutcomeCount())

    brainMemory.recordCompletedSequence(
      recentCandleSequence = candles.take(46),
      dataQuality = ExtractionQuality.HIGH,
      setupSnapshot = supportExhaustionSnapshot,
      patternFingerprint = sampleFp,
      predictedDirection = com.example.model.RecommendationState.WAIT
    )
    assertEquals(1, brainMemory.getOutcomeCount())
    val sealedOutcome = brainMemory.getOutcomeMemoryRecords().first()
    assertEquals(com.example.model.RecommendationState.UP_CANDIDATE, sealedOutcome.predictedDirection)
    assertNotNull(sealedOutcome.wasPredictionCorrect)

    // Corrupted write is rejected and does not modify or add to OutcomeMemory
    val rejectedResult = brainMemory.recordCompletedSequence(
      recentCandleSequence = candles.take(2),
      dataQuality = ExtractionQuality.LOW
    )
    assertFalse(rejectedResult.isValidForMemory)
    assertEquals(1, brainMemory.getRejectedWriteCount())
    assertEquals(1, brainMemory.getOutcomeCount())

    // 6. Verify full ProTraderBrainEngine and SignalConsensusEngine integration (Requirement 8 & 11)
    val srAnalysis = viewModel.supportResistanceEngine.analyzeSupportResistance(candles)
    val seqFeatures = viewModel.candleSequenceEngine.analyzeSequenceFeatures(candles)
    val indicators = viewModel.indicatorEngine.evaluateIndicators(candles)
    val paSnapshot = viewModel.priceActionEngine.analyzePriceAction(candles)
    val patterns = viewModel.candlestickPatternEngine.detectPatterns(
      candles = candles,
      supportResistance = srAnalysis,
      momentum = paSnapshot.priceActionState.momentum,
      indicators = indicators
    )
    val regime = viewModel.marketRegimeEngine.detectRegime(
      candles = candles,
      indicators = indicators,
      priceAction = paSnapshot.priceActionState,
      supportResistance = srAnalysis,
      sequenceFeatures = seqFeatures
    )

    val initialAnalysis = viewModel.signalConsensusEngine.buildMarketAnalysis(
      candles = candles,
      dataQuality = ExtractionQuality.HIGH,
      indicators = indicators,
      supportResistance = srAnalysis,
      priceAction = paSnapshot,
      patterns = patterns,
      sequenceFeatures = seqFeatures,
      marketRegime = regime
    )

    val fullAnalysis = viewModel.signalConsensusEngine.buildMarketAnalysis(
      candles = candles,
      dataQuality = ExtractionQuality.HIGH,
      indicators = indicators,
      supportResistance = srAnalysis,
      priceAction = paSnapshot,
      patterns = patterns,
      sequenceFeatures = seqFeatures,
      marketRegime = regime,
      outcomeMemoryRecords = buildOutcomeRecords(55, initialAnalysis.patternFingerprint)
    )

    val assessment = fullAnalysis.brainAssessment
    assertNotNull(assessment.marketState)
    assertTrue(assessment.patternFingerprint.candleDirectionSequence.isNotEmpty())
    assertEquals(55, assessment.historicalPatternEvidence.historicalMatchesCount)
    assertNotNull(assessment.recommendationState)
    // Part 5 strict rule: final UI signal remains WAIT until Part 6
    assertEquals(NextCandleSignal.WAIT, fullAnalysis.recommendedSignal)
  }

  @Test
  fun part6FinalSmartPredictionEngine_verifiesForecastStabilityInvalidationAndHistory() {
    val rawCandles = DemoPlaceholderCandleSeeder.generateInitialDemoCandles(count = 55)
    val cleanFormingCandle = rawCandles.last().copy(
      open = 1.08500,
      high = 1.08545,
      low = 1.08495,
      close = 1.08540,
      isComplete = false,
      qualityScore = 0.95f
    )
    val candles = rawCandles.dropLast(1) + cleanFormingCandle
    val viewModel = OtcVisionViewModel()

    // 1. CurrentCandleMonitor verification (Requirements 2 & 7)
    val srAnalysis = viewModel.supportResistanceEngine.analyzeSupportResistance(candles)
    val marketStructure = viewModel.marketStructureEngine.analyzeStructure(candles)
    val monitorState = viewModel.currentCandleMonitor.monitorCurrentCandle(
      candles = candles,
      supportResistance = srAnalysis,
      marketStructure = marketStructure
    )
    assertTrue(monitorState.currentPrice > 0.0)
    assertTrue(monitorState.pricePositionInRange in 0.0..1.0)
    assertTrue(monitorState.bodyToRangeRatio in 0.0..1.0)
    assertTrue(monitorState.isBehaviorStable)

    // 2. Construct a high-quality Grade A bullish MarketAnalysis & BrainAssessment (Requirements 3 & 4)
    val seqFeatures = viewModel.candleSequenceEngine.analyzeSequenceFeatures(candles)
    val indicators = viewModel.indicatorEngine.evaluateIndicators(candles)
    val paSnapshot = viewModel.priceActionEngine.analyzePriceAction(candles)
    val patterns = viewModel.candlestickPatternEngine.detectPatterns(
      candles = candles,
      supportResistance = srAnalysis,
      momentum = paSnapshot.priceActionState.momentum,
      indicators = indicators
    )
    val baseAnalysis = viewModel.signalConsensusEngine.buildMarketAnalysis(
      candles = candles,
      dataQuality = ExtractionQuality.HIGH,
      indicators = indicators,
      supportResistance = srAnalysis,
      priceAction = paSnapshot,
      patterns = patterns,
      sequenceFeatures = seqFeatures,
      marketRegime = com.example.model.MarketRegime.TRENDING_UP
    )

    val cleanPaContext = baseAnalysis.priceActionContext.copy(
      breakoutState = com.example.model.BreakoutAnalysisState(),
      reversalState = com.example.model.ReversalAnalysisState()
    )
    val strongBullishAssessment = baseAnalysis.brainAssessment.copy(
      marketState = baseAnalysis.marketStateSnapshot.copy(
        dataQuality = ExtractionQuality.HIGH,
        isScreenDetectionStable = true,
        supportResistance = srAnalysis.copy(
          supportRejection = true,
          isNearSupport = true,
          resistanceRejection = false
        ),
        priceAction = paSnapshot.priceActionState.copy(isUptrend = true, isDowntrend = false),
        breakoutRetestState = cleanPaContext.breakoutState,
        reversalState = cleanPaContext.reversalState,
        chochState = com.example.model.ChochClassification.NONE,
        bosState = com.example.model.BosClassification.NONE,
        isFailedRetest = false,
        momentum = com.example.model.PriceActionMomentumState()
      ),
      totalBullishWeight = 5.2,
      totalBearishWeight = 0.8,
      bullishEvidenceDescriptions = listOf(
        "Support rejection confirmed",
        "Bullish HH/HL market structure",
        "Strong bullish momentum"
      ),
      setupQuality = com.example.model.SetupQualityGrade.A,
      dataQuality = ExtractionQuality.HIGH,
      stability = com.example.model.MarketStabilityState.STABLE,
      recommendationState = com.example.model.RecommendationState.UP_CANDIDATE
    )
    val strongBullishAnalysis = baseAnalysis.copy(
      dataQuality = ExtractionQuality.HIGH,
      marketRegime = com.example.model.MarketRegime.TRENDING_UP,
      supportResistanceAnalysis = srAnalysis.copy(supportRejection = true, isNearSupport = true),
      priceActionContext = cleanPaContext,
      conflictAnalysis = com.example.model.ConflictAnalysisResult(hasConflict = false),
      setupQualityGrade = com.example.model.SetupQualityGrade.A,
      brainAssessment = strongBullishAssessment
    )

    val predEngine = com.example.engine.prediction.DefaultPredictionEngine()
    val upPrediction = predEngine.generateNextCandlePrediction(
      candles = candles,
      marketAnalysis = strongBullishAnalysis,
      isChartRegionValid = true,
      isScreenDetectionStable = true,
      nowMillis = 1_700_000_000_000L
    )
    assertEquals(com.example.model.PredictionDirection.UP, upPrediction.direction)
    assertEquals(com.example.model.PredictionLifecycleState.ACTIVE, upPrediction.state)
    assertEquals(NextCandleSignal.UP, upPrediction.uiSignal)
    assertEquals("Setup: A", upPrediction.setupDisplayBadgeText)
    assertTrue(upPrediction.primaryWhyLine.startsWith("UP —"))
    assertEquals(
      com.example.model.PredictionAgreementLevel.STRONG_AGREEMENT,
      upPrediction.agreementLevel
    )

    // 3. Verify Anti-Flipping Stability (Requirement 6):
    // Immediate moderate DOWN attempt within cooldown is prevented from flipping UP -> DOWN directly
    val moderateBearishAssessment = strongBullishAssessment.copy(
      totalBullishWeight = 1.0,
      totalBearishWeight = 3.5,
      bearishEvidenceDescriptions = listOf("Resistance rejection"),
      setupQuality = com.example.model.SetupQualityGrade.B,
      recommendationState = com.example.model.RecommendationState.DOWN_CANDIDATE
    )
    val moderateBearishAnalysis = strongBullishAnalysis.copy(
      marketRegime = com.example.model.MarketRegime.SIDEWAYS,
      setupQualityGrade = com.example.model.SetupQualityGrade.B,
      brainAssessment = moderateBearishAssessment
    )
    val flipAttemptPrediction = predEngine.generateNextCandlePrediction(
      candles = candles,
      marketAnalysis = moderateBearishAnalysis,
      isChartRegionValid = true,
      isScreenDetectionStable = true,
      nowMillis = 1_700_000_000_500L // only 500ms later
    )
    assertTrue(flipAttemptPrediction.stabilitySnapshot.wasRapidFlipPrevented)
    assertEquals(com.example.model.PredictionDirection.NONE, flipAttemptPrediction.direction)
    assertEquals(NextCandleSignal.WAIT, flipAttemptPrediction.uiSignal)

    // 4. Verify Prediction Invalidation (Requirement 8):
    // Re-establish UP prediction, then trigger a market structure change & opposite momentum invalidation
    predEngine.resetStability()
    val activeUp = predEngine.generateNextCandlePrediction(
      candles = candles,
      marketAnalysis = strongBullishAnalysis,
      nowMillis = 1_700_000_010_000L
    )
    assertEquals(com.example.model.PredictionDirection.UP, activeUp.direction)

    val invalidatedAssessment = strongBullishAssessment.copy(
      marketState = strongBullishAssessment.marketState.copy(
        chochState = com.example.model.ChochClassification.BEARISH_CHOCH,
        supportResistance = srAnalysis.copy(resistanceRejection = true)
      )
    )
    val invalidatedPrediction = predEngine.generateNextCandlePrediction(
      candles = candles,
      marketAnalysis = strongBullishAnalysis.copy(brainAssessment = invalidatedAssessment),
      nowMillis = 1_700_000_011_000L
    )
    assertEquals(com.example.model.PredictionLifecycleState.INVALIDATED, invalidatedPrediction.state)
    assertEquals(NextCandleSignal.WAIT, invalidatedPrediction.uiSignal)
    assertTrue(invalidatedPrediction.invalidationReasons.isNotEmpty())

    // 5. Verify First-Class WAIT System on Low Data Quality or Unconfirmed Breakout (Requirement 5 & 9)
    val lowQualityPrediction = predEngine.generateNextCandlePrediction(
      candles = candles,
      marketAnalysis = strongBullishAnalysis.copy(dataQuality = ExtractionQuality.LOW),
      nowMillis = 1_700_000_015_000L
    )
    assertEquals(com.example.model.PredictionLifecycleState.WAIT, lowQualityPrediction.state)
    assertEquals(NextCandleSignal.WAIT, lowQualityPrediction.uiSignal)
    assertEquals("WAIT — chart data unclear", lowQualityPrediction.primaryWhyLine)

    // 6. Verify Prediction History Repository (Requirement 10)
    val historyRepo = com.example.engine.prediction.DefaultPredictionHistoryRepository()
    val lastCompletedTs = candles.filter { it.isComplete }.last().timestamp
    historyRepo.stageActivePrediction(
      prediction = upPrediction,
      marketRegime = com.example.model.MarketRegime.TRENDING_UP,
      lastCompletedCandleTimestamp = lastCompletedTs
    )
    val nextCompletedBullishCandle = candles.last().copy(
      timestamp = lastCompletedTs + 60_000L,
      open = 1.08500,
      high = 1.08560,
      low = 1.08490,
      close = 1.08550,
      isComplete = true,
      qualityScore = 0.95f
    )
    val finalizedRecord = historyRepo.onCandleCompleted(nextCompletedBullishCandle)
    assertNotNull(finalizedRecord)
    assertEquals(com.example.model.PredictionDirection.UP, finalizedRecord!!.direction)
    assertEquals(PriceDirection.UP, finalizedRecord.actualNextCandleResult)
    assertEquals(com.example.model.PredictionOutcomeStatus.CORRECT, finalizedRecord.outcomeStatus)
    assertEquals(1, historyRepo.getTotalFinalizedCount())
  }
}

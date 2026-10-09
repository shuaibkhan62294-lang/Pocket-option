package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.candle.CandleDataManager
import com.example.data.candle.DemoPlaceholderCandleSeeder
import com.example.data.candle.RollingCandleRepository
import com.example.engine.alert.AlertVibrationManager
import com.example.engine.alert.AndroidAlertVibrationManager
import com.example.engine.backtest.BacktestingEngine
import com.example.engine.backtest.DefaultBacktestingEngine
import com.example.engine.brain.DefaultProTraderBrainEngine
import com.example.engine.brain.ProTraderBrainEngine
import com.example.engine.breakout.BreakoutEngine
import com.example.engine.breakout.DefaultBreakoutEngine
import com.example.engine.candlestructure.AdvancedCandleStructureEngine
import com.example.engine.candlestructure.DefaultAdvancedCandleStructureEngine
import com.example.engine.capture.DefaultScreenCaptureManager
import com.example.engine.capture.ScreenCaptureManager
import com.example.engine.chart.ChartEngine
import com.example.engine.chart.ChartViewportMetrics
import com.example.engine.chart.DefaultChartEngine
import com.example.engine.chart.LiveChartEngine
import com.example.engine.conflict.ConflictEngine
import com.example.engine.conflict.DefaultConflictEngine
import com.example.engine.consensus.DefaultSignalConsensusEngine
import com.example.engine.consensus.SignalConsensusEngine
import com.example.engine.context.ContextAwareIntelligenceEngine
import com.example.engine.context.DefaultContextAwareIntelligenceEngine
import com.example.engine.context.DefaultPriceActionContextEngine
import com.example.engine.context.PriceActionContextEngine
import com.example.engine.fingerprint.DefaultPatternFingerprintEngine
import com.example.engine.fingerprint.PatternFingerprintEngine
import com.example.engine.indicator.DefaultIndicatorEngine
import com.example.engine.indicator.IndicatorEngine
import com.example.engine.liquidity.DefaultLiquiditySweepEngine
import com.example.engine.liquidity.LiquiditySweepEngine
import com.example.engine.matching.DefaultHistoricalPatternMatcher
import com.example.engine.matching.HistoricalPatternMatcher
import com.example.engine.memory.DefaultProTraderBrainMemory
import com.example.engine.memory.ProTraderBrainMemory
import com.example.engine.movement.DefaultPriceMovementDetector
import com.example.engine.movement.PriceMovementDetector
import com.example.engine.pattern.CandlestickPatternEngine
import com.example.engine.pattern.DefaultCandlestickPatternEngine
import com.example.engine.prediction.CurrentCandleMonitor
import com.example.engine.prediction.DefaultCurrentCandleMonitor
import com.example.engine.prediction.DefaultPredictionEngine
import com.example.engine.prediction.DefaultPredictionHistoryRepository
import com.example.engine.prediction.DefaultPredictionStabilityEngine
import com.example.engine.prediction.PredictionEngine
import com.example.engine.prediction.PredictionHistoryRepository
import com.example.engine.prediction.PredictionStabilityEngine
import com.example.engine.priceaction.DefaultPriceActionEngine
import com.example.engine.priceaction.PriceActionEngine
import com.example.engine.quality.DataQualityCheckInput
import com.example.engine.quality.DataQualityEngine
import com.example.engine.quality.DefaultDataQualityEngine
import com.example.engine.regime.DefaultMarketRegimeEngine
import com.example.engine.regime.DefaultRegimeReasoningEngine
import com.example.engine.regime.MarketRegimeEngine
import com.example.engine.regime.RegimeReasoningEngine
import com.example.engine.region.ChartRegion
import com.example.engine.region.ChartRegionManager
import com.example.engine.region.DefaultChartRegionManager
import com.example.engine.reversal.DefaultReversalEngine
import com.example.engine.reversal.ReversalEngine
import com.example.engine.safety.DefaultMemorySafetyValidator
import com.example.engine.safety.MemorySafetyValidator
import com.example.engine.sequence.CandleSequenceEngine
import com.example.engine.sequence.DefaultCandleSequenceEngine
import com.example.engine.setup.DefaultSetupQualityEngine
import com.example.engine.setup.SetupQualityEngine
import com.example.engine.sr.DefaultSupportResistanceEngine
import com.example.engine.sr.SupportResistanceEngine
import com.example.engine.structure.DefaultMarketStructureEngine
import com.example.engine.structure.MarketStructureEngine
import com.example.engine.vision.DefaultVisualCandleDetector
import com.example.engine.vision.VisualCandleDetector
import com.example.engine.vision.VisualExtractionResult
import com.example.engine.weighting.DefaultEvidenceWeightingEngine
import com.example.engine.weighting.EvidenceWeightingEngine
import com.example.model.AnalyzerRunState
import com.example.model.BrainAssessment
import com.example.model.Candle
import com.example.model.CandleSource
import com.example.model.ChartDataSourceMode
import com.example.model.CurrentCandleTrackerSnapshot
import com.example.model.ExtractionQuality
import com.example.model.FinalizedPredictionRecord
import com.example.model.LiveChartSyncState
import com.example.model.MarketAnalysis
import com.example.model.NextCandlePrediction
import com.example.model.NextCandleSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Complete UI state for the OTC Vision AI main screen (Parts 1, 2, & 3).
 * Keeps all complicated technical calculations hidden inside [marketAnalysis].
 */
data class OtcVisionUiState(
  val analyzerState: AnalyzerRunState = AnalyzerRunState.OFF,
  val isScreenCaptureActive: Boolean = false,
  val selectedChartRegion: ChartRegion? = ChartRegion.DEFAULT_CENTER_CHART_REGION,
  val isChartRegionValid: Boolean = true,
  val isRegionSelectorDialogOpen: Boolean = false,
  val dataSourceMode: ChartDataSourceMode = ChartDataSourceMode.EMPTY_WAITING,
  val candles: List<Candle> = emptyList(),
  val currentCandle: Candle? = null,
  val currentCandleTracker: CurrentCandleTrackerSnapshot? = null,
  val viewportMetrics: ChartViewportMetrics = DefaultChartEngine().computeViewportMetrics(
    allCandles = emptyList(),
    visibleCapacity = ChartEngine.DEFAULT_VISIBLE_CANDLES
  ),
  val visibleCandleCapacity: Int = ChartEngine.DEFAULT_VISIBLE_CANDLES,
  val scrollOffsetFromLatest: Int = 0,
  val isAutoScrollEnabled: Boolean = true,
  val syncState: LiveChartSyncState = LiveChartSyncState.WAITING,
  val extractionQuality: ExtractionQuality = ExtractionQuality.UNREADABLE,
  val dataQualityLabel: String = "Waiting",
  val marketAnalysis: MarketAnalysis = MarketAnalysis(),
  val brainAssessment: BrainAssessment = BrainAssessment(),
  val nextCandlePrediction: NextCandlePrediction = NextCandlePrediction(),
  val setupQualityBadgeText: String = "Setup: NO SETUP",
  val predictionHistory: List<FinalizedPredictionRecord> = emptyList(),
  val isWhySectionExpanded: Boolean = false,
  val whyExplanations: List<String> = listOf("Waiting for chart data..."),
  val nextCandleSignal: NextCandleSignal = NextCandleSignal.WAITING,
  val nextCandleSubtitle: String = "Live candle analysis will appear here.",
  val statusNoticeBanner: String? = null,
  val isBrainStatusExpanded: Boolean = false,
  val brainChartReadingStatus: String = "Waiting",
  val brainCandleTrackingStatus: String = "Waiting",
  val brainSequenceMemoryStatus: String = "Waiting",
  val brainDataQualityStatus: String = "Waiting",
  val connectionStatusText: String = "Not connected",
  val chartStatusText: String = "Waiting",
  val analysisStatusText: String = "Waiting"
) {
  val analyzerStatusHeaderText: String
    get() = when (analyzerState) {
      AnalyzerRunState.OFF -> "Analyzer: OFF"
      AnalyzerRunState.ACTIVE -> "Analyzer: ACTIVE"
      AnalyzerRunState.PAUSED -> "Analyzer: PAUSED"
    }

  val screenCaptureHeaderText: String
    get() = if (isScreenCaptureActive) "Screen Capture: ON" else "Screen Capture: OFF"

  val isDemoPreviewActive: Boolean
    get() = dataSourceMode == ChartDataSourceMode.DEMO_PLACEHOLDER && candles.isNotEmpty()

  val nextCandleCardDisplayTitle: String
    get() = when (nextCandleSignal) {
      NextCandleSignal.WAITING -> "WAITING"
      NextCandleSignal.WAIT -> "WAIT"
      NextCandleSignal.UP -> "UP"
      NextCandleSignal.DOWN -> "DOWN"
    }
}

/**
 * Main ViewModel coordinating Parts 1–5 modules for OTC Vision AI.
 */
class OtcVisionViewModel(
  val chartEngine: LiveChartEngine = DefaultChartEngine(),
  val candleDataManager: CandleDataManager = RollingCandleRepository(
    maxCapacity = CandleDataManager.DEFAULT_ROLLING_CAPACITY
  ),
  val screenCaptureManager: ScreenCaptureManager = DefaultScreenCaptureManager(),
  val chartRegionManager: ChartRegionManager = DefaultChartRegionManager(),
  val visualCandleDetector: VisualCandleDetector = DefaultVisualCandleDetector(),
  val priceMovementDetector: PriceMovementDetector = DefaultPriceMovementDetector(),
  val candleSequenceEngine: CandleSequenceEngine = DefaultCandleSequenceEngine(),
  val dataQualityEngine: DataQualityEngine = DefaultDataQualityEngine(),
  val memorySafetyValidator: MemorySafetyValidator = DefaultMemorySafetyValidator(),
  val proTraderBrainMemory: ProTraderBrainMemory = DefaultProTraderBrainMemory(
    safetyValidator = memorySafetyValidator
  ),
  val indicatorEngine: IndicatorEngine = DefaultIndicatorEngine(),
  val supportResistanceEngine: SupportResistanceEngine = DefaultSupportResistanceEngine(),
  val priceActionEngine: PriceActionEngine = DefaultPriceActionEngine(),
  val candlestickPatternEngine: CandlestickPatternEngine = DefaultCandlestickPatternEngine(),
  val candleStructureEngine: AdvancedCandleStructureEngine = DefaultAdvancedCandleStructureEngine(),
  val marketStructureEngine: MarketStructureEngine = DefaultMarketStructureEngine(),
  val liquiditySweepEngine: LiquiditySweepEngine = DefaultLiquiditySweepEngine(),
  val breakoutEngine: BreakoutEngine = DefaultBreakoutEngine(),
  val reversalEngine: ReversalEngine = DefaultReversalEngine(),
  val priceActionContextEngine: PriceActionContextEngine = DefaultPriceActionContextEngine(
    candleStructureEngine = candleStructureEngine,
    marketStructureEngine = marketStructureEngine,
    liquiditySweepEngine = liquiditySweepEngine,
    breakoutEngine = breakoutEngine,
    reversalEngine = reversalEngine
  ),
  val conflictEngine: ConflictEngine = DefaultConflictEngine(),
  val setupQualityEngine: SetupQualityEngine = DefaultSetupQualityEngine(),
  val patternFingerprintEngine: PatternFingerprintEngine = DefaultPatternFingerprintEngine(),
  val historicalPatternMatcher: HistoricalPatternMatcher = DefaultHistoricalPatternMatcher(
    fingerprintEngine = patternFingerprintEngine
  ),
  val contextAwareIntelligenceEngine: ContextAwareIntelligenceEngine = DefaultContextAwareIntelligenceEngine(),
  val regimeReasoningEngine: RegimeReasoningEngine = DefaultRegimeReasoningEngine(),
  val evidenceWeightingEngine: EvidenceWeightingEngine = DefaultEvidenceWeightingEngine(),
  val proTraderBrainEngine: ProTraderBrainEngine = DefaultProTraderBrainEngine(
    fingerprintEngine = patternFingerprintEngine,
    historicalPatternMatcher = historicalPatternMatcher,
    contextAwareEngine = contextAwareIntelligenceEngine,
    regimeReasoningEngine = regimeReasoningEngine,
    evidenceWeightingEngine = evidenceWeightingEngine
  ),
  val marketRegimeEngine: MarketRegimeEngine = DefaultMarketRegimeEngine(),
  val signalConsensusEngine: SignalConsensusEngine = DefaultSignalConsensusEngine(
    priceActionContextEngine = priceActionContextEngine,
    conflictEngine = conflictEngine,
    setupQualityEngine = setupQualityEngine,
    historicalPatternMatcher = historicalPatternMatcher,
    proTraderBrainEngine = proTraderBrainEngine
  ),
  val currentCandleMonitor: CurrentCandleMonitor = DefaultCurrentCandleMonitor(),
  val predictionStabilityEngine: PredictionStabilityEngine = DefaultPredictionStabilityEngine(),
  val predictionHistoryRepository: PredictionHistoryRepository = DefaultPredictionHistoryRepository(),
  val predictionEngine: PredictionEngine = DefaultPredictionEngine(
    currentCandleMonitor = currentCandleMonitor,
    stabilityEngine = predictionStabilityEngine
  ),
  val backtestingEngine: BacktestingEngine = DefaultBacktestingEngine(),
  val alertVibrationManager: AlertVibrationManager = AndroidAlertVibrationManager()
) : ViewModel() {

  private val _uiState = MutableStateFlow(OtcVisionUiState())
  val uiState: StateFlow<OtcVisionUiState> = _uiState.asStateFlow()

  private var demoTickerJob: Job? = null
  private val demoRandom = Random(98765L)
  private var ticksInCurrentCandle = 0
  private var consecutiveReadableFrames = 0
  private var lastSeenCompletedCount = 0

  init {
    chartRegionManager.selectChartRegion(ChartRegion.DEFAULT_CENTER_CHART_REGION)

    viewModelScope.launch {
      candleDataManager.candlesFlow.collect { updatedCandles ->
        runBackgroundEnginesAndRefreshUi(updatedCandles)
      }
    }
  }

  // ---------------------------------------------------------------------------
  // 1. Live Chart Capture Controls (ScreenCaptureManager)
  // ---------------------------------------------------------------------------

  fun createScreenCapturePermissionIntent(context: Context): Intent? {
    if (!chartRegionManager.hasValidSelection()) {
      _uiState.update {
        it.copy(
          nextCandleSignal = NextCandleSignal.WAIT,
          nextCandleSubtitle = "Please select the chart area again",
          statusNoticeBanner = "Please select the chart area again"
        )
      }
      return null
    }
    screenCaptureManager.markAwaitingPermission()
    return screenCaptureManager.createScreenCaptureIntent(context)
  }

  fun onScreenCapturePermissionResult(
    context: Context,
    resultCode: Int,
    data: Intent?
  ) {
    if (resultCode != Activity.RESULT_OK || data == null) {
      screenCaptureManager.onPermissionDenied()
      _uiState.update {
        it.copy(
          isScreenCaptureActive = false,
          syncState = LiveChartSyncState.WAITING,
          nextCandleSignal = NextCandleSignal.WAIT,
          nextCandleSubtitle = "Screen capture stopped",
          statusNoticeBanner = "Screen capture stopped",
          brainChartReadingStatus = "Stopped",
          connectionStatusText = "Not connected"
        )
      }
      return
    }

    if (_uiState.value.dataSourceMode == ChartDataSourceMode.DEMO_PLACEHOLDER) {
      stopDemoTickerJob()
      viewModelScope.launch { candleDataManager.clear() }
    }

    consecutiveReadableFrames = 0
    val started = screenCaptureManager.startCaptureWithPermissionResult(
      context = context,
      resultCode = resultCode,
      data = data,
      onFrameAvailable = { frameBitmap, timestamp ->
        processCapturedChartBitmap(frameBitmap, timestamp)
      }
    )

    if (started) {
      alertVibrationManager.onAnalyzerStateChanged()
      _uiState.update {
        it.copy(
          isScreenCaptureActive = true,
          dataSourceMode = ChartDataSourceMode.LIVE_CAPTURE,
          syncState = LiveChartSyncState.SYNCING,
          nextCandleSignal = NextCandleSignal.WAIT,
          nextCandleSubtitle = "Collecting candle data...",
          statusNoticeBanner = null,
          brainChartReadingStatus = "Syncing...",
          brainCandleTrackingStatus = "Calibrating",
          connectionStatusText = "Chart Connected",
          chartStatusText = "Syncing...",
          analysisStatusText = "Waiting"
        )
      }
    }
  }

  fun stopScreenCapture() {
    screenCaptureManager.stopCaptureSession()
    alertVibrationManager.onAnalyzerStateChanged()
    consecutiveReadableFrames = 0

    _uiState.update { state ->
      val nextMode = if (state.dataSourceMode == ChartDataSourceMode.LIVE_CAPTURE) {
        ChartDataSourceMode.EMPTY_WAITING
      } else {
        state.dataSourceMode
      }
      state.copy(
        isScreenCaptureActive = false,
        dataSourceMode = nextMode,
        syncState = if (nextMode == ChartDataSourceMode.DEMO_PLACEHOLDER) {
          LiveChartSyncState.DEMO_PREVIEW
        } else {
          LiveChartSyncState.WAITING
        },
        nextCandleSignal = NextCandleSignal.WAIT,
        nextCandleSubtitle = "Screen capture stopped",
        statusNoticeBanner = "Screen capture stopped",
        brainChartReadingStatus = "Stopped",
        brainCandleTrackingStatus = "Waiting",
        connectionStatusText = "Not connected",
        chartStatusText = "Waiting",
        analysisStatusText = "Waiting"
      )
    }
  }

  fun processCapturedChartBitmap(
    bitmap: Bitmap,
    timestamp: Long = System.currentTimeMillis()
  ) {
    val region = chartRegionManager.selectedRegion.value
    if (region == null || !region.isValid()) {
      _uiState.update {
        it.copy(
          extractionQuality = ExtractionQuality.UNREADABLE,
          dataQualityLabel = "Waiting",
          nextCandleSignal = NextCandleSignal.WAIT,
          nextCandleSubtitle = "Please select the chart area again",
          statusNoticeBanner = "Please select the chart area again",
          brainChartReadingStatus = "Select chart area"
        )
      }
      return
    }

    val extraction = visualCandleDetector.extractFromBitmap(
      fullFrameBitmap = bitmap,
      region = region,
      timestamp = timestamp
    )
    ingestExtractionResult(extraction)
  }

  fun ingestExtractionResult(extraction: VisualExtractionResult) {
    viewModelScope.launch {
      if (!extraction.isReadable) {
        consecutiveReadableFrames = 0
        val unclearMsg = if (extraction.extractionQuality == ExtractionQuality.UNREADABLE &&
          extraction.diagnosticReason == "Chart not readable"
        ) {
          "Chart not readable"
        } else {
          "WAIT — Chart data unclear"
        }
        val qualityWord = if (extraction.extractionQuality == ExtractionQuality.LOW) "Low" else "Unreadable"

        _uiState.update {
          it.copy(
            extractionQuality = extraction.extractionQuality,
            dataQualityLabel = qualityWord,
            syncState = if (it.isScreenCaptureActive) LiveChartSyncState.SYNCING else LiveChartSyncState.WAITING,
            nextCandleSignal = NextCandleSignal.WAIT,
            nextCandleSubtitle = unclearMsg,
            statusNoticeBanner = unclearMsg,
            brainChartReadingStatus = "Chart not readable",
            brainDataQualityStatus = qualityWord,
            whyExplanations = listOf(unclearMsg)
          )
        }
        return@launch
      }

      consecutiveReadableFrames++
      val domainCandles = extraction.toDomainCandles(nowMillis = extraction.timestamp)
      val latestForming = domainCandles.lastOrNull()
      if (latestForming != null) {
        priceMovementDetector.recordPriceUpdate(
          price = latestForming.close,
          timestamp = extraction.timestamp
        )
      }

      candleDataManager.ingestVisualExtractedCandles(
        extractedCandles = domainCandles,
        nowMillis = extraction.timestamp
      )

      val allCandles = candleDataManager.candlesFlow.value
      candleSequenceEngine.updateSequence(allCandles)

      val qualityCheck = dataQualityEngine.evaluateQuality(
        DataQualityCheckInput(
          isScreenCaptureActive = _uiState.value.isScreenCaptureActive || true,
          isChartRegionValid = chartRegionManager.hasValidSelection(),
          isChartVisible = extraction.isReadable,
          extractionQuality = extraction.extractionQuality,
          isCandleDetectionStable = consecutiveReadableFrames >= 1,
          candles = allCandles,
          currentCandle = allCandles.lastOrNull(),
          isPriceMovementUpdating = priceMovementDetector.isPriceUpdatingRecently(extraction.timestamp),
          minRequiredCandles = 5
        )
      )

      val syncState = chartEngine.resolveSyncState(
        dataSourceMode = ChartDataSourceMode.LIVE_CAPTURE,
        isScreenCaptureActive = true,
        isDataReliable = qualityCheck.isReliableForAnalysis,
        candleCount = allCandles.size
      )

      _uiState.update { state ->
        state.copy(
          dataSourceMode = ChartDataSourceMode.LIVE_CAPTURE,
          extractionQuality = extraction.extractionQuality,
          dataQualityLabel = qualityCheck.dataQualityWord,
          syncState = syncState,
          nextCandleSignal = NextCandleSignal.WAIT,
          nextCandleSubtitle = qualityCheck.primaryReasonMessage,
          statusNoticeBanner = if (qualityCheck.isReliableForAnalysis) null else qualityCheck.primaryReasonMessage,
          brainChartReadingStatus = if (qualityCheck.isReliableForAnalysis) "Reading live chart" else "Calibrating",
          brainCandleTrackingStatus = candleDataManager.getCandleTrackingStatusWord(),
          brainSequenceMemoryStatus = candleSequenceEngine.getSequenceStatusWord(),
          brainDataQualityStatus = qualityCheck.dataQualityWord,
          connectionStatusText = "Chart Connected",
          chartStatusText = if (syncState == LiveChartSyncState.LIVE) "Live" else "Syncing...",
          analysisStatusText = "Waiting"
        )
      }
    }
  }

  // ---------------------------------------------------------------------------
  // 2. Chart Region Controls (ChartRegionManager)
  // ---------------------------------------------------------------------------

  fun openChartRegionSelector() {
    _uiState.update { it.copy(isRegionSelectorDialogOpen = true) }
  }

  fun dismissChartRegionSelector() {
    _uiState.update { it.copy(isRegionSelectorDialogOpen = false) }
  }

  fun selectChartRegion(region: ChartRegion = ChartRegion.DEFAULT_CENTER_CHART_REGION) {
    chartRegionManager.selectChartRegion(region)
    val isValid = chartRegionManager.hasValidSelection()
    _uiState.update { state ->
      state.copy(
        selectedChartRegion = region,
        isChartRegionValid = isValid,
        statusNoticeBanner = if (isValid) null else "Please select the chart area again",
        nextCandleSubtitle = if (isValid) {
          "Live candle analysis will appear here."
        } else {
          "Please select the chart area again"
        }
      )
    }
  }

  fun reselectChartRegion(region: ChartRegion) {
    chartRegionManager.reselectChartRegion(region)
    val isValid = chartRegionManager.hasValidSelection()
    _uiState.update { state ->
      state.copy(
        selectedChartRegion = region,
        isChartRegionValid = isValid,
        statusNoticeBanner = if (isValid) null else "Please select the chart area again"
      )
    }
  }

  fun clearChartRegionSelection() {
    chartRegionManager.clearSelection()
    _uiState.update { state ->
      state.copy(
        selectedChartRegion = null,
        isChartRegionValid = false,
        nextCandleSignal = NextCandleSignal.WAIT,
        nextCandleSubtitle = "Please select the chart area again",
        statusNoticeBanner = "Please select the chart area again",
        brainChartReadingStatus = "Chart area cleared"
      )
    }
  }

  // ---------------------------------------------------------------------------
  // 3. Expandable "Brain Status" & "Why?" Controls
  // ---------------------------------------------------------------------------

  fun toggleBrainStatusExpanded() {
    _uiState.update { it.copy(isBrainStatusExpanded = !it.isBrainStatusExpanded) }
  }

  fun toggleWhySectionExpanded() {
    _uiState.update { it.copy(isWhySectionExpanded = !it.isWhySectionExpanded) }
  }

  // ---------------------------------------------------------------------------
  // 4. Part 1 Analyzer Controls ([ START ANALYZER ] [ PAUSE ] [ STOP ])
  // ---------------------------------------------------------------------------

  fun startAnalyzer() {
    val previousState = _uiState.value.analyzerState
    if (previousState == AnalyzerRunState.ACTIVE) return

    screenCaptureManager.prepareCaptureSession()
    alertVibrationManager.onAnalyzerStateChanged()

    val hasValidRegion = chartRegionManager.hasValidSelection()
    val subtitle = when {
      !hasValidRegion -> "Please select the chart area again"
      _uiState.value.isScreenCaptureActive && _uiState.value.candles.size < 5 -> "Collecting candle data..."
      else -> "Live candle analysis will appear here."
    }

    _uiState.update { state ->
      state.copy(
        analyzerState = AnalyzerRunState.ACTIVE,
        nextCandleSignal = NextCandleSignal.WAITING,
        nextCandleSubtitle = subtitle,
        connectionStatusText = if (state.isScreenCaptureActive) "Chart Connected" else "Not connected",
        chartStatusText = if (state.isScreenCaptureActive) "Syncing..." else "Waiting",
        analysisStatusText = "Waiting"
      )
    }

    if (_uiState.value.dataSourceMode == ChartDataSourceMode.DEMO_PLACEHOLDER) {
      startDemoCandleStreamIfNeeded()
    }
  }

  fun pauseAnalyzer() {
    if (_uiState.value.analyzerState != AnalyzerRunState.ACTIVE) return

    screenCaptureManager.pauseCaptureSession()
    alertVibrationManager.onAnalyzerStateChanged()
    stopDemoTickerJob()

    _uiState.update { state ->
      state.copy(
        analyzerState = AnalyzerRunState.PAUSED,
        nextCandleSignal = NextCandleSignal.WAITING,
        nextCandleSubtitle = "Live candle analysis will appear here.",
        brainCandleTrackingStatus = "Paused",
        connectionStatusText = if (state.isScreenCaptureActive) "Chart Connected" else "Not connected",
        chartStatusText = "Waiting",
        analysisStatusText = "Waiting"
      )
    }
  }

  fun stopAnalyzer() {
    if (_uiState.value.analyzerState == AnalyzerRunState.OFF) return

    alertVibrationManager.onAnalyzerStateChanged()
    stopDemoTickerJob()

    _uiState.update { state ->
      state.copy(
        analyzerState = AnalyzerRunState.OFF,
        nextCandleSignal = NextCandleSignal.WAITING,
        nextCandleSubtitle = "Live candle analysis will appear here.",
        brainCandleTrackingStatus = "Waiting",
        connectionStatusText = if (state.isScreenCaptureActive) "Chart Connected" else "Not connected",
        chartStatusText = "Waiting",
        analysisStatusText = "Waiting"
      )
    }
  }

  // ---------------------------------------------------------------------------
  // 5. Optional Clearly-Labeled Demo Preview (UI & Engine Demonstration)
  // ---------------------------------------------------------------------------

  fun toggleDemoChartPreview() {
    val currentMode = _uiState.value.dataSourceMode
    if (currentMode == ChartDataSourceMode.DEMO_PLACEHOLDER) {
      disableDemoChartPreview()
    } else {
      enableDemoChartPreview()
    }
  }

  fun enableDemoChartPreview() {
    viewModelScope.launch {
      val initialDemoCandles = DemoPlaceholderCandleSeeder.generateInitialDemoCandles(
        count = 105
      )
      _uiState.update { state ->
        state.copy(
          dataSourceMode = ChartDataSourceMode.DEMO_PLACEHOLDER,
          syncState = LiveChartSyncState.DEMO_PREVIEW,
          extractionQuality = ExtractionQuality.HIGH,
          dataQualityLabel = "Good (Demo)",
          scrollOffsetFromLatest = 0,
          isAutoScrollEnabled = true,
          brainChartReadingStatus = "Demo UI Preview",
          brainCandleTrackingStatus = "Tracking live candle",
          brainDataQualityStatus = "Good (Demo)"
        )
      }
      candleDataManager.replaceAllCandles(initialDemoCandles)
      if (_uiState.value.analyzerState != AnalyzerRunState.PAUSED) {
        startDemoCandleStreamIfNeeded()
      }
    }
  }

  fun disableDemoChartPreview() {
    stopDemoTickerJob()
    viewModelScope.launch {
      candleDataManager.clear()
      candleSequenceEngine.clear()
      priceMovementDetector.clear()
      _uiState.update { state ->
        state.copy(
          dataSourceMode = ChartDataSourceMode.EMPTY_WAITING,
          syncState = LiveChartSyncState.WAITING,
          extractionQuality = ExtractionQuality.UNREADABLE,
          dataQualityLabel = "Waiting",
          scrollOffsetFromLatest = 0,
          isAutoScrollEnabled = true,
          brainChartReadingStatus = "Waiting",
          brainCandleTrackingStatus = "Waiting",
          brainSequenceMemoryStatus = "Waiting",
          brainDataQualityStatus = "Waiting",
          whyExplanations = listOf("Waiting for chart data...")
        )
      }
    }
  }

  fun onUserScrollCandles(deltaCandles: Int) {
    _uiState.update { state ->
      val maxOffset = (state.candles.size - state.visibleCandleCapacity).coerceAtLeast(0)
      val newOffset = (state.scrollOffsetFromLatest + deltaCandles).coerceIn(0, maxOffset)
      state.copy(
        scrollOffsetFromLatest = newOffset,
        isAutoScrollEnabled = (newOffset == 0)
      )
    }
    recomputeViewport()
  }

  fun scrollToNewestCandle() {
    _uiState.update { state ->
      state.copy(
        scrollOffsetFromLatest = 0,
        isAutoScrollEnabled = true
      )
    }
    recomputeViewport()
  }

  fun setVisibleCandleCapacity(capacity: Int) {
    val coerced = capacity.coerceIn(14, 40)
    if (_uiState.value.visibleCandleCapacity == coerced) return
    _uiState.update { it.copy(visibleCandleCapacity = coerced) }
    recomputeViewport()
  }

  private fun startDemoCandleStreamIfNeeded() {
    stopDemoTickerJob()
    ticksInCurrentCandle = 0
    demoTickerJob = viewModelScope.launch {
      while (isActive) {
        delay(700L)
        if (_uiState.value.dataSourceMode != ChartDataSourceMode.DEMO_PLACEHOLDER) break
        if (_uiState.value.analyzerState == AnalyzerRunState.PAUSED) continue

        val latest = candleDataManager.currentCandleFlow.value
        val basePrice = latest?.close ?: 1.08450
        ticksInCurrentCandle++

        if (ticksInCurrentCandle >= 7) {
          ticksInCurrentCandle = 0
          val nextOpen = DemoPlaceholderCandleSeeder.roundPrice(
            basePrice + demoRandom.nextDouble(-0.00008, 0.00008)
          )
          candleDataManager.closeCurrentCandleAndOpenNext(
            nextOpenPrice = nextOpen,
            nextTimestamp = System.currentTimeMillis(),
            source = CandleSource.DEMO_PLACEHOLDER,
            qualityScore = 0.95f
          )
        } else {
          val step = demoRandom.nextDouble(-0.00022, 0.00022)
          val updatedPrice = DemoPlaceholderCandleSeeder.roundPrice(basePrice + step)
          val now = System.currentTimeMillis()
          priceMovementDetector.recordPriceUpdate(updatedPrice, now)
          candleDataManager.updateCurrentCandle(
            price = updatedPrice,
            timestamp = now,
            volumeDelta = 2.5,
            source = CandleSource.DEMO_PLACEHOLDER,
            qualityScore = 0.95f
          )
        }
      }
    }
  }

  private fun stopDemoTickerJob() {
    demoTickerJob?.cancel()
    demoTickerJob = null
  }

  private fun recomputeViewport() {
    val state = _uiState.value
    val effectiveOffset = if (state.isAutoScrollEnabled) 0 else state.scrollOffsetFromLatest
    val metrics = chartEngine.computeViewportMetrics(
      allCandles = state.candles,
      visibleCapacity = state.visibleCandleCapacity,
      scrollOffsetFromLatest = effectiveOffset
    )
    _uiState.update {
      it.copy(
        viewportMetrics = metrics,
        scrollOffsetFromLatest = effectiveOffset
      )
    }
  }

  /**
   * Executes the Part 3 & Part 4 Market Analysis + Price Action & Candlestick Intelligence
   * Engines across the latest 30–40+ candles and updates the structured [MarketAnalysis]
   * object and simple "Why?" explanations.
   */
  private fun runBackgroundEnginesAndRefreshUi(candles: List<Candle>) {
    candleSequenceEngine.updateSequence(candles)

    // Analyze latest 40 candles (+ current forming candle) across all Part 3 & 4 engines
    val analysisWindow = candles.takeLast(45)
    val supportResistance = supportResistanceEngine.analyzeSupportResistance(analysisWindow)
    val sequenceFeatures = candleSequenceEngine.analyzeSequenceFeatures(
      candles = analysisWindow,
      isNearResistance = supportResistance.isNearResistance,
      isNearSupport = supportResistance.isNearSupport
    )
    val indicators = indicatorEngine.evaluateIndicators(analysisWindow)
    val priceAction = priceActionEngine.analyzePriceAction(analysisWindow)
    val patterns = candlestickPatternEngine.detectPatterns(
      candles = analysisWindow,
      supportResistance = supportResistance,
      momentum = priceAction.priceActionState.momentum,
      indicators = indicators
    )
    val regime = marketRegimeEngine.detectRegime(
      candles = analysisWindow,
      indicators = indicators,
      priceAction = priceAction.priceActionState,
      supportResistance = supportResistance,
      sequenceFeatures = sequenceFeatures
    )

    val currentState = _uiState.value
    val effectiveQuality = when {
      currentState.dataSourceMode == ChartDataSourceMode.DEMO_PLACEHOLDER && candles.isNotEmpty() ->
        ExtractionQuality.HIGH
      candles.isNotEmpty() && currentState.extractionQuality == ExtractionQuality.UNREADABLE ->
        ExtractionQuality.HIGH
      else -> currentState.extractionQuality
    }

    val isDetectionStable = currentState.dataSourceMode == ChartDataSourceMode.DEMO_PLACEHOLDER ||
      consecutiveReadableFrames >= 1 ||
      candles.size >= 5

    val microSnapshot = priceMovementDetector.getCurrentSnapshot()
    val marketAnalysis = signalConsensusEngine.buildMarketAnalysis(
      candles = analysisWindow,
      dataQuality = effectiveQuality,
      indicators = indicators,
      supportResistance = supportResistance,
      priceAction = priceAction,
      patterns = patterns,
      sequenceFeatures = sequenceFeatures,
      marketRegime = regime,
      isChartRegionValid = currentState.isChartRegionValid,
      microStructure = microSnapshot,
      outcomeMemoryRecords = proTraderBrainMemory.getOutcomeMemoryRecords(),
      isScreenDetectionStable = isDetectionStable
    )

    val completedCandles = candles.filter { it.isComplete }
    if (completedCandles.size > lastSeenCompletedCount && completedCandles.isNotEmpty()) {
      predictionHistoryRepository.onCandleCompleted(completedCandles.last())
    }

    val nextCandlePrediction = predictionEngine.generateNextCandlePrediction(
      candles = analysisWindow,
      marketAnalysis = marketAnalysis,
      microStructure = microSnapshot,
      isChartRegionValid = currentState.isChartRegionValid,
      isScreenDetectionStable = isDetectionStable
    )

    if (completedCandles.isNotEmpty()) {
      predictionHistoryRepository.stageActivePrediction(
        prediction = nextCandlePrediction,
        marketRegime = marketAnalysis.marketRegime,
        lastCompletedCandleTimestamp = completedCandles.last().timestamp
      )
    }

    backtestingEngine.runRollingEvaluation(candles)

    if (completedCandles.size > lastSeenCompletedCount && completedCandles.size >= 5) {
      lastSeenCompletedCount = completedCandles.size
      val indicatorMap = buildMap {
        indicators.emaSignal.ema9?.let { put("EMA9", it) }
        indicators.emaSignal.ema21?.let { put("EMA21", it) }
        indicators.rsiSignal.rsiValue?.let { put("RSI14", it) }
        indicators.atrState.atrValue?.let { put("ATR14", it) }
      }
      val srContextLabel = when {
        supportResistance.resistanceRejection -> "RESISTANCE_REJECTION"
        supportResistance.supportRejection -> "SUPPORT_REJECTION"
        supportResistance.isNearResistance -> "NEAR_RESISTANCE"
        supportResistance.isNearSupport -> "NEAR_SUPPORT"
        else -> "MID_RANGE"
      }
      proTraderBrainMemory.recordCompletedSequence(
        recentCandleSequence = completedCandles.takeLast(40),
        marketState = marketAnalysis.marketRegime.name,
        detectedPatterns = patterns.patterns.map { it.patternName },
        indicatorValues = indicatorMap,
        predictionResult = nextCandlePrediction.uiSignal,
        marketRegime = marketAnalysis.marketRegime,
        priceActionContext = marketAnalysis.priceActionContext,
        supportResistanceContext = srContextLabel,
        momentumState = marketAnalysis.momentumState,
        volatilityState = marketAnalysis.volatilityState,
        setupQuality = marketAnalysis.setupQualityGrade,
        dataQuality = effectiveQuality,
        featureVector = marketAnalysis.featureVector,
        setupSnapshot = marketAnalysis.marketStateSnapshot,
        patternFingerprint = marketAnalysis.patternFingerprint,
        predictedDirection = marketAnalysis.brainAssessment.recommendationState,
        isScreenDetectionStable = isDetectionStable
      )
    } else if (completedCandles.isEmpty()) {
      lastSeenCompletedCount = 0
    }

    val effectiveOffset = if (currentState.isAutoScrollEnabled) {
      0
    } else {
      currentState.scrollOffsetFromLatest
    }

    val metrics = chartEngine.computeViewportMetrics(
      allCandles = candles,
      visibleCapacity = currentState.visibleCandleCapacity,
      scrollOffsetFromLatest = effectiveOffset
    )

    val latestCandle = candles.lastOrNull()
    val trackerSnapshot = latestCandle?.let {
      CurrentCandleTrackerSnapshot.fromCandle(it)
    }

    val combinedWhy = if (candles.isEmpty()) {
      marketAnalysis.simpleWhyExplanations
    } else {
      (nextCandlePrediction.reasons + marketAnalysis.simpleWhyExplanations).distinct().take(5)
    }

    _uiState.update { state ->
      val resolvedSignal = when {
        !state.isChartRegionValid -> NextCandleSignal.WAIT
        candles.isEmpty() && !state.isScreenCaptureActive -> NextCandleSignal.WAITING
        else -> nextCandlePrediction.uiSignal
      }
      val resolvedSubtitle = when {
        !state.isChartRegionValid -> "Please select the chart area again"
        candles.isEmpty() && !state.isScreenCaptureActive -> "Live candle analysis will appear here."
        else -> nextCandlePrediction.primaryWhyLine
      }
      state.copy(
        candles = candles,
        currentCandle = latestCandle,
        currentCandleTracker = trackerSnapshot,
        viewportMetrics = metrics,
        scrollOffsetFromLatest = effectiveOffset,
        marketAnalysis = marketAnalysis,
        brainAssessment = marketAnalysis.brainAssessment,
        nextCandlePrediction = nextCandlePrediction,
        setupQualityBadgeText = nextCandlePrediction.setupDisplayBadgeText,
        predictionHistory = predictionHistoryRepository.getFinalizedHistory(limit = 50),
        whyExplanations = combinedWhy,
        brainSequenceMemoryStatus = candleSequenceEngine.getSequenceStatusWord(),
        brainCandleTrackingStatus = if (candles.isEmpty()) {
          "Waiting"
        } else {
          candleDataManager.getCandleTrackingStatusWord()
        },
        nextCandleSignal = resolvedSignal,
        nextCandleSubtitle = resolvedSubtitle
      )
    }
  }

  override fun onCleared() {
    super.onCleared()
    stopDemoTickerJob()
    screenCaptureManager.stopCaptureSession()
  }
}

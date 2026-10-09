package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.AnalyzerRunState
import com.example.ui.components.CandlestickChartPanel
import com.example.ui.components.ChartRegionSelectorDialog
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavyElevated
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.DeepNavyBlack
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.HighlightBlue
import com.example.ui.theme.SignalDownRed
import com.example.ui.theme.SignalDownRedDim
import com.example.ui.theme.SignalUpGreen
import com.example.ui.theme.SignalUpGreenDim
import com.example.ui.theme.SignalWaitYellow
import com.example.ui.theme.SignalWaitYellowDim
import com.example.ui.theme.TextMutedSlate
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySlate

@Composable
fun OtcVisionHomeScreen(
  viewModel: OtcVisionViewModel,
  modifier: Modifier = Modifier
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current

  // Legitimate Android MediaProjection screen-capture permission launcher
  val screenCaptureLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { activityResult ->
    viewModel.onScreenCapturePermissionResult(
      context = context,
      resultCode = activityResult.resultCode,
      data = activityResult.data
    )
  }

  val onStartCaptureClick = {
    val intent = viewModel.createScreenCapturePermissionIntent(context)
    if (intent != null) {
      try {
        screenCaptureLauncher.launch(intent)
      } catch (_: Exception) {
        viewModel.stopScreenCapture()
      }
    }
  }

  if (uiState.isRegionSelectorDialogOpen) {
    ChartRegionSelectorDialog(
      initialRegion = uiState.selectedChartRegion,
      onConfirmRegion = { region ->
        if (uiState.selectedChartRegion == null) {
          viewModel.selectChartRegion(region)
        } else {
          viewModel.reselectChartRegion(region)
        }
      },
      onClearSelection = viewModel::clearChartRegionSelection,
      onDismiss = viewModel::dismissChartRegionSelector
    )
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = DeepNavyBlack,
    contentWindowInsets = WindowInsets.safeDrawing
  ) { innerPadding ->
    BoxWithConstraints(
      modifier = Modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(
              Color(0xFF081020),
              DeepNavyBlack,
              Color(0xFF050811)
            )
          )
        )
        .padding(innerPadding)
    ) {
      val isWideLayout = maxWidth >= 700.dp
      val screenHeight = maxHeight

      if (isWideLayout) {
        Row(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
          Column(
            modifier = Modifier
              .weight(1.35f)
              .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            TopHeaderBar(
              analyzerState = uiState.analyzerState,
              statusText = uiState.analyzerStatusHeaderText,
              isScreenCaptureActive = uiState.isScreenCaptureActive,
              screenCaptureText = uiState.screenCaptureHeaderText
            )
            CandlestickChartPanel(
              viewportMetrics = uiState.viewportMetrics,
              totalCandleCount = uiState.candles.size,
              isDemoPreviewActive = uiState.isDemoPreviewActive,
              isLiveCaptureActive = uiState.isScreenCaptureActive,
              syncState = uiState.syncState,
              extractionQuality = uiState.extractionQuality,
              dataQualityLabel = uiState.dataQualityLabel,
              isAutoScrollEnabled = uiState.isAutoScrollEnabled,
              chartEngine = viewModel.chartEngine,
              onToggleDemoPreview = viewModel::toggleDemoChartPreview,
              onScrollCandles = viewModel::onUserScrollCandles,
              onScrollToNewest = viewModel::scrollToNewestCandle,
              onCapacityMeasured = viewModel::setVisibleCandleCapacity,
              modifier = Modifier.weight(1f)
            )
            CaptureAndChartRegionControlsCard(
              isScreenCaptureActive = uiState.isScreenCaptureActive,
              hasSelectedRegion = uiState.selectedChartRegion != null && uiState.isChartRegionValid,
              selectedRegionLabel = uiState.selectedChartRegion?.label,
              onStartCapture = onStartCaptureClick,
              onStopCapture = viewModel::stopScreenCapture,
              onOpenRegionSelector = viewModel::openChartRegionSelector,
              onClearRegionSelection = viewModel::clearChartRegionSelection
            )
          }

          Column(
            modifier = Modifier
              .weight(1f)
              .fillMaxHeight()
              .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            NextCandleAnalysisSection(
              displayStatusTitle = uiState.nextCandleCardDisplayTitle,
              subtitleText = uiState.nextCandleSubtitle,
              isWhyExpanded = uiState.isWhySectionExpanded,
              whyExplanations = uiState.whyExplanations,
              onToggleWhyExpand = viewModel::toggleWhySectionExpanded
            )
            ExpandableBrainStatusCard(
              isExpanded = uiState.isBrainStatusExpanded,
              onToggleExpand = viewModel::toggleBrainStatusExpanded,
              chartReadingStatus = uiState.brainChartReadingStatus,
              candleTrackingStatus = uiState.brainCandleTrackingStatus,
              sequenceMemoryStatus = uiState.brainSequenceMemoryStatus,
              dataQualityStatus = uiState.brainDataQualityStatus
            )
            AnalyzerControlButtonsSection(
              analyzerState = uiState.analyzerState,
              onStartClick = viewModel::startAnalyzer,
              onPauseClick = viewModel::pauseAnalyzer,
              onStopClick = viewModel::stopAnalyzer
            )
            SystemStatusFooterCard(
              connectionText = uiState.connectionStatusText,
              chartText = uiState.chartStatusText,
              analysisText = uiState.analysisStatusText
            )
          }
        }
      } else {
        val chartMinHeight = (screenHeight * 0.34f).coerceIn(210.dp, 340.dp)
        Column(
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            // 1. Top Title & Analyzer / Screen Capture Status Indicators
            TopHeaderBar(
              analyzerState = uiState.analyzerState,
              statusText = uiState.analyzerStatusHeaderText,
              isScreenCaptureActive = uiState.isScreenCaptureActive,
              screenCaptureText = uiState.screenCaptureHeaderText
            )

            // 2. Main LIVE CHART Panel
            CandlestickChartPanel(
              viewportMetrics = uiState.viewportMetrics,
              totalCandleCount = uiState.candles.size,
              isDemoPreviewActive = uiState.isDemoPreviewActive,
              isLiveCaptureActive = uiState.isScreenCaptureActive,
              syncState = uiState.syncState,
              extractionQuality = uiState.extractionQuality,
              dataQualityLabel = uiState.dataQualityLabel,
              isAutoScrollEnabled = uiState.isAutoScrollEnabled,
              chartEngine = viewModel.chartEngine,
              onToggleDemoPreview = viewModel::toggleDemoChartPreview,
              onScrollCandles = viewModel::onUserScrollCandles,
              onScrollToNewest = viewModel::scrollToNewestCandle,
              onCapacityMeasured = viewModel::setVisibleCandleCapacity,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = chartMinHeight, max = 360.dp)
            )

            // 3. Live Screen Capture & Chart Region Controls
            CaptureAndChartRegionControlsCard(
              isScreenCaptureActive = uiState.isScreenCaptureActive,
              hasSelectedRegion = uiState.selectedChartRegion != null && uiState.isChartRegionValid,
              selectedRegionLabel = uiState.selectedChartRegion?.label,
              onStartCapture = onStartCaptureClick,
              onStopCapture = viewModel::stopScreenCapture,
              onOpenRegionSelector = viewModel::openChartRegionSelector,
              onClearRegionSelection = viewModel::clearChartRegionSelection
            )

            // 4. NEXT CANDLE Status Card + Expandable "Why?" Section
            NextCandleAnalysisSection(
              displayStatusTitle = uiState.nextCandleCardDisplayTitle,
              subtitleText = uiState.nextCandleSubtitle,
              isWhyExpanded = uiState.isWhySectionExpanded,
              whyExplanations = uiState.whyExplanations,
              onToggleWhyExpand = viewModel::toggleWhySectionExpanded
            )

            // 5. Expandable "Brain Status" Section (Part 2 Requirement 11)
            ExpandableBrainStatusCard(
              isExpanded = uiState.isBrainStatusExpanded,
              onToggleExpand = viewModel::toggleBrainStatusExpanded,
              chartReadingStatus = uiState.brainChartReadingStatus,
              candleTrackingStatus = uiState.brainCandleTrackingStatus,
              sequenceMemoryStatus = uiState.brainSequenceMemoryStatus,
              dataQualityStatus = uiState.brainDataQualityStatus
            )

            // 6. Control Buttons: [ START ANALYZER ] [ PAUSE ] [ STOP ]
            AnalyzerControlButtonsSection(
              analyzerState = uiState.analyzerState,
              onStartClick = viewModel::startAnalyzer,
              onPauseClick = viewModel::pauseAnalyzer,
              onStopClick = viewModel::stopAnalyzer
            )

            // 7. Small Status Section: Connection, Chart, Analysis
            SystemStatusFooterCard(
              connectionText = uiState.connectionStatusText,
              chartText = uiState.chartStatusText,
              analysisText = uiState.analysisStatusText
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TopHeaderBar(
  analyzerState: AnalyzerRunState,
  statusText: String,
  isScreenCaptureActive: Boolean,
  screenCaptureText: String
) {
  val indicatorDotColor by animateColorAsState(
    targetValue = when (analyzerState) {
      AnalyzerRunState.OFF -> TextMutedSlate
      AnalyzerRunState.ACTIVE -> ElectricCyan
      AnalyzerRunState.PAUSED -> SignalWaitYellow
    },
    animationSpec = tween(250),
    label = "status_dot_color"
  )

  val pillBorderColor by animateColorAsState(
    targetValue = when (analyzerState) {
      AnalyzerRunState.OFF -> DarkNavyBorder
      AnalyzerRunState.ACTIVE -> ElectricCyan.copy(alpha = 0.7f)
      AnalyzerRunState.PAUSED -> SignalWaitYellow.copy(alpha = 0.7f)
    },
    animationSpec = tween(250),
    label = "status_border_color"
  )

  val pillBgColor by animateColorAsState(
    targetValue = when (analyzerState) {
      AnalyzerRunState.OFF -> DarkNavySurface
      AnalyzerRunState.ACTIVE -> DeepCyanContainer
      AnalyzerRunState.PAUSED -> SignalWaitYellowDim
    },
    animationSpec = tween(250),
    label = "status_bg_color"
  )

  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Box(
          modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Color.White)
            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(11.dp))
            .testTag("app_brand_logo"),
          contentAlignment = Alignment.Center
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_brand_logo),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier
              .size(30.dp)
          )
        }

        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold
          ),
          color = TextPrimaryWhite,
          modifier = Modifier.testTag("app_title_text")
        )
      }

      // Status Indicator: "Analyzer: OFF" / "Analyzer: ACTIVE" / "Analyzer: PAUSED"
      Surface(
        shape = RoundedCornerShape(50),
        color = pillBgColor,
        border = BorderStroke(1.dp, pillBorderColor),
        modifier = Modifier.testTag("analyzer_status_indicator")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .clip(CircleShape)
              .background(indicatorDotColor)
          )
          Text(
            text = statusText,
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold
            ),
            color = when (analyzerState) {
              AnalyzerRunState.OFF -> TextSecondarySlate
              AnalyzerRunState.ACTIVE -> ElectricCyan
              AnalyzerRunState.PAUSED -> SignalWaitYellow
            }
          )
        }
      }
    }

    // Screen Capture Status Pill ("Screen Capture: OFF / ON")
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Surface(
        shape = RoundedCornerShape(50),
        color = if (isScreenCaptureActive) SignalUpGreenDim else DarkNavySurface,
        border = BorderStroke(
          1.dp,
          if (isScreenCaptureActive) SignalUpGreen.copy(alpha = 0.7f) else DarkNavyBorder
        ),
        modifier = Modifier.testTag("screen_capture_status_indicator")
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Box(
            modifier = Modifier
              .size(7.dp)
              .clip(CircleShape)
              .background(if (isScreenCaptureActive) SignalUpGreen else TextMutedSlate)
          )
          Text(
            text = screenCaptureText,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = if (isScreenCaptureActive) SignalUpGreen else TextSecondarySlate
          )
        }
      }

      Text(
        text = "Local On-Device Vision",
        style = MaterialTheme.typography.labelSmall,
        color = TextMutedSlate
      )
    }
  }
}

@Composable
private fun CaptureAndChartRegionControlsCard(
  isScreenCaptureActive: Boolean,
  hasSelectedRegion: Boolean,
  selectedRegionLabel: String?,
  onStartCapture: () -> Unit,
  onStopCapture: () -> Unit,
  onOpenRegionSelector: () -> Unit,
  onClearRegionSelection: () -> Unit
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("capture_and_region_controls_card"),
    shape = RoundedCornerShape(16.dp),
    color = DarkNavySurface,
    border = BorderStroke(1.dp, DarkNavyBorder)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Row 1: Chart Area Selection Buttons (Select Chart Area / Re-select Chart Area / Clear Selection)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        OutlinedButton(
          onClick = onOpenRegionSelector,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.65f)),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = DeepCyanContainer.copy(alpha = 0.5f),
            contentColor = ElectricCyan
          ),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier
            .weight(1.3f)
            .heightIn(min = 48.dp)
            .testTag(
              if (hasSelectedRegion) "reselect_chart_area_button" else "select_chart_area_button"
            )
        ) {
          Icon(
            imageVector = Icons.Default.CropFree,
            contentDescription = null,
            modifier = Modifier.size(17.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (hasSelectedRegion) {
              stringResource(R.string.btn_reselect_chart_area)
            } else {
              stringResource(R.string.btn_select_chart_area)
            },
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp)
          )
        }

        OutlinedButton(
          onClick = onClearRegionSelection,
          enabled = hasSelectedRegion,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.dp, DarkNavyBorder),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = DarkNavyCard,
            contentColor = TextSecondarySlate,
            disabledContainerColor = DarkNavySurface,
            disabledContentColor = TextMutedSlate
          ),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("clear_selection_button")
        ) {
          Icon(
            imageVector = Icons.Default.DeleteOutline,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = stringResource(R.string.btn_clear_selection),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp)
          )
        }
      }

      // Row 2: Screen Capture Start / STOP CAPTURE Button
      if (!isScreenCaptureActive) {
        OutlinedButton(
          onClick = onStartCapture,
          shape = RoundedCornerShape(12.dp),
          border = BorderStroke(1.5.dp, ElectricCyan),
          colors = ButtonDefaults.outlinedButtonColors(
            containerColor = DeepCyanContainer,
            contentColor = ElectricCyan
          ),
          contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .testTag("start_capture_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ScreenShare,
            contentDescription = null,
            modifier = Modifier.size(19.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.btn_start_capture),
            style = MaterialTheme.typography.labelLarge.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp
            )
          )
        }
      } else {
        Button(
          onClick = onStopCapture,
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = SignalDownRed,
            contentColor = Color.White
          ),
          contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .testTag("stop_capture_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.StopScreenShare,
            contentDescription = null,
            modifier = Modifier.size(19.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.btn_stop_capture),
            style = MaterialTheme.typography.labelLarge.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp
            )
          )
        }
      }

      if (selectedRegionLabel != null && hasSelectedRegion) {
        Text(
          text = "Region: $selectedRegionLabel (Chart-only privacy crop active)",
          style = MaterialTheme.typography.labelSmall,
          color = TextMutedSlate,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  }
}

@Composable
private fun NextCandleAnalysisSection(
  displayStatusTitle: String,
  subtitleText: String,
  isWhyExpanded: Boolean,
  whyExplanations: List<String>,
  onToggleWhyExpand: () -> Unit
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = stringResource(R.string.section_next_candle),
        style = MaterialTheme.typography.titleMedium.copy(
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.2.sp
        ),
        color = ElectricCyan,
        modifier = Modifier.testTag("next_candle_section_title")
      )

      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        SignalKeyPill(
          label = stringResource(R.string.status_up),
          textColor = SignalUpGreen.copy(alpha = 0.45f),
          bgColor = SignalUpGreenDim.copy(alpha = 0.35f),
          borderColor = SignalUpGreen.copy(alpha = 0.25f)
        )
        SignalKeyPill(
          label = stringResource(R.string.status_down),
          textColor = SignalDownRed.copy(alpha = 0.45f),
          bgColor = SignalDownRedDim.copy(alpha = 0.35f),
          borderColor = SignalDownRed.copy(alpha = 0.25f)
        )
        SignalKeyPill(
          label = stringResource(R.string.status_wait),
          textColor = SignalWaitYellow,
          bgColor = SignalWaitYellowDim,
          borderColor = SignalWaitYellow.copy(alpha = 0.8f)
        )
      }
    }

    // Large WAIT / WAITING Status Card
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .testTag("next_candle_card"),
      shape = RoundedCornerShape(20.dp),
      color = DarkNavyCard,
      border = BorderStroke(1.5.dp, SignalWaitYellow.copy(alpha = 0.55f)),
      tonalElevation = 6.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .background(
            Brush.radialGradient(
              colors = listOf(
                SignalWaitYellowDim.copy(alpha = 0.45f),
                DarkNavyCard
              )
            )
          )
          .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = SignalWaitYellow,
            modifier = Modifier.size(28.dp)
          )
          Text(
            text = displayStatusTitle,
            style = MaterialTheme.typography.displayMedium.copy(
              fontWeight = FontWeight.Bold,
              letterSpacing = 2.sp
            ),
            color = SignalWaitYellow,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("next_candle_status_text")
          )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = subtitleText,
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondarySlate,
          textAlign = TextAlign.Center,
          modifier = Modifier.testTag("next_candle_subtitle_text")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Optional small expandable "Why?" pill (Part 3 Requirement 23)
        Surface(
          modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onToggleWhyExpand)
            .testTag("why_expandable_header"),
          shape = RoundedCornerShape(50),
          color = DarkNavySurface,
          border = BorderStroke(1.dp, DarkNavyBorder)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Text(
              text = stringResource(R.string.why_section_title),
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
              color = ElectricCyan
            )
            Icon(
              imageVector = if (isWhyExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
              contentDescription = null,
              tint = ElectricCyan,
              modifier = Modifier.size(16.dp)
            )
          }
        }

        AnimatedVisibility(
          visible = isWhyExpanded,
          enter = expandVertically() + fadeIn(),
          exit = shrinkVertically() + fadeOut()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 10.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(DarkNavySurface.copy(alpha = 0.85f))
              .padding(horizontal = 14.dp, vertical = 10.dp)
              .testTag("why_expandable_content"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            whyExplanations.forEach { reason ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Box(
                  modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(ElectricCyan)
                )
                Text(
                  text = reason,
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextPrimaryWhite
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ExpandableBrainStatusCard(
  isExpanded: Boolean,
  onToggleExpand: () -> Unit,
  chartReadingStatus: String,
  candleTrackingStatus: String,
  sequenceMemoryStatus: String,
  dataQualityStatus: String
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onToggleExpand)
      .testTag("brain_status_header"),
    shape = RoundedCornerShape(16.dp),
    color = DarkNavySurface,
    border = BorderStroke(1.dp, DarkNavyBorder)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Psychology,
            contentDescription = null,
            tint = ElectricCyan,
            modifier = Modifier.size(20.dp)
          )
          Text(
            text = stringResource(R.string.brain_status_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = TextPrimaryWhite
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = dataQualityStatus,
            style = MaterialTheme.typography.labelSmall,
            color = ElectricCyan
          )
          Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "Collapse Brain Status" else "Expand Brain Status",
            tint = TextSecondarySlate
          )
        }
      }

      AnimatedVisibility(
        visible = isExpanded,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .testTag("brain_status_content"),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          BrainStatusWordRow(
            label = stringResource(R.string.brain_label_chart_reading),
            wordValue = chartReadingStatus,
            valueTestTag = "brain_status_chart_reading"
          )
          BrainStatusWordRow(
            label = stringResource(R.string.brain_label_candle_tracking),
            wordValue = candleTrackingStatus,
            valueTestTag = "brain_status_candle_tracking"
          )
          BrainStatusWordRow(
            label = stringResource(R.string.brain_label_sequence_memory),
            wordValue = sequenceMemoryStatus,
            valueTestTag = "brain_status_sequence_memory"
          )
          BrainStatusWordRow(
            label = stringResource(R.string.brain_label_data_quality),
            wordValue = dataQualityStatus,
            valueTestTag = "brain_status_data_quality"
          )
        }
      }
    }
  }
}

@Composable
private fun BrainStatusWordRow(
  label: String,
  wordValue: String,
  valueTestTag: String
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(10.dp))
      .background(DarkNavyCard)
      .padding(horizontal = 12.dp, vertical = 9.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.bodyMedium,
      color = TextSecondarySlate
    )
    Text(
      text = wordValue,
      style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
      color = TextPrimaryWhite,
      modifier = Modifier.testTag(valueTestTag)
    )
  }
}

@Composable
private fun SignalKeyPill(
  label: String,
  textColor: Color,
  bgColor: Color,
  borderColor: Color
) {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = bgColor,
    border = BorderStroke(1.dp, borderColor)
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp
      ),
      color = textColor,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
    )
  }
}

@Composable
private fun AnalyzerControlButtonsSection(
  analyzerState: AnalyzerRunState,
  onStartClick: () -> Unit,
  onPauseClick: () -> Unit,
  onStopClick: () -> Unit
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp)
  ) {
    val isAlreadyActive = analyzerState == AnalyzerRunState.ACTIVE
    Button(
      onClick = onStartClick,
      enabled = !isAlreadyActive,
      shape = RoundedCornerShape(16.dp),
      colors = ButtonDefaults.buttonColors(
        containerColor = ElectricCyan,
        contentColor = DeepNavyBlack,
        disabledContainerColor = DeepCyanContainer,
        disabledContentColor = ElectricCyan
      ),
      contentPadding = PaddingValues(vertical = 15.dp, horizontal = 20.dp),
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 54.dp)
        .border(
          width = if (isAlreadyActive) 1.5.dp else 0.dp,
          color = if (isAlreadyActive) ElectricCyan else Color.Transparent,
          shape = RoundedCornerShape(16.dp)
        )
        .testTag("start_analyzer_button")
    ) {
      Icon(
        imageVector = Icons.Default.PlayArrow,
        contentDescription = null,
        modifier = Modifier.size(22.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = stringResource(R.string.btn_start_analyzer),
        style = MaterialTheme.typography.labelLarge.copy(
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          letterSpacing = 1.sp
        )
      )
    }

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      val canPause = analyzerState == AnalyzerRunState.ACTIVE
      val isPaused = analyzerState == AnalyzerRunState.PAUSED

      OutlinedButton(
        onClick = onPauseClick,
        enabled = canPause,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
          width = 1.5.dp,
          color = when {
            isPaused -> SignalWaitYellow
            canPause -> SignalWaitYellow.copy(alpha = 0.75f)
            else -> DarkNavyBorder
          }
        ),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = if (isPaused) SignalWaitYellowDim else DarkNavySurface,
          contentColor = SignalWaitYellow,
          disabledContainerColor = if (isPaused) SignalWaitYellowDim else DarkNavySurface,
          disabledContentColor = if (isPaused) SignalWaitYellow else TextMutedSlate
        ),
        contentPadding = PaddingValues(vertical = 13.dp, horizontal = 16.dp),
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 52.dp)
          .testTag("pause_button")
      ) {
        Icon(
          imageVector = Icons.Default.Pause,
          contentDescription = null,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.btn_pause),
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
      }

      val canStop = analyzerState != AnalyzerRunState.OFF
      OutlinedButton(
        onClick = onStopClick,
        enabled = canStop,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
          width = 1.5.dp,
          color = if (canStop) SignalDownRed.copy(alpha = 0.8f) else DarkNavyBorder
        ),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = if (canStop) SignalDownRedDim.copy(alpha = 0.6f) else DarkNavySurface,
          contentColor = SignalDownRed,
          disabledContainerColor = DarkNavySurface,
          disabledContentColor = TextMutedSlate
        ),
        contentPadding = PaddingValues(vertical = 13.dp, horizontal = 16.dp),
        modifier = Modifier
          .weight(1f)
          .heightIn(min = 52.dp)
          .testTag("stop_button")
      ) {
        Icon(
          imageVector = Icons.Default.Stop,
          contentDescription = null,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = stringResource(R.string.btn_stop),
          style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
      }
    }
  }
}

@Composable
private fun SystemStatusFooterCard(
  connectionText: String,
  chartText: String,
  analysisText: String
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("status_footer_section"),
    shape = RoundedCornerShape(16.dp),
    color = DarkNavySurface,
    border = BorderStroke(1.dp, DarkNavyBorder)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      StatusMetricItem(
        label = stringResource(R.string.label_connection),
        value = connectionText,
        valueColor = if (connectionText == "Chart Connected") SignalUpGreen else TextSecondarySlate,
        valueTestTag = "status_connection_value",
        modifier = Modifier.weight(1.2f)
      )
      StatusVerticalDivider()
      StatusMetricItem(
        label = stringResource(R.string.label_chart),
        value = chartText,
        valueColor = if (chartText == "Live") SignalUpGreen else SignalWaitYellow,
        valueTestTag = "status_chart_value",
        modifier = Modifier.weight(1f)
      )
      StatusVerticalDivider()
      StatusMetricItem(
        label = stringResource(R.string.label_analysis),
        value = analysisText,
        valueColor = SignalWaitYellow,
        valueTestTag = "status_analysis_value",
        modifier = Modifier.weight(1f)
      )
    }
  }
}

@Composable
private fun StatusMetricItem(
  label: String,
  value: String,
  valueColor: Color,
  valueTestTag: String,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier.padding(horizontal = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(3.dp)
  ) {
    Text(
      text = "$label:",
      style = MaterialTheme.typography.labelSmall,
      color = TextMutedSlate,
      textAlign = TextAlign.Center
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Bold
      ),
      color = valueColor,
      textAlign = TextAlign.Center,
      modifier = Modifier.testTag(valueTestTag)
    )
  }
}

@Composable
private fun StatusVerticalDivider() {
  Box(
    modifier = Modifier
      .width(1.dp)
      .height(28.dp)
      .background(DarkNavyElevated)
  )
}

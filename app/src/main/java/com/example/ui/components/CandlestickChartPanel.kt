package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.chart.ChartEngine
import com.example.engine.chart.ChartViewportMetrics
import com.example.model.Candle
import com.example.model.ExtractionQuality
import com.example.model.LiveChartSyncState
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavyElevated
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.DeepNavyBlack
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GridLineSubtle
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SignalDownRed
import com.example.ui.theme.SignalUpGreen
import com.example.ui.theme.SignalUpGreenDim
import com.example.ui.theme.SignalWaitYellow
import com.example.ui.theme.SignalWaitYellowDim
import com.example.ui.theme.TextMutedSlate
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySlate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Dedicated LIVE CHART panel and Mirrored Candlestick Chart Component for OTC Vision AI (Parts 1 & 2).
 */
@Composable
fun CandlestickChartPanel(
  viewportMetrics: ChartViewportMetrics,
  totalCandleCount: Int,
  isDemoPreviewActive: Boolean,
  isLiveCaptureActive: Boolean,
  syncState: LiveChartSyncState,
  extractionQuality: ExtractionQuality,
  dataQualityLabel: String,
  isAutoScrollEnabled: Boolean,
  chartEngine: ChartEngine,
  onToggleDemoPreview: () -> Unit,
  onScrollCandles: (Int) -> Unit,
  onScrollToNewest: () -> Unit,
  onCapacityMeasured: (Int) -> Unit,
  modifier: Modifier = Modifier
) {
  val hasCandlesToRender = viewportMetrics.visibleCandles.isNotEmpty() &&
    (isDemoPreviewActive || isLiveCaptureActive)

  Surface(
    modifier = modifier
      .fillMaxWidth()
      .testTag("live_chart_panel"),
    shape = RoundedCornerShape(20.dp),
    color = DarkNavySurface,
    border = BorderStroke(1.dp, DarkNavyBorder),
    tonalElevation = 4.dp
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp)
    ) {
      // Top Bar of the LIVE CHART panel: "LIVE CHART" + "SYNCING..." / "● LIVE" + Demo toggle
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Box(
            modifier = Modifier
              .size(10.dp)
              .clip(CircleShape)
              .background(
                when (syncState) {
                  LiveChartSyncState.LIVE -> SignalUpGreen
                  LiveChartSyncState.SYNCING -> SignalWaitYellow
                  LiveChartSyncState.DEMO_PREVIEW -> ElectricCyan
                  LiveChartSyncState.WAITING -> TextMutedSlate
                }
              )
          )
          Text(
            text = stringResource(R.string.section_live_chart),
            style = MaterialTheme.typography.titleMedium.copy(
              fontWeight = FontWeight.Bold,
              letterSpacing = 1.2.sp
            ),
            color = ElectricCyan,
            modifier = Modifier.testTag("live_chart_title")
          )

          // Sync state badge: "SYNCING..." or "● LIVE"
          val syncBadgeText = when (syncState) {
            LiveChartSyncState.LIVE -> stringResource(R.string.chart_live_indicator)
            LiveChartSyncState.SYNCING -> stringResource(R.string.chart_syncing)
            LiveChartSyncState.DEMO_PREVIEW -> "DEMO"
            LiveChartSyncState.WAITING -> null
          }
          if (syncBadgeText != null) {
            val badgeColor = when (syncState) {
              LiveChartSyncState.LIVE -> SignalUpGreen
              LiveChartSyncState.SYNCING -> SignalWaitYellow
              LiveChartSyncState.DEMO_PREVIEW -> ElectricCyan
              LiveChartSyncState.WAITING -> TextSecondarySlate
            }
            val badgeBg = when (syncState) {
              LiveChartSyncState.LIVE -> SignalUpGreenDim
              LiveChartSyncState.SYNCING -> SignalWaitYellowDim
              LiveChartSyncState.DEMO_PREVIEW -> DeepCyanContainer
              LiveChartSyncState.WAITING -> DarkNavyElevated
            }
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = badgeBg,
              border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.65f)),
              modifier = Modifier.testTag("chart_sync_badge")
            ) {
              Text(
                text = syncBadgeText,
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  fontSize = 10.sp
                ),
                color = badgeColor,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
              )
            }
          }
        }

        // Toggle chip for Demo / Placeholder UI Preview
        Surface(
          modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onToggleDemoPreview)
            .testTag("demo_chart_toggle_button"),
          shape = RoundedCornerShape(50),
          color = if (isDemoPreviewActive) DeepCyanContainer else DarkNavyElevated,
          border = BorderStroke(
            width = 1.dp,
            color = if (isDemoPreviewActive) ElectricCyan.copy(alpha = 0.7f) else DarkNavyBorder
          )
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = if (isDemoPreviewActive) {
                Icons.Outlined.VisibilityOff
              } else {
                Icons.Outlined.Visibility
              },
              contentDescription = null,
              tint = if (isDemoPreviewActive) ElectricCyan else TextSecondarySlate,
              modifier = Modifier.size(14.dp)
            )
            Text(
              text = if (isDemoPreviewActive) {
                stringResource(R.string.toggle_demo_feed_off)
              } else {
                stringResource(R.string.toggle_demo_feed_on)
              },
              style = MaterialTheme.typography.labelMedium,
              color = if (isDemoPreviewActive) ElectricCyan else TextSecondarySlate
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // Simple Status Row under LIVE CHART header:
      // ● Chart Connected / ○ Chart Not Connected  |  ● Data Quality: Good / Waiting
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        val isChartConnected = isLiveCaptureActive
        Text(
          text = if (isChartConnected) {
            stringResource(R.string.status_chart_connected)
          } else {
            stringResource(R.string.status_chart_disconnected)
          },
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = if (isChartConnected) SignalUpGreen else TextSecondarySlate,
          modifier = Modifier.testTag("chart_connected_status_badge")
        )

        val qualityColor = when (extractionQuality) {
          ExtractionQuality.HIGH -> SignalUpGreen
          ExtractionQuality.MEDIUM -> ElectricCyan
          ExtractionQuality.LOW -> SignalWaitYellow
          ExtractionQuality.UNREADABLE -> TextSecondarySlate
        }
        Text(
          text = "● Data Quality: $dataQualityLabel",
          style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
          color = qualityColor,
          modifier = Modifier.testTag("chart_data_quality_badge")
        )
      }

      // Clearly labeled DEMO / PLACEHOLDER banner when Demo Preview is shown
      AnimatedVisibility(
        visible = isDemoPreviewActive,
        enter = fadeIn(),
        exit = fadeOut()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SignalWaitYellowDim.copy(alpha = 0.75f))
            .border(1.dp, SignalWaitYellow.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("demo_mode_banner"),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = stringResource(R.string.demo_mode_badge),
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp
            ),
            color = SignalWaitYellow,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "${totalCandleCount}c buffer",
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimaryWhite
          )
        }
      }

      // Main Candlestick Chart Frame
      BoxWithConstraints(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .clip(RoundedCornerShape(14.dp))
          .background(
            Brush.verticalGradient(
              colors = listOf(
                DeepNavyBlack,
                Color(0xFF08101E),
                DeepNavyBlack
              )
            )
          )
          .border(1.dp, GridLineSubtle, RoundedCornerShape(14.dp))
      ) {
        val availableWidthDp = maxWidth
        LaunchedEffect(availableWidthDp) {
          // Display latest 30–40 candles on wide screens, or comfortable 18–36 on compact phones
          val chartPlotWidthDp = (availableWidthDp.value - 68f).coerceAtLeast(160f)
          val idealCapacity = (chartPlotWidthDp / 9.5f).roundToInt().coerceIn(20, 40)
          onCapacityMeasured(idealCapacity)
        }

        if (!hasCandlesToRender) {
          EmptyChartPlaceholderState(
            viewportMetrics = viewportMetrics,
            syncState = syncState,
            chartEngine = chartEngine,
            onLoadDemoPreview = onToggleDemoPreview
          )
        } else {
          LiveCandlestickCanvasContent(
            viewportMetrics = viewportMetrics,
            isAutoScrollEnabled = isAutoScrollEnabled,
            chartEngine = chartEngine,
            onScrollCandles = onScrollCandles,
            onScrollToNewest = onScrollToNewest
          )
        }
      }
    }
  }
}

@Composable
private fun EmptyChartPlaceholderState(
  viewportMetrics: ChartViewportMetrics,
  syncState: LiveChartSyncState,
  chartEngine: ChartEngine,
  onLoadDemoPreview: () -> Unit
) {
  Box(modifier = Modifier.fillMaxSize()) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val priceColumnWidth = 64.dp.toPx()
      val bottomAxisHeight = 24.dp.toPx()
      val plotWidth = (size.width - priceColumnWidth).coerceAtLeast(1f)
      val plotHeight = (size.height - bottomAxisHeight).coerceAtLeast(1f)

      val horizontalLines = 5
      for (i in 0 until horizontalLines) {
        val y = (plotHeight / (horizontalLines - 1).coerceAtLeast(1)) * i
        drawLine(
          color = GridLineSubtle.copy(alpha = 0.65f),
          start = Offset(0f, y),
          end = Offset(plotWidth, y),
          strokeWidth = 1.dp.toPx()
        )
      }

      val verticalLines = 6
      for (i in 1 until verticalLines) {
        val x = (plotWidth / verticalLines) * i
        drawLine(
          color = GridLineSubtle.copy(alpha = 0.45f),
          start = Offset(x, 0f),
          end = Offset(x, plotHeight),
          strokeWidth = 1.dp.toPx()
        )
      }

      drawLine(
        color = DarkNavyBorder,
        start = Offset(plotWidth, 0f),
        end = Offset(plotWidth, plotHeight),
        strokeWidth = 1.dp.toPx()
      )
      drawLine(
        color = DarkNavyBorder,
        start = Offset(0f, plotHeight),
        end = Offset(size.width, plotHeight),
        strokeWidth = 1.dp.toPx()
      )
    }

    Column(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .fillMaxHeight()
        .padding(bottom = 24.dp, end = 6.dp, top = 4.dp),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.End
    ) {
      viewportMetrics.priceGridLevels.forEach { level ->
        Text(
          text = chartEngine.formatPrice(level),
          style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
          color = TextMutedSlate.copy(alpha = 0.65f)
        )
      }
    }

    Column(
      modifier = Modifier
        .align(Alignment.Center)
        .padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(54.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(Color.White)
          .border(1.5.dp, ElectricCyan.copy(alpha = 0.55f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
      ) {
        androidx.compose.foundation.Image(
          painter = androidx.compose.ui.res.painterResource(id = R.drawable.ic_brand_logo),
          contentDescription = stringResource(R.string.app_name),
          modifier = Modifier.size(38.dp)
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = if (syncState == LiveChartSyncState.SYNCING) {
          stringResource(R.string.chart_syncing)
        } else {
          stringResource(R.string.chart_placeholder_waiting)
        },
        style = MaterialTheme.typography.titleLarge.copy(
          fontWeight = FontWeight.Bold
        ),
        color = TextPrimaryWhite,
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("chart_placeholder_text")
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = stringResource(R.string.chart_placeholder_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondarySlate,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(12.dp))

      OutlinedButton(
        onClick = onLoadDemoPreview,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
        colors = ButtonDefaults.outlinedButtonColors(
          containerColor = DeepCyanContainer.copy(alpha = 0.45f),
          contentColor = ElectricCyan
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
        modifier = Modifier.testTag("load_demo_chart_button")
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.TrendingUp,
          contentDescription = null,
          modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.toggle_demo_feed_on),
          style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
        )
      }
    }
  }
}

@Composable
private fun LiveCandlestickCanvasContent(
  viewportMetrics: ChartViewportMetrics,
  isAutoScrollEnabled: Boolean,
  chartEngine: ChartEngine,
  onScrollCandles: (Int) -> Unit,
  onScrollToNewest: () -> Unit
) {
  val candles = viewportMetrics.visibleCandles
  val latestCandle: Candle? = candles.lastOrNull()

  val animatedClose by animateFloatAsState(
    targetValue = (latestCandle?.close ?: 1.08500).toFloat(),
    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
    label = "live_candle_close"
  )
  val animatedHigh by animateFloatAsState(
    targetValue = (latestCandle?.high ?: 1.08500).toFloat(),
    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
    label = "live_candle_high"
  )
  val animatedLow by animateFloatAsState(
    targetValue = (latestCandle?.low ?: 1.08500).toFloat(),
    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
    label = "live_candle_low"
  )

  val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
  val livePulseAlpha by infiniteTransition.animateFloat(
    initialValue = 0.25f,
    targetValue = 0.85f,
    animationSpec = infiniteRepeatable(
      animation = tween(900, easing = FastOutSlowInEasing),
      repeatMode = RepeatMode.Reverse
    ),
    label = "live_pulse_alpha"
  )

  var dragAccumulator by remember { mutableFloatStateOf(0f) }
  val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
  val chartDescription = stringResource(R.string.cd_candlestick_chart)

  Box(modifier = Modifier.fillMaxSize()) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .semantics { contentDescription = chartDescription }
        .pointerInput(candles.size) {
          detectHorizontalDragGestures(
            onDragStart = { dragAccumulator = 0f },
            onHorizontalDrag = { change, dragAmount ->
              change.consume()
              dragAccumulator += dragAmount
              val thresholdPx = 18.dp.toPx()
              if (abs(dragAccumulator) >= thresholdPx) {
                val steps = (dragAccumulator / thresholdPx).toInt()
                dragAccumulator -= steps * thresholdPx
                onScrollCandles(steps)
              }
            }
          )
        }
        .testTag("candlestick_chart_canvas")
    ) {
      val priceAxisWidth = 68.dp.toPx()
      val timeAxisHeight = 26.dp.toPx()
      val topPadding = 10.dp.toPx()
      val bottomPadding = 8.dp.toPx()

      val plotWidth = (size.width - priceAxisWidth).coerceAtLeast(10f)
      val plotHeight = (size.height - timeAxisHeight - topPadding - bottomPadding).coerceAtLeast(10f)
      val plotBottomY = topPadding + plotHeight

      val gridCount = viewportMetrics.priceGridLevels.size
      for (i in 0 until gridCount) {
        val fraction = if (gridCount > 1) i.toFloat() / (gridCount - 1).toFloat() else 0.5f
        val y = topPadding + fraction * plotHeight
        drawLine(
          color = GridLineSubtle.copy(alpha = 0.75f),
          start = Offset(0f, y),
          end = Offset(plotWidth, y),
          strokeWidth = 1.dp.toPx()
        )
      }

      val slotCount = max(candles.size, 12)
      val slotWidth = plotWidth / slotCount.toFloat()
      val bodyWidth = (slotWidth * 0.64f).coerceIn(3.dp.toPx(), 16.dp.toPx())
      val wickStrokeWidth = 1.6.dp.toPx()

      for (i in candles.indices step 5) {
        val centerX = (i + 0.5f) * slotWidth
        if (centerX < plotWidth) {
          drawLine(
            color = GridLineSubtle.copy(alpha = 0.45f),
            start = Offset(centerX, 0f),
            end = Offset(centerX, plotBottomY),
            strokeWidth = 1.dp.toPx()
          )
        }
      }

      for (i in candles.indices) {
        val candle = candles[i]
        val isNewestActive = (i == candles.lastIndex && !candle.isComplete && isAutoScrollEnabled)

        val effectiveClose = if (isNewestActive) animatedClose.toDouble() else candle.close
        val effectiveHigh = if (isNewestActive) {
          max(animatedHigh.toDouble(), max(candle.open, effectiveClose))
        } else {
          candle.high
        }
        val effectiveLow = if (isNewestActive) {
          min(animatedLow.toDouble(), min(candle.open, effectiveClose))
        } else {
          candle.low
        }

        val isBullish = effectiveClose >= candle.open
        val candleColor = if (isBullish) SignalUpGreen else SignalDownRed

        val centerX = (i + 0.5f) * slotWidth
        val highY = topPadding + viewportMetrics.priceToNormalizedY(effectiveHigh) * plotHeight
        val lowY = topPadding + viewportMetrics.priceToNormalizedY(effectiveLow) * plotHeight
        val openY = topPadding + viewportMetrics.priceToNormalizedY(candle.open) * plotHeight
        val closeY = topPadding + viewportMetrics.priceToNormalizedY(effectiveClose) * plotHeight

        val bodyTopY = min(openY, closeY)
        val bodyBottomY = max(openY, closeY)
        val rawBodyHeight = bodyBottomY - bodyTopY
        val minBodyHeight = 2.5.dp.toPx()
        val finalBodyHeight = max(rawBodyHeight, minBodyHeight)

        if (isNewestActive) {
          drawRoundRect(
            color = candleColor.copy(alpha = 0.16f * livePulseAlpha),
            topLeft = Offset(centerX - bodyWidth * 0.85f, highY - 3.dp.toPx()),
            size = Size(bodyWidth * 1.7f, (lowY - highY + 6.dp.toPx()).coerceAtLeast(8.dp.toPx())),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
          )
        }

        drawLine(
          color = candleColor,
          start = Offset(centerX, highY),
          end = Offset(centerX, lowY),
          strokeWidth = wickStrokeWidth
        )

        drawRoundRect(
          color = candleColor,
          topLeft = Offset(centerX - bodyWidth / 2f, bodyTopY),
          size = Size(bodyWidth, finalBodyHeight),
          cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        )
      }

      if (latestCandle != null) {
        val currentPrice = if (!latestCandle.isComplete && isAutoScrollEnabled) {
          animatedClose.toDouble()
        } else {
          latestCandle.close
        }
        val currentPriceY = topPadding + viewportMetrics.priceToNormalizedY(currentPrice) * plotHeight
        val lineColor = if (currentPrice >= latestCandle.open) SignalUpGreen else SignalDownRed

        drawLine(
          color = lineColor.copy(alpha = 0.85f),
          start = Offset(0f, currentPriceY),
          end = Offset(plotWidth, currentPriceY),
          strokeWidth = 1.2.dp.toPx(),
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )
      }

      drawLine(
        color = DarkNavyBorder,
        start = Offset(plotWidth, 0f),
        end = Offset(plotWidth, plotBottomY),
        strokeWidth = 1.dp.toPx()
      )
      drawLine(
        color = DarkNavyBorder,
        start = Offset(0f, plotBottomY),
        end = Offset(size.width, plotBottomY),
        strokeWidth = 1.dp.toPx()
      )
    }

    Column(
      modifier = Modifier
        .align(Alignment.TopEnd)
        .fillMaxHeight()
        .width(66.dp)
        .padding(top = 6.dp, bottom = 28.dp, end = 6.dp),
      verticalArrangement = Arrangement.SpaceBetween,
      horizontalAlignment = Alignment.End
    ) {
      viewportMetrics.priceGridLevels.forEach { priceLevel ->
        Text(
          text = chartEngine.formatPrice(priceLevel),
          style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp
          ),
          color = TextSecondarySlate
        )
      }
    }

    if (latestCandle != null) {
      val livePriceColor = if (latestCandle.isBullish) SignalUpGreen else SignalDownRed
      Surface(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(start = 10.dp, top = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = DarkNavyCard.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, livePriceColor.copy(alpha = 0.6f))
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Box(
            modifier = Modifier
              .size(7.dp)
              .clip(CircleShape)
              .background(livePriceColor)
          )
          Text(
            text = chartEngine.formatPrice(latestCandle.close),
            style = MaterialTheme.typography.labelMedium.copy(
              fontWeight = FontWeight.Bold
            ),
            color = TextPrimaryWhite
          )
        }
      }
    }

    Row(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .fillMaxWidth()
        .padding(end = 68.dp, start = 8.dp, bottom = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      val firstCandle = candles.firstOrNull()
      val midCandle = candles.getOrNull(candles.size / 2)
      val lastCandle = candles.lastOrNull()

      Text(
        text = firstCandle?.let { timeFormatter.format(Date(it.timestamp)) } ?: "--:--:--",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = TextMutedSlate
      )
      Text(
        text = midCandle?.let { timeFormatter.format(Date(it.timestamp)) } ?: "--:--:--",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = TextMutedSlate
      )
      Text(
        text = lastCandle?.let { timeFormatter.format(Date(it.timestamp)) } ?: "--:--:--",
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = ElectricCyan
      )
    }

    AnimatedVisibility(
      visible = !isAutoScrollEnabled,
      enter = fadeIn(),
      exit = fadeOut(),
      modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = 76.dp, bottom = 34.dp)
    ) {
      Surface(
        modifier = Modifier
          .clip(RoundedCornerShape(50))
          .clickable(onClick = onScrollToNewest)
          .testTag("auto_scroll_button"),
        shape = RoundedCornerShape(50),
        color = DeepCyanContainer,
        border = BorderStroke(1.dp, ElectricCyan)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Icon(
            imageVector = Icons.Default.FastForward,
            contentDescription = stringResource(R.string.cd_auto_scroll),
            tint = ElectricCyan,
            modifier = Modifier.size(14.dp)
          )
          Text(
            text = "Newest Candle",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = ElectricCyan
          )
        }
      }
    }
  }
}

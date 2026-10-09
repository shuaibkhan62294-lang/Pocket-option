package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.region.ChartRegion
import com.example.ui.theme.DarkNavyBorder
import com.example.ui.theme.DarkNavyCard
import com.example.ui.theme.DarkNavyElevated
import com.example.ui.theme.DarkNavySurface
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.DeepNavyBlack
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GridLineSubtle
import com.example.ui.theme.SignalDownRed
import com.example.ui.theme.SignalUpGreen
import com.example.ui.theme.TextMutedSlate
import com.example.ui.theme.TextPrimaryWhite
import com.example.ui.theme.TextSecondarySlate

/**
 * Beginner-friendly Chart Region Selector dialog (Requirement 2).
 *
 * Allows the user to select or fine-tune the normalized rectangular area containing ONLY
 * the visible chart, excluding account headers, passwords, or trade execution panels.
 */
@Composable
fun ChartRegionSelectorDialog(
  initialRegion: ChartRegion?,
  onConfirmRegion: (ChartRegion) -> Unit,
  onClearSelection: () -> Unit,
  onDismiss: () -> Unit
) {
  val startRegion = initialRegion ?: ChartRegion.DEFAULT_CENTER_CHART_REGION
  var left by remember(startRegion) { mutableFloatStateOf(startRegion.leftFraction) }
  var top by remember(startRegion) { mutableFloatStateOf(startRegion.topFraction) }
  var right by remember(startRegion) { mutableFloatStateOf(startRegion.rightFraction) }
  var bottom by remember(startRegion) { mutableFloatStateOf(startRegion.bottomFraction) }
  var selectedPresetLabel by remember(startRegion) { mutableStateOf(startRegion.label) }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth(0.94f)
        .testTag("chart_region_selector_dialog"),
      shape = RoundedCornerShape(22.dp),
      color = DarkNavySurface,
      border = BorderStroke(1.5.dp, ElectricCyan.copy(alpha = 0.6f))
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        // Header
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
              imageVector = Icons.Default.CropFree,
              contentDescription = null,
              tint = ElectricCyan,
              modifier = Modifier.size(22.dp)
            )
            Text(
              text = "Select Chart Area",
              style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
              color = TextPrimaryWhite
            )
          }

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(36.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Close",
              tint = TextSecondarySlate
            )
          }
        }

        Text(
          text = "Frame only the candlestick chart area. Top account bars and bottom buttons are excluded for privacy.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondarySlate
        )

        // Quick Preset Chips for Beginners
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          val presets = listOf(
            ChartRegion.DEFAULT_CENTER_CHART_REGION,
            ChartRegion.UPPER_SPLIT_CHART_REGION,
            ChartRegion.WIDE_LANDSCAPE_CHART_REGION
          )
          presets.forEach { preset ->
            val isSelected = selectedPresetLabel == preset.label
            Surface(
              modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                  left = preset.leftFraction
                  top = preset.topFraction
                  right = preset.rightFraction
                  bottom = preset.bottomFraction
                  selectedPresetLabel = preset.label
                },
              shape = RoundedCornerShape(10.dp),
              color = if (isSelected) DeepCyanContainer else DarkNavyCard,
              border = BorderStroke(
                1.dp,
                if (isSelected) ElectricCyan else DarkNavyBorder
              )
            ) {
              Text(
                text = preset.label,
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp
                ),
                color = if (isSelected) ElectricCyan else TextSecondarySlate,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
              )
            }
          }
        }

        // Interactive Visual Region Bounding Box Canvas
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DeepNavyBlack)
            .border(1.dp, DarkNavyBorder, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
              detectDragGestures { change, dragAmount ->
                change.consume()
                val w = size.width.toFloat().coerceAtLeast(1f)
                val h = size.height.toFloat().coerceAtLeast(1f)
                val touchX = (change.position.x / w).coerceIn(0f, 1f)
                val touchY = (change.position.y / h).coerceIn(0f, 1f)
                val dx = dragAmount.x / w
                val dy = dragAmount.y / h

                val centerX = (left + right) / 2f
                val centerY = (top + bottom) / 2f

                if (touchX < centerX) {
                  left = (left + dx).coerceIn(0.02f, right - 0.25f)
                } else {
                  right = (right + dx).coerceIn(left + 0.25f, 0.98f)
                }

                if (touchY < centerY) {
                  top = (top + dy).coerceIn(0.05f, bottom - 0.22f)
                } else {
                  bottom = (bottom + dy).coerceIn(top + 0.22f, 0.95f)
                }
                selectedPresetLabel = "Custom Chart Area"
              }
            }
            .testTag("chart_region_interactive_canvas")
        ) {
          Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Top & Bottom excluded privacy zones
            drawRect(
              color = DarkNavyElevated.copy(alpha = 0.45f),
              topLeft = Offset(0f, 0f),
              size = Size(w, h * 0.12f)
            )
            drawRect(
              color = DarkNavyElevated.copy(alpha = 0.45f),
              topLeft = Offset(0f, h * 0.85f),
              size = Size(w, h * 0.15f)
            )

            // Sample background chart candles inside the screen preview
            val sampleCount = 12
            val startX = w * 0.08f
            val endX = w * 0.90f
            val slotW = (endX - startX) / sampleCount
            for (i in 0 until sampleCount) {
              val isBull = i % 2 == 0 || i % 5 == 0
              val cColor = if (isBull) SignalUpGreen.copy(alpha = 0.45f) else SignalDownRed.copy(alpha = 0.45f)
              val cx = startX + (i + 0.5f) * slotW
              val cy = h * (0.48f - (i % 4 - 1.5f) * 0.06f)
              drawLine(
                color = cColor,
                start = Offset(cx, cy - 22.dp.toPx()),
                end = Offset(cx, cy + 22.dp.toPx()),
                strokeWidth = 2.dp.toPx()
              )
              drawRoundRect(
                color = cColor,
                topLeft = Offset(cx - slotW * 0.28f, cy - 11.dp.toPx()),
                size = Size(slotW * 0.56f, 22.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
              )
            }

            // Selected Region Highlight Box
            val rectLeft = left * w
            val rectTop = top * h
            val rectWidth = (right - left) * w
            val rectHeight = (bottom - top) * h

            drawRoundRect(
              color = ElectricCyan.copy(alpha = 0.12f),
              topLeft = Offset(rectLeft, rectTop),
              size = Size(rectWidth, rectHeight),
              cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            )
            drawRoundRect(
              color = ElectricCyan,
              topLeft = Offset(rectLeft, rectTop),
              size = Size(rectWidth, rectHeight),
              cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
              style = Stroke(
                width = 2.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f), 0f)
              )
            )

            // Corner grab handles
            val handleRadius = 5.dp.toPx()
            listOf(
              Offset(rectLeft, rectTop),
              Offset(rectLeft + rectWidth, rectTop),
              Offset(rectLeft, rectTop + rectHeight),
              Offset(rectLeft + rectWidth, rectTop + rectHeight)
            ).forEach { corner ->
              drawCircle(color = ElectricCyan, radius = handleRadius, center = corner)
            }
          }

          Text(
            text = "EXCLUDED: TOP STATUS / ACCOUNT BAR",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = TextMutedSlate,
            modifier = Modifier
              .align(Alignment.TopCenter)
              .padding(top = 5.dp)
          )

          Surface(
            modifier = Modifier.align(Alignment.Center),
            shape = RoundedCornerShape(8.dp),
            color = DeepNavyBlack.copy(alpha = 0.82f),
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f))
          ) {
            Text(
              text = "$selectedPresetLabel (${((right - left) * 100).toInt()}% × ${((bottom - top) * 100).toInt()}%)",
              style = MaterialTheme.typography.labelMedium,
              color = ElectricCyan,
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
          }

          Text(
            text = "EXCLUDED: TRADE BUTTONS / FOOTER",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = TextMutedSlate,
            modifier = Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 5.dp)
          )
        }

        // Action Buttons
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          OutlinedButton(
            onClick = {
              onClearSelection()
              onDismiss()
            },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DarkNavyBorder),
            colors = ButtonDefaults.outlinedButtonColors(
              containerColor = DarkNavyCard,
              contentColor = TextSecondarySlate
            ),
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 14.dp),
            modifier = Modifier
              .weight(1f)
              .testTag("dialog_clear_region_button")
          ) {
            Text(
              text = "Clear Selection",
              style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
            )
          }

          Button(
            onClick = {
              onConfirmRegion(
                ChartRegion(
                  leftFraction = left,
                  topFraction = top,
                  rightFraction = right,
                  bottomFraction = bottom,
                  label = selectedPresetLabel
                )
              )
              onDismiss()
            },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = ElectricCyan,
              contentColor = DeepNavyBlack
            ),
            contentPadding = PaddingValues(vertical = 12.dp, horizontal = 14.dp),
            modifier = Modifier
              .weight(1.2f)
              .testTag("dialog_confirm_region_button")
          ) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Save Chart Area",
              style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
              )
            )
          }
        }
      }
    }
  }
}

package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OtcVisionDarkColorScheme = darkColorScheme(
  primary = ElectricCyan,
  onPrimary = DeepNavyBlack,
  primaryContainer = DeepCyanContainer,
  onPrimaryContainer = ElectricCyan,
  secondary = HighlightBlue,
  onSecondary = Color.White,
  secondaryContainer = DarkNavyElevated,
  onSecondaryContainer = TextPrimaryWhite,
  tertiary = SignalWaitYellow,
  onTertiary = DeepNavyBlack,
  tertiaryContainer = SignalWaitYellowDim,
  onTertiaryContainer = SignalWaitYellow,
  background = DeepNavyBlack,
  onBackground = TextPrimaryWhite,
  surface = DarkNavySurface,
  onSurface = TextPrimaryWhite,
  surfaceVariant = DarkNavyCard,
  onSurfaceVariant = TextSecondarySlate,
  outline = DarkNavyBorder,
  outlineVariant = GridLineSubtle,
  error = SignalDownRed,
  onError = Color.White,
  errorContainer = SignalDownRedDim,
  onErrorContainer = SignalDownRed
)

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = OtcVisionDarkColorScheme,
    typography = Typography,
    content = content
  )
}

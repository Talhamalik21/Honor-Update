package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = MinimalBlueMedium,
    onPrimary = HonorDarkBg,
    primaryContainer = MinimalBlueDark,
    onPrimaryContainer = MinimalBlueContainer,
    secondary = MinimalEmerald,
    onSecondary = HonorDarkBg,
    background = HonorDarkBg,
    surface = HonorDarkSurface,
    onBackground = HonorDarkOnSurface,
    onSurface = HonorDarkOnSurface,
    error = HonorCrimson
  )

private val LightColorScheme =
  lightColorScheme(
    primary = MinimalBlue,
    onPrimary = HonorLightSurface,
    primaryContainer = MinimalBlueContainer,
    onPrimaryContainer = MinimalBlueDark,
    secondary = MinimalEmerald,
    onSecondary = HonorLightSurface,
    background = HonorLightBg,
    surface = HonorLightSurface,
    onBackground = HonorLightOnSurface,
    onSurface = HonorLightOnSurface,
    error = HonorCrimson
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Set to false to enforce premium brand identity
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

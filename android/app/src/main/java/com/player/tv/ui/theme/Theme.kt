package com.player.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val colors = darkColorScheme(
    primary = Brand.Primary,
    onPrimary = Brand.TextPrimary,
    secondary = Brand.Secondary,
    onSecondary = Brand.TextPrimary,
    background = Brand.Background,
    onBackground = Brand.TextPrimary,
    surface = Brand.Surface,
    onSurface = Brand.TextPrimary,
    surfaceVariant = Brand.SurfaceHigh,
    onSurfaceVariant = Brand.TextSecondary,
    inverseSurface = Brand.TextPrimary,
    inverseOnSurface = Brand.Background,
    border = Brand.Primary,
    error = Brand.Error,
)

@Composable
fun PlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}

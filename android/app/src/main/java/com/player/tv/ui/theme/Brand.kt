package com.player.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette. Change these values to rebrand the app (keep res/values/colors.xml in sync,
 * it is used by the launcher icon and banner).
 */
object Brand {
    val Primary = Color(0xFF4F8CFF)
    val Secondary = Color(0xFF8A5CFF)
    val Background = Color(0xFF0B0E14)
    val BackgroundGlow = Color(0xFF16213D)
    val Surface = Color(0xFF151A24)
    val SurfaceHigh = Color(0xFF1F2636)
    val TextPrimary = Color(0xFFEDF1F8)
    val TextSecondary = Color(0xFF98A2B8)
    val Error = Color(0xFFFF6B6B)
    val Warning = Color(0xFFFFB547)

    val LiveGradient = listOf(Color(0xFFFF5F6D), Color(0xFFFFA45C))
    val MoviesGradient = listOf(Color(0xFF4F8CFF), Color(0xFF8A5CFF))
    val SeriesGradient = listOf(Color(0xFF0FA38F), Color(0xFF5BDB7F))
}

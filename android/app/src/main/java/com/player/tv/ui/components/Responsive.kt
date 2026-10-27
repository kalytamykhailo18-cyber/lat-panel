package com.player.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Screen size class, from the window size in dp:
 * - COMPACT: phones held upright (< 600 dp wide)
 * - MEDIUM: tablets held upright, phones in landscape, small windows
 * - EXPANDED: TVs (960 x 540 dp) and tablets in landscape
 */
enum class SizeClass { COMPACT, MEDIUM, EXPANDED }

data class ScreenInfo(val size: SizeClass, val widthDp: Int, val heightDp: Int) {
    val compact get() = size == SizeClass.COMPACT
    val expanded get() = size == SizeClass.EXPANDED
    /** Little vertical room (phones in landscape): fixed-height blocks must shrink and scroll. */
    val shortHeight get() = heightDp < 480

    /** Outer horizontal padding of a screen. */
    val gutter: Dp get() = when (size) {
        SizeClass.COMPACT -> 16.dp
        SizeClass.MEDIUM -> 24.dp
        SizeClass.EXPANDED -> if (widthDp >= 1200) 56.dp else 40.dp
    }
}

@Composable
fun screenInfo(): ScreenInfo {
    // The app's own window, so split screen and freeform windows on tablets get the right layout.
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current.density
    val config = LocalConfiguration.current
    val w = if (window.width > 0) (window.width / density).toInt() else config.screenWidthDp
    val h = if (window.height > 0) (window.height / density).toInt() else config.screenHeightDp
    val size = when {
        w < 600 -> SizeClass.COMPACT
        w < 900 || h < 480 -> SizeClass.MEDIUM
        else -> SizeClass.EXPANDED
    }
    return ScreenInfo(size, w, h)
}

/**
 * Two blocks side by side (intro text and a panel), centred vertically. On phones they stack;
 * whenever the content is taller than the screen it scrolls instead of being cut off.
 */
@Composable
fun AdaptivePanes(
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
    firstWeight: Float = 1f,
    secondWeight: Float = 1f,
    topInset: Dp = 0.dp,
) {
    val screen = screenInfo()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = if (screen.expanded) 80.dp else screen.gutter
        val vertical = if (screen.compact || screen.shortHeight) 20.dp else 48.dp
        val frame = Modifier
            .fillMaxWidth()
            .heightIn(min = maxHeight)
            .padding(start = side, end = side, top = maxOf(vertical, topInset), bottom = vertical)
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (screen.compact) {
                Column(frame, verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically)) {
                    first(Modifier.fillMaxWidth())
                    second(Modifier.fillMaxWidth())
                }
            } else {
                Row(
                    frame,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (screen.expanded) 64.dp else 32.dp),
                ) {
                    first(Modifier.weight(firstWeight))
                    second(Modifier.weight(secondWeight))
                }
            }
        }
    }
}

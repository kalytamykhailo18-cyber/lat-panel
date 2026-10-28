package com.player.tv.ui.components

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.player.tv.R
import com.player.tv.Routes
import com.player.tv.ui.theme.Brand
import kotlinx.coroutines.delay
import java.util.Date

private enum class Section(val route: String, @StringRes val label: Int, val icon: () -> ImageVector) {
    HOME(Routes.HOME, R.string.home_title, { AppIcons.Home }),
    SEARCH(Routes.SEARCH, R.string.home_search, { AppIcons.Search }),
    LIVE(Routes.LIVE, R.string.home_live, { AppIcons.LiveTv }),
    GUIDE(Routes.GUIDE, R.string.guide_title, { AppIcons.Guide }),
    MOVIES(Routes.MOVIES, R.string.home_movies, { AppIcons.Movie }),
    SERIES(Routes.SERIES, R.string.home_series, { AppIcons.Series }),
    SETTINGS(Routes.SETTINGS, R.string.home_settings, { AppIcons.Settings }),
}

val RailWidth = 76.dp
private val RailExpandedWidth = 240.dp

/**
 * Side menu of the main sections. Collapsed it shows only icons; when the remote moves focus
 * into it (Left from the content) it widens over the content and shows the labels. Coming back
 * into the menu always lands on the current section.
 */
@Composable
fun MainShell(current: String, onNavigate: (String) -> Unit, content: @Composable BoxScope.() -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) RailExpandedWidth else RailWidth, tween(200), label = "rail")
    val currentFocus = remember { FocusRequester() }
    val short = screenInfo().shortHeight   // phones in landscape: tighter menu, no clock

    AppBackground {
        Box(Modifier.fillMaxSize().padding(start = RailWidth), content = content)
        AnimatedVisibility(expanded, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color(0xF00B0E14), 0.5f to Color(0xA00B0E14), 1f to Color(0x400B0E14))))
        }
        Column(
            Modifier
                .width(width)
                .fillMaxHeight()
                .background(Brush.horizontalGradient(listOf(Brand.Surface, Brand.Surface.copy(alpha = if (expanded) 1f else 0.85f))))
                .onFocusChanged { expanded = it.hasFocus }
                .focusRestorer(currentFocus)
                .focusGroup()
                .padding(vertical = if (short) 10.dp else 20.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (short) 2.dp else 6.dp),
        ) {
            Row(Modifier.height(if (short) 40.dp else 48.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Logo(Modifier.size(if (short) 36.dp else 44.dp))
                if (expanded) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = Brand.TextPrimary,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }
            }
            Spacer(Modifier.height(if (short) 4.dp else 18.dp))
            Section.entries.forEach { section ->
                val selected = section.route == current
                RailItem(
                    label = stringResource(section.label),
                    icon = section.icon(),
                    selected = selected,
                    expanded = expanded,
                    height = if (short) 40.dp else 48.dp,
                    modifier = if (selected) Modifier.focusRequester(currentFocus) else Modifier,
                    onClick = { if (!selected) onNavigate(section.route) },
                )
            }
            Spacer(Modifier.weight(1f))
            if (!short) Clock(Modifier.align(if (expanded) Alignment.Start else Alignment.CenterHorizontally).padding(start = if (expanded) 8.dp else 0.dp))
        }
    }
}

@Composable
private fun RailItem(label: String, icon: ImageVector, selected: Boolean, expanded: Boolean, height: androidx.compose.ui.unit.Dp, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(height).tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Brand.Primary.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = if (selected) Brand.Primary else Brand.TextSecondary,
            focusedContainerColor = Brand.Primary,
            focusedContentColor = Color.White,
        ),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp))
            if (expanded) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun Clock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = System.currentTimeMillis()
        }
    }
    Text(DateFormat.getTimeFormat(context).format(Date(now)), modifier = modifier, style = MaterialTheme.typography.titleMedium,
        color = Brand.TextPrimary, maxLines = 1, softWrap = false)
}

package com.player.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Icon
import com.player.tv.ui.components.AppIcons
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.player.tv.R
import com.player.tv.data.EpgEntry
import com.player.tv.ui.theme.Brand
import java.util.Date
import kotlin.math.abs

data class OverlayState(
    val item: PlayItem? = null,
    val showBanner: Boolean = false,
    val epg: List<EpgEntry> = emptyList(),
    val message: String? = null,
    val typedNumber: String? = null,   // digits being typed to jump to a channel
    val controlsVisible: Boolean = false,  // VOD: Media3 controls on screen
    val loading: Boolean = false,          // until the first frame: "Cargando <title>…"
    val failed: Boolean = false,           // playback gave up: retry / back panel
    val notice: String? = null,            // short message top-right (resume point, next episode)
)

@Composable
fun PlayerOverlay(
    state: OverlayState,
    onTap: () -> Unit = {},
    onSwipe: (step: Int) -> Unit = {},
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    // Live TV on touch screens: tap shows the channel banner, swipe up/down changes channel.
    // VOD keeps the gestures for Media3's own controls underneath.
    val touch = if (state.item?.isLive == true) {
        Modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
            .pointerInput(Unit) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { _, amount -> dragged += amount },
                    onDragEnd = { if (abs(dragged) > 80.dp.toPx()) onSwipe(if (dragged < 0) +1 else -1) },
                )
            }
    } else Modifier
    Box(Modifier.fillMaxSize().then(touch)) {
        val item = state.item
        AnimatedVisibility(
            visible = state.showBanner && item != null && item.isLive,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            if (item != null) ChannelBanner(item, state.epg)
        }
        // Back button for mouse and touch (the remote uses its Back key). Shown with the
        // channel banner or the VOD controls; not focusable, so the D-pad never lands on it.
        AnimatedVisibility(
            visible = state.showBanner || state.controlsVisible || state.failed,
            modifier = Modifier.align(Alignment.TopStart).padding(28.dp),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xAA000000))
                    .pointerInput(Unit) { detectTapGestures(onTap = { onBack() }) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Back, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        state.typedNumber?.let {
            Text(
                it,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(40.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 28.dp, vertical = 12.dp),
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        // What is loading, under Media3's buffering spinner.
        val loadingItem = state.item
        AnimatedVisibility(
            visible = state.loading && !state.failed && loadingItem != null,
            modifier = Modifier.align(Alignment.Center).padding(top = 110.dp, start = 48.dp, end = 48.dp),
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(250)),
        ) {
            Text(
                stringResource(R.string.player_loading, loadingItem?.title.orEmpty()),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        // Keep the last notice text while it fades out.
        var lastNotice by remember { mutableStateOf("") }
        state.notice?.let { lastNotice = it }
        AnimatedVisibility(
            visible = state.notice != null,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 32.dp, end = 40.dp),
            enter = fadeIn(tween(250)) + slideInHorizontally(tween(300)) { it / 3 },
            exit = fadeOut(tween(300)),
        ) {
            Text(
                lastNotice,
                modifier = Modifier
                    .background(Color(0xCC000000), RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
        }
        AnimatedVisibility(
            visible = state.failed,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(tween(250)) + scaleIn(tween(300), initialScale = 0.92f),
            exit = fadeOut(tween(200)),
        ) {
            ErrorPanel(onRetry = onRetry, onBack = onBack)
        }
        state.message?.let {
            Text(
                it,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                    .padding(horizontal = 28.dp, vertical = 16.dp),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun ChannelBanner(item: PlayItem, epg: List<EpgEntry>) {
    val context = LocalContext.current
    val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
    fun range(e: EpgEntry) = "${timeFormat.format(Date(e.start * 1000))} – ${timeFormat.format(Date(e.end * 1000))}"

    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xEE000000))))
            .padding(horizontal = 56.dp, vertical = 36.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.size(width = 120.dp, height = 80.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33FFFFFF))) {
                AsyncImage(item.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (item.number > 0) {
                        Text(item.number.toString(), style = MaterialTheme.typography.headlineSmall, color = Brand.Primary, fontWeight = FontWeight.Bold)
                    }
                    Text(item.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val now = epg.getOrNull(0)
                val next = epg.getOrNull(1)
                if (now == null) {
                    Text(stringResource(R.string.epg_none), color = Brand.TextSecondary, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text("${stringResource(R.string.epg_now)}: ${now.title}  ·  ${range(now)}", color = Color.White, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    val total = (now.end - now.start).coerceAtLeast(1)
                    val elapsed = (System.currentTimeMillis() / 1000 - now.start).coerceIn(0, total)
                    Box(Modifier.fillMaxWidth(0.6f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x44FFFFFF))) {
                        Box(Modifier.fillMaxWidth(elapsed.toFloat() / total).height(4.dp).background(Brand.Primary))
                    }
                    if (next != null) {
                        Text("${stringResource(R.string.epg_next)}: ${next.title}  ·  ${range(next)}", color = Brand.TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Shown when playback gave up: what happened and what the user can do, by tap or remote. */
@Composable
private fun ErrorPanel(onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Color(0xE6000000), RoundedCornerShape(16.dp))
            .padding(horizontal = 36.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.player_error), style = MaterialTheme.typography.titleLarge, color = Color.White, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PanelAction(stringResource(R.string.retry), AppIcons.Replay, primary = true, onClick = onRetry)
            PanelAction(stringResource(R.string.player_back), AppIcons.Back, primary = false, onClick = onBack)
        }
        Text(stringResource(R.string.player_error_keys), style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary)
    }
}

@Composable
private fun PanelAction(label: String, icon: ImageVector, primary: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (primary) Brand.Primary else Color(0x33FFFFFF))
            .pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) }
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = Color.White)
    }
}

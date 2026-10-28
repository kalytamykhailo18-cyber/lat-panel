package com.player.tv.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.player.tv.R
import com.player.tv.data.EpgEntry
import com.player.tv.ui.theme.Brand
import kotlin.math.abs

data class OverlayState(
    val item: PlayItem? = null,
    val index: Int = 0,                    // position of [item] in the queue
    val showBanner: Boolean = false,       // live: short channel banner while zapping
    val epg: List<EpgEntry> = emptyList(),
    val message: String? = null,
    val typedNumber: String? = null,       // digits being typed to jump to a channel
    val controlsVisible: Boolean = false,  // VOD controls, or the live option bar
    val loading: Boolean = false,          // until the first frame: "Cargando <title>…"
    val failed: Boolean = false,           // playback gave up: retry / back panel
    val notice: String? = null,            // short message top-right (resume point, next episode)
    val vod: VodState = VodState(),
    val menu: MenuState? = null,           // Audio / Subtitles / Speed / Screen panel
    val channelList: Boolean = false,      // live: channel list over the picture
    val favorites: Set<Int> = emptySet(),
    val listNow: Map<Int, String> = emptyMap(),   // channel list: queue index -> program on now
)

@Composable
fun PlayerOverlay(
    state: OverlayState,
    queue: List<PlayItem> = emptyList(),
    onTap: () -> Unit = {},
    onSwipe: (step: Int) -> Unit = {},
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onMenuChoice: (Int) -> Unit = {},
    vodActions: VodActions? = null,
    liveActions: LiveActions? = null,
) {
    // Touch screens. Live: tap shows / hides the option bar, swipe up/down changes channel.
    // VOD: tap shows / hides the controls. With a menu or the channel list open, tap closes it.
    val live = state.item?.isLive == true
    val touch = if (live) {
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
    } else Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
    Box(Modifier.fillMaxSize().then(touch)) {
        val item = state.item
        AnimatedVisibility(
            visible = state.controlsVisible && item != null && !item.isLive && !state.failed && vodActions != null,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(250)),
        ) {
            if (item != null && vodActions != null) VodControls(item, state.vod, state.menu, vodActions)
        }
        AnimatedVisibility(
            visible = live && !state.failed && !state.channelList && (state.controlsVisible || state.showBanner),
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            if (item != null) {
                LiveBanner(item, state.epg, favorite = item.epgStreamId in state.favorites, menu = state.menu,
                    actions = if (state.controlsVisible) liveActions else null)
            }
        }
        AnimatedVisibility(
            visible = live && state.channelList && liveActions != null,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { -it / 3 },
            exit = fadeOut(tween(150)) + slideOutHorizontally(tween(180)) { -it / 3 },
        ) {
            if (liveActions != null) ChannelListPanel(queue, state.index, state.favorites, state.listNow, liveActions)
        }
        // Keep the last menu while its panel slides out.
        var lastMenu by remember { mutableStateOf<MenuState?>(null) }
        state.menu?.let { lastMenu = it }
        AnimatedVisibility(
            visible = state.menu != null,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = fadeIn(tween(180)) + slideInHorizontally(tween(220)) { it / 3 },
            exit = fadeOut(tween(150)) + slideOutHorizontally(tween(180)) { it / 3 },
        ) {
            lastMenu?.let { MenuPanel(it, onMenuChoice) }
        }
        // Back button for mouse and touch (the remote uses its Back key). Shown with the
        // channel banner or the controls; not focusable, so the D-pad never lands on it.
        AnimatedVisibility(
            visible = (state.showBanner || state.controlsVisible || state.failed) && !state.channelList && state.menu == null,
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
        // With the channel list open the error moves right, so the list stays usable next to it.
        AnimatedVisibility(
            visible = state.failed,
            modifier = if (state.channelList) Modifier.align(Alignment.CenterEnd).padding(end = 40.dp) else Modifier.align(Alignment.Center),
            enter = fadeIn(tween(250)) + scaleIn(tween(300), initialScale = 0.92f),
            exit = fadeOut(tween(200)),
        ) {
            ErrorPanel(live, onRetry = onRetry, onBack = onBack, modifier = Modifier.widthIn(max = 460.dp))
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

/** Shown when playback gave up: what happened and what the user can do, by tap or remote. */
@Composable
private fun ErrorPanel(live: Boolean, onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
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
        Text(stringResource(if (live) R.string.player_error_keys_live else R.string.player_error_keys), style = MaterialTheme.typography.bodySmall,
            color = Brand.TextSecondary, textAlign = TextAlign.Center)
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

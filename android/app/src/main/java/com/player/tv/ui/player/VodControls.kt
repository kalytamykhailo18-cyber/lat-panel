package com.player.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.player.tv.R
import com.player.tv.ui.components.AppIcons
import com.player.tv.ui.components.requestWhenReady
import com.player.tv.ui.components.screenInfo
import com.player.tv.ui.components.tapToClick
import com.player.tv.ui.theme.Brand
import com.player.tv.ui.vod.formatDuration

/** What the movie / episode controls need from the player activity. */
interface VodActions : MenuActions {
    fun playPause()
    fun seekBy(deltaMs: Long)
    fun seekTo(positionMs: Long)
    fun next()
}

data class VodState(
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val playing: Boolean = false,
    val hasNext: Boolean = false,
    val focusSeekBar: Boolean = false,    // opened with Left/Right: keep seeking from the bar
    val speedLabel: String = "1x",
)

/**
 * Movie / episode controls: title on top; progress bar, transport buttons and the Audio,
 * Subtitles, Speed and Screen menus at the bottom. Subtitles is always there, also when the
 * content has none (the menu then says so), so the option is never "missing".
 * Narrow screens (phones) put the menus on a second row so nothing is cut off.
 */
@Composable
fun VodControls(item: PlayItem, state: VodState, menu: MenuState?, actions: VodActions) {
    val playFocus = remember { FocusRequester() }
    val seekFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { (if (state.focusSeekBar) seekFocus else playFocus).requestWhenReady() }
    // Closing a menu brings focus back to the button that opened it (the panel that had it is gone).
    val menuFocus = remember { PlayerMenu.entries.associateWith { FocusRequester() } }
    var lastMenu by remember { mutableStateOf<PlayerMenu?>(null) }
    LaunchedEffect(menu?.menu) {
        val open = menu?.menu
        if (open != null) lastMenu = open
        else lastMenu?.let {
            lastMenu = null
            menuFocus.getValue(it).requestWhenReady()
        }
    }
    val narrow = screenInfo().widthDp < 900

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.align(Alignment.TopStart).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                .padding(start = 110.dp, end = 48.dp, top = if (narrow) 26.dp else 34.dp, bottom = 60.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.subtitle?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Brand.TextSecondary, maxLines = 1) }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(start = 48.dp, end = 48.dp, top = if (narrow) 48.dp else 80.dp, bottom = if (narrow) 16.dp else 28.dp),
            verticalArrangement = Arrangement.spacedBy(if (narrow) 8.dp else 10.dp),
        ) {
            SeekBar(state, actions, Modifier.focusRequester(seekFocus))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(state.positionMs), style = MaterialTheme.typography.bodyMedium, color = Color.White)
                Spacer(Modifier.weight(1f))
                if (state.durationMs > 0) {
                    Text("-" + formatDuration((state.durationMs - state.positionMs).coerceAtLeast(0)), style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary)
                }
            }
            val transport = @Composable {
                RoundButton(AppIcons.FastRewind, onClick = { actions.seekBy(-10_000) })
                RoundButton(if (state.playing) AppIcons.Pause else AppIcons.Play, big = true, modifier = Modifier.focusRequester(playFocus), onClick = actions::playPause)
                RoundButton(AppIcons.FastForward, onClick = { actions.seekBy(30_000) })
            }
            val options = @Composable {
                OptionButton(AppIcons.Audio, stringResource(R.string.player_audio), Modifier.focusRequester(menuFocus.getValue(PlayerMenu.AUDIO))) { actions.openMenu(PlayerMenu.AUDIO) }
                OptionButton(AppIcons.Subtitles, stringResource(R.string.player_subtitles), Modifier.focusRequester(menuFocus.getValue(PlayerMenu.SUBTITLES))) { actions.openMenu(PlayerMenu.SUBTITLES) }
                OptionButton(AppIcons.Speed, state.speedLabel, Modifier.focusRequester(menuFocus.getValue(PlayerMenu.SPEED))) { actions.openMenu(PlayerMenu.SPEED) }
                OptionButton(AppIcons.AspectRatio, stringResource(R.string.player_screen), Modifier.focusRequester(menuFocus.getValue(PlayerMenu.SCREEN))) { actions.openMenu(PlayerMenu.SCREEN) }
                if (state.hasNext) OptionButton(AppIcons.SkipNext, stringResource(R.string.player_next), onClick = actions::next)
            }
            if (narrow) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally)) { transport() }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) { options() }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    transport()
                    Spacer(Modifier.weight(1f))
                    options()
                }
            }
        }
    }
}

@Composable
private fun SeekBar(state: VodState, actions: VodActions, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val duration = state.durationMs.coerceAtLeast(1)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { actions.seekBy(-10_000); true }
                    Key.DirectionRight -> { actions.seekBy(10_000); true }
                    Key.DirectionCenter, Key.Enter -> { actions.playPause(); true }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction)
            .pointerInput(duration) {
                detectTapGestures { offset -> actions.seekTo((offset.x / size.width * duration).toLong()) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val barHeight = if (focused) 8.dp else 5.dp
        val played = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        val buffered = (state.bufferedMs.toFloat() / duration).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(4.dp)).background(Color(0x40FFFFFF))) {
            Box(Modifier.fillMaxWidth(buffered).fillMaxHeight().background(Color(0x55FFFFFF)))
            Box(Modifier.fillMaxWidth(played).fillMaxHeight().background(Brand.Primary))
        }
        if (focused) {
            Box(Modifier.offset(x = maxWidth * played - 9.dp).size(18.dp).clip(CircleShape).background(Color.White))
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, modifier: Modifier = Modifier, big: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(if (big) 64.dp else 52.dp).tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x33FFFFFF),
            contentColor = Color.White,
            focusedContainerColor = Brand.Primary,
            focusedContentColor = Color.White,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(if (big) 34.dp else 26.dp))
        }
    }
}

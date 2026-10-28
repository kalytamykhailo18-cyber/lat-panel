package com.player.tv.ui.player

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.player.tv.R
import com.player.tv.data.EpgEntry
import com.player.tv.ui.components.AppIcons
import com.player.tv.ui.components.requestWhenReady
import com.player.tv.ui.components.screenInfo
import com.player.tv.ui.components.tapToClick
import com.player.tv.ui.theme.Brand
import java.util.Date

/** What the live TV controls need from the player activity. */
interface LiveActions : MenuActions {
    fun openChannels()
    fun toggleFavorite()
    /** From the channel list: play that channel (the list stays open to keep browsing). */
    fun playChannel(index: Int)
    /** Focus moved in the channel list: load what that channel is showing now. */
    fun focusChannel(index: Int)
}

/**
 * Bottom banner of a live channel: logo, number, name, what is on now and next. With
 * [actions] it carries the option buttons (Canales, Audio, Subtítulos, Pantalla, Favorito)
 * and takes the remote's focus; without them it is the short banner shown while zapping.
 */
@Composable
fun LiveBanner(item: PlayItem, epg: List<EpgEntry>, favorite: Boolean, menu: MenuState?, actions: LiveActions?) {
    val context = LocalContext.current
    val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
    fun range(e: EpgEntry) = "${timeFormat.format(Date(e.start * 1000))} – ${timeFormat.format(Date(e.end * 1000))}"
    val narrow = screenInfo().widthDp < 900
    val touch = LocalConfiguration.current.touchscreen == Configuration.TOUCHSCREEN_FINGER

    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000), Color(0xF2000000))))
            .padding(horizontal = if (narrow) 28.dp else 56.dp, vertical = if (narrow) 16.dp else 32.dp)
            .padding(top = if (narrow) 24.dp else 40.dp),
        verticalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (narrow) 16.dp else 24.dp)) {
            Box(
                Modifier.size(width = if (narrow) 88.dp else 120.dp, height = if (narrow) 58.dp else 80.dp)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0x33FFFFFF)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.logo.isNullOrBlank()) Icon(AppIcons.LiveTv, null, tint = Brand.TextSecondary, modifier = Modifier.size(32.dp))
                else AsyncImage(item.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (item.number > 0) {
                        Text(item.number.toString(), style = MaterialTheme.typography.headlineSmall, color = Brand.Primary, fontWeight = FontWeight.Bold)
                    }
                    Text(item.title, modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.headlineSmall, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (favorite) Icon(AppIcons.Star, contentDescription = null, tint = Brand.Primary, modifier = Modifier.size(22.dp))
                }
                val now = epg.getOrNull(0)
                val next = epg.getOrNull(1)
                if (now == null) {
                    Text(stringResource(R.string.epg_none), color = Brand.TextSecondary, style = MaterialTheme.typography.bodyLarge)
                } else {
                    Text("${stringResource(R.string.epg_now)}: ${now.title}  ·  ${range(now)}", color = Color.White, style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val total = (now.end - now.start).coerceAtLeast(1)
                    val elapsed = (System.currentTimeMillis() / 1000 - now.start).coerceIn(0, total)
                    Box(Modifier.fillMaxWidth(0.6f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x44FFFFFF))) {
                        Box(Modifier.fillMaxWidth(elapsed.toFloat() / total).height(4.dp).background(Brand.Primary))
                    }
                    if (next != null) {
                        Text("${stringResource(R.string.epg_next)}: ${next.title}  ·  ${range(next)}", color = Brand.TextSecondary,
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (actions != null) {
            LiveActionRow(favorite, menu, actions, touch)
        } else {
            Text(stringResource(if (touch) R.string.live_player_hint_touch else R.string.live_player_hint), style = MaterialTheme.typography.bodySmall,
                color = Brand.TextSecondary)
        }
    }
}

@Composable
private fun LiveActionRow(favorite: Boolean, menu: MenuState?, actions: LiveActions, touch: Boolean) {
    val channelsFocus = remember { FocusRequester() }
    val menuFocus = remember { PlayerMenu.entries.associateWith { FocusRequester() } }
    LaunchedEffect(Unit) { channelsFocus.requestWhenReady() }
    // Closing a menu brings focus back to the button that opened it.
    var lastMenu by remember { mutableStateOf<PlayerMenu?>(null) }
    LaunchedEffect(menu?.menu) {
        val open = menu?.menu
        if (open != null) lastMenu = open
        else lastMenu?.let {
            lastMenu = null
            menuFocus.getValue(it).requestWhenReady()
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OptionButton(AppIcons.ChannelList, stringResource(R.string.player_channels), Modifier.focusRequester(channelsFocus), onClick = actions::openChannels)
        OptionButton(AppIcons.Audio, stringResource(R.string.player_audio), Modifier.focusRequester(menuFocus.getValue(PlayerMenu.AUDIO))) { actions.openMenu(PlayerMenu.AUDIO) }
        OptionButton(AppIcons.Subtitles, stringResource(R.string.player_subtitles), Modifier.focusRequester(menuFocus.getValue(PlayerMenu.SUBTITLES))) { actions.openMenu(PlayerMenu.SUBTITLES) }
        OptionButton(AppIcons.AspectRatio, stringResource(R.string.player_screen), Modifier.focusRequester(menuFocus.getValue(PlayerMenu.SCREEN))) { actions.openMenu(PlayerMenu.SCREEN) }
        OptionButton(if (favorite) AppIcons.Star else AppIcons.StarOutline,
            stringResource(if (favorite) R.string.player_favorite_on else R.string.player_favorite), onClick = actions::toggleFavorite)
        if (!touch && screenInfo().widthDp >= 900) {
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.live_player_zap_hint), style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * Channel list over the left of the picture: browse the category while the channel keeps
 * playing. OK plays the focused channel and the list stays open; OK on the channel already
 * playing, or Back, closes it.
 */
@Composable
fun ChannelListPanel(
    items: List<PlayItem>,
    playingIndex: Int,
    favorites: Set<Int>,
    nowShowing: Map<Int, String>,
    actions: LiveActions,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (playingIndex - 3).coerceAtLeast(0))
    val playingFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { playingFocus.requestWhenReady() }
    val narrow = screenInfo().widthDp < 900
    var focusedIndex by remember { mutableStateOf(playingIndex) }

    Column(
        Modifier.width(if (narrow) 320.dp else 420.dp).fillMaxHeight()
            .background(Brush.horizontalGradient(listOf(Color(0xF7101520), Color(0xEB101520))))
            .padding(top = if (narrow) 16.dp else 28.dp),
    ) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.player_channels), style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(pluralStringResource(R.plurals.channel_count, items.size, items.size), style = MaterialTheme.typography.bodyMedium,
                color = Brand.TextSecondary, modifier = Modifier.padding(bottom = 3.dp))
            Spacer(Modifier.weight(1f))
            Text("${focusedIndex + 1}/${items.size}", style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(if (narrow) 8.dp else 14.dp))
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(items, key = { index, it -> "$index|${it.url}" }) { index, item ->
                ChannelListRow(
                    item = item,
                    playing = index == playingIndex,
                    favorite = item.epgStreamId in favorites,
                    now = nowShowing[index],
                    modifier = (if (index == playingIndex) Modifier.focusRequester(playingFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) { focusedIndex = index; actions.focusChannel(index) } },
                    onClick = { actions.playChannel(index) },
                )
            }
        }
    }
}

@Composable
private fun ChannelListRow(item: PlayItem, playing: Boolean, favorite: Boolean, now: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }.tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (playing) Brand.Primary.copy(alpha = 0.18f) else Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Brand.Primary,
            focusedContentColor = Color.White,
        ),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (item.number > 0) item.number.toString() else "", modifier = Modifier.width(40.dp), style = MaterialTheme.typography.titleSmall,
                color = if (focused) Color.White else Brand.TextSecondary, maxLines = 1)
            Box(Modifier.size(width = 52.dp, height = 34.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x22FFFFFF)), contentAlignment = Alignment.Center) {
                if (!item.logo.isNullOrBlank()) AsyncImage(item.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (focused && now != null) {
                    Text("${stringResource(R.string.epg_now)}: $now", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (favorite) Icon(AppIcons.Star, contentDescription = null, tint = if (focused) Color.White else Brand.Primary, modifier = Modifier.size(16.dp))
            if (playing) Icon(AppIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

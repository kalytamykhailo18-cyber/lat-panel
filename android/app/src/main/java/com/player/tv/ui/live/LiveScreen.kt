package com.player.tv.ui.live

import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.player.tv.R
import com.player.tv.data.EpgEntry
import com.player.tv.data.Graph
import com.player.tv.data.LiveChannel
import com.player.tv.data.Parental
import com.player.tv.ui.components.AnimatedLoadable
import com.player.tv.ui.components.AppIcons
import com.player.tv.ui.components.CategoryColumn
import com.player.tv.ui.components.ChannelRow
import com.player.tv.ui.components.EmptyView
import com.player.tv.ui.components.ErrorView
import com.player.tv.ui.components.Loadable
import com.player.tv.ui.components.LoadingView
import com.player.tv.ui.components.LockedView
import com.player.tv.ui.components.PinDialog
import com.player.tv.ui.components.Spinner
import com.player.tv.ui.components.loadable
import com.player.tv.ui.components.map
import com.player.tv.ui.components.screenInfo
import com.player.tv.ui.player.PlayItem
import com.player.tv.ui.player.PlayQueue
import com.player.tv.ui.player.Players
import com.player.tv.ui.theme.Brand
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Date

const val ALL = "__all"
const val FAVORITES = "__fav"

class LiveViewModel : ViewModel() {
    private val xtream = Graph.requireXtream()

    val categories = MutableStateFlow<Loadable<List<Pair<String, String>>>>(Loadable.Loading)
    val selected = MutableStateFlow<String?>(null)
    val channels = MutableStateFlow<Loadable<List<LiveChannel>>>(Loadable.Loading)
    val locked = MutableStateFlow(false)
    private val _favorites = MutableStateFlow(Graph.store.favorites(xtream.account))
    val favorites: StateFlow<Set<Int>> = _favorites
    val focused = MutableStateFlow<LiveChannel?>(null)
    val epg = MutableStateFlow<List<EpgEntry>?>(null)

    /** Channel playing in the preview panel; OK on it again opens it full screen. */
    val preview = MutableStateFlow<LiveChannel?>(null)
    var previewPlayer: ExoPlayer? = null
        private set

    private var channelsJob: Job? = null
    private var epgJob: Job? = null

    init { loadCategories() }

    fun loadCategories() {
        viewModelScope.launch {
            categories.value = Loadable.Loading
            val result = loadable { xtream.liveCategories() }
            categories.value = result.map { list -> list.map { it.id to it.name } }
            if (result is Loadable.Ready && selected.value == null) {
                val first = result.data.firstOrNull { !Parental.isLocked(Parental.Type.LIVE, it.id) }?.id ?: ALL
                select(if (_favorites.value.isNotEmpty()) FAVORITES else first)
            }
        }
    }

    fun select(categoryId: String, force: Boolean = false) {
        if (categoryId == selected.value && !force) return
        selected.value = categoryId
        channelsJob?.cancel()
        if (Parental.isLocked(Parental.Type.LIVE, categoryId)) {
            locked.value = true
            return
        }
        locked.value = false
        channelsJob = viewModelScope.launch {
            channels.value = Loadable.Loading
            delay(200)   // focus may be passing over several categories
            channels.value = loadable {
                val visible = { list: List<LiveChannel> -> list.filterNot { Parental.isLocked(Parental.Type.LIVE, it.categoryId) } }
                when (categoryId) {
                    ALL -> visible(xtream.liveChannels(null))
                    FAVORITES -> visible(xtream.liveChannels(null).filter { it.streamId in _favorites.value })
                    else -> xtream.liveChannels(categoryId)
                }
            }
        }
    }

    fun unlock(pin: String): Boolean {
        val id = selected.value ?: return false
        if (!Parental.checkPin(pin)) return false
        Parental.unlock(Parental.Type.LIVE, id)
        select(id, force = true)
        return true
    }

    fun focus(channel: LiveChannel) {
        if (focused.value == channel) return
        focused.value = channel
        epg.value = null
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            delay(350)
            epg.value = runCatching { xtream.epg(channel.streamId, 4) }.getOrDefault(emptyList())
        }
    }

    /** Returns true when the channel was added. */
    fun toggleFavorite(channel: LiveChannel): Boolean {
        val added = Graph.store.toggleFavorite(xtream.account, channel.streamId)
        _favorites.value = Graph.store.favorites(xtream.account)
        if (selected.value == FAVORITES) select(FAVORITES, force = true)
        return added
    }

    /** Favorites can change in the player (Favorito button): pick them up when coming back. */
    fun refreshFavorites() {
        val now = Graph.store.favorites(xtream.account)
        if (now == _favorites.value) return
        _favorites.value = now
        if (selected.value == FAVORITES) select(FAVORITES, force = true)
    }

    fun startPreview(context: Context, channel: LiveChannel) {
        preview.value = channel
        val player = previewPlayer ?: Players.create(context.applicationContext).also { previewPlayer = it }
        val url = xtream.liveUrl(channel, Graph.store.liveHls)
        player.setMediaItem(MediaItem.Builder().setUri(url).apply { if (url.endsWith(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }.build())
        player.prepare()
        player.playWhenReady = true
    }

    /** Screen hidden: free the provider connection. */
    fun pausePreview() {
        previewPlayer?.stop()
    }

    fun resumePreview() {
        val player = previewPlayer ?: return
        if (preview.value != null) {
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun openFullScreen(context: Context, list: List<LiveChannel>, index: Int) {
        previewPlayer?.stop()   // providers often allow a single connection
        val hls = Graph.store.liveHls
        PlayQueue.start(context, list.map {
            PlayItem(it.name, xtream.liveUrl(it, hls), isLive = true, logo = it.logo, number = it.number, epgStreamId = it.streamId)
        }, index)
    }

    override fun onCleared() {
        previewPlayer?.release()
        previewPlayer = null
    }
}

@Composable
fun LiveScreen(vm: LiveViewModel = viewModel()) {
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val focused by vm.focused.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    var askPin by remember { mutableStateOf(false) }
    val screen = screenInfo()

    LifecycleStartEffect(Unit) {
        vm.refreshFavorites()
        vm.resumePreview()
        onStopOrDispose { vm.pausePreview() }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedLoadable(
            categories,
            loading = { LoadingView(message = stringResource(R.string.loading_categories)) },
            failed = { kind -> ErrorView(kind, onRetry = vm::loadCategories) },
        ) { categoryList ->
            val items = listOf(
                FAVORITES to "★ " + stringResource(R.string.category_favorites),
                ALL to stringResource(R.string.category_all),
            ) + categoryList
            val channelArea: @Composable () -> Unit = {
                if (locked) {
                    LockedView(onUnlock = { askPin = true })
                } else AnimatedLoadable(
                    channels,
                    loading = { LoadingView(message = stringResource(R.string.loading_channels)) },
                    failed = { kind -> ErrorView(kind, onRetry = { selected?.let { vm.select(it, force = true) } }) },
                ) { channelList ->
                    if (channelList.isEmpty()) {
                        EmptyView(stringResource(if (selected == FAVORITES) R.string.live_no_favorites else R.string.empty_list))
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().focusRestorer(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
                        ) {
                            itemsIndexed(channelList, key = { _, it -> it.streamId }) { index, channel ->
                                ChannelRow(
                                    number = channel.number,
                                    name = channel.name,
                                    logo = channel.logo,
                                    favorite = channel.streamId in favorites,
                                    playing = preview?.streamId == channel.streamId,
                                    onClick = {
                                        if (preview?.streamId == channel.streamId || screen.compact) vm.openFullScreen(context, channelList, index)
                                        else vm.startPreview(context, channel)
                                    },
                                    onLongClick = {
                                        val added = vm.toggleFavorite(channel)
                                        Toast.makeText(context, if (added) R.string.favorite_added else R.string.favorite_removed, Toast.LENGTH_SHORT).show()
                                    },
                                    onFocused = { vm.focus(channel) },
                                )
                            }
                        }
                    }
                }
            }

            val count = (channels as? Loadable.Ready)?.data?.size?.takeIf { !locked }
            val label = items.firstOrNull { it.first == selected }?.second.orEmpty()
            Row(Modifier.fillMaxSize().padding(start = 20.dp, top = 24.dp, end = 24.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.width(if (screen.expanded) 210.dp else 180.dp).fillMaxHeight()) {
                    Text(stringResource(R.string.home_live), style = MaterialTheme.typography.headlineSmall, color = Brand.TextPrimary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                    CategoryColumn(items, selected, vm::select, isLocked = { Parental.isLocked(Parental.Type.LIVE, it) })
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Text(if (count != null) "$label  ·  $count" else label, style = MaterialTheme.typography.titleMedium, color = Brand.TextSecondary,
                        maxLines = 1, modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 4.dp))
                    Box(Modifier.weight(1f).fillMaxWidth()) { channelArea() }
                }
                if (!screen.compact) {
                    PreviewPanel(vm, preview, focused, epg, Modifier.weight(if (screen.expanded) 1.1f else 0.9f).fillMaxHeight())
                }
            }
        }
    }
    if (askPin) PinDialog(stringResource(R.string.parental_enter_pin), onDismiss = { askPin = false }, onSubmit = { pin ->
        vm.unlock(pin).also { if (it) askPin = false }
    })
}

/** Right column: the channel playing in small, and the guide of the focused channel. */
@OptIn(UnstableApi::class)
@Composable
private fun PreviewPanel(vm: LiveViewModel, preview: LiveChannel?, focused: LiveChannel?, epg: List<EpgEntry>?, modifier: Modifier) {
    val context = LocalContext.current
    val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black)) {
            val player = vm.previewPlayer
            if (preview != null && player != null) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                            isFocusable = false
                            this.player = player
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    stringResource(R.string.live_fullscreen_hint),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).background(Color(0xB3000000), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium, color = Color.White,
                )
            } else {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(AppIcons.Play, contentDescription = null, tint = Brand.TextSecondary, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.live_preview_hint), style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary, textAlign = TextAlign.Center)
                }
            }
        }
        val channel = focused ?: preview
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Brand.Surface).padding(18.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (channel == null) {
                Text(stringResource(R.string.live_favorite_hint), color = Brand.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(width = 64.dp, height = 40.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x14FFFFFF))) {
                    AsyncImage(channel.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(3.dp))
                }
                Text(
                    (if (channel.number > 0) "${channel.number}  " else "") + channel.name,
                    style = MaterialTheme.typography.titleLarge, color = Brand.TextPrimary, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                epg == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Spinner(size = 18.dp, strokeWidth = 2.dp)
                    Text(stringResource(R.string.loading_guide), color = Brand.TextSecondary)
                }
                epg.isEmpty() -> Text(stringResource(R.string.epg_none), color = Brand.TextSecondary)
                else -> epg.forEachIndexed { i, e ->
                    val range = "${timeFormat.format(Date(e.start * 1000))} – ${timeFormat.format(Date(e.end * 1000))}"
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(range, style = MaterialTheme.typography.labelMedium, color = if (i == 0) Brand.Primary else Brand.TextSecondary)
                            if (i == 0) {
                                Text(stringResource(R.string.epg_now).uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White,
                                    modifier = Modifier.background(Brand.Primary, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp))
                            }
                        }
                        Text(e.title, style = if (i == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                            color = if (i == 0) Brand.TextPrimary else Brand.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (i == 0) {
                            val total = (e.end - e.start).coerceAtLeast(1)
                            val elapsed = (System.currentTimeMillis() / 1000 - e.start).coerceIn(0, total)
                            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x22FFFFFF))) {
                                Box(Modifier.fillMaxWidth(elapsed.toFloat() / total).fillMaxHeight().background(Brand.Primary))
                            }
                            e.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.live_keys_hint), style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary, maxLines = 2)
    }
}

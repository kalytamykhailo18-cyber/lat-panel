package com.player.tv.ui.live

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.player.tv.R
import com.player.tv.data.EpgEntry
import com.player.tv.data.Graph
import com.player.tv.data.LiveChannel
import com.player.tv.ui.components.AnimatedLoadable
import com.player.tv.ui.components.AppBackground
import com.player.tv.ui.components.CategoryColumn
import com.player.tv.ui.components.CategoryRow
import com.player.tv.ui.components.screenInfo
import com.player.tv.ui.components.ChannelRow
import com.player.tv.ui.components.EmptyView
import com.player.tv.ui.components.ErrorView
import com.player.tv.ui.components.Loadable
import com.player.tv.ui.components.LoadingView
import com.player.tv.ui.components.loadable
import com.player.tv.ui.components.BackButton
import com.player.tv.ui.components.ScreenHeader
import com.player.tv.ui.components.Spinner
import com.player.tv.ui.components.map
import com.player.tv.ui.player.PlayItem
import com.player.tv.ui.player.PlayQueue
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
    private val _favorites = MutableStateFlow(Graph.store.favorites(xtream.account))
    val favorites: StateFlow<Set<Int>> = _favorites
    val focused = MutableStateFlow<LiveChannel?>(null)
    val epg = MutableStateFlow<List<EpgEntry>?>(null)

    private var channelsJob: Job? = null
    private var epgJob: Job? = null

    init { loadCategories() }

    fun loadCategories() {
        viewModelScope.launch {
            categories.value = Loadable.Loading
            val result = loadable { xtream.liveCategories() }
            categories.value = result.map { list -> list.map { it.id to it.name } }
            if (result is Loadable.Ready && selected.value == null) {
                select(if (_favorites.value.isNotEmpty()) FAVORITES else result.data.firstOrNull()?.id ?: ALL)
            }
        }
    }

    fun select(categoryId: String, force: Boolean = false) {
        if (categoryId == selected.value && !force) return
        selected.value = categoryId
        channelsJob?.cancel()
        channelsJob = viewModelScope.launch {
            channels.value = Loadable.Loading
            delay(200)   // focus may be passing over several categories
            channels.value = loadable {
                when (categoryId) {
                    ALL -> xtream.liveChannels(null)
                    FAVORITES -> xtream.liveChannels(null).filter { it.streamId in _favorites.value }
                    else -> xtream.liveChannels(categoryId)
                }
            }
        }
    }

    fun focus(channel: LiveChannel) {
        if (focused.value == channel) return
        focused.value = channel
        epg.value = null
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            delay(400)
            epg.value = runCatching { xtream.shortEpg(channel.streamId) }.getOrDefault(emptyList())
        }
    }

    /** Returns true when the channel was added. */
    fun toggleFavorite(channel: LiveChannel): Boolean {
        val added = Graph.store.toggleFavorite(xtream.account, channel.streamId)
        _favorites.value = Graph.store.favorites(xtream.account)
        if (selected.value == FAVORITES) select(FAVORITES, force = true)
        return added
    }

    fun play(context: android.content.Context, list: List<LiveChannel>, index: Int) {
        val hls = Graph.store.liveHls
        PlayQueue.start(context, list.map {
            PlayItem(it.name, xtream.liveUrl(it, hls), isLive = true, logo = it.logo, number = it.number, epgStreamId = it.streamId)
        }, index)
    }
}

@Composable
fun LiveScreen(vm: LiveViewModel = viewModel()) {
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val focused by vm.focused.collectAsStateWithLifecycle()
    val epg by vm.epg.collectAsStateWithLifecycle()
    val screen = screenInfo()

    AppBackground {
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
                AnimatedLoadable(
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
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            itemsIndexed(channelList, key = { _, it -> it.streamId }) { index, channel ->
                                ChannelRow(
                                    number = channel.number,
                                    name = channel.name,
                                    logo = channel.logo,
                                    favorite = channel.streamId in favorites,
                                    onClick = { vm.play(context, channelList, index) },
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

            if (screen.compact) {
                // Phones: categories as a scrolling row of pills above the channels.
                Column(Modifier.fillMaxSize().padding(horizontal = screen.gutter, vertical = 16.dp)) {
                    ScreenHeader(stringResource(R.string.home_live))
                    CategoryRow(items, selected, vm::select)
                    Box(Modifier.weight(1f).fillMaxWidth()) { channelArea() }
                }
            } else {
                Row(Modifier.fillMaxSize().padding(screen.gutter.coerceAtMost(24.dp)), horizontalArrangement = Arrangement.spacedBy(if (screen.expanded) 20.dp else 16.dp)) {
                    Column(Modifier.width(if (screen.expanded) 230.dp else 190.dp).fillMaxHeight()) {
                        ScreenHeader(stringResource(R.string.home_live), Modifier.padding(start = 4.dp, bottom = 8.dp))
                        CategoryColumn(items, selected, vm::select)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) { channelArea() }
                    // The guide panel needs room; smaller screens show it in the player instead.
                    if (screen.expanded) ChannelInfo(focused, epg, Modifier.width(300.dp).fillMaxHeight())
                }
            }
        }
        if (categories !is Loadable.Ready) BackButton(Modifier.align(Alignment.TopStart).padding(24.dp))
    }
}

@Composable
private fun ChannelInfo(channel: LiveChannel?, epg: List<EpgEntry>?, modifier: Modifier) {
    val context = LocalContext.current
    val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Brand.Surface).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (channel == null) {
            Text(stringResource(R.string.live_favorite_hint), color = Brand.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }
        Box(Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF))) {
            AsyncImage(channel.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(20.dp))
        }
        Text(channel.name, style = MaterialTheme.typography.titleLarge, color = Brand.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 2)
        when {
            epg == null -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Spinner(size = 18.dp, strokeWidth = 2.dp)
                Text(stringResource(R.string.loading_guide), color = Brand.TextSecondary)
            }
            epg.isEmpty() -> Text(stringResource(R.string.epg_none), color = Brand.TextSecondary)
            else -> epg.forEachIndexed { i, e ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(if (i == 0) R.string.epg_now else R.string.epg_next).uppercase() +
                            "  ·  ${timeFormat.format(Date(e.start * 1000))} – ${timeFormat.format(Date(e.end * 1000))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (i == 0) Brand.Primary else Brand.TextSecondary,
                    )
                    Text(e.title, style = MaterialTheme.typography.titleMedium, color = Brand.TextPrimary, maxLines = 2)
                    if (i == 0 && e.description != null) {
                        Text(e.description, style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary, maxLines = 4)
                    }
                }
            }
        }
        Box(Modifier.weight(1f))
        Text(stringResource(R.string.live_favorite_hint), color = Brand.TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

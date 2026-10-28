package com.player.tv.ui.guide

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.player.tv.R
import com.player.tv.data.EpgEntry
import com.player.tv.data.Graph
import com.player.tv.data.LiveChannel
import com.player.tv.data.Parental
import com.player.tv.ui.components.AnimatedLoadable
import com.player.tv.ui.components.AppIcons
import com.player.tv.ui.components.CategoryRow
import com.player.tv.ui.components.EmptyView
import com.player.tv.ui.components.ErrorView
import com.player.tv.ui.components.Loadable
import com.player.tv.ui.components.LoadingView
import com.player.tv.ui.components.LockedView
import com.player.tv.ui.components.PinDialog
import com.player.tv.ui.components.loadable
import com.player.tv.ui.components.map
import com.player.tv.ui.components.screenInfo
import com.player.tv.ui.components.tapToClick
import com.player.tv.ui.player.PlayItem
import com.player.tv.ui.player.PlayQueue
import com.player.tv.ui.theme.Brand
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.Date

private const val ALL = "__all"
private const val SLOT = 30 * 60L           // grid lines every 30 minutes
private const val SHIFT = 60 * 60L          // Left / Right at the edge move the grid one hour

class GuideViewModel : ViewModel() {
    private val xtream = Graph.requireXtream()

    val categories = MutableStateFlow<Loadable<List<Pair<String, String>>>>(Loadable.Loading)
    val selected = MutableStateFlow<String?>(null)
    val channels = MutableStateFlow<Loadable<List<LiveChannel>>>(Loadable.Loading)
    val locked = MutableStateFlow(false)
    val windowStart = MutableStateFlow(nowSlot())

    /** Program lists per channel, loaded as rows scroll into view. */
    val epg = mutableStateMapOf<Int, List<EpgEntry>>()
    private val requested = mutableSetOf<Int>()
    private val epgLimit = Semaphore(4)
    private var channelsJob: Job? = null

    init { loadCategories() }

    fun loadCategories() {
        viewModelScope.launch {
            categories.value = Loadable.Loading
            val result = loadable { xtream.liveCategories() }
            categories.value = result.map { list -> list.map { it.id to it.name } }
            if (result is Loadable.Ready && selected.value == null) {
                select(result.data.firstOrNull { !Parental.isLocked(Parental.Type.LIVE, it.id) }?.id ?: ALL)
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
            delay(200)
            channels.value = loadable {
                if (categoryId == ALL) xtream.liveChannels(null).filterNot { Parental.isLocked(Parental.Type.LIVE, it.categoryId) }
                else xtream.liveChannels(categoryId)
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

    fun requestEpg(streamId: Int) {
        if (!requested.add(streamId)) return
        viewModelScope.launch {
            epgLimit.withPermit {
                epg[streamId] = runCatching { xtream.epg(streamId, 24) }.getOrDefault(emptyList())
            }
        }
    }

    /** Moves the grid in time; never before the current half hour. Returns false at the limit. */
    fun shift(seconds: Long): Boolean {
        val target = (windowStart.value + seconds).coerceAtLeast(nowSlot())
        if (target == windowStart.value) return false
        windowStart.value = target
        return true
    }

    fun play(context: android.content.Context, list: List<LiveChannel>, channel: LiveChannel) {
        val hls = Graph.store.liveHls
        PlayQueue.start(context, list.map {
            PlayItem(it.name, xtream.liveUrl(it, hls), isLive = true, logo = it.logo, number = it.number, epgStreamId = it.streamId)
        }, list.indexOf(channel))
    }

    private fun nowSlot() = System.currentTimeMillis() / 1000 / SLOT * SLOT
}

private data class Cell(val entry: EpgEntry?, val start: Long, val end: Long)

/** Programs clipped to the window; gaps become empty cells so every row spans the full width. */
private fun cells(entries: List<EpgEntry>, start: Long, end: Long): List<Cell> {
    val out = mutableListOf<Cell>()
    var t = start
    entries.sortedBy { it.start }.forEach { e ->
        if (e.end <= t || e.start >= end) return@forEach
        if (e.start > t) out += Cell(null, t, e.start)
        val s = maxOf(e.start, t)
        val f = minOf(e.end, end)
        if (f > s) out += Cell(e, s, f)
        t = f
    }
    if (t < end) out += Cell(null, t, end)
    return out
}

private enum class Edge { FIRST, LAST }

@Composable
fun GuideScreen(vm: GuideViewModel = viewModel()) {
    val context = LocalContext.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val windowStart by vm.windowStart.collectAsStateWithLifecycle()
    var askPin by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<Pair<LiveChannel, EpgEntry?>?>(null) }
    var pendingFocus by remember { mutableStateOf<Pair<Int, Edge>?>(null) }
    val screen = screenInfo()
    val windowSeconds = if (screen.expanded) 2 * 3600L else 90 * 60L
    val windowEnd = windowStart + windowSeconds
    val timeFormat = DateFormat.getTimeFormat(context)
    val startsAt = stringResource(R.string.guide_starts_at)

    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis() / 1000
        }
    }

    Column(Modifier.fillMaxSize().padding(start = 20.dp, top = 24.dp, end = 24.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.headlineSmall, color = Brand.TextPrimary, modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.weight(1f))
            Text(DateFormat.getLongDateFormat(context).format(Date(windowStart * 1000)), style = MaterialTheme.typography.titleMedium, color = Brand.TextSecondary)
        }
        AnimatedLoadable(
            categories,
            modifier = Modifier.fillMaxSize(),
            loading = { LoadingView(message = stringResource(R.string.loading_categories)) },
            failed = { kind -> ErrorView(kind, onRetry = vm::loadCategories) },
        ) { categoryList ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryRow(listOf(ALL to stringResource(R.string.category_all)) + categoryList, selected, vm::select)
                ProgramInfo(info, timeFormat)
                if (locked) {
                    LockedView(onUnlock = { askPin = true })
                    return@Column
                }
                AnimatedLoadable(
                    channels,
                    loading = { LoadingView(message = stringResource(R.string.loading_channels)) },
                    failed = { kind -> ErrorView(kind, onRetry = { selected?.let { vm.select(it, force = true) } }) },
                ) { channelList ->
                    if (channelList.isEmpty()) {
                        EmptyView(stringResource(R.string.empty_list))
                        return@AnimatedLoadable
                    }
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val channelWidth = if (screen.expanded) 230.dp else 190.dp
                        val gap = 6.dp
                        val timelineWidth = maxWidth - channelWidth - gap
                        Column(Modifier.fillMaxSize()) {
                            // Time ruler
                            Box(Modifier.fillMaxWidth().height(28.dp)) {
                                var t = windowStart
                                while (t < windowEnd) {
                                    val x = channelWidth + gap + timelineWidth * ((t - windowStart).toFloat() / windowSeconds)
                                    Text(timeFormat.format(Date(t * 1000)), modifier = Modifier.offset(x = x + 6.dp), style = MaterialTheme.typography.labelLarge, color = Brand.TextSecondary)
                                    t += SLOT
                                }
                                if (windowStart > System.currentTimeMillis() / 1000 / SLOT * SLOT) {
                                    Icon(AppIcons.ChevronLeft, null, tint = Brand.TextSecondary, modifier = Modifier.offset(x = channelWidth - 18.dp).size(20.dp))
                                }
                                Icon(AppIcons.ChevronRight, null, tint = Brand.TextSecondary, modifier = Modifier.align(Alignment.CenterEnd).size(20.dp))
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().focusRestorer(),
                                verticalArrangement = Arrangement.spacedBy(gap),
                                contentPadding = PaddingValues(bottom = 8.dp),
                            ) {
                                items(channelList, key = { it.streamId }) { channel ->
                                    LaunchedEffect(channel.streamId) { vm.requestEpg(channel.streamId) }
                                    GuideRow(
                                        channel = channel,
                                        entries = vm.epg[channel.streamId],
                                        windowStart = windowStart,
                                        windowEnd = windowEnd,
                                        now = now,
                                        channelWidth = channelWidth,
                                        gap = gap,
                                        timeFormat = timeFormat,
                                        pendingFocus = pendingFocus?.takeIf { it.first == channel.streamId }?.second,
                                        onFocusHandled = { pendingFocus = null },
                                        onFocus = { info = channel to it },
                                        onPlay = { vm.play(context, channelList, channel) },
                                        onFuture = { e -> Toast.makeText(context, startsAt.format(timeFormat.format(Date(e.start * 1000))), Toast.LENGTH_SHORT).show() },
                                        onShift = { forward ->
                                            vm.shift(if (forward) SHIFT else -SHIFT).also { shifted ->
                                                if (shifted) pendingFocus = channel.streamId to (if (forward) Edge.FIRST else Edge.LAST)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                        // "Now" marker across the grid
                        if (now in windowStart until windowEnd) {
                            val x = channelWidth + gap + timelineWidth * ((now - windowStart).toFloat() / windowSeconds)
                            Box(Modifier.offset(x = x - 1.dp, y = 24.dp).width(2.dp).fillMaxHeight().background(Brand.Error.copy(alpha = 0.85f)))
                        }
                    }
                }
            }
        }
    }
    if (askPin) PinDialog(stringResource(R.string.parental_enter_pin), onDismiss = { askPin = false }, onSubmit = { pin ->
        vm.unlock(pin).also { if (it) askPin = false }
    })
}

@Composable
private fun ProgramInfo(info: Pair<LiveChannel, EpgEntry?>?, timeFormat: java.text.DateFormat) {
    Column(Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(12.dp)).background(Brand.Surface).padding(horizontal = 18.dp, vertical = 10.dp)) {
        if (info == null) {
            Text(stringResource(R.string.guide_hint), style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary)
            return@Column
        }
        val (channel, entry) = info
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(entry?.title ?: stringResource(R.string.epg_none), style = MaterialTheme.typography.titleMedium, color = Brand.TextPrimary,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            if (entry != null) {
                Text("${timeFormat.format(Date(entry.start * 1000))} – ${timeFormat.format(Date(entry.end * 1000))}", style = MaterialTheme.typography.labelLarge, color = Brand.Primary)
            }
            Text(channel.name, style = MaterialTheme.typography.labelLarge, color = Brand.TextSecondary, maxLines = 1)
        }
        entry?.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun GuideRow(
    channel: LiveChannel,
    entries: List<EpgEntry>?,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    channelWidth: androidx.compose.ui.unit.Dp,
    gap: androidx.compose.ui.unit.Dp,
    timeFormat: java.text.DateFormat,
    pendingFocus: Edge?,
    onFocusHandled: () -> Unit,
    onFocus: (EpgEntry?) -> Unit,
    onPlay: () -> Unit,
    onFuture: (EpgEntry) -> Unit,
    onShift: (forward: Boolean) -> Boolean,   // false at the limit
) {
    val first = remember { FocusRequester() }
    val last = remember { FocusRequester() }
    val rowCells = if (entries == null) listOf(Cell(null, windowStart, windowEnd)) else cells(entries, windowStart, windowEnd)
    LaunchedEffect(pendingFocus, windowStart) {
        if (pendingFocus != null) {
            androidx.compose.runtime.withFrameNanos { }
            runCatching { (if (pendingFocus == Edge.FIRST) first else last).requestFocus() }
            onFocusHandled()
        }
    }
    Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
        Surface(
            onClick = onPlay,
            modifier = Modifier.width(channelWidth).fillMaxHeight().onFocusChanged { if (it.isFocused) onFocus(entries?.firstOrNull { e -> now in e.start until e.end }) }.tapToClick(onPlay),
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            colors = ClickableSurfaceDefaults.colors(containerColor = Brand.SurfaceHigh, contentColor = Brand.TextPrimary, focusedContainerColor = Brand.Primary, focusedContentColor = Color.White),
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (channel.number > 0) Text(channel.number.toString(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(30.dp))
                Box(Modifier.size(width = 48.dp, height = 32.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x22FFFFFF))) {
                    AsyncImage(channel.logo, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
                }
                Text(channel.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            rowCells.forEachIndexed { index, cell ->
                val isFirst = index == 0
                val isLast = index == rowCells.lastIndex
                val airing = cell.entry != null && now in cell.entry.start until cell.entry.end
                val click = {
                    when {
                        cell.entry == null || airing -> onPlay()
                        cell.entry.start > now -> onFuture(cell.entry)
                        else -> onPlay()
                    }
                }
                Surface(
                    onClick = click,
                    modifier = Modifier
                        .weight((cell.end - cell.start).toFloat().coerceAtLeast(1f))
                        .fillMaxHeight()
                        .then(if (isFirst) Modifier.focusRequester(first) else Modifier)
                        .then(if (isLast && !isFirst) Modifier.focusRequester(last) else Modifier)
                        .onFocusChanged { if (it.isFocused) onFocus(cell.entry) }
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when {
                                e.key == Key.DirectionRight && isLast -> { onShift(true); true }
                                e.key == Key.DirectionLeft && isFirst -> onShift(false)   // at "now": Left goes on to the channel
                                else -> false
                            }
                        }
                        .tapToClick(click),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (airing) Brand.SurfaceHigh else Brand.Surface,
                        contentColor = if (cell.entry == null) Brand.TextSecondary else Brand.TextPrimary,
                        focusedContainerColor = Brand.Primary,
                        focusedContentColor = Color.White,
                    ),
                ) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
                        Text(
                            when {
                                entries == null -> stringResource(R.string.loading_guide)
                                cell.entry == null && entries.isEmpty() -> stringResource(R.string.epg_none)
                                cell.entry == null -> ""
                                else -> cell.entry.title
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (airing) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        cell.entry?.let {
                            Text("${timeFormat.format(Date(it.start * 1000))} – ${timeFormat.format(Date(it.end * 1000))}",
                                style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Clip)
                        }
                    }
                }
            }
        }
    }
}

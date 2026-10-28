package com.player.tv.ui.home

import androidx.activity.compose.BackHandler
import android.content.Context
import android.view.LayoutInflater
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.player.tv.R
import com.player.tv.Routes
import com.player.tv.data.Graph
import com.player.tv.data.LiveChannel
import com.player.tv.data.Movie
import com.player.tv.data.Parental
import com.player.tv.data.RecentItem
import com.player.tv.data.Series
import com.player.tv.data.allowed
import com.player.tv.ui.components.AppIcons
import com.player.tv.ui.components.Button
import com.player.tv.ui.components.OutlinedButton
import com.player.tv.ui.components.PosterCard
import com.player.tv.ui.components.RowPager
import com.player.tv.ui.components.Spinner
import com.player.tv.ui.components.requestWhenReady
import com.player.tv.ui.components.screenInfo
import com.player.tv.ui.components.tapToClick
import com.player.tv.ui.player.PlayItem
import com.player.tv.ui.player.PlayQueue
import com.player.tv.ui.player.Players
import com.player.tv.ui.theme.Brand
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Date

/** Something on the home rows; the banner on top describes the focused one. */
sealed interface HomeItem {
    val key: String
    val title: String
    val image: String?

    data class Recent(val item: RecentItem) : HomeItem {
        override val key get() = "r:" + item.key
        override val title get() = item.title
        override val image get() = item.image
    }

    data class Channel(val channel: LiveChannel) : HomeItem {
        override val key get() = "c:${channel.streamId}"
        override val title get() = channel.name
        override val image get() = channel.logo
    }

    data class MovieItem(val movie: Movie) : HomeItem {
        override val key get() = "m:${movie.streamId}"
        override val title get() = movie.name
        override val image get() = movie.poster
    }

    data class SeriesItem(val series: Series) : HomeItem {
        override val key get() = "s:${series.seriesId}"
        override val title get() = series.name
        override val image get() = series.cover
    }
}

private enum class HomeRowKind { RECENTS, CHANNELS, MOVIES, SERIES }

// Fixed parts of a home row: title with its padding, gap, focus room above and below the cards.
private val RowChrome = 40.dp + 4.dp + 24.dp
private val RowChromeShort = 32.dp + 4.dp + 16.dp
// Caption under a 16:9 card (name), and under a poster (name and rating).
private val CaptionHeight = 28.dp
private val PosterCaption = 56.dp

/** Poster width so the poster and its captions fill the row's card height (2:3 posters). */
private fun posterWidth(image: Dp, short: Boolean): Dp = ((image - if (short) CaptionHeight else PosterCaption) * 2f / 3f).coerceIn(72.dp, 150.dp)

/** Banner text and artwork; filled in once the details of the focused item arrive. */
data class HeroDetail(val backdrop: String?, val meta: String?, val plot: String?)

class HomeViewModel : ViewModel() {
    private val xtream = Graph.requireXtream()

    val recents = MutableStateFlow<List<RecentItem>>(emptyList())
    val channels = MutableStateFlow<List<LiveChannel>?>(null)   // null while loading
    val movies = MutableStateFlow<List<Movie>?>(null)
    val series = MutableStateFlow<List<Series>?>(null)
    val hero = MutableStateFlow<HomeItem?>(null)
    val heroDetail = MutableStateFlow<HeroDetail?>(null)
    private var heroJob: Job? = null

    /**
     * Muted live preview in the banner while a channel card keeps the focus. One stream at a
     * time (providers limit connections), started after a short pause so passing over cards
     * opens nothing, and stopped as soon as the focus moves on or the screen is left.
     */
    val preview = MutableStateFlow<LiveChannel?>(null)
    val previewReady = MutableStateFlow(false)   // first frame on screen: fade the video in
    var previewPlayer: ExoPlayer? = null
        private set
    private var previewJob: Job? = null
    private var appContext: Context? = null
    private var visible = false
    private var loadedFavorites: Set<Int> = emptySet()

    init {
        refreshRecents()
        loadChannels()
        viewModelScope.launch {
            movies.value = runCatching {
                xtream.movies(null).allowed(Parental.Type.VOD) { it.categoryId }.sortedByDescending { it.added }.take(ROW_SIZE)
            }.getOrDefault(emptyList())
            if (hero.value == null) movies.value?.firstOrNull()?.let { focus(HomeItem.MovieItem(it)) }
        }
        viewModelScope.launch {
            series.value = runCatching {
                xtream.series(null).allowed(Parental.Type.SERIES) { it.categoryId }.sortedByDescending { it.lastModified }.take(ROW_SIZE)
            }.getOrDefault(emptyList())
        }
    }

    /** Back on Home: "continue watching" and favorites may have changed in the player. */
    fun refreshRecents() {
        recents.value = Graph.store.recents(xtream.account)
        val favorites = Graph.store.favorites(xtream.account)
        if (favorites != loadedFavorites && channels.value != null) loadChannels()
    }


    /** Favorite channels first. The channel list is cached, so reloading is cheap. */
    private fun loadChannels() {
        viewModelScope.launch {
            val favorites = Graph.store.favorites(xtream.account)
            loadedFavorites = favorites
            channels.value = runCatching {
                val all = xtream.liveChannels(null).allowed(Parental.Type.LIVE) { it.categoryId }
                (all.filter { it.streamId in favorites } + all.filter { it.streamId !in favorites }).take(ROW_SIZE)
            }.getOrDefault(emptyList())
        }
    }

    fun focus(item: HomeItem) {
        if (hero.value?.key == item.key) return
        schedulePreview(item)
        hero.value = item
        heroDetail.value = null
        heroJob?.cancel()
        heroJob = viewModelScope.launch {
            delay(400)   // only for the item the user stops on
            heroDetail.value = runCatching { detail(item) }.getOrNull()
        }
    }

    private suspend fun detail(item: HomeItem): HeroDetail? = when (item) {
        is HomeItem.MovieItem -> xtream.movieInfo(item.movie.streamId).let { info ->
            HeroDetail(info.backdrop, listOfNotNull(info.releaseDate?.take(4), info.duration, info.movie.rating?.let { "★ $it" }, info.genre).joinToString("  ·  "), info.plot)
        }
        is HomeItem.SeriesItem -> xtream.seriesInfo(item.series.seriesId).let { info ->
            HeroDetail(info.backdrop, listOfNotNull(info.releaseDate?.take(4), info.series.rating?.let { "★ $it" }, info.genre).joinToString("  ·  "), info.plot ?: item.series.plot)
        }
        is HomeItem.Channel -> xtream.shortEpg(item.channel.streamId).firstOrNull()?.let { HeroDetail(null, it.title, it.description) }
        is HomeItem.Recent -> null
    }

    fun resumePreview(context: Context) {
        appContext = context.applicationContext
        visible = true
        schedulePreview(hero.value)
    }

    fun pausePreview() {
        visible = false
        stopPreview()
    }

    private fun schedulePreview(item: HomeItem?) {
        val channel = (item as? HomeItem.Channel)?.channel
        if (channel != null && channel == preview.value) return
        stopPreview()
        if (channel == null || !visible) return
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DELAY_MS)
            startPreview(channel)
        }
    }

    @OptIn(UnstableApi::class)
    private fun startPreview(channel: LiveChannel) {
        val context = appContext ?: return
        val player = previewPlayer ?: Players.create(context).also { p ->
            p.volume = 0f
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            p.addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() { previewReady.value = true }
                override fun onPlayerError(error: PlaybackException) = stopPreview()   // the banner simply stays as it was
            })
            previewPlayer = p
        }
        val url = xtream.liveUrl(channel, Graph.store.liveHls)
        player.setMediaItem(MediaItem.Builder().setUri(url).apply { if (url.substringBefore('?').endsWith(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) }.build())
        player.prepare()
        player.playWhenReady = true
        preview.value = channel
    }

    private fun stopPreview() {
        previewJob?.cancel()
        previewPlayer?.stop()
        preview.value = null
        previewReady.value = false
    }

    override fun onCleared() {
        previewPlayer?.release()
        previewPlayer = null
    }

    fun playChannel(context: Context, list: List<LiveChannel>, index: Int) {
        stopPreview()   // the full-screen player needs the connection
        val hls = Graph.store.liveHls
        PlayQueue.start(context, list.map {
            PlayItem(it.name, xtream.liveUrl(it, hls), isLive = true, logo = it.logo, number = it.number, epgStreamId = it.streamId)
        }, index)
    }

    private companion object {
        const val ROW_SIZE = 20
        const val PREVIEW_DELAY_MS = 900L
    }
}

@Composable
fun HomeScreen(onOpen: (String) -> Unit, vm: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val recents by vm.recents.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    val movies by vm.movies.collectAsStateWithLifecycle()
    val series by vm.series.collectAsStateWithLifecycle()
    val hero by vm.hero.collectAsStateWithLifecycle()
    val heroDetail by vm.heroDetail.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val previewReady by vm.previewReady.collectAsStateWithLifecycle()
    var confirmExit by remember { mutableStateOf(false) }
    val firstItem = remember { FocusRequester() }
    var initialFocusDone by remember { mutableStateOf(false) }

    // Re-read on every return to the home screen so progress from the player shows up.
    LifecycleResumeEffect(Unit) {
        vm.refreshRecents()
        vm.resumePreview(context)
        onPauseOrDispose { vm.pausePreview() }
    }
    BackHandler { confirmExit = true }

    val firstRow = when {
        recents.isNotEmpty() -> HomeRowKind.RECENTS.ordinal
        !channels.isNullOrEmpty() -> HomeRowKind.CHANNELS.ordinal
        !movies.isNullOrEmpty() -> HomeRowKind.MOVIES.ordinal
        !series.isNullOrEmpty() -> HomeRowKind.SERIES.ordinal
        else -> -1
    }
    LaunchedEffect(firstRow) {
        if (firstRow >= 0 && !initialFocusDone) {
            firstItem.requestWhenReady()
            initialFocusDone = true
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val short = screenInfo().shortHeight
        val heroHeight = maxHeight * 0.42f
        val livePreview = (hero as? HomeItem.Channel)?.takeIf { it.channel == preview }?.let { vm.previewPlayer }
        Hero(hero, heroDetail, livePreview, previewReady, Modifier.fillMaxWidth().height(heroHeight + 40.dp))

        // One row of cards at a time under the banner; Down / Up slide the next row in.
        val rows = buildList {
            if (recents.isNotEmpty()) add(HomeRowKind.RECENTS)
            if (channels?.isNotEmpty() != false) add(HomeRowKind.CHANNELS)
            if (movies?.isNotEmpty() != false) add(HomeRowKind.MOVIES)
            if (series?.isNotEmpty() != false) add(HomeRowKind.SERIES)
        }
        val titles = mapOf(
            HomeRowKind.RECENTS to stringResource(R.string.home_continue),
            HomeRowKind.CHANNELS to stringResource(R.string.home_live),
            HomeRowKind.MOVIES to stringResource(R.string.home_recent_movies),
            HomeRowKind.SERIES to stringResource(R.string.home_recent_series),
        )
        RowPager(
            count = rows.size,
            modifier = Modifier.fillMaxSize().padding(top = heroHeight),
            nextLabel = { i -> if (short) null else rows.getOrNull(i + 1)?.let { titles[it] } },
        ) { i, height ->
            val kind = rows[i]
            val title = titles.getValue(kind)
            val first = firstItem.takeIf { firstRow == kind.ordinal }
            // Card image height: what the row leaves after its title, focus room and card captions.
            val image = height - (if (short) RowChromeShort else RowChrome)
            when (kind) {
                HomeRowKind.RECENTS -> HomeRow(title, recents, { HomeItem.Recent(it) }, first, vm::focus, short) { _, r, m, onFocus ->
                    RecentCard(r, minOf(250.dp, (image - CaptionHeight) * 16f / 9f), m, onFocus) { onOpen(r.route) }
                }
                HomeRowKind.CHANNELS -> LoadingRow(title, channels) { list ->
                    HomeRow(title, list, { HomeItem.Channel(it) }, first, vm::focus, short) { index, c, m, onFocus ->
                        ChannelCard(c, minOf(190.dp, (image - CaptionHeight) * 16f / 9f), m, onFocus) { vm.playChannel(context, list, index) }
                    }
                }
                HomeRowKind.MOVIES -> LoadingRow(title, movies) { list ->
                    HomeRow(title, list, { HomeItem.MovieItem(it) }, first, vm::focus, short) { _, mv, m, onFocus ->
                        PosterCard(mv.name, mv.poster, onClick = { onOpen(Routes.movie(mv.streamId)) },
                            modifier = m.width(posterWidth(image, short)).onFocusChanged { if (it.isFocused) onFocus() },
                            subtitle = if (short) null else mv.rating?.let { "★ $it" })
                    }
                }
                HomeRowKind.SERIES -> LoadingRow(title, series) { list ->
                    HomeRow(title, list, { HomeItem.SeriesItem(it) }, first, vm::focus, short) { _, sr, m, onFocus ->
                        PosterCard(sr.name, sr.cover, onClick = { onOpen(Routes.seriesDetail(sr.seriesId)) },
                            modifier = m.width(posterWidth(image, short)).onFocusChanged { if (it.isFocused) onFocus() },
                            subtitle = if (short) null else sr.rating?.let { "★ $it" })
                    }
                }
            }
        }
    }

    if (confirmExit) ExitDialog(onDismiss = { confirmExit = false })
}

@Composable
private fun Hero(item: HomeItem?, detail: HeroDetail?, livePlayer: ExoPlayer?, liveReady: Boolean, modifier: Modifier) {
    Box(modifier) {
        val artwork = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.68f).fillMaxHeight()
        if (item is HomeItem.Channel) {
            ChannelArtwork(item.channel, livePlayer, liveReady, artwork)
        } else {
            val image = detail?.backdrop ?: item?.image
            // Artwork on the right, fading into the background on its left and bottom edges.
            Crossfade(image, animationSpec = tween(400), label = "hero", modifier = artwork) { url ->
                if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().heroFade())
            }
        }
        if (item == null) return@Box
        val short = screenInfo().shortHeight
        Column(Modifier.fillMaxWidth(0.55f).padding(start = 32.dp, top = if (short) 14.dp else 36.dp), verticalArrangement = Arrangement.spacedBy(if (short) 4.dp else 10.dp)) {
            Text(
                stringResource(
                    when (item) {
                        is HomeItem.Channel -> R.string.home_kind_live
                        is HomeItem.MovieItem -> R.string.home_kind_movie
                        is HomeItem.SeriesItem -> R.string.home_kind_series
                        is HomeItem.Recent -> R.string.home_continue
                    }
                ).uppercase(),
                style = MaterialTheme.typography.labelLarge, color = Color.White,
                modifier = Modifier.background(Brand.Primary, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 3.dp),
            )
            Text(item.title, style = if (short) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall, color = Brand.TextPrimary, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.meta?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = Brand.TextSecondary, maxLines = 1) }
            detail?.plot?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Brand.TextPrimary.copy(alpha = 0.85f), maxLines = if (short) 1 else 2, overflow = TextOverflow.Ellipsis) }
            if (item is HomeItem.Channel && livePlayer != null && liveReady) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(AppIcons.VolumeOff, contentDescription = null, tint = Brand.TextSecondary, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.home_preview_hint), style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Fades the banner artwork out towards its left and bottom edges, so it melts into whatever
 * is behind it (the page background has a soft glow, a flat dark overlay would show an edge).
 */
private fun Modifier.heroFade() = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.drawWithContent {
    drawContent()
    drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.45f to Color.Black.copy(alpha = 0.4f), 1f to Color.Black), blendMode = BlendMode.DstIn)
    drawRect(Brush.verticalGradient(0.45f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
}

/**
 * Banner artwork of a channel: its logo while the preview connects, then the live picture
 * (muted) fading in with the same edge fades as movie artwork. Without a preview the logo stays.
 */
@OptIn(UnstableApi::class)
@Composable
private fun ChannelArtwork(channel: LiveChannel, player: ExoPlayer?, ready: Boolean, modifier: Modifier) {
    val shown by animateFloatAsState(if (player != null && ready) 1f else 0f, tween(600), label = "preview")
    Box(modifier) {
        if (player != null) {
            AndroidView(
                factory = { ctx -> (LayoutInflater.from(ctx).inflate(R.layout.preview_player, null) as PlayerView).apply { this.player = player } },
                update = { it.player = player },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = shown }.heroFade(),
            )
        }
        if (shown < 1f && !channel.logo.isNullOrBlank()) {
            AsyncImage(channel.logo, null, contentScale = ContentScale.Fit,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 64.dp).fillMaxHeight(0.4f).aspectRatio(16f / 9f).graphicsLayer { alpha = 0.9f * (1f - shown) })
        }
    }
}

@Composable
private fun <T> LoadingRow(title: String, items: List<T>?, content: @Composable (List<T>) -> Unit) {
    when {
        items == null -> Column(Modifier.padding(start = 32.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Brand.TextPrimary)
            Row(Modifier.height(80.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Spinner(size = 22.dp, strokeWidth = 3.dp)
                Text(stringResource(R.string.loading), color = Brand.TextSecondary)
            }
        }
        items.isNotEmpty() -> content(items)
    }
}

@Composable
private fun <T> HomeRow(
    title: String,
    items: List<T>,
    toHomeItem: (T) -> HomeItem,
    firstFocus: FocusRequester?,
    onFocus: (HomeItem) -> Unit,
    short: Boolean,
    card: @Composable (index: Int, item: T, modifier: Modifier, onFocus: () -> Unit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Brand.TextPrimary, maxLines = 1,
            modifier = Modifier.padding(start = 32.dp, top = if (short) 2.dp else 8.dp))
        LazyRow(
            modifier = Modifier.focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = if (short) 8.dp else 12.dp),
        ) {
            itemsIndexed(items) { index, item ->
                val m = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier
                card(index, item, m) { onFocus(toHomeItem(item)) }
            }
        }
    }
}

@Composable
private fun RecentCard(item: RecentItem, width: Dp, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).onFocusChanged { if (it.isFocused) onFocus() }.tapToClick(onClick),
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Brand.SurfaceHigh, focusedContainerColor = Brand.SurfaceHigh),
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(item.image, item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (item.durationMs > 0) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
                        Box(Modifier.fillMaxWidth((item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)).height(4.dp).background(Brand.Primary))
                    }
                }
            }
        }
        Text(item.title, style = MaterialTheme.typography.bodyMedium, color = Brand.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChannelCard(channel: LiveChannel, width: Dp, modifier: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.width(width), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).onFocusChanged { if (it.isFocused) onFocus() }.tapToClick(onClick),
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape)),
            colors = ClickableSurfaceDefaults.colors(containerColor = Brand.SurfaceHigh, focusedContainerColor = Brand.SurfaceHigh),
        ) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF1F2A44), Color(0xFF151A24))))) {
                AsyncImage(channel.logo, channel.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(18.dp))
                if (channel.number > 0) {
                    Text(channel.number.toString(), modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0x99000000), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp),
                        style = MaterialTheme.typography.labelMedium, color = Color.White)
                }
            }
        }
        Text(channel.name, style = MaterialTheme.typography.bodyMedium, color = Brand.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ExitDialog(onDismiss: () -> Unit) {
    val activity = LocalActivity.current
    val stayFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = Brand.SurfaceHigh),
            modifier = Modifier.widthIn(max = 460.dp),
        ) {
            Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(stringResource(R.string.exit_title), style = MaterialTheme.typography.headlineSmall, color = Brand.TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onDismiss, modifier = Modifier.focusRequester(stayFocus)) { Text(stringResource(R.string.exit_stay)) }
                    OutlinedButton(onClick = { activity?.finish() }) { Text(stringResource(R.string.exit_confirm)) }
                }
            }
        }
    }
    LaunchedEffect(Unit) { stayFocus.requestWhenReady() }
}

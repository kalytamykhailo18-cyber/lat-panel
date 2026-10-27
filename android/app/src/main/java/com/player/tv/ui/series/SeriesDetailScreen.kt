package com.player.tv.ui.series

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.player.tv.R
import com.player.tv.data.Episode
import com.player.tv.data.Graph
import com.player.tv.data.RecentItem
import com.player.tv.data.Season
import com.player.tv.Routes
import com.player.tv.data.SeriesInfo
import com.player.tv.ui.components.AnimatedLoadable
import com.player.tv.ui.components.AppBackground
import com.player.tv.ui.components.BackButton
import com.player.tv.ui.components.Backdrop
import com.player.tv.ui.components.EmptyView
import com.player.tv.ui.components.ErrorView
import com.player.tv.ui.components.Loadable
import com.player.tv.ui.components.Pill
import com.player.tv.ui.components.tapToClick
import com.player.tv.ui.components.LoadingView
import com.player.tv.ui.components.loadable
import com.player.tv.ui.components.requestWhenReady
import com.player.tv.ui.components.screenInfo
import com.player.tv.ui.player.PlayItem
import com.player.tv.ui.player.PlayQueue
import com.player.tv.ui.theme.Brand
import com.player.tv.ui.vod.formatDuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class SeriesDetailViewModel(private val seriesId: Int) : ViewModel() {
    val info = MutableStateFlow<Loadable<SeriesInfo>>(Loadable.Loading)

    init { load() }

    fun load() {
        viewModelScope.launch {
            info.value = Loadable.Loading
            info.value = loadable { Graph.requireXtream().seriesInfo(seriesId) }
        }
    }
}

@Composable
fun SeriesDetailScreen(seriesId: Int, vm: SeriesDetailViewModel = viewModel(key = "series$seriesId") { SeriesDetailViewModel(seriesId) }) {
    val state by vm.info.collectAsStateWithLifecycle()
    AppBackground {
        AnimatedLoadable(
            state,
            loading = { LoadingView(message = stringResource(R.string.loading_details)) },
            failed = { kind -> ErrorView(kind, onRetry = vm::load) },
        ) { info ->
            SeriesDetail(info)
        }
        BackButton(Modifier.align(Alignment.TopStart).padding(24.dp))
    }
}

@Composable
private fun SeriesDetail(info: SeriesInfo) {
    val context = LocalContext.current
    var seasonIndex by rememberSaveable { mutableIntStateOf(0) }
    val season: Season? = info.seasons.getOrNull(seasonIndex)
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestWhenReady() }
    // Re-read saved positions when coming back from the player.
    var resumeRefresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeRefresh++
        onPauseOrDispose { }
    }

    fun play(episodes: List<Episode>, index: Int) {
        val xtream = Graph.requireXtream()
        val items = episodes.map { e ->
            PlayItem(
                title = "${info.series.name} · E${e.number} ${e.title}".trim(),
                url = xtream.episodeUrl(e),
                isLive = false,
                resumeKey = "episode:${e.id}",
                recent = RecentItem(
                    key = "series:${info.series.seriesId}",
                    title = "${info.series.name} · T${season?.number ?: 1} E${e.number}",
                    image = info.backdrop ?: info.series.cover,
                    route = Routes.seriesDetail(info.series.seriesId),
                ),
            )
        }
        PlayQueue.start(context, items, index, Graph.store.resumePosition("episode:${episodes[index].id}"))
    }

    val screen = screenInfo()
    val side = if (screen.expanded) 56.dp else screen.gutter
    Box(Modifier.fillMaxSize()) {
        Backdrop(info.backdrop ?: info.series.cover)
        // Header, seasons and episodes scroll together, so a long plot never pushes the list off screen.
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = side, end = side, top = 84.dp, bottom = 32.dp),
            modifier = Modifier.fillMaxSize().focusRestorer(),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        info.series.name,
                        style = if (screen.compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
                        color = Brand.TextPrimary, fontWeight = FontWeight.Bold, maxLines = 3,
                    )
                    val meta = listOfNotNull(info.releaseDate?.take(4), info.series.rating?.let { "★ $it" }, info.genre).joinToString("  ·  ")
                    if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.titleMedium, color = Brand.TextSecondary)
                    info.plot?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge, color = Brand.TextPrimary, maxLines = if (screen.compact) 6 else 3,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 900.dp))
                    }
                }
            }

            if (info.seasons.isEmpty()) {
                item(key = "empty") { Box(Modifier.fillMaxWidth().height(200.dp)) { EmptyView(stringResource(R.string.no_episodes)) } }
                return@LazyColumn
            }

            item(key = "seasons") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp), modifier = Modifier.focusRestorer()) {
                    itemsIndexed(info.seasons, key = { _, s -> s.number }) { index, s ->
                        Pill(
                            text = stringResource(R.string.season_n, s.number),
                            selected = index == seasonIndex,
                            onClick = { seasonIndex = index },
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
            }

            if (season != null) {
                itemsIndexed(season.episodes, key = { _, e -> "e" + e.id }) { index, episode ->
                    EpisodeRow(
                        episode,
                        fallbackImage = info.series.cover,
                        resumeMs = remember(resumeRefresh, episode.id) { Graph.store.resumePosition("episode:${episode.id}") },
                        compact = screen.compact,
                        onClick = { play(season.episodes, index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, fallbackImage: String?, resumeMs: Long, compact: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().widthIn(max = 1000.dp).tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Brand.Primary), shape = shape)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Brand.Surface.copy(alpha = 0.9f),
            focusedContainerColor = Brand.SurfaceHigh,
            contentColor = Brand.TextPrimary,
            focusedContentColor = Color.White,
        ),
    ) {
        Row(Modifier.padding(if (compact) 8.dp else 12.dp), horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = if (compact) 112.dp else 176.dp, height = if (compact) 63.dp else 99.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x22FFFFFF))) {
                AsyncImage(episode.image ?: fallbackImage, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                // How far this episode was watched.
                val totalMs = parseDurationMs(episode.duration)
                if (resumeMs > 0 && totalMs > 0) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color(0x66000000))) {
                        Box(Modifier.fillMaxWidth((resumeMs.toFloat() / totalMs).coerceIn(0f, 1f)).height(4.dp).background(Brand.Primary))
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.episode_n, episode.number) + (episode.duration?.let { "  ·  $it" } ?: ""),
                    style = MaterialTheme.typography.labelLarge,
                    color = Brand.Primary,
                )
                Text(episode.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (resumeMs > 0) {
                    Text(stringResource(R.string.episode_resume, formatDuration(resumeMs)), style = MaterialTheme.typography.labelMedium, color = Brand.Warning)
                }
                episode.plot?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary, maxLines = 2) }
            }
        }
    }
}

/** "00:45:00", "45:00" or "2700" (seconds) as milliseconds; 0 when unknown. */
private fun parseDurationMs(text: String?): Long {
    val parts = text?.trim()?.split(":")?.map { it.toLongOrNull() ?: return 0 } ?: return 0
    val seconds = when (parts.size) {
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        2 -> parts[0] * 60 + parts[1]
        1 -> parts[0]
        else -> 0
    }
    return seconds * 1000
}

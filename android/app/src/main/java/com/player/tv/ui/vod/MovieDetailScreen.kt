package com.player.tv.ui.vod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.player.tv.ui.components.Button
import androidx.tv.material3.MaterialTheme
import com.player.tv.ui.components.OutlinedButton
import androidx.tv.material3.Text
import com.player.tv.R
import com.player.tv.data.Graph
import com.player.tv.data.MovieInfo
import com.player.tv.data.RecentItem
import com.player.tv.Routes
import com.player.tv.ui.components.AnimatedLoadable
import com.player.tv.ui.components.AppBackground
import com.player.tv.ui.components.BackButton
import com.player.tv.ui.components.Backdrop
import com.player.tv.ui.components.ErrorView
import com.player.tv.ui.components.Loadable
import com.player.tv.ui.components.LoadingView
import com.player.tv.ui.components.PosterImage
import com.player.tv.ui.components.loadable
import com.player.tv.ui.components.requestWhenReady
import com.player.tv.ui.components.screenInfo
import com.player.tv.ui.player.PlayItem
import com.player.tv.ui.player.PlayQueue
import com.player.tv.ui.theme.Brand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MovieDetailViewModel(private val movieId: Int) : ViewModel() {
    val info = MutableStateFlow<Loadable<MovieInfo>>(Loadable.Loading)

    init { load() }

    fun load() {
        viewModelScope.launch {
            info.value = Loadable.Loading
            info.value = loadable { Graph.requireXtream().movieInfo(movieId) }
        }
    }
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun MovieDetailScreen(movieId: Int, vm: MovieDetailViewModel = viewModel(key = "movie$movieId") { MovieDetailViewModel(movieId) }) {
    val state by vm.info.collectAsStateWithLifecycle()
    AppBackground {
        AnimatedLoadable(
            state,
            loading = { LoadingView(message = stringResource(R.string.loading_details)) },
            failed = { kind -> ErrorView(kind, onRetry = vm::load) },
        ) { info ->
            MovieDetail(info)
        }
        BackButton(Modifier.align(Alignment.TopStart).padding(24.dp))
    }
}

@Composable
private fun MovieDetail(info: MovieInfo) {
    val context = LocalContext.current
    val resumeKey = "movie:${info.movie.streamId}"
    var resume by remember { mutableLongStateOf(0L) }
    LifecycleResumeEffect(resumeKey) {
        resume = Graph.store.resumePosition(resumeKey)
        onPauseOrDispose { }
    }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocus.requestWhenReady() }

    fun play(from: Long) {
        val xtream = Graph.requireXtream()
        PlayQueue.start(context, listOf(PlayItem(
            info.movie.name, xtream.movieUrl(info.movie), isLive = false, resumeKey = resumeKey,
            recent = RecentItem(resumeKey, info.movie.name, info.backdrop ?: info.movie.poster, Routes.movie(info.movie.streamId)),
        )), 0, from)
    }

    val screen = screenInfo()
    val title: @Composable () -> Unit = {
        Text(
            info.movie.name,
            style = if (screen.compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displaySmall,
            color = Brand.TextPrimary, fontWeight = FontWeight.Bold, maxLines = 3,
        )
        val meta = listOfNotNull(
            info.releaseDate?.take(4),
            info.duration,
            info.movie.rating?.let { "★ $it" },
            info.genre,
        ).joinToString("  ·  ")
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.titleMedium, color = Brand.TextSecondary)
    }
    val body: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            if (resume > 0) {
                Button(onClick = { play(resume) }, modifier = Modifier.focusRequester(playFocus)) {
                    Text(stringResource(R.string.resume_from, formatDuration(resume)))
                }
                OutlinedButton(onClick = { play(0) }) { Text(stringResource(R.string.play_from_start)) }
            } else {
                Button(onClick = { play(0) }, modifier = Modifier.focusRequester(playFocus)) { Text(stringResource(R.string.play)) }
            }
        }
        info.plot?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Brand.TextPrimary, modifier = Modifier.widthIn(max = 900.dp)) }
        info.director?.let { Text("Director: $it", style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary, maxLines = 1) }
        info.cast?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary, maxLines = 3) }
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(info.backdrop ?: info.movie.poster)
        // Scrolls when the text does not fit (phones, long plots).
        val side = if (screen.expanded) 56.dp else screen.gutter
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = side, end = side, top = 88.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (screen.compact) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PosterImage(info.movie.poster, info.movie.name, modifier = Modifier.width(110.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) { title() }
                }
                body()
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(if (screen.expanded) 40.dp else 24.dp)) {
                    PosterImage(info.movie.poster, info.movie.name, modifier = Modifier.width(if (screen.expanded) 190.dp else 150.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        title()
                        body()
                    }
                }
            }
        }
    }
}

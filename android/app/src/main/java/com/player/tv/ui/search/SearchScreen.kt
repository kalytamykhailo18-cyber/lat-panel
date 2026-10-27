package com.player.tv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.player.tv.R
import com.player.tv.data.Graph
import com.player.tv.data.LiveChannel
import com.player.tv.data.Movie
import com.player.tv.data.Series
import com.player.tv.ui.components.AnimatedLoadable
import com.player.tv.ui.components.AppBackground
import com.player.tv.ui.components.BackButton
import com.player.tv.ui.components.EmptyView
import com.player.tv.ui.components.ErrorView
import com.player.tv.ui.components.Loadable
import com.player.tv.ui.components.LoadingView
import com.player.tv.ui.components.PosterCard
import com.player.tv.ui.components.TvTextField
import com.player.tv.ui.components.loadable
import com.player.tv.ui.components.requestWhenReady
import com.player.tv.ui.components.screenInfo
import com.player.tv.ui.player.PlayItem
import com.player.tv.ui.player.PlayQueue
import com.player.tv.ui.theme.Brand
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.Normalizer

class SearchViewModel : ViewModel() {
    data class Results(val live: List<LiveChannel>, val movies: List<Movie>, val series: List<Series>) {
        val isEmpty get() = live.isEmpty() && movies.isEmpty() && series.isEmpty()
    }

    private val xtream = Graph.requireXtream()
    val query = MutableStateFlow("")
    val results = MutableStateFlow<Loadable<Results>?>(null)
    private var job: Job? = null

    fun onQuery(text: String) {
        query.value = text
        job?.cancel()
        val q = normalize(text)
        if (q.length < 2) {
            results.value = null
            return
        }
        job = viewModelScope.launch {
            delay(350)
            if (results.value == null) results.value = Loadable.Loading
            results.value = loadable {
                // Full lists are fetched once and cached by XtreamClient.
                val live = async { xtream.liveChannels(null) }
                val movies = async { xtream.movies(null) }
                val series = async { xtream.series(null) }
                Results(
                    live.await().filter { normalize(it.name).contains(q) }.take(MAX),
                    movies.await().filter { normalize(it.name).contains(q) }.take(MAX),
                    series.await().filter { normalize(it.name).contains(q) }.take(MAX),
                )
            }
        }
    }

    fun retry() = onQuery(query.value)

    fun playLive(context: android.content.Context, list: List<LiveChannel>, index: Int) {
        val hls = Graph.store.liveHls
        PlayQueue.start(context, list.map {
            PlayItem(it.name, xtream.liveUrl(it, hls), isLive = true, logo = it.logo, number = it.number, epgStreamId = it.streamId)
        }, index)
    }

    private fun normalize(s: String) =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").trim()

    private companion object {
        const val MAX = 60
    }
}

@Composable
fun SearchScreen(onOpenMovie: (Int) -> Unit, onOpenSeries: (Int) -> Unit, vm: SearchViewModel = viewModel()) {
    val context = LocalContext.current
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fieldFocus.requestWhenReady() }
    val screen = screenInfo()
    val posterWidth = if (screen.compact) 116.dp else 150.dp

    AppBackground {
        Column(Modifier.fillMaxSize().padding(horizontal = screen.gutter, vertical = if (screen.compact) 16.dp else 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BackButton(Modifier.padding(bottom = 4.dp))
                TvTextField(
                    value = query,
                    onValueChange = vm::onQuery,
                    label = stringResource(R.string.home_search),
                    placeholder = stringResource(R.string.search_hint),
                    imeAction = ImeAction.Search,
                    surfaceFocusRequester = fieldFocus,
                    modifier = if (screen.expanded) Modifier.fillMaxWidth(0.6f) else Modifier.weight(1f),
                )
            }
            val current = results
            if (current == null) EmptyView(stringResource(R.string.search_start_hint))
            else AnimatedLoadable(
                current,
                loading = { LoadingView(message = stringResource(R.string.search_loading)) },
                failed = { kind -> ErrorView(kind, onRetry = vm::retry) },
            ) { found ->
                if (found.isEmpty) {
                    EmptyView(stringResource(R.string.search_no_results))
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(24.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                        if (found.live.isNotEmpty()) item {
                            ResultRow(stringResource(R.string.home_live)) {
                                itemsIndexed(found.live, key = { _, it -> it.streamId }) { index, ch ->
                                    PosterCard(ch.name, ch.logo, onClick = { vm.playLive(context, found.live, index) }, modifier = Modifier.width(if (screen.compact) 160.dp else 200.dp),
                                        aspectRatio = 16f / 9f, imageScale = ContentScale.Fit)
                                }
                            }
                        }
                        if (found.movies.isNotEmpty()) item {
                            ResultRow(stringResource(R.string.home_movies)) {
                                items(found.movies, key = { it.streamId }) { m ->
                                    PosterCard(m.name, m.poster, onClick = { onOpenMovie(m.streamId) }, modifier = Modifier.width(posterWidth))
                                }
                            }
                        }
                        if (found.series.isNotEmpty()) item {
                            ResultRow(stringResource(R.string.home_series)) {
                                items(found.series, key = { it.seriesId }) { s ->
                                    PosterCard(s.name, s.cover, onClick = { onOpenSeries(s.seriesId) }, modifier = Modifier.width(posterWidth))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(title: String, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Brand.TextPrimary)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
            modifier = Modifier.focusRestorer(),
            content = content,
        )
    }
}

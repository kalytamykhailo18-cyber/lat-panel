package com.player.tv.ui.vod

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.player.tv.R
import com.player.tv.data.Category
import com.player.tv.data.Graph
import com.player.tv.ui.components.CatalogScreen
import com.player.tv.ui.components.CatalogViewModel
import com.player.tv.ui.components.PosterItem

class MoviesViewModel : CatalogViewModel() {
    private val xtream = Graph.requireXtream()

    override suspend fun loadCategories(): List<Category> = xtream.movieCategories()

    override suspend fun loadItems(categoryId: String?): List<PosterItem> =
        xtream.movies(categoryId).map { PosterItem(it.streamId, it.name, it.poster, it.rating?.let { r -> "★ $r" }) }
}

@Composable
fun MoviesScreen(onOpenMovie: (Int) -> Unit, vm: MoviesViewModel = viewModel()) {
    CatalogScreen(stringResource(R.string.home_movies), stringResource(R.string.loading_movies), vm, onOpenMovie)
}

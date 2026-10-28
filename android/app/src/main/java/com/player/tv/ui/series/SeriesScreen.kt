package com.player.tv.ui.series

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.player.tv.R
import com.player.tv.data.Category
import com.player.tv.data.Graph
import com.player.tv.data.Parental
import com.player.tv.ui.components.CatalogScreen
import com.player.tv.ui.components.CatalogViewModel
import com.player.tv.ui.components.PosterItem

class SeriesViewModel : CatalogViewModel(Parental.Type.SERIES) {
    private val xtream = Graph.requireXtream()

    override suspend fun loadCategories(): List<Category> = xtream.seriesCategories()

    override suspend fun loadItems(categoryId: String?): List<PosterItem> =
        xtream.series(categoryId).map { PosterItem(it.seriesId, it.name, it.cover, it.rating?.let { r -> "★ $r" }, it.categoryId) }
}

@Composable
fun SeriesScreen(onOpenSeries: (Int) -> Unit, vm: SeriesViewModel = viewModel()) {
    CatalogScreen(stringResource(R.string.home_series), stringResource(R.string.loading_series), vm, onOpenSeries)
}

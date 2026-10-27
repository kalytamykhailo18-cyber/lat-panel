package com.player.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.player.tv.R
import com.player.tv.data.Category
import com.player.tv.ui.theme.Brand
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Item shown in a poster grid. */
data class PosterItem(val id: Int, val title: String, val image: String?, val subtitle: String? = null)

/** Categories on the left, posters on the right. Shared by Movies and Series. */
abstract class CatalogViewModel : ViewModel() {
    protected abstract suspend fun loadCategories(): List<Category>
    protected abstract suspend fun loadItems(categoryId: String?): List<PosterItem>

    val categories = MutableStateFlow<Loadable<List<Pair<String, String>>>>(Loadable.Loading)
    val selected = MutableStateFlow<String?>(null)
    val items = MutableStateFlow<Loadable<List<PosterItem>>>(Loadable.Loading)
    private var itemsJob: Job? = null

    fun start() {
        if (categories.value is Loadable.Ready) return
        viewModelScope.launch {
            categories.value = Loadable.Loading
            val result = loadable { loadCategories() }
            categories.value = result.map { list -> list.map { it.id to it.name } }
            if (result is Loadable.Ready && selected.value == null) select(result.data.firstOrNull()?.id ?: ALL_ID)
        }
    }

    fun select(categoryId: String, force: Boolean = false) {
        if (categoryId == selected.value && !force) return
        selected.value = categoryId
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            items.value = Loadable.Loading
            delay(200)
            items.value = loadable { loadItems(categoryId.takeUnless { it == ALL_ID }) }
        }
    }

    companion object {
        const val ALL_ID = "__all"
    }
}

@Composable
fun CatalogScreen(title: String, itemsLoadingText: String, vm: CatalogViewModel, onOpen: (Int) -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.start() }
    val screen = screenInfo()

    AppBackground {
        AnimatedLoadable(
            categories,
            loading = { LoadingView(message = stringResource(R.string.loading_categories)) },
            failed = { kind -> ErrorView(kind, onRetry = vm::start) },
        ) { categoryList ->
            val allCategories = listOf(CatalogViewModel.ALL_ID to stringResource(R.string.category_all)) + categoryList
            val grid: @Composable () -> Unit = {
                AnimatedLoadable(
                    items,
                    loading = { LoadingView(message = itemsLoadingText) },
                    failed = { kind -> ErrorView(kind, onRetry = { selected?.let { vm.select(it, force = true) } }) },
                ) { itemList ->
                    if (itemList.isEmpty()) {
                        EmptyView(stringResource(R.string.empty_list))
                    } else {
                        val gap = if (screen.compact) 12.dp else 20.dp
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(if (screen.compact) 100.dp else 118.dp),
                            modifier = Modifier.fillMaxSize().focusRestorer(),
                            contentPadding = PaddingValues(if (screen.compact) 4.dp else 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            items(itemList, key = { it.id }) { item ->
                                PosterCard(item.title, item.image, onClick = { onOpen(item.id) }, subtitle = item.subtitle)
                            }
                        }
                    }
                }
            }
            if (screen.compact) {
                Column(Modifier.fillMaxSize().padding(horizontal = screen.gutter, vertical = 16.dp)) {
                    ScreenHeader(title)
                    CategoryRow(allCategories, selected, vm::select)
                    Box(Modifier.weight(1f).fillMaxWidth()) { grid() }
                }
            } else {
                Row(Modifier.fillMaxSize().padding(screen.gutter.coerceAtMost(24.dp)), horizontalArrangement = Arrangement.spacedBy(if (screen.expanded) 24.dp else 16.dp)) {
                    Column(Modifier.width(if (screen.expanded) 230.dp else 190.dp).fillMaxHeight()) {
                        ScreenHeader(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
                        CategoryColumn(allCategories, selected, vm::select)
                    }
                    Box(Modifier.weight(1f).fillMaxHeight()) { grid() }
                }
            }
        }
        if (categories !is Loadable.Ready) BackButton(Modifier.align(Alignment.TopStart).padding(24.dp))
    }
}

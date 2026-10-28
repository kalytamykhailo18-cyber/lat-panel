package com.player.tv.ui.components

import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.player.tv.data.Parental
import com.player.tv.ui.theme.Brand
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Item shown in a poster grid. [categoryId] lets "All" leave out locked categories. */
data class PosterItem(val id: Int, val title: String, val image: String?, val subtitle: String? = null, val categoryId: String? = null)

/** Categories on the left, posters on the right. Shared by Movies and Series. */
abstract class CatalogViewModel(val type: Parental.Type) : ViewModel() {
    protected abstract suspend fun loadCategories(): List<Category>
    protected abstract suspend fun loadItems(categoryId: String?): List<PosterItem>

    val categories = MutableStateFlow<Loadable<List<Pair<String, String>>>>(Loadable.Loading)
    val selected = MutableStateFlow<String?>(null)
    val items = MutableStateFlow<Loadable<List<PosterItem>>>(Loadable.Loading)
    val locked = MutableStateFlow(false)
    private var itemsJob: Job? = null

    fun start() {
        if (categories.value is Loadable.Ready) return
        viewModelScope.launch {
            categories.value = Loadable.Loading
            val result = loadable { loadCategories() }
            categories.value = result.map { list -> list.map { it.id to it.name } }
            if (result is Loadable.Ready && selected.value == null) {
                select(result.data.firstOrNull { !Parental.isLocked(type, it.id) }?.id ?: ALL_ID)
            }
        }
    }

    fun select(categoryId: String, force: Boolean = false) {
        if (categoryId == selected.value && !force) return
        selected.value = categoryId
        itemsJob?.cancel()
        if (Parental.isLocked(type, categoryId)) {
            locked.value = true
            return
        }
        locked.value = false
        itemsJob = viewModelScope.launch {
            items.value = Loadable.Loading
            delay(200)
            items.value = loadable {
                val list = loadItems(categoryId.takeUnless { it == ALL_ID })
                if (categoryId == ALL_ID) list.filterNot { Parental.isLocked(type, it.categoryId) } else list
            }
        }
    }

    fun unlock(pin: String): Boolean {
        val id = selected.value ?: return false
        if (!Parental.checkPin(pin)) return false
        Parental.unlock(type, id)
        select(id, force = true)
        return true
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
    val locked by vm.locked.collectAsStateWithLifecycle()
    var askPin by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { vm.start() }
    val screen = screenInfo()

    Box(Modifier.fillMaxSize()) {
        AnimatedLoadable(
            categories,
            loading = { LoadingView(message = stringResource(R.string.loading_categories)) },
            failed = { kind -> ErrorView(kind, onRetry = vm::start) },
        ) { categoryList ->
            val allCategories = listOf(CatalogViewModel.ALL_ID to stringResource(R.string.category_all)) + categoryList
            val grid: @Composable () -> Unit = {
                if (locked) {
                    LockedView(onUnlock = { askPin = true })
                } else AnimatedLoadable(
                    items,
                    loading = { LoadingView(message = itemsLoadingText) },
                    failed = { kind -> ErrorView(kind, onRetry = { selected?.let { vm.select(it, force = true) } }) },
                ) { itemList ->
                    if (itemList.isEmpty()) {
                        EmptyView(stringResource(R.string.empty_list))
                    } else {
                        PosterGrid(itemList, compact = screen.compact, onOpen = onOpen)
                    }
                }
            }
            val count = (items as? Loadable.Ready)?.data?.size?.takeIf { !locked }
            Row(Modifier.fillMaxSize().padding(start = 20.dp, top = 24.dp, end = 24.dp), horizontalArrangement = Arrangement.spacedBy(if (screen.expanded) 24.dp else 16.dp)) {
                Column(Modifier.width(if (screen.expanded) 240.dp else 200.dp).fillMaxHeight()) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = Brand.TextPrimary, modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                    CategoryColumn(allCategories, selected, vm::select, isLocked = { Parental.isLocked(vm.type, it) })
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    val label = allCategories.firstOrNull { it.first == selected }?.second.orEmpty()
                    Text(
                        if (count != null) "$label  ·  $count" else label,
                        style = MaterialTheme.typography.titleMedium, color = Brand.TextSecondary, maxLines = 1,
                        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 4.dp),
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) { grid() }
                }
            }
        }
    }
    if (askPin) PinDialog(stringResource(R.string.parental_enter_pin), onDismiss = { askPin = false }, onSubmit = { pin ->
        vm.unlock(pin).also { if (it) askPin = false }
    })
}

/**
 * Posters in whole rows: the cards are sized so an exact number of rows fits the space, and
 * moving down scrolls one full row at a time, so a row is never shown cut in half.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PosterGrid(items: List<PosterItem>, compact: Boolean, onOpen: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val gap = if (compact) 12.dp else 20.dp
        val side = if (compact) 4.dp else 12.dp
        val caption = 56.dp   // title and rating under the poster
        val usable = maxWidth - side * 2
        val target = if (compact) 96.dp else 120.dp
        var columns = ((usable + gap) / (target + gap)).toInt().coerceAtLeast(2)
        var cardWidth = (usable - gap * (columns - 1)) / columns
        // Each cell carries half the gap above and below, so rows sit exactly one cell apart.
        // Low screens (phones): posters as tall as one row allows, and as many columns as fit.
        if (cardWidth * 1.5f + caption + gap > maxHeight) {
            cardWidth = (maxHeight - caption - gap) / 1.5f
            columns = ((usable + gap) / (cardWidth + gap)).toInt().coerceAtLeast(2)
            cardWidth = (usable - gap * (columns - 1)) / columns
            cardWidth = minOf(cardWidth, (maxHeight - caption - gap) / 1.5f)
        }
        val cell = cardWidth * 1.5f + caption + gap
        val rows = (maxHeight / cell).toInt().coerceAtLeast(1)
        val pitch = with(LocalDensity.current) { cell.toPx() }
        CompositionLocalProvider(LocalBringIntoViewSpec provides remember(pitch) { wholeRowsBringIntoView(pitch) }) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxWidth().height(cell * rows).focusRestorer(),
                contentPadding = PaddingValues(horizontal = side),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                items(items, key = { it.id }) { item ->
                    Box(Modifier.height(cell).padding(vertical = gap / 2)) {
                        PosterCard(item.title, item.image, onClick = { onOpen(item.id) }, subtitle = item.subtitle, modifier = Modifier.width(cardWidth))
                    }
                }
            }
        }
    }
}

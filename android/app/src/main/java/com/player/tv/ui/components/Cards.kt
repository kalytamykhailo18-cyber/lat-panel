package com.player.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.Glow
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SelectableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.player.tv.ui.theme.Brand

private val focusBorder = Border(BorderStroke(2.dp, Brand.Primary), shape = RoundedCornerShape(10.dp))

/** Vertical poster (2:3) with the title below, for movies and series. */
@Composable
fun PosterCard(
    title: String,
    image: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    aspectRatio: Float = 2f / 3f,
    imageScale: ContentScale = ContentScale.Crop,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio).tapToClick(onClick),
            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
            scale = CardDefaults.scale(focusedScale = 1.06f),
            border = CardDefaults.border(focusedBorder = focusBorder),
            glow = CardDefaults.glow(focusedGlow = Glow(elevationColor = Brand.Primary.copy(alpha = 0.45f), elevation = 14.dp)),
            colors = CardDefaults.colors(containerColor = Brand.SurfaceHigh),
        ) {
            Box(Modifier.fillMaxSize()) {
                Text(
                    title,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp),
                    color = Brand.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                )
                AsyncImage(model = image, contentDescription = title, contentScale = imageScale, modifier = Modifier.fillMaxSize())
            }
        }
        Text(title, style = MaterialTheme.typography.bodyMedium, color = Brand.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Brand.TextSecondary, maxLines = 1)
        }
    }
}

/** Left-hand category list. A category is selected as soon as it gets focus. */
@Composable
fun CategoryColumn(
    categories: List<Pair<String, String>>,   // id to label
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    val selectedFocus = remember { FocusRequester() }
    // Focus the selected category when the screen first opens, but not when returning from a
    // detail screen (the grid restores focus to the item the user came from).
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(categories) {
        val index = categories.indexOfFirst { it.first == selectedId }
        if (index > 0) state.scrollToItem(index)
        if (!initialFocusDone) {
            withFrameNanos { }
            selectedFocus.safeRequest()
            initialFocusDone = true
        }
    }
    LazyColumn(
        state = state,
        modifier = modifier.focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
    ) {
        itemsIndexed(categories, key = { _, c -> c.first }) { _, (id, label) ->
            val selected = id == selectedId
            Surface(
                selected = selected,
                onClick = { onSelect(id) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (selected) Modifier.focusRequester(selectedFocus) else Modifier)
                    .onFocusChanged { if (it.isFocused) onSelect(id) }
                    .tapToClick({ onSelect(id) }),
                shape = SelectableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                scale = SelectableSurfaceDefaults.scale(focusedScale = 1.02f),
                colors = SelectableSurfaceDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Brand.TextSecondary,
                    selectedContainerColor = Brand.SurfaceHigh,
                    selectedContentColor = Brand.TextPrimary,
                    focusedContainerColor = Brand.Primary,
                    focusedContentColor = Color.White,
                    focusedSelectedContainerColor = Brand.Primary,
                    focusedSelectedContentColor = Color.White,
                ),
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Category pills in one scrolling row; replaces the side column on narrow screens. */
@Composable
fun CategoryRow(
    categories: List<Pair<String, String>>,   // id to label
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    LaunchedEffect(categories) {
        val index = categories.indexOfFirst { it.first == selectedId }
        if (index > 0) state.scrollToItem(index)
    }
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth().focusRestorer(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 8.dp),
    ) {
        items(categories, key = { it.first }) { (id, label) ->
            Pill(label, id == selectedId, { onSelect(id) }, Modifier.widthIn(max = 240.dp))
        }
    }
}

/** One row of the live channel list. */
@Composable
fun ChannelRow(
    number: Int,
    name: String,
    logo: String?,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onFocused() }.tapToClick(onClick, onLongClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Brand.Surface,
            contentColor = Brand.TextPrimary,
            focusedContainerColor = Brand.Primary,
            focusedContentColor = Color.White,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (number > 0) number.toString() else "", modifier = Modifier.size(width = 34.dp, height = 20.dp),
                style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.size(width = 56.dp, height = 36.dp).clip(RoundedCornerShape(4.dp)).background(Color(0x22FFFFFF))) {
                AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(2.dp))
            }
            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (favorite) Text("★", color = Color(0xFFFFC94D), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Full-screen backdrop for detail screens: the artwork fades into the background on the left
 * (where the text sits) and at the bottom, so there is no visible edge.
 */
@Composable
fun Backdrop(image: String?, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0f to Brand.Background, 0.45f to Brand.Background.copy(alpha = 0.8f), 1f to Brand.Background.copy(alpha = 0.2f)
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(0.5f to Color.Transparent, 1f to Brand.Background)
            )
        )
    }
}

/** Poster image without a caption, for detail screens. */
@Composable
fun PosterImage(image: String?, title: String, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(Brand.SurfaceHigh)) {
        Text(title, modifier = Modifier.align(Alignment.Center).padding(12.dp), color = Brand.TextSecondary,
            style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        AsyncImage(model = image, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

/** Selectable pill (season tabs, option toggles) with readable colors in every state. */
@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.tapToClick(onClick),
        shape = SelectableSurfaceDefaults.shape(shape),
        scale = SelectableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = SelectableSurfaceDefaults.border(
            border = Border(BorderStroke(1.dp, Brand.SurfaceHigh), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
            focusedSelectedBorder = Border(BorderStroke(2.dp, Color.White), shape = shape),
        ),
        colors = SelectableSurfaceDefaults.colors(
            containerColor = Brand.Surface,
            contentColor = Brand.TextSecondary,
            selectedContainerColor = Brand.Primary.copy(alpha = 0.25f),
            selectedContentColor = Brand.TextPrimary,
            focusedContainerColor = Brand.Primary,
            focusedContentColor = Color.White,
            focusedSelectedContainerColor = Brand.Primary,
            focusedSelectedContentColor = Color.White,
        ),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp), style = MaterialTheme.typography.titleSmall,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

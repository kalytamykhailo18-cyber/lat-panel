package com.player.tv.ui.home

import androidx.activity.compose.LocalActivity
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.tv.material3.Border
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import com.player.tv.ui.components.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import com.player.tv.ui.components.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.player.tv.R
import com.player.tv.Routes
import com.player.tv.data.Graph
import com.player.tv.data.RecentItem
import com.player.tv.ui.components.AppBackground
import com.player.tv.ui.components.AppIcons
import com.player.tv.ui.components.ScreenInfo
import com.player.tv.ui.components.enterAnimation
import com.player.tv.ui.components.screenInfo
import com.player.tv.ui.components.Logo
import com.player.tv.ui.components.tapToClick
import com.player.tv.ui.components.requestWhenReady
import com.player.tv.ui.theme.Brand
import kotlinx.coroutines.delay
import java.util.Date

@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    val firstTile = remember { FocusRequester() }
    var recents by remember { mutableStateOf(emptyList<RecentItem>()) }
    var confirmExit by remember { mutableStateOf(false) }

    // Re-read on every return to the home screen so progress from the player shows up.
    LifecycleResumeEffect(Unit) {
        recents = Graph.xtream?.account?.let(Graph.store::recents).orEmpty()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) { firstTile.requestWhenReady() }
    BackHandler { confirmExit = true }

    val screen = screenInfo()
    AppBackground {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Tiles take the room that is left; on short or narrow screens the page scrolls instead.
            val tileHeight = when {
                screen.compact -> ((maxHeight - if (recents.isEmpty()) 170.dp else 400.dp) / 3).coerceIn(110.dp, 200.dp)
                else -> (maxHeight - if (recents.isEmpty()) 150.dp else 330.dp).coerceIn(150.dp, if (screen.expanded) 330.dp else 420.dp)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = screen.gutter, vertical = if (screen.compact) 16.dp else 28.dp),
            ) {
            TopBar(onOpen, screen)
            Spacer(Modifier.height(if (screen.compact) 16.dp else 24.dp))
            val tiles: @Composable (Modifier, Modifier, Modifier) -> Unit = { m1, m2, m3 ->
                BigTile(stringResource(R.string.home_live), "LIVE", AppIcons.LiveTv, Brand.LiveGradient, m1.enterAnimation(0).focusRequester(firstTile)) { onOpen(Routes.LIVE) }
                BigTile(stringResource(R.string.home_movies), "VOD", AppIcons.Movie, Brand.MoviesGradient, m2.enterAnimation(1)) { onOpen(Routes.MOVIES) }
                BigTile(stringResource(R.string.home_series), "TV", AppIcons.Series, Brand.SeriesGradient, m3.enterAnimation(2)) { onOpen(Routes.SERIES) }
            }
            if (screen.compact) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val m = Modifier.fillMaxWidth().height(tileHeight)
                    tiles(m, m, m)
                }
            } else {
                Row(Modifier.fillMaxWidth().height(tileHeight), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    tiles(Modifier.weight(1f), Modifier.weight(1f), Modifier.weight(1f))
                }
            }
            if (recents.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.home_continue), style = MaterialTheme.typography.titleMedium, color = Brand.TextPrimary)
                LazyRow(
                    modifier = Modifier.focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 4.dp),
                ) {
                    itemsIndexed(recents, key = { _, it -> it.key }) { index, item ->
                        RecentCard(item, Modifier.enterAnimation(3 + index)) { onOpen(item.route) }
                    }
                }
            }
            }
        }
    }

    if (confirmExit) ExitDialog(onDismiss = { confirmExit = false })
}

@Composable
private fun TopBar(onOpen: (String) -> Unit, screen: ScreenInfo) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = System.currentTimeMillis()
        }
    }
    val expiresAt = Graph.xtream?.userInfo?.expiresAt

    val expiry: (@Composable () -> Unit)? = if (Graph.xtream?.userInfo == null) null else {
        {
            // Last week before expiry: a visible warning instead of a grey date.
            val secondsLeft = expiresAt?.let { it - now / 1000 }
            val daysLeft = secondsLeft?.let { Math.floorDiv(it, 86_400L).toInt() }
            val (label, color) = when {
                expiresAt == null -> stringResource(R.string.home_no_expiry) to Brand.TextSecondary
                secondsLeft!! < 0 -> stringResource(R.string.home_expired) to Brand.Error
                daysLeft == 0 -> stringResource(R.string.home_expires_today) to Brand.Warning
                daysLeft!! <= 7 -> pluralStringResource(R.plurals.home_expires_in, daysLeft, daysLeft) to Brand.Warning
                else -> stringResource(R.string.home_expires, DateFormat.getMediumDateFormat(context).format(Date(expiresAt * 1000))) to Brand.TextSecondary
            }
            Text(label, style = MaterialTheme.typography.bodyMedium, color = color, maxLines = 1, softWrap = false)
        }
    }
    val clock: @Composable () -> Unit = {
        Text(DateFormat.getTimeFormat(context).format(Date(now)), style = MaterialTheme.typography.headlineSmall, color = Brand.TextPrimary, maxLines = 1, softWrap = false)
    }
    val brand: @Composable RowScope.() -> Unit = {
        Logo(Modifier.size(if (screen.compact) 36.dp else 44.dp))
        Text(
            stringResource(R.string.app_name),
            style = if (screen.compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            color = Brand.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }

    if (screen.compact) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                brand()
                Spacer(Modifier.weight(1f))
                clock()
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TopAction(stringResource(R.string.home_search), AppIcons.Search) { onOpen(Routes.SEARCH) }
                TopAction(stringResource(R.string.home_settings), AppIcons.Settings) { onOpen(Routes.SETTINGS) }
                Spacer(Modifier.weight(1f))
                expiry?.invoke()
            }
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            brand()
            Spacer(Modifier.width(8.dp))
            TopAction(stringResource(R.string.home_search), AppIcons.Search) { onOpen(Routes.SEARCH) }
            TopAction(stringResource(R.string.home_settings), AppIcons.Settings) { onOpen(Routes.SETTINGS) }
            Spacer(Modifier.weight(1f))
            expiry?.invoke()
            clock()
        }
    }
}

@Composable
private fun TopAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = Modifier.tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Brand.Surface,
            contentColor = Brand.TextSecondary,
            focusedContainerColor = Brand.Primary,
            focusedContentColor = Color.White,
        ),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun BigTile(title: String, badge: String, icon: ImageVector, gradient: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight().tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = shape)),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(elevationColor = gradient.first().copy(alpha = 0.55f), elevation = 20.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
    ) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(gradient), shape)) {
            // A light sheen and a large soft icon give the tile depth.
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)), shape))
            Box(Modifier.fillMaxSize().padding(if (screenInfo().expanded) 24.dp else 16.dp)) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.22f), modifier = Modifier.align(Alignment.TopEnd).fillMaxHeight(0.55f).aspectRatio(1f))
            Text(
                badge,
                modifier = Modifier.align(Alignment.TopStart).background(Color(0x33000000), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
            Text(title, modifier = Modifier.align(Alignment.BottomStart), style = if (screenInfo().expanded) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun RecentCard(item: RecentItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.width(220.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).tapToClick(onClick),
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

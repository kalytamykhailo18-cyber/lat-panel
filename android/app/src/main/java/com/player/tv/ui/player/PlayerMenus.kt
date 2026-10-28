package com.player.tv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.player.tv.R
import com.player.tv.ui.components.AppIcons
import com.player.tv.ui.components.requestWhenReady
import com.player.tv.ui.components.screenInfo
import com.player.tv.ui.components.tapToClick
import com.player.tv.ui.theme.Brand

/** Option menus of the player, shared by live TV and movies / episodes. */
enum class PlayerMenu(val title: Int) {
    AUDIO(R.string.player_audio),
    SUBTITLES(R.string.player_subtitles),
    SPEED(R.string.player_speed),
    SCREEN(R.string.player_screen),
}

data class MenuState(
    val menu: PlayerMenu,
    val options: List<String>,
    val selected: Int = -1,
    val note: String? = null,   // e.g. "this channel has no subtitles"
)

interface MenuActions {
    fun openMenu(menu: PlayerMenu)
    fun chooseOption(index: Int)
}

/** Side panel with the options of the open menu; focus starts on the current choice. */
@Composable
fun MenuPanel(state: MenuState, onChoose: (Int) -> Unit, modifier: Modifier = Modifier) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(state.menu) { firstFocus.requestWhenReady() }
    val narrow = screenInfo().widthDp < 900
    Column(
        modifier.width(if (narrow) 300.dp else 360.dp).fillMaxHeight().background(Color(0xF2151A24))
            .padding(horizontal = 24.dp, vertical = if (narrow) 20.dp else 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(state.menu.title), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        state.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Brand.TextSecondary) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            itemsIndexed(state.options) { index, label ->
                val selected = index == state.selected
                val isFocusTarget = if (state.selected >= 0) selected else index == 0
                Surface(
                    onClick = { onChoose(index) },
                    modifier = Modifier.fillMaxWidth().then(if (isFocusTarget) Modifier.focusRequester(firstFocus) else Modifier)
                        .tapToClick({ onChoose(index) }),
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = if (selected) Brand.Primary.copy(alpha = 0.2f) else Color.Transparent,
                        contentColor = Color.White,
                        focusedContainerColor = Brand.Primary,
                        focusedContentColor = Color.White,
                    ),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (selected) Icon(AppIcons.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

/** Pill button of the player bars (Audio, Subtítulos, Canales…). */
@Composable
fun OptionButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp).tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0x33FFFFFF),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
}

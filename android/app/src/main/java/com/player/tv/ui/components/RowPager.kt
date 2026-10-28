package com.player.tv.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.player.tv.ui.theme.Brand

/** Space [RowPager] keeps at the bottom of a page for the name of the next row. */
val NextRowHintHeight = 26.dp

/**
 * Shows one row of cards at a time in the given space, never a row cut in half. Moving the
 * focus down (or up) to another row slides that row into the same place. Each page gets the
 * full height minus the hint at the bottom that names the next row, so it is clear there is more.
 * Touch: swipe up / down changes the row.
 */
@Composable
fun RowPager(
    count: Int,
    modifier: Modifier = Modifier,
    nextLabel: (index: Int) -> String? = { null },
    hintStart: Dp = 32.dp,
    row: @Composable (index: Int, height: Dp) -> Unit,
) {
    var current by remember { mutableIntStateOf(0) }
    if (current >= count) current = (count - 1).coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .clipToBounds()
            .pointerInput(count) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { _, amount -> dragged += amount },
                    onDragEnd = {
                        if (dragged < -48.dp.toPx()) current = (current + 1).coerceAtMost(count - 1)
                        else if (dragged > 48.dp.toPx()) current = (current - 1).coerceAtLeast(0)
                    },
                )
            },
    ) {
        val page = maxHeight
        val offset by animateDpAsState(page * current, tween(320), label = "rows")
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .offset { IntOffset(0, -offset.roundToPx()) },
        ) {
            for (index in 0 until count) {
                Box(Modifier.fillMaxWidth().height(page).onFocusChanged { if (it.hasFocus) current = index }) {
                    val hint = nextLabel(index)
                    row(index, page - NextRowHintHeight)
                    if (hint != null) {
                        Row(
                            Modifier.align(Alignment.BottomStart).padding(start = hintStart, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(AppIcons.ChevronRight, contentDescription = null, tint = Brand.TextSecondary, modifier = Modifier.size(18.dp).rotate(90f))
                            Text(hint, style = MaterialTheme.typography.labelLarge, color = Brand.TextSecondary, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Scrolls a list of rows that are all [pitch] px tall (row plus its spacing) by whole rows only,
 * and only when the focused item is not fully visible. With a viewport that holds an exact
 * number of rows, no row is ever shown cut. (Android TV's default keeps the focused item at
 * 30% of the screen, which cuts rows.)
 */
@OptIn(ExperimentalFoundationApi::class)
fun wholeRowsBringIntoView(pitch: Float) = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = offset + size
        val delta = when {
            offset >= 0f && end <= containerSize -> 0f
            offset < 0f || size > containerSize -> offset
            else -> end - containerSize
        }
        if (delta == 0f || pitch <= 0f) return delta
        val rows = kotlin.math.abs(delta) / pitch
        // Round to whole rows, with a little tolerance for rounding of dp to px.
        val whole = if (rows - kotlin.math.floor(rows) < 0.02f) kotlin.math.floor(rows) else kotlin.math.ceil(rows)
        return kotlin.math.sign(delta) * whole * pitch
    }
}

package com.player.tv.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput

/**
 * TV Material components only react to the remote (D-pad + OK). This adds mouse clicks and
 * touch taps, so the app also works on phones, tablets and TV boxes used with a mouse.
 */
fun Modifier.tapToClick(onClick: () -> Unit, onLongClick: (() -> Unit)? = null, enabled: Boolean = true): Modifier =
    if (!enabled) this
    else composed {
        // Focus on press: the item lights up (border/scale) so the user sees the tap registered.
        // The detector must not restart when the item recomposes (it does, on focus), or the
        // tap in progress would be lost: keep the latest callbacks in state instead of as keys.
        val focus = remember { FocusRequester() }
        val click by rememberUpdatedState(onClick)
        val longClick by rememberUpdatedState(onLongClick)
        val hasLongClick = onLongClick != null
        focusRequester(focus)
            .pointerInput(Unit) {
                // Mouse hover highlights the item, the same way the remote's focus does.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Enter && event.changes.any { it.type == PointerType.Mouse }) {
                            runCatching { focus.requestFocus() }
                        }
                    }
                }
            }
            .pointerInput(hasLongClick) {
            detectTapGestures(
                onPress = { runCatching { focus.requestFocus() } },
                onTap = { click() },
                onLongPress = if (hasLongClick) { _ -> longClick?.invoke() } else null,
            )
        }
    }

/** TV Material Button that also responds to mouse and touch. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = androidx.tv.material3.Button(
    onClick = onClick,
    modifier = modifier.tapToClick(onClick, enabled = enabled),
    enabled = enabled,
    content = content,
)

/** TV Material OutlinedButton that also responds to mouse and touch. */
@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) = androidx.tv.material3.OutlinedButton(
    onClick = onClick,
    modifier = modifier.tapToClick(onClick, enabled = enabled),
    enabled = enabled,
    content = content,
)

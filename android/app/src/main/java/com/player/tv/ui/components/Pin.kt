package com.player.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.player.tv.R
import com.player.tv.ui.theme.Brand

private const val PIN_LENGTH = 4

/**
 * 4-digit PIN entry with an on-screen keypad (remote, mouse, touch) that also accepts the
 * remote's number keys. [onSubmit] returns false to reject the PIN and ask again.
 */
@Composable
fun PinDialog(title: String, onDismiss: () -> Unit, onSubmit: (String) -> Boolean, message: String? = null) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val firstKey = remember { FocusRequester() }

    fun type(digit: Char) {
        if (pin.length >= PIN_LENGTH) return
        error = false
        pin += digit
        if (pin.length == PIN_LENGTH) {
            if (!onSubmit(pin)) {
                error = true
                pin = ""
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            colors = SurfaceDefaults.colors(containerColor = Brand.SurfaceHigh),
            modifier = Modifier.widthIn(max = 420.dp).onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val digit = DIGIT_KEYS[event.key]
                when {
                    digit != null -> { type(digit); true }
                    event.key == Key.Backspace || event.key == Key.Delete -> { pin = pin.dropLast(1); true }
                    else -> false
                }
            },
        ) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Icon(AppIcons.Lock, contentDescription = null, tint = Brand.Primary, modifier = Modifier.size(32.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, color = Brand.TextPrimary, textAlign = TextAlign.Center)
                Text(
                    if (error) stringResource(R.string.pin_wrong) else message ?: stringResource(R.string.pin_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (error) Brand.Error else Brand.TextSecondary,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    repeat(PIN_LENGTH) { i ->
                        val filled = i < pin.length
                        Box(
                            Modifier.size(18.dp).clip(CircleShape)
                                .background(if (filled) Brand.Primary else Color.Transparent)
                                .border(2.dp, if (error) Brand.Error else Brand.TextSecondary, CircleShape)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("123", "456", "789").forEachIndexed { row, digits ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            digits.forEachIndexed { col, d ->
                                KeypadKey(d.toString(), modifier = if (row == 0 && col == 0) Modifier.focusRequester(firstKey) else Modifier) { type(d) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KeypadKey(icon = AppIcons.Backspace) { pin = pin.dropLast(1); error = false }
                        KeypadKey("0") { type('0') }
                        KeypadKey(icon = AppIcons.Back) { onDismiss() }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { firstKey.requestWhenReady() }
}

@Composable
private fun KeypadKey(text: String? = null, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.size(width = 76.dp, height = 52.dp).tapToClick(onClick),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Brand.Surface,
            contentColor = Brand.TextPrimary,
            focusedContainerColor = Brand.Primary,
            focusedContentColor = Color.White,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            else Text(text.orEmpty(), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

private val DIGIT_KEYS = mapOf(
    Key.Zero to '0', Key.One to '1', Key.Two to '2', Key.Three to '3', Key.Four to '4',
    Key.Five to '5', Key.Six to '6', Key.Seven to '7', Key.Eight to '8', Key.Nine to '9',
    Key.NumPad0 to '0', Key.NumPad1 to '1', Key.NumPad2 to '2', Key.NumPad3 to '3', Key.NumPad4 to '4',
    Key.NumPad5 to '5', Key.NumPad6 to '6', Key.NumPad7 to '7', Key.NumPad8 to '8', Key.NumPad9 to '9',
)

/** Shown instead of a locked category's content. */
@Composable
fun LockedView(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(AppIcons.Lock, contentDescription = null, tint = Brand.TextSecondary, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.parental_locked_category), color = Brand.TextPrimary, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Button(onClick = onUnlock) { Text(stringResource(R.string.parental_unlock)) }
    }
}

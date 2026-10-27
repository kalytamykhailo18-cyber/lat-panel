package com.player.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.player.tv.ui.theme.Brand

/**
 * Text field that behaves well with a TV remote: D-pad focus lands on a button-like surface,
 * OK opens the on-screen keyboard, and Done/Back returns to D-pad navigation. A plain
 * BasicTextField would grab the D-pad and pop the keyboard just by being focused.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {},
    surfaceFocusRequester: FocusRequester = remember { FocusRequester() },
    openEditor: Boolean = false,        // set true to open the keyboard from outside (e.g. "Next")
    onEditorOpened: () -> Unit = {},
) {
    var editing by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    // Text typed on a hardware keyboard while only the surface was focused; kept, not lost.
    var pendingText by remember { mutableStateOf("") }
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(10.dp)

    fun startEditing() {
        hadFocus = false
        editing = true
    }

    fun stopEditing() {
        editing = false
        keyboard?.hide()
        surfaceFocusRequester.safeRequest()
    }

    LaunchedEffect(openEditor) {
        if (openEditor) {
            startEditing()
            onEditorOpened()
        }
    }

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Brand.TextSecondary, modifier = Modifier.padding(bottom = 6.dp))
        if (editing) {
            // Cursor at the end of existing text, so typing continues it instead of prefixing it.
            var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
            if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
            BasicTextField(
                value = field,
                onValueChange = {
                    field = it
                    if (it.text != value) onValueChange(it.text)
                },
                singleLine = true,
                textStyle = TextStyle(color = Brand.TextPrimary, fontSize = 18.sp),
                cursorBrush = SolidColor(Brand.Primary),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onAny = { stopEditing(); onImeAction() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(fieldFocus)
                    // Close when focus moves away. The first event arrives before the field has
                    // focus, so only a loss after having had it counts (else it closes instantly).
                    .onFocusChanged {
                        if (it.isFocused) hadFocus = true
                        else if (hadFocus && editing) editing = false
                    }
                    .onPreviewKeyEvent {
                        if (it.key == Key.Back && it.type == KeyEventType.KeyUp) { stopEditing(); true } else false
                    }
                    .border(BorderStroke(2.dp, Brand.Primary), shape)
                    .background(Brand.Surface, shape)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
            LaunchedEffect(Unit) {
                if (pendingText.isNotEmpty()) {
                    onValueChange(value + pendingText)
                    pendingText = ""
                }
                fieldFocus.requestWhenReady()
                keyboard?.show()
            }
        } else {
            Surface(
                onClick = ::startEditing,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(surfaceFocusRequester)
                    .tapToClick(::startEditing)
                    // A hardware keyboard (PC, TV box) can type straight away: the first letter opens the field.
                    .onPreviewKeyEvent {
                        val char = it.nativeKeyEvent.unicodeChar
                        if (it.type == KeyEventType.KeyDown && char != 0 && !Character.isISOControl(char)) {
                            pendingText += String(Character.toChars(char))
                            startEditing()
                            true
                        } else false
                    },
                shape = ClickableSurfaceDefaults.shape(shape),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Brand.Surface,
                    focusedContainerColor = Brand.SurfaceHigh,
                    contentColor = Brand.TextPrimary,
                    focusedContentColor = Brand.TextPrimary,
                ),
                border = ClickableSurfaceDefaults.border(
                    focusedBorder = androidx.tv.material3.Border(BorderStroke(2.dp, Brand.Primary), shape = shape),
                ),
            ) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    val shown = if (password) "•".repeat(value.length) else value
                    Text(
                        shown.ifEmpty { placeholder },
                        color = if (value.isEmpty()) Color(0xFF6B748A) else Brand.TextPrimary,
                        fontSize = 18.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

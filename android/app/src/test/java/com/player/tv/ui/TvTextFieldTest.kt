package com.player.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.tv.material3.Text
import com.player.tv.App
import com.player.tv.ui.components.TvTextField
import com.player.tv.ui.theme.PlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The field must accept text after a tap/click, and from a hardware keyboard while focused. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land", application = App::class)
class TvTextFieldTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private var text = ""

    private fun setUp() = compose.setContent {
        PlayerTheme {
            var value by remember { mutableStateOf("") }
            TvTextField(value = value, onValueChange = { value = it; text = it }, label = "Buscar", placeholder = "Escribe aquí")
            Text("value=$value")
        }
    }

    @Test fun clickThenType() {
        setUp()
        compose.onNodeWithText("Escribe aquí").performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextInput("el")
        compose.waitForIdle()
        assertEquals("el", text)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun hardwareKeyboardWhileFocused() {
        setUp()
        // Only focused (remote/keyboard navigation), not opened: typing must still land in the field.
        compose.onNodeWithText("Escribe aquí").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.waitForIdle()
        compose.onNodeWithText("Escribe aquí").performKeyInput { pressKey(Key.A) }
        compose.waitForIdle()
        assertEquals("a", text)
        compose.onNode(hasSetTextAction()).performTextInput("b")
        compose.waitForIdle()
        assertEquals("ab", text)
    }
}

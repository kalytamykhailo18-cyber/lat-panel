package com.player.tv.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.player.tv.App
import com.player.tv.data.Category
import com.player.tv.data.Graph
import com.player.tv.data.Parental
import com.player.tv.data.allowed
import com.player.tv.ui.components.PinDialog
import com.player.tv.ui.components.PosterGrid
import com.player.tv.ui.components.PosterItem
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.player.tv.ui.player.PlayItem
import com.player.tv.ui.player.VodActions
import com.player.tv.ui.player.VodControls
import com.player.tv.ui.player.ChannelListPanel
import com.player.tv.ui.player.LiveActions
import com.player.tv.ui.player.LiveBanner
import com.player.tv.ui.player.MenuPanel
import com.player.tv.ui.player.MenuState
import com.player.tv.ui.player.PlayerMenu
import com.player.tv.ui.player.VodState
import com.player.tv.ui.theme.PlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "es-w960dp-h540dp-land", application = App::class)
class NewUiTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class Recorder : VodActions {
        val calls = mutableListOf<String>()
        override fun playPause() { calls += "playPause" }
        override fun seekBy(deltaMs: Long) { calls += "seekBy:$deltaMs" }
        override fun seekTo(positionMs: Long) { calls += "seekTo" }
        override fun openMenu(menu: PlayerMenu) { calls += "menu:$menu" }
        override fun chooseOption(index: Int) { calls += "choose:$index" }
        override fun next() { calls += "next" }
    }

    @Test fun vodControls_remote() {
        val actions = Recorder()
        compose.setContent { PlayerTheme { VodControls(PlayItem("Peli", "", isLive = false), VodState(durationMs = 600_000, playing = true), null, actions) } }
        compose.waitForIdle()
        // Opens on Play/Pause: OK pauses.
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(listOf("playPause"), actions.calls)
        // Up reaches the progress bar; Left / Right seek.
        compose.onRoot().performKeyInput { pressKey(Key.DirectionUp); pressKey(Key.DirectionRight); pressKey(Key.DirectionLeft) }
        compose.waitForIdle()
        assertEquals(listOf("playPause", "seekBy:10000", "seekBy:-10000"), actions.calls)
    }

    @Test fun vodControls_subtitlesButtonAlwaysThere() {
        val actions = Recorder()
        compose.setContent { PlayerTheme { VodControls(PlayItem("Peli", "", isLive = false), VodState(durationMs = 600_000), null, actions) } }
        compose.onNodeWithText("Subtítulos").performClick()
        compose.onNodeWithText("Audio").performClick()
        assertEquals(listOf("menu:SUBTITLES", "menu:AUDIO"), actions.calls)
    }

    @Test fun vodControls_menuChoice() {
        val actions = Recorder()
        val menu = MenuState(PlayerMenu.SUBTITLES, listOf("Desactivados", "Español", "Inglés"), 0)
        compose.setContent { PlayerTheme { MenuPanel(menu, actions::chooseOption) } }
        compose.onNodeWithText("Español").performClick()
        assertEquals(listOf("choose:1"), actions.calls)
    }

    private class LiveRecorder : LiveActions {
        val calls = mutableListOf<String>()
        override fun openChannels() { calls += "channels" }
        override fun toggleFavorite() { calls += "favorite" }
        override fun playChannel(index: Int) { calls += "play:$index" }
        override fun focusChannel(index: Int) { calls += "focus:$index" }
        override fun openMenu(menu: PlayerMenu) { calls += "menu:$menu" }
        override fun chooseOption(index: Int) { calls += "choose:$index" }
    }

    private val channels = (1..6).map { PlayItem("Canal $it", "u$it", isLive = true, number = it, epgStreamId = it) }

    @Test fun liveControls_everyOptionReachable() {
        val actions = LiveRecorder()
        compose.setContent { PlayerTheme { LiveBanner(channels[0], emptyList(), favorite = false, menu = null, actions = actions) } }
        compose.waitForIdle()
        // Focus starts on Canales: OK opens the list; Right moves along the bar.
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionCenter) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionCenter) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionCenter) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(listOf("channels", "menu:AUDIO", "menu:SUBTITLES", "menu:SCREEN", "favorite"), actions.calls)
    }

    @Test fun channelList_startsOnPlayingChannel_okPlaysFocused() {
        val actions = LiveRecorder()
        compose.setContent { PlayerTheme { ChannelListPanel(channels, playingIndex = 2, favorites = setOf(4), nowShowing = emptyMap(), actions = actions) } }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown); pressKey(Key.DirectionCenter) }
        compose.waitForIdle()
        assertEquals(listOf("focus:2", "focus:3", "play:3"), actions.calls)
    }

    @Test fun posterGrid_neverShowsACutRow() {
        val items = (1..40).map { PosterItem(it, "Peli $it", null, "★ 7") }
        compose.setContent { PlayerTheme { Box(Modifier.size(700.dp, 460.dp).testTag("area")) { PosterGrid(items, compact = false, onOpen = {}) } } }
        compose.waitForIdle()
        compose.onAllNodesWithText("Peli 1")[0].requestFocus()
        repeat(6) { step ->
            compose.waitForIdle()
            val area = compose.onNodeWithTag("area").getUnclippedBoundsInRoot()
            items.forEach { item ->
                val nodes = compose.onAllNodesWithText(item.title).fetchSemanticsNodes()
                nodes.forEach { node ->
                    val b = node.boundsInRoot
                    if (b.height > 0f && b.bottom > area.top.value && b.top < area.bottom.value) {
                        assertTrue("step $step: ${item.title} cut (${b.top}..${b.bottom} in ${area.top}..${area.bottom})",
                            b.top >= area.top.value - 0.5f && b.bottom <= area.bottom.value + 0.5f)
                    }
                }
            }
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        }
        compose.waitForIdle()
        // It really scrolled: the first row is gone from view, a later one is shown.
        val area = compose.onNodeWithTag("area").getUnclippedBoundsInRoot()
        val first = compose.onAllNodesWithText("Peli 1").fetchSemanticsNodes().firstOrNull()?.boundsInRoot
        assertTrue("first row still visible", first == null || first.bottom <= area.top.value + 0.5f)
    }

    @Test fun pin_keypadAndRemoteDigits() {
        var submitted = ""
        compose.setContent { PlayerTheme { PinDialog("PIN", onDismiss = {}, onSubmit = { submitted = it; true }) } }
        compose.waitForIdle()
        compose.onNodeWithText("1").performClick()
        compose.onNodeWithText("2").performClick()
        compose.onNodeWithText("3").performClick()
        compose.onNodeWithText("4").performClick()
        compose.waitForIdle()
        assertEquals("1234", submitted)
    }

    @Test fun parental_locksAndFilters() {
        val store = Graph.store
        store.parentalEnabled = true
        store.parentalPin = "4321"
        store.lockedCategories = emptySet()
        Parental.relock()
        Parental.lockAdult(mapOf(Parental.Type.VOD to listOf(Category("1", "Acción"), Category("2", "ADULTOS +18"), Category("3", "XXX"))))
        assertFalse(Parental.isLocked(Parental.Type.VOD, "1"))
        assertTrue(Parental.isLocked(Parental.Type.VOD, "2"))
        assertTrue(Parental.isLocked(Parental.Type.VOD, "3"))
        assertFalse("other catalogs keep their own ids", Parental.isLocked(Parental.Type.LIVE, "2"))

        val items = listOf("a" to "1", "b" to "2", "c" to null)
        assertEquals(listOf("a", "c"), items.allowed(Parental.Type.VOD) { it.second }.map { it.first })

        assertFalse(Parental.checkPin("0000"))
        assertTrue(Parental.checkPin("4321"))
        Parental.unlock(Parental.Type.VOD, "2")
        assertFalse(Parental.isLocked(Parental.Type.VOD, "2"))

        store.parentalEnabled = false
        assertFalse("off: nothing locked", Parental.isLocked(Parental.Type.VOD, "3"))
        store.lockedCategories = emptySet()
    }
}

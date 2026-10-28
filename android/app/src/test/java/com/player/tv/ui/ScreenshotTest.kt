package com.player.tv.ui

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.player.tv.App
import com.player.tv.data.Account
import com.player.tv.data.EpgEntry
import com.player.tv.data.Graph
import com.player.tv.data.RecentItem
import com.player.tv.Routes
import com.player.tv.ui.activation.ActivationScreen
import com.player.tv.ui.components.MainShell
import com.player.tv.ui.components.PinDialog
import com.player.tv.ui.guide.GuideScreen
import com.player.tv.ui.player.VodActions
import com.player.tv.ui.player.LiveActions
import com.player.tv.ui.player.MenuState
import com.player.tv.ui.player.PlayerMenu
import com.player.tv.ui.player.VodState
import com.player.tv.ui.home.HomeScreen
import com.player.tv.ui.live.FAVORITES
import com.player.tv.ui.live.LiveScreen
import com.player.tv.ui.live.LiveViewModel
import com.player.tv.ui.login.ManualLoginScreen
import com.player.tv.ui.player.OverlayState
import com.player.tv.ui.player.PlayItem
import com.player.tv.ui.player.PlayerOverlay
import com.player.tv.ui.search.SearchScreen
import com.player.tv.ui.search.SearchViewModel
import com.player.tv.ui.series.SeriesDetailScreen
import com.player.tv.ui.series.SeriesScreen
import com.player.tv.ui.settings.SettingsScreen
import com.player.tv.ui.theme.PlayerTheme
import com.player.tv.ui.vod.MovieDetailScreen
import com.player.tv.ui.vod.MoviesScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.Duration

/**
 * Renders every screen at Fire TV resolution (960x540 dp, xhdpi = 1920x1080 px) to PNG files.
 * Needs a provider at XTREAM_URL and runs only when SCREENSHOT_DIR is set:
 *   SCREENSHOT_DIR=build/screens XTREAM_URL=http://127.0.0.1:8098 ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "es-w960dp-h540dp-land-xhdpi", application = App::class)
class ScreenshotTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir = System.getenv("SCREENSHOT_DIR")?.let(::File)
    private val xtreamUrl = System.getenv("XTREAM_URL") ?: "http://127.0.0.1:8098"
    private val user = System.getenv("XTREAM_USER") ?: "u1"
    private val pass = System.getenv("XTREAM_PASS") ?: "p1"

    @Before
    fun setUp() {
        assumeTrue("SCREENSHOT_DIR not set", outDir != null)
        // Other screen sizes, e.g. SCREEN=w400dp-h860dp-port (phone) or w1280dp-h800dp-land (tablet).
        System.getenv("SCREEN")?.let {
            org.robolectric.RuntimeEnvironment.setQualifiers("+$it")
            compose.activityRule.scenario.recreate()
        }
        // No crossfade and synchronous delivery: animations do not run on Robolectric's paused clock.
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(context)
                .okHttpClient(Graph.http)
                .crossfade(false)
                .allowHardware(false)
                .dispatcher(kotlinx.coroutines.Dispatchers.IO)
                .build()
        )
    }

    private fun signIn() = runBlocking { Graph.signIn(Account(xtreamUrl, user, pass, Account.Source.DEVICE)) }

    private fun shoot(name: String, waitFor: String?, settleMs: Long = 2500, keys: List<Key> = emptyList(), content: @Composable () -> Unit) {
        // Screens have endless loops (clock, panel polling), so the test clock is driven by hand
        // instead of waiting for the UI to become idle.
        compose.mainClock.autoAdvance = false
        compose.setContent { PlayerTheme { content() } }
        fun step() {
            compose.mainClock.advanceTimeBy(100)
            // Robolectric's SystemClock is frozen otherwise: image crossfades and posted work would never finish.
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
            Thread.sleep(50)
        }
        if (waitFor != null) {
            val deadline = System.currentTimeMillis() + 20_000
            while (compose.onAllNodesWithText(waitFor, substring = true).fetchSemanticsNodes().isEmpty()) {
                check(System.currentTimeMillis() < deadline) { "'$waitFor' never appeared on $name" }
                step()
            }
        }
        // Remote control presses, e.g. move focus from the categories to the channel list.
        keys.forEach { key ->
            repeat(5) { step() }
            compose.onRoot().performKeyInput { pressKey(key) }
        }
        // Let images arrive and focus animations finish.
        val end = System.currentTimeMillis() + settleMs
        while (System.currentTimeMillis() < end) step()
        // Draw the window directly: captureToImage() waits for a frame the paused clock never produces.
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(android.graphics.Canvas(bitmap)) }
        outDir!!.mkdirs()
        File(outDir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun s01_activation() = shoot("01_activation", "ID") {
        ActivationScreen(onMessages = {}, onSignedIn = {}, onManualLogin = {})
    }

    @Test fun s02_manualLogin() = shoot("02_manual_login", "Ingreso manual") {
        ManualLoginScreen(onSignedIn = {}, onUseDeviceActivation = {})
    }

    @Composable
    private fun Shell(route: String, content: @Composable () -> Unit) = MainShell(route, onNavigate = {}) { content() }

    @Test fun s03_home() { signIn(); shoot("03_home", "Películas recientes", settleMs = 6000) { Shell(Routes.HOME) { HomeScreen(onOpen = {}) } } }

    @Test fun s03d_homeNextRow() { signIn(); shoot("03d_home_next_row", "Películas recientes", settleMs = 6000, keys = listOf(Key.DirectionDown)) { Shell(Routes.HOME) { HomeScreen(onOpen = {}) } } }

    @Test fun s03c_homeMenuOpen() {
        signIn()
        shoot("03c_home_menu", "Películas recientes", settleMs = 4000, keys = listOf(Key.DirectionLeft)) { Shell(Routes.HOME) { HomeScreen(onOpen = {}) } }
    }

    @Test fun s03b_homeContinueWatching() {
        signIn()
        val account = Graph.requireXtream().account
        listOf(
            RecentItem("movie:1001", "Película 1", "https://picsum.photos/seed/back1001/640/360", "movie/1001", 2_400_000, 6_720_000),
            RecentItem("series:501", "Serie 1 · T1 E3", "https://picsum.photos/seed/sback501/640/360", "series/501", 600_000, 2_700_000),
            RecentItem("movie:1004", "Película 4", "https://picsum.photos/seed/back1004/640/360", "movie/1004", 5_000_000, 6_000_000),
        ).reversed().forEach { Graph.store.saveRecent(account, it) }
        shoot("03b_home_continue", "Continuar", settleMs = 5000) { Shell(Routes.HOME) { HomeScreen(onOpen = {}) } }
        listOf("movie:1001", "series:501", "movie:1004").forEach { Graph.store.removeRecent(account, it) }
    }

    @Test fun s04_live() { signIn(); shoot("04_live", System.getenv("LIVE_WAIT") ?: "Canal", settleMs = 6000, keys = listOf(Key.DirectionRight)) { Shell(Routes.LIVE) { LiveScreen() } } }

    @Test fun s05_movies() { signIn(); shoot("05_movies", "★", settleMs = 5000) { Shell(Routes.MOVIES) { MoviesScreen(onOpenMovie = {}) } } }

    @Test fun s06_movieDetail() {
        signIn()
        val id = runBlocking { Graph.requireXtream().movies(null).first().streamId }
        shoot("06_movie_detail", "Reproducir", settleMs = 8000) { MovieDetailScreen(movieId = id) }
    }

    @Test fun s07_series() { signIn(); shoot("07_series", "★", settleMs = 5000) { Shell(Routes.SERIES) { SeriesScreen(onOpenSeries = {}) } } }

    @Test fun s08_seriesDetail() {
        signIn()
        val id = runBlocking { Graph.requireXtream().series(null).first().seriesId }
        shoot("08_series_detail", "Temporada", settleMs = 8000) { SeriesDetailScreen(seriesId = id) }
    }

    @Test fun s09_search() {
        signIn()
        val vm = SearchViewModel().apply { onQuery(System.getenv("SEARCH_QUERY") ?: "Canal 1") }
        shoot("09_search", System.getenv("SEARCH_WAIT") ?: "TV en vivo", settleMs = 5000) { Shell(Routes.SEARCH) { SearchScreen(onOpenMovie = {}, onOpenSeries = {}, vm = vm) } }
    }

    @Test fun s10_settings() { signIn(); shoot("10_settings", "Ajustes") { Shell(Routes.SETTINGS) { SettingsScreen(onSwitchToManual = {}, onSwitchToDevice = {}) } } }

    @Test fun s10b_settingsPlayer() {
        signIn()
        shoot("10b_settings_player", "Ajustes", keys = listOf(Key.DirectionDown)) { Shell(Routes.SETTINGS) { SettingsScreen(onSwitchToManual = {}, onSwitchToDevice = {}) } }
    }

    @Test fun s10c_settingsParental() {
        signIn()
        shoot("10c_settings_parental", "Ajustes", keys = listOf(Key.DirectionDown, Key.DirectionDown)) { Shell(Routes.SETTINGS) { SettingsScreen(onSwitchToManual = {}, onSwitchToDevice = {}) } }
    }

    @Test fun s17_guide() { signIn(); shoot("17_guide", "Bunny", settleMs = 6000) { Shell(Routes.GUIDE) { GuideScreen() } } }

    private val noActions = object : VodActions {
        override fun playPause() {}
        override fun seekBy(deltaMs: Long) {}
        override fun seekTo(positionMs: Long) {}
        override fun openMenu(menu: PlayerMenu) {}
        override fun chooseOption(index: Int) {}
        override fun next() {}
    }

    private fun vodShot(name: String, vod: VodState, menu: MenuState? = null) = shoot(name, "Subtítulos") {
        Box(Modifier.fillMaxSize().background(Color(0xFF2A3A4A))) {
            PlayerOverlay(OverlayState(item = PlayItem("Tears of Steel", "", isLive = false, subtitle = "Película · 2012"), controlsVisible = true, vod = vod, menu = menu), vodActions = noActions)
        }
    }

    @Test fun s18_vodControls() = vodShot("18_vod_controls", VodState(positionMs = 312_000, durationMs = 734_000, bufferedMs = 420_000, playing = true, hasNext = true))

    @Test fun s19_vodSubtitles() = vodShot("19_vod_subtitles", VodState(positionMs = 312_000, durationMs = 734_000, playing = true),
        MenuState(PlayerMenu.SUBTITLES, listOf("Desactivados", "Español", "Inglés", "Francés", "Alemán", "Ruso"), 1, "Tu elección se mantiene al cambiar de canal o de episodio."))

    private val noLive = object : LiveActions {
        override fun openChannels() {}
        override fun toggleFavorite() {}
        override fun playChannel(index: Int) {}
        override fun focusChannel(index: Int) {}
        override fun openMenu(menu: PlayerMenu) {}
        override fun chooseOption(index: Int) {}
    }

    private val liveQueue = listOf("Canal 5", "Canal 7 HD", "Deportes 1", "Noticias 24", "Cine Clásico", "Música Latina", "Infantil", "Documentales", "Cocina TV", "Viajes HD")
        .mapIndexed { i, name -> PlayItem(name, "", isLive = true, number = i + 5, epgStreamId = i, logo = "https://picsum.photos/seed/ch$i/200/120") }

    private fun liveState(controls: Boolean = false, list: Boolean = false, menu: MenuState? = null): OverlayState {
        val now = System.currentTimeMillis() / 1000
        return OverlayState(
            item = liveQueue[1], index = 1, controlsVisible = controls, channelList = list, menu = menu, favorites = setOf(1, 3),
            listNow = mapOf(1 to "Noticiero central"),
            epg = listOf(EpgEntry("Noticiero central", null, now - 1200, now + 2400), EpgEntry("Documental: Océanos", null, now + 2400, now + 6000)),
        )
    }

    private fun liveShot(name: String, waitFor: String, state: OverlayState) = shoot(name, waitFor) {
        Box(Modifier.fillMaxSize().background(Color(0xFF203040))) { PlayerOverlay(state, liveQueue, liveActions = noLive) }
    }

    @Test fun s21_liveControls() = liveShot("21_live_controls", "Canales", liveState(controls = true))

    @Test fun s22_liveChannelList() = liveShot("22_live_channel_list", "Deportes", liveState(list = true))

    @Test fun s23_liveSubtitles() = liveShot("23_live_subtitles", "Desactivados",
        liveState(controls = true, menu = MenuState(PlayerMenu.SUBTITLES, listOf("Desactivados", "Español (CC1)"), 0, "Tu elección se mantiene al cambiar de canal o de episodio.")))

    @Test fun s24_vodScreen() = vodShot("24_vod_screen", VodState(positionMs = 312_000, durationMs = 734_000, playing = true),
        MenuState(PlayerMenu.SCREEN, listOf("Ajustar a la pantalla", "Llenar la pantalla", "Zoom"), 0, "Se recuerda para todos los canales y películas."))

    @Test fun s20_pin() = shoot("20_pin", "PIN") { Box(Modifier.fillMaxSize().background(Color(0xFF0B0E14))) { PinDialog("Ingresa el PIN", onDismiss = {}, onSubmit = { false }) } }

    @Test fun s11_playerBanner() {
        val now = System.currentTimeMillis() / 1000
        val state = OverlayState(
            item = PlayItem("Canal 7 HD", "", isLive = true, number = 7, logo = "https://picsum.photos/seed/logo7/200/120"),
            showBanner = true,
            epg = listOf(EpgEntry("Noticiero central", null, now - 1200, now + 2400), EpgEntry("Documental: Océanos", null, now + 2400, now + 6000)),
        )
        shoot("11_player_banner", "Noticiero") {
            Box(Modifier.fillMaxSize().background(Color(0xFF203040))) { PlayerOverlay(state) }
        }
    }

    @Test fun s12_searchHint() {
        signIn()
        val vm = SearchViewModel()
        shoot("12_search_hint", "2 letras") { SearchScreen(onOpenMovie = {}, onOpenSeries = {}, vm = vm) }
    }

    @Test fun s13_liveFavoritesEmpty() {
        signIn()
        val vm = LiveViewModel().apply { select(FAVORITES) }
        shoot("13_live_favorites_empty", "favoritos", settleMs = 3000) { LiveScreen(vm = vm) }
    }

    @Test fun s14_seriesResume() {
        signIn()
        val info = runBlocking { Graph.requireXtream().seriesInfo(Graph.requireXtream().series(null).first().seriesId) }
        val ep = info.seasons.first().episodes.first()
        Graph.store.saveResumePosition("episode:${ep.id}", 5 * 60_000L)
        shoot("14_series_resume", "Continuar desde", settleMs = 6000) { SeriesDetailScreen(seriesId = info.series.seriesId) }
        Graph.store.saveResumePosition("episode:${ep.id}", 0)
    }

    private fun overlayShot(name: String, waitFor: String, state: OverlayState) =
        shoot(name, waitFor) { Box(Modifier.fillMaxSize().background(Color(0xFF101010))) { PlayerOverlay(state) } }

    @Test fun s15_playerLoading() = overlayShot(
        "15_player_loading", "Cargando",
        OverlayState(item = PlayItem("Big Buck Bunny", "", isLive = false), loading = true, notice = "Continuando desde 5:00", controlsVisible = true),
    )

    @Test fun s16_playerError() = overlayShot(
        "16_player_error", "Reintentar",
        OverlayState(item = PlayItem("Big Buck Bunny", "", isLive = false), failed = true),
    )
}

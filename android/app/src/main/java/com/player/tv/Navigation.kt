package com.player.tv

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.player.tv.ui.activation.ActivationScreen
import com.player.tv.ui.components.MainShell
import com.player.tv.ui.guide.GuideScreen
import com.player.tv.ui.home.HomeScreen
import com.player.tv.ui.live.LiveScreen
import com.player.tv.ui.login.ManualLoginScreen
import com.player.tv.ui.search.SearchScreen
import com.player.tv.ui.series.SeriesDetailScreen
import com.player.tv.ui.series.SeriesScreen
import com.player.tv.ui.settings.SettingsScreen
import com.player.tv.ui.splash.SplashScreen
import com.player.tv.ui.vod.MovieDetailScreen
import com.player.tv.ui.vod.MoviesScreen

object Routes {
    const val SPLASH = "splash"
    const val ACTIVATION = "activation"
    const val LOGIN = "login"
    const val HOME = "home"
    const val LIVE = "live"
    const val MOVIES = "movies"
    const val MOVIE = "movie/{id}"
    const val SERIES = "series"
    const val SERIES_DETAIL = "series/{id}"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val GUIDE = "guide"

    fun movie(id: Int) = "movie/$id"
    fun seriesDetail(id: Int) = "series/$id"
}

@Composable
fun AppNavHost(nav: NavHostController, app: AppViewModel) {
    // Replaces the whole back stack, e.g. after signing in or out.
    fun resetTo(route: String) = nav.navigate(route) { popUpTo(0) { inclusive = true } }

    // Side menu: sections sit directly above Home, so Back from any section returns Home.
    fun openSection(route: String) {
        if (route == Routes.HOME) nav.popBackStack(Routes.HOME, inclusive = false)
        else nav.navigate(route) {
            popUpTo(Routes.HOME)
            launchSingleTop = true
        }
    }

    // Forward: new screen fades in sliding slightly from the right; back: the reverse.
    NavHost(
        navController = nav,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn(tween(300, delayMillis = 60)) + slideInHorizontally(tween(320)) { it / 20 } },
        exitTransition = { fadeOut(tween(180)) },
        popEnterTransition = { fadeIn(tween(300, delayMillis = 60)) + slideInHorizontally(tween(320)) { -it / 20 } },
        popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(240)) { it / 20 } },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(app, onResolved = ::resetTo)
        }
        composable(Routes.ACTIVATION) {
            ActivationScreen(
                onMessages = app::offerMessages,
                onSignedIn = { resetTo(Routes.HOME) },
                onManualLogin = { nav.navigate(Routes.LOGIN) },
            )
        }
        composable(Routes.LOGIN) {
            ManualLoginScreen(
                onSignedIn = { resetTo(Routes.HOME) },
                onUseDeviceActivation = { resetTo(Routes.ACTIVATION) },
            )
        }
        composable(Routes.HOME) {
            DisposableEffect(Unit) {
                app.startMonitor()
                onDispose { }
            }
            MainShell(Routes.HOME, ::openSection) { HomeScreen(onOpen = { nav.navigate(it) }) }
        }
        composable(Routes.LIVE) { MainShell(Routes.LIVE, ::openSection) { LiveScreen() } }
        composable(Routes.GUIDE) { MainShell(Routes.GUIDE, ::openSection) { GuideScreen() } }
        composable(Routes.MOVIES) { MainShell(Routes.MOVIES, ::openSection) { MoviesScreen(onOpenMovie = { nav.navigate(Routes.movie(it)) }) } }
        composable(Routes.MOVIE, arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
            MovieDetailScreen(movieId = entry.arguments!!.getInt("id"))
        }
        composable(Routes.SERIES) { MainShell(Routes.SERIES, ::openSection) { SeriesScreen(onOpenSeries = { nav.navigate(Routes.seriesDetail(it)) }) } }
        composable(Routes.SERIES_DETAIL, arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
            SeriesDetailScreen(seriesId = entry.arguments!!.getInt("id"))
        }
        composable(Routes.SEARCH) {
            MainShell(Routes.SEARCH, ::openSection) {
                SearchScreen(
                    onOpenMovie = { nav.navigate(Routes.movie(it)) },
                    onOpenSeries = { nav.navigate(Routes.seriesDetail(it)) },
                )
            }
        }
        composable(Routes.SETTINGS) {
            MainShell(Routes.SETTINGS, ::openSection) { SettingsScreen(
                onSwitchToManual = {
                    app.stopMonitor()
                    nav.navigate(Routes.LOGIN)
                },
                onSwitchToDevice = {
                    app.stopMonitor()
                    resetTo(Routes.ACTIVATION)
                },
            ) }
        }
    }
}

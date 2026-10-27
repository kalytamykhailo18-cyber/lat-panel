package com.player.tv.ui.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.player.tv.AppViewModel
import com.player.tv.R
import com.player.tv.ui.components.AppBackground
import com.player.tv.ui.components.Logo
import com.player.tv.ui.components.Spinner
import com.player.tv.ui.components.enterAnimation
import com.player.tv.ui.components.screenInfo
import com.player.tv.ui.theme.Brand
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(app: AppViewModel, onResolved: (String) -> Unit) {
    var slow by remember { mutableStateOf(false) }
    AppBackground {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Logo(Modifier.size(if (screenInfo().shortHeight) 80.dp else 120.dp).enterAnimation(0))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, color = Brand.TextPrimary, modifier = Modifier.enterAnimation(1))
            Spinner()
            Text(stringResource(R.string.splash_connecting), style = MaterialTheme.typography.bodyLarge, color = Brand.TextSecondary)
            if (slow) {
                Text(
                    stringResource(R.string.splash_slow),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Brand.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
    LaunchedEffect(Unit) { onResolved(app.resolveStartRoute()) }
    LaunchedEffect(Unit) {
        delay(6_000)
        slow = true   // a bad network can take up to ~15 s; say so instead of looking frozen
    }
}

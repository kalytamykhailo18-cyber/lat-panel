package com.player.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.player.tv.ui.components.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.player.tv.data.Graph
import com.player.tv.data.PanelMessage
import com.player.tv.ui.components.requestWhenReady
import com.player.tv.ui.theme.Brand
import com.player.tv.ui.theme.PlayerTheme

class MainActivity : ComponentActivity() {

    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // After process death the in-memory session is gone, so never restore a deep screen:
        // always start again from the splash screen, which signs in again.
        super.onCreate(if (Graph.xtream == null) null else savedInstanceState)

        setContent {
            PlayerTheme {
                val navController = rememberNavController()
                AppNavHost(navController, appViewModel)

                val messages by appViewModel.messages.collectAsStateWithLifecycle()
                messages.firstOrNull()?.let { MessageDialog(it, onDismiss = { appViewModel.dismissMessage(it) }) }

                LaunchedEffect(Unit) {
                    appViewModel.deactivated.collect {
                        navController.navigate(Routes.ACTIVATION) { popUpTo(0) { inclusive = true } }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageDialog(message: PanelMessage, onDismiss: () -> Unit) {
    val focus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = Brand.SurfaceHigh),
            modifier = Modifier.widthIn(max = 620.dp),
        ) {
            Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(message.title, style = MaterialTheme.typography.headlineSmall, color = Brand.TextPrimary)
                Text(message.body, style = MaterialTheme.typography.bodyLarge, color = Brand.TextSecondary)
                Button(onClick = onDismiss, modifier = Modifier.focusRequester(focus)) { Text(stringResource(R.string.ok)) }
            }
        }
    }
    LaunchedEffect(message.id) { focus.requestWhenReady() }
}

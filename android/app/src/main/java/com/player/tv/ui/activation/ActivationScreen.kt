package com.player.tv.ui.activation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import com.player.tv.ui.components.OutlinedButton
import androidx.tv.material3.Text
import com.player.tv.R
import com.player.tv.data.AppException
import com.player.tv.data.Graph
import com.player.tv.data.PanelMessage
import com.player.tv.data.PanelStatus
import com.player.tv.data.SessionStore
import com.player.tv.ui.components.AdaptivePanes
import com.player.tv.ui.components.AppBackground
import com.player.tv.ui.components.screenInfo
import com.player.tv.ui.components.Logo
import com.player.tv.ui.components.Spinner
import com.player.tv.ui.components.enterAnimation
import com.player.tv.ui.components.errorMessage
import com.player.tv.ui.components.requestWhenReady
import com.player.tv.ui.theme.Brand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ActivationViewModel : ViewModel() {

    data class State(
        val status: PanelStatus.Status? = null,
        val error: AppException.Kind? = null,
        val signingIn: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    val signedIn = MutableSharedFlow<Unit>(replay = 1)
    val messages = MutableSharedFlow<List<PanelMessage>>(extraBufferCapacity = 4)

    init {
        Graph.store.mode = SessionStore.Mode.DEVICE
        Graph.signOut()
    }

    /**
     * Asks the panel for this device's status until an admin activates it, then signs in.
     * Runs in the screen's composition scope, so it pauses while the manual login is open.
     */
    suspend fun pollUntilActive() {
        while (true) {
            var wait = 10
            try {
                val status = Graph.panel.check()
                wait = status.pollSeconds
                messages.tryEmit(status.messages)
                val account = status.account
                if (status.status == PanelStatus.Status.ACTIVE && account != null) {
                    _state.value = State(status.status, signingIn = true)
                    Graph.signIn(account)
                    signedIn.emit(Unit)
                    return
                }
                _state.value = State(status.status)
            } catch (e: CancellationException) {
                throw e
            } catch (e: AppException) {
                _state.value = _state.value.copy(error = e.kind, signingIn = false)
            }
            delay(wait * 1000L)
        }
    }
}

@Composable
fun ActivationScreen(
    onMessages: (List<PanelMessage>) -> Unit,
    onSignedIn: () -> Unit,
    onManualLogin: () -> Unit,
    vm: ActivationViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val buttonFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { vm.signedIn.collect { onSignedIn() } }
    LaunchedEffect(Unit) { vm.pollUntilActive() }
    LaunchedEffect(Unit) { vm.messages.collect(onMessages) }
    LaunchedEffect(Unit) { buttonFocus.requestWhenReady() }

    val screen = screenInfo()
    AppBackground {
        AdaptivePanes(firstWeight = 0.9f, secondWeight = 1.1f, first = { paneModifier ->
            Column(paneModifier.enterAnimation(0), verticalArrangement = Arrangement.spacedBy(if (screen.compact) 14.dp else 20.dp)) {
                Logo(Modifier.size(if (screen.compact) 64.dp else 88.dp))
                Text(
                    stringResource(R.string.activation_title),
                    style = if (screen.compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                    color = Brand.TextPrimary,
                )
                Text(
                    stringResource(R.string.activation_instructions),
                    style = MaterialTheme.typography.titleMedium,
                    color = Brand.TextSecondary,
                    modifier = Modifier.widthIn(max = 520.dp),
                )
                OutlinedButton(onClick = onManualLogin, modifier = Modifier.focusRequester(buttonFocus)) {
                    Text(stringResource(R.string.activation_manual))
                }
            }
        }, second = { paneModifier ->
            Column(
                paneModifier
                    .enterAnimation(1)
                    .background(Brand.Surface, RoundedCornerShape(24.dp))
                    .border(BorderStroke(1.dp, Brand.SurfaceHigh), RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(stringResource(R.string.activation_device_id).uppercase(), style = MaterialTheme.typography.labelLarge, color = Brand.TextSecondary)
                Text(
                    Graph.identity.deviceId,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (screen.compact) 26.sp else 32.sp,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    softWrap = false,
                    color = Brand.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                val statusText = when {
                    state.signingIn -> stringResource(R.string.activation_signing_in)
                    state.error != null -> errorMessage(state.error!!)
                    state.status == PanelStatus.Status.DISABLED -> stringResource(R.string.activation_disabled)
                    state.status == PanelStatus.Status.EXPIRED -> stringResource(R.string.activation_expired)
                    else -> stringResource(R.string.activation_waiting)
                }
                val problem = state.error != null || state.status == PanelStatus.Status.DISABLED || state.status == PanelStatus.Status.EXPIRED
                val statusColor = if (problem) Brand.Error else Brand.Primary
                // Moving indicator + explanation, so the user knows the app is still working on it.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Spinner(size = 22.dp, strokeWidth = 3.dp, color = statusColor)
                    Text(statusText, style = MaterialTheme.typography.titleMedium, color = statusColor, textAlign = TextAlign.Center)
                }
                Text(
                    stringResource(if (state.error != null) R.string.activation_retrying else R.string.activation_auto_check),
                    style = MaterialTheme.typography.bodySmall,
                    color = Brand.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        })
    }
}

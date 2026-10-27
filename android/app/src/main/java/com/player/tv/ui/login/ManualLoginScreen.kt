package com.player.tv.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.player.tv.ui.components.Button
import androidx.tv.material3.MaterialTheme
import com.player.tv.ui.components.OutlinedButton
import androidx.tv.material3.Text
import com.player.tv.R
import com.player.tv.data.AppException
import com.player.tv.data.Graph
import com.player.tv.ui.components.AdaptivePanes
import com.player.tv.ui.components.AppBackground
import com.player.tv.ui.components.screenInfo
import com.player.tv.ui.components.BackButton
import com.player.tv.ui.components.Pill
import com.player.tv.ui.components.Spinner
import com.player.tv.ui.components.enterAnimation
import com.player.tv.ui.components.Logo
import com.player.tv.ui.components.TvTextField
import com.player.tv.ui.components.errorMessage
import com.player.tv.ui.components.requestWhenReady
import com.player.tv.ui.theme.Brand
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manual Xtream login. The customer types only username and password; the app finds the
 * operator's DNS that accepts them (see Graph.signInManual). There is no server field: the app
 * works exclusively with the operator's servers.
 */
class ManualLoginViewModel : ViewModel() {

    data class State(
        val username: String = "",
        val password: String = "",
        val busy: Boolean = false,
        val progress: Pair<Int, Int>? = null,   // (current, total) while searching the DNS list
        val error: AppException.Kind? = null,
        val validationError: Int? = null,
    )

    private val _state = MutableStateFlow(
        Graph.store.manualAccount?.let { State(it.username, it.password) } ?: State()
    )
    val state: StateFlow<State> = _state
    val signedIn = MutableSharedFlow<Unit>(replay = 1)

    // Editing a field clears the previous error: it no longer describes what is on screen.
    fun onUsername(value: String) = _state.update { it.copy(username = value.trim(), error = null, validationError = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value.trim(), error = null, validationError = null) }

    fun submit() {
        val s = _state.value
        if (s.busy) return
        if (s.username.isBlank() || s.password.isBlank()) {
            _state.update { it.copy(validationError = R.string.login_missing_fields, error = null) }
            return
        }
        _state.update { it.copy(busy = true, error = null, validationError = null, progress = null) }
        viewModelScope.launch {
            try {
                Graph.signInManual(s.username, s.password) { current, total ->
                    _state.update { it.copy(progress = current to total) }
                }
                signedIn.emit(Unit)
            } catch (e: AppException) {
                _state.update { it.copy(busy = false, progress = null, error = e.kind) }
            }
        }
    }
}

@Composable
fun ManualLoginScreen(
    onSignedIn: () -> Unit,
    onUseDeviceActivation: () -> Unit,
    vm: ManualLoginViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val firstFocus = remember { FocusRequester() }
    var openPassword by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.signedIn.collect { onSignedIn() } }
    LaunchedEffect(Unit) { firstFocus.requestWhenReady() }

    val screen = screenInfo()
    AppBackground {
        AdaptivePanes(secondWeight = 1.2f, topInset = 88.dp, first = { paneModifier ->
            Column(paneModifier.enterAnimation(0), verticalArrangement = Arrangement.spacedBy(if (screen.compact) 12.dp else 20.dp)) {
                Logo(Modifier.size(if (screen.compact) 56.dp else 88.dp))
                Text(
                    stringResource(R.string.login_title),
                    style = if (screen.compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displaySmall,
                    color = Brand.TextPrimary,
                )
                Text(stringResource(R.string.login_subtitle), style = MaterialTheme.typography.titleMedium, color = Brand.TextSecondary)
            }
        }, second = { paneModifier ->
            Column(paneModifier.widthIn(max = 560.dp).enterAnimation(1), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                TvTextField(
                    value = state.username,
                    onValueChange = vm::onUsername,
                    label = stringResource(R.string.login_username),
                    surfaceFocusRequester = firstFocus,
                    onImeAction = { openPassword = true },   // "Next" goes straight to typing the password
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvTextField(
                        value = state.password,
                        onValueChange = vm::onPassword,
                        label = stringResource(R.string.login_password),
                        password = !showPassword,
                        imeAction = ImeAction.Done,
                        onImeAction = vm::submit,
                        openEditor = openPassword,
                        onEditorOpened = { openPassword = false },
                        modifier = Modifier.weight(1f),
                    )
                    Pill(
                        text = stringResource(if (showPassword) R.string.login_hide_password else R.string.login_show_password),
                        selected = showPassword,
                        onClick = { showPassword = !showPassword },
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                val message = state.validationError?.let { stringResource(it) } ?: state.error?.let { errorMessage(it) }
                val progress = state.progress
                when {
                    message != null -> Text(message, color = Brand.Error, style = MaterialTheme.typography.bodyLarge)
                    state.busy -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spinner(size = 22.dp, strokeWidth = 3.dp)
                        Text(
                            if (progress != null) stringResource(R.string.login_searching, progress.first, progress.second)
                            else stringResource(R.string.login_connecting),
                            color = Brand.Primary,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = vm::submit, enabled = !state.busy) {
                        Text(stringResource(if (state.busy) R.string.activation_signing_in else R.string.login_button))
                    }
                    OutlinedButton(onClick = onUseDeviceActivation) { Text(stringResource(R.string.login_use_device)) }
                }
            }
        })
        // Drawn last so it stays on top of the scrolling content.
        BackButton(Modifier.align(Alignment.TopStart).padding(if (screen.compact) 16.dp else 24.dp))
    }
}

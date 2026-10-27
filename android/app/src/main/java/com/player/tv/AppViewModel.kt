package com.player.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.player.tv.data.Account
import com.player.tv.data.AppException
import com.player.tv.data.Graph
import com.player.tv.data.PanelMessage
import com.player.tv.data.PanelStatus
import com.player.tv.data.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Activity-wide state: decides the first screen, keeps checking the panel while the user is
 * watching (deactivation, new credentials, messages) and queues panel messages for display.
 */
class AppViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<PanelMessage>>(emptyList())
    val messages: StateFlow<List<PanelMessage>> = _messages

    /** Emitted when the panel no longer allows this device; the UI returns to the activation screen. */
    private val _deactivated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val deactivated: SharedFlow<Unit> = _deactivated

    private var monitor: Job? = null

    /** Returns the route of the first screen. */
    suspend fun resolveStartRoute(): String {
        val store = Graph.store

        if (store.mode == SessionStore.Mode.MANUAL) {
            val manual = store.manualAccount
            if (manual != null) {
                return try {
                    try {
                        Graph.signIn(manual)
                    } catch (e: AppException) {
                        // The account may have moved to another DNS: search the list again.
                        Graph.signInManual(manual.username, manual.password)
                    }
                    Routes.HOME
                } catch (e: AppException) {
                    if (e.kind == AppException.Kind.AUTH) {
                        Routes.LOGIN
                    } else {
                        Graph.useCached(manual)   // provider unreachable right now; screens offer retry
                        Routes.HOME
                    }
                }
            }
            store.mode = SessionStore.Mode.DEVICE
        }

        return try {
            val status = Graph.panel.check()
            offerMessages(status.messages)
            if (status.status == PanelStatus.Status.ACTIVE && status.account != null) {
                Graph.signIn(status.account)
                Routes.HOME
            } else {
                Routes.ACTIVATION
            }
        } catch (e: AppException) {
            // Panel unreachable: start with the last credentials it gave us, if any.
            val cached = store.deviceAccount
            if (e.kind == AppException.Kind.KEY_MISMATCH || cached == null) return Routes.ACTIVATION
            try {
                Graph.signIn(cached)
                Routes.HOME
            } catch (signInError: AppException) {
                if (signInError.kind == AppException.Kind.AUTH) {
                    Routes.ACTIVATION
                } else {
                    Graph.useCached(cached)
                    Routes.HOME
                }
            }
        }
    }

    fun startMonitor() {
        if (monitor?.isActive == true) return
        monitor = viewModelScope.launch {
            while (true) {
                val wait = try {
                    val status = Graph.panel.check()
                    handle(status)
                    status.pollSeconds
                } catch (e: CancellationException) {
                    throw e
                } catch (e: AppException) {
                    if (e.kind == AppException.Kind.KEY_MISMATCH && deviceSessionActive()) {
                        Graph.signOut()
                        _deactivated.tryEmit(Unit)
                        return@launch
                    }
                    120
                }
                delay(wait * 1000L)
            }
        }
    }

    fun stopMonitor() {
        monitor?.cancel()
        monitor = null
    }

    fun offerMessages(incoming: List<PanelMessage>) {
        val fresh = incoming.filter { !Graph.store.isMessageSeen(it.id) }
        if (fresh.isEmpty()) return
        _messages.update { queued -> queued + fresh.filter { m -> queued.none { it.id == m.id } } }
    }

    fun dismissMessage(message: PanelMessage) {
        Graph.store.markMessageSeen(message.id)
        _messages.update { it - message }
    }

    private fun deviceSessionActive() =
        Graph.store.mode == SessionStore.Mode.DEVICE && Graph.xtream?.account?.source == Account.Source.DEVICE

    private fun handle(status: PanelStatus) {
        offerMessages(status.messages)
        if (!deviceSessionActive()) return   // manual login is independent of the panel status
        val account = status.account
        if (status.status != PanelStatus.Status.ACTIVE || account == null) {
            stopMonitor()
            Graph.signOut()
            _deactivated.tryEmit(Unit)
        } else if (account != Graph.xtream?.account) {
            // Admin changed DNS or credentials: switch silently, verify on next screen load.
            Graph.store.deviceAccount = account
            Graph.useCached(account)
            viewModelScope.launch { runCatching { Graph.signIn(account) } }
        }
    }
}
